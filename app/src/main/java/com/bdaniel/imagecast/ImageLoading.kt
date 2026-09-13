package com.bdaniel.imagecast

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** A picture ready to be shown locally and pushed to a receiver. */
data class LoadedImage(
    val uri: Uri,
    /** Downsampled bitmap used for on-device rendering. */
    val bitmap: Bitmap,
    /** Pixel size of the original file (before downsampling). */
    val sourceWidth: Int,
    val sourceHeight: Int,
) {
    val aspectRatio: Float get() = bitmap.width.toFloat() / bitmap.height.toFloat()
}

/** Re-encoded bytes of a picture, sized for transfer over the cast channel. */
data class CastPayload(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int,
    val height: Int,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

object ImageLoading {

    /** Biggest bitmap kept in memory for the on-device preview. */
    private const val MAX_PREVIEW_DIMENSION = 4096

    /**
     * Longest edge of the copy sent to the receiver. 2560px keeps a 4K TV sharp
     * at moderate zoom while staying small enough to stream over the cast
     * channel in a couple of seconds.
     */
    private const val MAX_CAST_DIMENSION = 2560
    private const val CAST_JPEG_QUALITY = 88

    fun load(context: Context, uri: Uri): LoadedImage {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        require(srcW > 0 && srcH > 0) { "Not a decodable image: $uri" }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(srcW, srcH, MAX_PREVIEW_DIMENSION)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Could not decode image: $uri")

        val rotated = applyExifRotation(context, uri, decoded)
        // EXIF rotation can swap the reported source dimensions too.
        val swapped = rotated.width > rotated.height != srcW > srcH
        return LoadedImage(
            uri = uri,
            bitmap = rotated,
            sourceWidth = if (swapped) srcH else srcW,
            sourceHeight = if (swapped) srcW else srcH,
        )
    }

    /** Re-encodes [image] into a compact JPEG/PNG suitable for the cast channel. */
    fun encodeForCast(image: LoadedImage): CastPayload {
        val src = image.bitmap
        val longest = max(src.width, src.height)
        val scaled = if (longest > MAX_CAST_DIMENSION) {
            val factor = MAX_CAST_DIMENSION.toFloat() / longest
            Bitmap.createScaledBitmap(
                src,
                max(1, (src.width * factor).toInt()),
                max(1, (src.height * factor).toInt()),
                true,
            )
        } else {
            src
        }

        val hasAlpha = scaled.hasAlpha()
        val format = when {
            hasAlpha && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Bitmap.CompressFormat.WEBP_LOSSY
            hasAlpha -> Bitmap.CompressFormat.PNG
            else -> Bitmap.CompressFormat.JPEG
        }
        val mime = when (format) {
            Bitmap.CompressFormat.PNG -> "image/png"
            Bitmap.CompressFormat.JPEG -> "image/jpeg"
            else -> "image/webp"
        }

        val out = ByteArrayOutputStream(1 shl 20)
        scaled.compress(format, CAST_JPEG_QUALITY, out)
        val encodedWidth = scaled.width
        val encodedHeight = scaled.height
        if (scaled !== src) scaled.recycle()

        return CastPayload(out.toByteArray(), mime, encodedWidth, encodedHeight)
    }

    private fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        while (max(width, height) / sample > maxDimension) sample *= 2
        return sample
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
