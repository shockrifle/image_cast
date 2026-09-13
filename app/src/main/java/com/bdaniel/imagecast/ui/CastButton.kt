package com.bdaniel.imagecast.ui

import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

/**
 * The standard cast icon. It is a plain Android View, so it is bridged into
 * Compose and given an AppCompat theme (which the media router widgets and
 * their device-picker dialog require).
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { _ ->
            val themed = ContextThemeWrapper(
                context,
                androidx.appcompat.R.style.Theme_AppCompat_NoActionBar,
            )
            MediaRouteButton(themed).also { button ->
                runCatching { CastButtonFactory.setUpMediaRouteButton(context, button) }
            }
        },
    )
}
