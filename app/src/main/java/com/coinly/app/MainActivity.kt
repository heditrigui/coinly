package com.coinly.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinly.app.ui.screens.LockScreen
import com.coinly.app.ui.theme.CoinlyTheme

class MainActivity : FragmentActivity() {
    private val vm: FinanceViewModel by viewModels()
    private lateinit var lock: AppLock

    /** Whether the user has unlocked the app on this visit. It locks again after a minute in the background. */
    private var unlocked by mutableStateOf(false)
    private var leftAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lock = AppLock(this)
        unlocked = savedInstanceState?.getBoolean("unlocked") ?: false
        leftAt = savedInstanceState?.getLong("leftAt") ?: 0L
        if (savedInstanceState == null) handle(intent)
        enableEdgeToEdge()
        val control = LockControl(
            available = lock::available,
            confirm = { title, onOk -> lock.authenticate(title) { ok -> if (ok) { unlocked = true; onOk() } } },
        )
        setContent {
            val data by vm.data.collectAsStateWithLifecycle()
            val dark = data.isDark(isSystemInDarkTheme())
            // Keep status/navigation bar icons readable when the app theme differs from the phone's.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LaunchedEffect(data.appLock) { lock.hideInRecents(data.appLock) }
            // A lock restored from a backup on a phone without a screen lock would lock the user out, so it only applies when it can be unlocked.
            val locked = data.appLock && !unlocked && lock.available()
            LaunchedEffect(locked) { if (locked) unlock() }
            CoinlyTheme(dark = dark) {
                if (locked) LockScreen(onUnlock = ::unlock) else CoinlyApp(vm, control)
            }
        }
    }

    private fun unlock() = lock.authenticate("Unlock Coinly") { ok -> if (ok) unlocked = true }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** The widget's + button opens straight into a new expense. */
    private fun handle(intent: Intent?) {
        if (intent?.action == CoinlyWidget.ACTION_ADD_EXPENSE) vm.requestQuickAdd()
    }

    override fun onStart() {
        super.onStart()
        if (unlocked && leftAt > 0 && SystemClock.elapsedRealtime() - leftAt > RELOCK_AFTER_MS) unlocked = false
    }

    override fun onResume() {
        super.onResume()
        // Book any salary/rent that came due while the app was closed.
        vm.refreshRecurring()
    }

    override fun onStop() {
        super.onStop()
        leftAt = SystemClock.elapsedRealtime()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("unlocked", unlocked)
        outState.putLong("leftAt", leftAt)
    }

    private companion object {
        /** Long enough to pick a backup file or share a CSV without unlocking again. */
        const val RELOCK_AFTER_MS = 60_000L
    }
}

/** What the settings screen needs to turn the app lock on or off: the user confirms with the lock itself. */
class LockControl(val available: () -> Boolean, val confirm: (title: String, onOk: () -> Unit) -> Unit)
