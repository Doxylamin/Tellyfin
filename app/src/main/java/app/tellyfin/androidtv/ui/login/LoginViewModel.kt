package app.tellyfin.androidtv.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.tellyfin.androidtv.data.api.JellyfinRepository
import app.tellyfin.androidtv.data.prefs.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = PreferencesRepository(application)
    val jellyfinRepo = JellyfinRepository(application)

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        checkExistingSession()
    }

    private fun checkExistingSession() {
        viewModelScope.launch {
            val token = prefsRepo.accessToken.first()
            val url = prefsRepo.serverUrl.first()
            val uid = prefsRepo.userId.first()
            if (token != null && url != null) {
                jellyfinRepo.configure(url, token, uid ?: "")
                _state.value = _state.value.copy(isLoggedIn = true)
            }
        }
    }

    fun onServerUrlChange(value: String) { _state.value = _state.value.copy(serverUrl = value, error = null) }
    fun onUsernameChange(value: String) { _state.value = _state.value.copy(username = value, error = null) }
    fun onPasswordChange(value: String) { _state.value = _state.value.copy(password = value, error = null) }

    fun login() {
        val s = _state.value
        if (s.serverUrl.isBlank() || s.username.isBlank()) {
            _state.value = s.copy(error = "Server URL and username are required")
            return
        }
        viewModelScope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            try {
                val (url, token, userId) = jellyfinRepo.authenticate(s.serverUrl, s.username, s.password)
                prefsRepo.saveSession(url, token, userId, s.username)
                jellyfinRepo.configure(url, token, userId)
                _state.value = _state.value.copy(isLoading = false, isLoggedIn = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Login failed")
            }
        }
    }

    fun logOut() {
        viewModelScope.launch {
            prefsRepo.clearSession()
            _state.value = LoginUiState()
        }
    }
}
