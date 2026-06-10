package app.tellyfin.androidtv

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import app.tellyfin.androidtv.ui.login.LoginScreen
import app.tellyfin.androidtv.ui.login.LoginViewModel
import app.tellyfin.androidtv.ui.player.PlayerScreen
import app.tellyfin.androidtv.ui.player.PlayerViewModel
import app.tellyfin.androidtv.ui.theme.TellyfinTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val loginViewModel: LoginViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    // Long-press OK/Enter → same as MENU for devices without a dedicated menu button
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var confirmLongPressed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TellyfinTheme {
                val loginState by loginViewModel.state.collectAsState()
                if (loginState.isLoggedIn) {
                    PlayerScreen(
                        viewModel = playerViewModel,
                        onLogOut = { loginViewModel.logOut() },
                        onInstallApk = { installApk(it) }
                    )
                } else {
                    LoginScreen(
                        viewModel = loginViewModel,
                        onLoginSuccess = {}
                    )
                }
            }
        }
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isLoggedIn = loginViewModel.state.value.isLoggedIn
        val isConfirm = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        event.keyCode == KeyEvent.KEYCODE_ENTER

        // Intercept OK/Enter to distinguish tap (normal action) from hold (MENU equivalent)
        if (isLoggedIn && isConfirm) {
            return when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        confirmLongPressed = false
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        longPressRunnable = Runnable {
                            confirmLongPressed = true
                            playerViewModel.handleKeyEvent(KeyEvent.KEYCODE_MENU)
                        }.also {
                            longPressHandler.postDelayed(
                                it, ViewConfiguration.getLongPressTimeout().toLong()
                            )
                        }
                    }
                    true
                }
                KeyEvent.ACTION_UP -> {
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    longPressRunnable = null
                    if (!confirmLongPressed) playerViewModel.handleKeyEvent(event.keyCode)
                    confirmLongPressed = false
                    true
                }
                else -> super.dispatchKeyEvent(event)
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN && isLoggedIn &&
            playerViewModel.handleKeyEvent(event.keyCode)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
