package com.bdaniel.imagecast

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.api.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Owns the cast session and speaks the small "remote rendering" protocol:
 * the picture is streamed once to the receiver, then only the zoom/pan state is
 * pushed as the user manipulates it. The receiver draws the image itself at its
 * own native resolution, so nothing is ever downscaled to the phone viewport.
 */
class CastController(
    context: Context,
    private val scope: CoroutineScope,
) {

    enum class Connection { UNAVAILABLE, DISCONNECTED, CONNECTING, CONNECTED }

    /** Progress of the one-off image upload; null when nothing is in flight. */
    data class Transfer(val sentBytes: Int, val totalBytes: Int) {
        val fraction: Float get() = if (totalBytes == 0) 0f else sentBytes.toFloat() / totalBytes
    }

    private val appContext = context.applicationContext

    private val _connection = MutableStateFlow(Connection.DISCONNECTED)
    val connection: StateFlow<Connection> = _connection.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _transfer = MutableStateFlow<Transfer?>(null)
    val transfer: StateFlow<Transfer?> = _transfer.asStateFlow()

    private val _imageReady = MutableStateFlow(false)

    /** True once the receiver holds the current picture and can be transformed. */
    val imageReady: StateFlow<Boolean> = _imageReady.asStateFlow()

    private var castContext: CastContext? = null
    private var session: CastSession? = null

    /** The picture that should currently be on screen, kept so we can re-send on reconnect. */
    private var payload: CastPayload? = null
    private var payloadGeneration = 0
    private var uploadJob: Job? = null

    /**
     * Latest transform the user has produced. A single consumer coroutine drains
     * this, which conflates bursts of gesture updates into "send the newest one
     * as soon as the channel is free".
     */
    private val pendingTransform = Channel<ViewTransform>(Channel.CONFLATED)
    private var lastSentTransform: ViewTransform = ViewTransform.IDENTITY

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(s: CastSession) = onConnecting(s)
        override fun onSessionStarted(s: CastSession, sessionId: String) = onConnected(s)
        override fun onSessionStartFailed(s: CastSession, error: Int) = onDisconnected()
        override fun onSessionEnding(s: CastSession) = Unit
        override fun onSessionEnded(s: CastSession, error: Int) = onDisconnected()
        override fun onSessionResuming(s: CastSession, sessionId: String) = onConnecting(s)
        override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) = onConnected(s)
        override fun onSessionResumeFailed(s: CastSession, error: Int) = onDisconnected()
        override fun onSessionSuspended(s: CastSession, reason: Int) = onDisconnected()
    }

    init {
        castContext = runCatching { CastContext.getSharedInstance(appContext) }
            .onFailure { Log.w(TAG, "Cast is unavailable on this device", it) }
            .getOrNull()

        val manager = castContext?.sessionManager
        if (manager == null) {
            _connection.value = Connection.UNAVAILABLE
        } else {
            manager.addSessionManagerListener(sessionListener, CastSession::class.java)
            manager.currentCastSession?.let { if (it.isConnected) onConnected(it) }
        }
        scope.launch { transformPump() }
    }

    fun release() {
        castContext?.sessionManager
            ?.removeSessionManagerListener(sessionListener, CastSession::class.java)
    }

    // ---------------------------------------------------------------- session

    private fun onConnecting(s: CastSession) {
        _connection.value = Connection.CONNECTING
        _deviceName.value = s.castDevice?.friendlyName
    }

    private fun onConnected(s: CastSession) {
        session = s
        _connection.value = Connection.CONNECTED
        _deviceName.value = s.castDevice?.friendlyName
        attachChannel(s)
        // A fresh (or resumed) receiver knows nothing; push the current picture.
        payload?.let { startUpload(it) }
    }

    private fun onDisconnected() {
        uploadJob?.cancel()
        session = null
        _connection.value = if (castContext == null) Connection.UNAVAILABLE else Connection.DISCONNECTED
        _deviceName.value = null
        _transfer.value = null
        _imageReady.value = false
    }

    private fun attachChannel(s: CastSession) {
        val callback = Cast.MessageReceivedCallback { _: CastDevice?, _: String, message: String ->
            Log.d(TAG, "receiver -> $message")
        }
        runCatching { s.setMessageReceivedCallbacks(NAMESPACE, callback) }
            .onFailure { Log.w(TAG, "Could not open the cast channel", it) }
    }

    // ------------------------------------------------------------------ image

    /** Streams [newPayload] to the receiver, replacing whatever it is showing. */
    fun setImage(newPayload: CastPayload?) {
        payload = newPayload
        payloadGeneration++
        _imageReady.value = false
        uploadJob?.cancel()
        _transfer.value = null

        if (newPayload == null) {
            scope.launch { send(JSONObject().put("t", MSG_CLEAR)) }
            return
        }
        if (_connection.value == Connection.CONNECTED) startUpload(newPayload)
    }

    private fun startUpload(toSend: CastPayload) {
        val generation = payloadGeneration
        uploadJob?.cancel()
        uploadJob = scope.launch {
            val chunks = withContext(Dispatchers.Default) { chunk(toSend.bytes) }
            val total = toSend.bytes.size
            _transfer.value = Transfer(0, total)

            val started = send(
                JSONObject()
                    .put("t", MSG_BEGIN)
                    .put("gen", generation)
                    .put("mime", toSend.mimeType)
                    .put("w", toSend.width)
                    .put("h", toSend.height)
                    .put("chunks", chunks.size)
                    .put("bytes", total)
            )
            if (!started) {
                _transfer.value = null
                return@launch
            }

            chunks.forEachIndexed { index, encoded ->
                if (!isActive || generation != payloadGeneration) return@launch
                val sent = send(
                    JSONObject()
                        .put("t", MSG_CHUNK)
                        .put("gen", generation)
                        .put("i", index)
                        .put("d", encoded)
                )
                if (!sent) {
                    Log.w(TAG, "Chunk $index failed; aborting transfer")
                    _transfer.value = null
                    return@launch
                }
                _transfer.value = Transfer(
                    sentBytes = minOf(total, (index + 1) * CHUNK_BYTES),
                    totalBytes = total,
                )
            }

            if (generation != payloadGeneration) return@launch
            send(JSONObject().put("t", MSG_END).put("gen", generation))
            _transfer.value = null
            _imageReady.value = true
            // Make sure the receiver framing matches the phone right away.
            pendingTransform.trySend(lastSentTransform)
        }
    }

    private fun chunk(bytes: ByteArray): List<String> {
        val out = ArrayList<String>((bytes.size / CHUNK_BYTES) + 1)
        var offset = 0
        while (offset < bytes.size) {
            val length = minOf(CHUNK_BYTES, bytes.size - offset)
            out += Base64.encodeToString(bytes, offset, length, Base64.NO_WRAP)
            offset += length
        }
        return out
    }

    // -------------------------------------------------------------- transform

    /** Queues the newest zoom/pan state; intermediate values are dropped. */
    fun setTransform(transform: ViewTransform) {
        lastSentTransform = transform
        pendingTransform.trySend(transform)
    }

    private suspend fun transformPump() {
        for (transform in pendingTransform) {
            if (_connection.value != Connection.CONNECTED || !_imageReady.value) continue
            send(
                JSONObject()
                    .put("t", MSG_TRANSFORM)
                    .put("s", transform.scale.toDouble())
                    .put("x", transform.offsetX.toDouble())
                    .put("y", transform.offsetY.toDouble())
            )
        }
    }

    // --------------------------------------------------------------- plumbing

    /** Sends one message and suspends until the receiver has acknowledged it. */
    private suspend fun send(message: JSONObject): Boolean {
        val current = session ?: return false
        return suspendCancellableCoroutine { continuation ->
            runCatching {
                current.sendMessage(NAMESPACE, message.toString())
                    .setResultCallback { status: Status ->
                        if (continuation.isActive) continuation.resume(status.isSuccess)
                    }
            }.onFailure {
                Log.w(TAG, "sendMessage failed", it)
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    companion object {
        const val NAMESPACE = "urn:x-cast:com.bdaniel.imagecast"

        private const val TAG = "CastController"

        /**
         * Cast custom messages are capped at 64 KB. ~32 KB of binary becomes
         * ~43.7 KB of base64, which leaves comfortable room for the JSON frame.
         * The size must be a multiple of 3 so that every chunk except the last
         * encodes without padding and the receiver can simply concatenate them.
         */
        private const val CHUNK_BYTES = 32_766

        private const val MSG_BEGIN = "begin"
        private const val MSG_CHUNK = "chunk"
        private const val MSG_END = "end"
        private const val MSG_TRANSFORM = "tf"
        private const val MSG_CLEAR = "clear"
    }
}
