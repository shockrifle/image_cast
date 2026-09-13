package com.bdaniel.imagecast

/**
 * The zoom/pan state, expressed so that it means the same thing on any display.
 *
 * The image is first laid out "contained" in the viewport (scaled so it fits
 * completely, centred). [scale] then multiplies that base size and [offsetX] /
 * [offsetY] shift it, measured in multiples of the *base* (contained) image
 * width and height.
 *
 * Because everything is relative to the image rather than to the phone's
 * pixels, the receiver can reproduce the exact same framing on a screen with a
 * different size and aspect ratio -- it just recomputes its own base fit. That
 * is what makes the TV use its whole panel instead of replaying a crop of the
 * phone's viewport.
 */
data class ViewTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    companion object {
        val IDENTITY = ViewTransform()

        /** Zoom-out far enough that the picture is a thumbnail in a sea of background. */
        const val MIN_SCALE = 0.05f
        const val MAX_SCALE = 40f
    }
}
