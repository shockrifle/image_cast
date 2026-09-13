package com.bdaniel.imagecast.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.bdaniel.imagecast.LoadedImage
import com.bdaniel.imagecast.ViewTransform
import kotlin.math.min

/**
 * Shows [image] with pinch-zoom and free panning in every direction.
 *
 * There are deliberately no bounds on the pan and the zoom floor goes well
 * below 1, so the picture can be pushed completely off-centre or shrunk until
 * it is a small tile surrounded by background -- the same framing the receiver
 * will reproduce.
 */
@Composable
fun ZoomPanImage(
    image: LoadedImage,
    transform: ViewTransform,
    onTransformChange: (ViewTransform) -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val currentTransform by rememberUpdatedState(transform)
    val currentCallback by rememberUpdatedState(onTransformChange)

    // The "contained" size: the image scaled to fit the viewport at scale 1.
    // Everything the user does is expressed relative to this, which is what
    // lets the receiver rebuild the same framing on a different screen.
    val baseWidthPx: Float
    val baseHeightPx: Float
    if (viewport.width == 0 || viewport.height == 0) {
        baseWidthPx = 0f
        baseHeightPx = 0f
    } else {
        val fit = min(
            viewport.width.toFloat() / image.bitmap.width,
            viewport.height.toFloat() / image.bitmap.height,
        )
        baseWidthPx = image.bitmap.width * fit
        baseHeightPx = image.bitmap.height * fit
    }

    val bitmap = remember(image) { image.bitmap.asImageBitmap() }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it }
            .pointerInput(image) {
                detectTransformGestures(panZoomLock = false) { centroid, pan, zoom, _ ->
                    if (baseWidthPx <= 0f || baseHeightPx <= 0f) return@detectTransformGestures
                    val previous = currentTransform
                    val newScale = (previous.scale * zoom)
                        .coerceIn(ViewTransform.MIN_SCALE, ViewTransform.MAX_SCALE)
                    // Actual zoom factor after clamping, so the pinch stays
                    // anchored under the fingers at the limits too.
                    val applied = newScale / previous.scale

                    val centroidX = (centroid.x - size.width / 2f) / baseWidthPx
                    val centroidY = (centroid.y - size.height / 2f) / baseHeightPx

                    currentCallback(
                        ViewTransform(
                            scale = newScale,
                            offsetX = previous.offsetX * applied + centroidX * (1f - applied) +
                                pan.x / baseWidthPx,
                            offsetY = previous.offsetY * applied + centroidY * (1f - applied) +
                                pan.y / baseHeightPx,
                        )
                    )
                }
            }
            .pointerInput(image) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (baseWidthPx <= 0f || baseHeightPx <= 0f) return@detectTapGestures
                        val previous = currentTransform
                        if (previous != ViewTransform.IDENTITY) {
                            currentCallback(ViewTransform.IDENTITY)
                        } else {
                            val applied = DOUBLE_TAP_SCALE
                            val centroidX = (tap.x - size.width / 2f) / baseWidthPx
                            val centroidY = (tap.y - size.height / 2f) / baseHeightPx
                            currentCallback(
                                ViewTransform(
                                    scale = applied,
                                    offsetX = centroidX * (1f - applied),
                                    offsetY = centroidY * (1f - applied),
                                )
                            )
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (baseWidthPx > 0f && baseHeightPx > 0f) {
            Image(
                bitmap = bitmap,
                contentDescription = "Selected image",
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .size(
                        width = with(density) { baseWidthPx.toDp() },
                        height = with(density) { baseHeightPx.toDp() },
                    )
                    .graphicsLayer {
                        scaleX = transform.scale
                        scaleY = transform.scale
                        // Translation is applied after scaling, in unscaled
                        // pixels -- matching the CSS transform on the receiver.
                        translationX = transform.offsetX * baseWidthPx
                        translationY = transform.offsetY * baseHeightPx
                    },
            )
        }
    }
}

private const val DOUBLE_TAP_SCALE = 2.5f
