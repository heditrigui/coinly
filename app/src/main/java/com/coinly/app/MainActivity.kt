package com.coinly.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinly.app.ui.theme.CoinlyTheme

class MainActivity : ComponentActivity() {
    private val vm: FinanceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val data by vm.data.collectAsStateWithLifecycle()
            val dark = data.isDark(isSystemInDarkTheme())
            // Keep status/navigation bar icons readable when the app theme differs from the phone's.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            CoinlyTheme(dark = dark) {
                CoinlyApp(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Book any salary/rent that came due while the app was closed.
        vm.refreshRecurring()
    }
}
