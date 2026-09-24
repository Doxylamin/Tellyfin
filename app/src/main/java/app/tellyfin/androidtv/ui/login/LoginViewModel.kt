package app.tellyfin.androidtv.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.tellyfin.androidtv.data.api.JellyfinRepository
import app.tellyfin.androidtv.data.api.candidateServerUrls
import app.tellyfin.androidtv.data.api.isReportable
import app.tellyfin.androidtv.data.api.splashscreenUrl
import app.tellyfin.androidtv.data.api.toUserMessage
import app.tellyfin.androidtv.data.prefs.PreferencesRepository
import app.tellyfin.androidtv.diagnostics.CrashReporting
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class LoginStep { SERVER, SIGN_IN }
enum class SignInMethod { PASSWORD, QUICK_CONNECT }

data class LoginUiState(
    val step: LoginStep = LoginStep.SERVER,
    val signInMethod: SignInMethod = SignInMethod.PASSWORD,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val quickConnectCode: String? = null,
    val splashscreenUrl: String? = null,
    val loginDisclaimer: String? = null,
    val serverName: String? = null
)

// Jellyfin expires a Quick Connect secret 5 minutes after it's issued.
private const val QUICK_CONNECT_TIMEOUT_MS = 5 * 60 * 1000L
private const val QUICK_CONNECT_POLL_INTERVAL_MS = 2_000L

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = PreferencesRepository(application)
    val jellyfinRepo = JellyfinRepository(application)

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var quickConnectJob: Job? = null

    init {
        checkExistingSession()
    }

    private fun checkExistingSession() {
        viewModelScope.launch {
            breadcrumb("checkExistingSession() started")
            val token = prefsRepo.accessToken.first()
            val url = prefsRepo.serverUrl.first()
            val uid = prefsRepo.userId.first()
            breadcrumb("checkExistingSession() prefs read, hasSession=${token != null && url != null}")
            if (token != null && url != null) {
                jellyfinRepo.configure(url, token, uid ?: "")
                breadcrumb("checkExistingSession() configure() done, setting isLoggedIn=true")
                _state.value = _state.value.copy(isLoggedIn = true)
            }
        }
    }

    private fun breadcrumb(message: String) = CrashReporting.addBreadcrumb(message, "login")

    fun onServerUrlChange(value: String) { _state.value = _state.value.copy(serverUrl = value, error = null) }
    fun onUsernameChange(value: String) { _state.value = _state.value.copy(username = value, error = null) }
    fun onPasswordChange(value: String) { _state.value = _state.value.copy(password = value, error = null) }

    /** Confirms the server is reachable, picks up its branding if any, then moves to sign-in. */
    fun continueFromServer() {
        val s = _state.value
        if (s.serverUrl.isBlank()) {
            _state.value = s.copy(error = "Server URL is required")
            return
        }
        viewModelScope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            var lastError: Exception? = null
            for (candidate in candidateServerUrls(s.serverUrl)) {
                try {
                    val info = jellyfinRepo.probeServer(candidate)
                    val branding = runCatching { jellyfinRepo.getBrandingOptions(candidate) }.getOrNull()
                    _state.value = _state.value.copy(
                        isLoading = false,
                        step = LoginStep.SIGN_IN,
                        serverUrl = candidate,
                        serverName = info.serverName?.takeIf { it.isNotBlank() },
                        splashscreenUrl = branding
                            ?.takeIf { it.splashscreenEnabled }
                            ?.let { splashscreenUrl(candidate) },
                        loginDisclaimer = branding?.loginDisclaimer?.takeIf { it.isNotBlank() }
                    )
                    if (_state.value.signInMethod == SignInMethod.QUICK_CONNECT) startQuickConnect()
                    return@launch
                } catch (e: Exception) {
                    lastError = e
                }
            }
            // Every candidate failed the same way (DNS/refused/etc. don't vary by scheme or
            // port), so the last attempt's mapped message already describes the real problem.
            _state.value = _state.value.copy(isLoading = false, error = lastError?.toReportedMessage())
        }
    }

    /** Back to the server step. Keeps the entered address so the user isn't retyping it. */
    fun backToServer() {
        cancelQuickConnect()
        _state.value = _state.value.copy(
            step = LoginStep.SERVER,
            password = "",
            error = null,
            splashscreenUrl = null,
            loginDisclaimer = null,
            serverName = null
        )
    }

    fun selectSignInMethod(method: SignInMethod) {
        if (_state.value.signInMethod == method) return
        _state.value = _state.value.copy(signInMethod = method, error = null)
        if (method == SignInMethod.QUICK_CONNECT) startQuickConnect() else cancelQuickConnect()
    }

    fun login() {
        val s = _state.value
        if (s.username.isBlank()) {
            _state.value = s.copy(error = "Username is required")
            return
        }
        viewModelScope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            breadcrumb("login() calling authenticate()")
            try {
                val (url, token, userId) = jellyfinRepo.authenticate(s.serverUrl, s.username, s.password)
                breadcrumb("login() authenticate() succeeded, saving session")
                prefsRepo.saveSession(url, token, userId, s.username)
                breadcrumb("login() saveSession() done, calling configure()")
                jellyfinRepo.configure(url, token, userId)
                breadcrumb("login() configure() done, setting isLoggedIn=true")
                _state.value = _state.value.copy(isLoading = false, isLoggedIn = true)
            } catch (e: Exception) {
                breadcrumb("login() failed: ${e.message}")
                _state.value = _state.value.copy(isLoading = false, error = e.toReportedMessage())
            }
        }
    }

    /** Starts Quick Connect: gets a code, then polls until the user approves it elsewhere. */
    private fun startQuickConnect() {
        val s = _state.value
        quickConnectJob = viewModelScope.launch {
            _state.value = s.copy(isLoading = true, error = null, quickConnectCode = null)
            try {
                val initiated = jellyfinRepo.initiateQuickConnect(s.serverUrl)
                _state.value = _state.value.copy(isLoading = false, quickConnectCode = initiated.code)
                pollQuickConnect(s.serverUrl, initiated.secret)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.toReportedMessage())
            }
        }
    }

    private suspend fun pollQuickConnect(serverUrl: String, secret: String) {
        breadcrumb("pollQuickConnect() started")
        val deadline = System.currentTimeMillis() + QUICK_CONNECT_TIMEOUT_MS
        try {
            while (System.currentTimeMillis() < deadline) {
                delay(QUICK_CONNECT_POLL_INTERVAL_MS)
                val result = jellyfinRepo.getQuickConnectState(serverUrl, secret)
                if (result.authenticated) {
                    breadcrumb("pollQuickConnect() authenticated, calling authenticateWithQuickConnect()")
                    val (url, token, userId) = jellyfinRepo.authenticateWithQuickConnect(serverUrl, secret)
                    breadcrumb("pollQuickConnect() authenticateWithQuickConnect() done, saving session")
                    prefsRepo.saveSession(url, token, userId)
                    breadcrumb("pollQuickConnect() saveSession() done, calling configure()")
                    jellyfinRepo.configure(url, token, userId)
                    breadcrumb("pollQuickConnect() configure() done, setting isLoggedIn=true")
                    _state.value = _state.value.copy(quickConnectCode = null, isLoggedIn = true)
                    return
                }
            }
            breadcrumb("pollQuickConnect() code expired")
            _state.value = _state.value.copy(
                quickConnectCode = null,
                error = "Quick Connect code expired — try again"
            )
        } catch (e: Exception) {
            breadcrumb("pollQuickConnect() failed: ${e.message}")
            _state.value = _state.value.copy(quickConnectCode = null, error = e.toReportedMessage())
        }
    }

    private fun cancelQuickConnect() {
        quickConnectJob?.cancel()
        quickConnectJob = null
        _state.value = _state.value.copy(quickConnectCode = null, isLoading = false)
    }

    /**
     * Friendly message for the login screen. Only reportable errors (not a clean server-returned
     * status, e.g. wrong password) get reported, and only get a ref if something actually
     * received the report (the no-Sentry flavor's CrashReporting.captureException() is a no-op
     * returning null, so no ref appears there).
     */
    private fun Exception.toReportedMessage(): String {
        if (!isReportable()) return toUserMessage()
        val ref = CrashReporting.captureException(this)
        return if (ref != null) "${toUserMessage()} (Ref: $ref)" else toUserMessage()
    }

    fun logOut() {
        viewModelScope.launch {
            prefsRepo.clearSession()
            _state.value = LoginUiState()
        }
    }
}
