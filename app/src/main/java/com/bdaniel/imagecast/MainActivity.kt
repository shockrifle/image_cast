package com.bdaniel.imagecast

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdaniel.imagecast.ui.ImageCastScreen
import com.google.android.gms.cast.framework.CastContext

/**
 * Deliberately an AppCompatActivity rather than a plain ComponentActivity:
 * MediaRouteButton shows its device picker as a DialogFragment and casts the
 * host to FragmentActivity, so anything less crashes the moment the cast icon
 * is tapped. The same applies to the Cast SDK's route-controller dialog.
 */
class MainActivity : AppCompatActivity() {

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
