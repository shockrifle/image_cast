package com.bdaniel.imagecast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdaniel.imagecast.ui.ImageCastScreen
import com.google.android.gms.cast.framework.CastContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Warm up discovery so the cast button is populated when the UI appears.
        // (Volume keys are routed to the receiver by the framework itself on
        // every API level this app supports, so no key handling is needed here.)
        runCatching { CastContext.getSharedInstance(applicationContext) }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val viewModel: MainViewModel = viewModel()
                ImageCastScreen(viewModel)
            }
        }
    }
}
