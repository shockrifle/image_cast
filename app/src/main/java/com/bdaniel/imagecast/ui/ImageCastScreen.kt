package com.bdaniel.imagecast.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdaniel.imagecast.CastController
import com.bdaniel.imagecast.MainViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCastScreen(viewModel: MainViewModel) {
    val image by viewModel.image.collectAsStateWithLifecycle()
    val transform by viewModel.transform.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val connection by viewModel.cast.connection.collectAsStateWithLifecycle()
    val deviceName by viewModel.cast.deviceName.collectAsStateWithLifecycle()
    val castTransfer by viewModel.cast.transfer.collectAsStateWithLifecycle()
    val imageReady by viewModel.cast.imageReady.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(
        // OpenDocument gives a persistable grant, so the picture survives a
        // rotation or a trip through the background.
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::pickImage) }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF101418),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                title = { Text("Image Cast") },
                actions = {
                    if (image != null) {
                        IconButton(onClick = viewModel::resetTransform) {
                            Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset zoom")
                        }
                    }
                    IconButton(onClick = { picker.launch(arrayOf("image/*")) }) {
                        Icon(Icons.Default.Image, contentDescription = "Pick an image")
                    }
                    if (connection != CastController.Connection.UNAVAILABLE) {
                        CastButton(modifier = Modifier.size(48.dp).padding(12.dp))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val current = image
                when {
                    current != null -> ZoomPanImage(
                        image = current,
                        transform = transform,
                        onTransformChange = viewModel::updateTransform,
                    )

                    loading -> CircularProgressIndicator()

                    else -> EmptyState(onPick = { picker.launch(arrayOf("image/*")) })
                }

                if (loading && image != null) {
                    CircularProgressIndicator()
                }
            }

            StatusBar(
                connection = connection,
                deviceName = deviceName,
                transfer = castTransfer,
                imageReady = imageReady,
                hasImage = image != null,
                scale = transform.scale,
            )
        }
    }
}

@Composable
private fun EmptyState(onPick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(32.dp),
    ) {
        Text(
            text = "Pick a picture, frame it with pinch and drag, then cast it.",
            color = Color(0xFFB0BEC5),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = onPick) { Text("Select an image") }
    }
}

@Composable
private fun StatusBar(
    connection: CastController.Connection,
    deviceName: String?,
    transfer: CastController.Transfer?,
    imageReady: Boolean,
    hasImage: Boolean,
    scale: Float,
) {
    val label = when (connection) {
        CastController.Connection.UNAVAILABLE -> "Casting unavailable on this device"
        CastController.Connection.DISCONNECTED -> "Not casting"
        CastController.Connection.CONNECTING -> "Connecting to ${deviceName ?: "receiver"}…"
        CastController.Connection.CONNECTED -> when {
            transfer != null -> "Sending image to ${deviceName ?: "receiver"}… " +
                "${(transfer.fraction * 100).roundToInt()}%"

            imageReady -> "Rendering on ${deviceName ?: "receiver"}"
            hasImage -> "Connected to ${deviceName ?: "receiver"}"
            else -> "Connected to ${deviceName ?: "receiver"} — pick an image"
        }
    }

    Column(modifier = Modifier.background(Color(0xFF101418)).fillMaxWidth()) {
        if (transfer != null) {
            LinearProgressIndicator(
                progress = { transfer.fraction },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = Color(0xFFB0BEC5),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (hasImage) {
                Text(
                    text = "${(scale * 100).roundToInt()}%",
                    color = Color(0xFFB0BEC5),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
