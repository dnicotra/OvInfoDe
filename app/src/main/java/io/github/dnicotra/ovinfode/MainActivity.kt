package io.github.dnicotra.ovinfode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.dnicotra.ovinfode.ui.AppNavHost
import io.github.dnicotra.ovinfode.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as OvInfoApp).container
        setContent {
            AppTheme {
                AppNavHost(container)
            }
        }
    }
}
