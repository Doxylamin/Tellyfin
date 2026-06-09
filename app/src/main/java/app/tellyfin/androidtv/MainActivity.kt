package app.tellyfin.androidtv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.tellyfin.androidtv.ui.login.LoginScreen
import app.tellyfin.androidtv.ui.login.LoginViewModel
import app.tellyfin.androidtv.ui.player.PlayerScreen
import app.tellyfin.androidtv.ui.player.PlayerViewModel
import app.tellyfin.androidtv.ui.theme.TellyfinTheme

class MainActivity : ComponentActivity() {

    private val loginViewModel: LoginViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TellyfinTheme {
                val loginState by loginViewModel.state.collectAsState()
                if (loginState.isLoggedIn) {
                    PlayerScreen(
                        viewModel = playerViewModel,
                        onLogOut = { loginViewModel.logOut() }
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val isLoggedIn = loginViewModel.state.value.isLoggedIn
            if (isLoggedIn && playerViewModel.handleKeyEvent(event.keyCode)) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
