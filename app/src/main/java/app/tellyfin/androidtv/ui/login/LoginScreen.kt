package app.tellyfin.androidtv.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import app.tellyfin.androidtv.ui.theme.Background
import app.tellyfin.androidtv.ui.theme.OnSurface
import app.tellyfin.androidtv.ui.theme.Purple
import app.tellyfin.androidtv.ui.theme.PurpleDim
import app.tellyfin.androidtv.ui.theme.Surface
import coil.compose.AsyncImage

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onLoginSuccess()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LoginBackground(splashscreenUrl = state.splashscreenUrl)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 72.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(72.dp)
        ) {
            BrandColumn(
                step = state.step,
                serverUrl = state.serverUrl,
                serverName = state.serverName,
                loginDisclaimer = state.loginDisclaimer,
                onChangeServer = viewModel::backToServer,
                modifier = Modifier.weight(1f)
            )

            LoginCard {
                when (state.step) {
                    LoginStep.SERVER -> ServerStep(state = state, viewModel = viewModel, focusManager = focusManager)
                    LoginStep.SIGN_IN -> SignInStep(state = state, viewModel = viewModel, focusManager = focusManager)
                }
            }
        }
    }
}

/** A soft brand-colored glow by default; the server's own splashscreen once one is known. */
@Composable
private fun LoginBackground(splashscreenUrl: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(PurpleDim.copy(alpha = 0.28f), Background)))
    ) {
        if (splashscreenUrl != null) {
            AsyncImage(
                model = splashscreenUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Scrim so the form stays readable over whatever the server's splashscreen looks like.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Background.copy(alpha = 0.72f))
            )
        }
    }
}

@Composable
private fun BrandColumn(
    step: LoginStep,
    serverUrl: String,
    serverName: String?,
    loginDisclaimer: String?,
    onChangeServer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = if (step == LoginStep.SIGN_IN && serverName != null) serverName else "Tellyfin",
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
            color = Purple
        )
        if (step == LoginStep.SERVER) {
            Text(
                text = "Live TV for Jellyfin",
                fontSize = 18.sp,
                color = OnSurface.copy(alpha = 0.7f)
            )
        } else {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Connecting to", fontSize = 13.sp, color = OnSurface.copy(alpha = 0.5f))
            Text(
                text = serverUrl,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = OnSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            GhostLink(text = "Change server", onClick = onChangeServer)

            if (loginDisclaimer != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = loginDisclaimer,
                    fontSize = 12.sp,
                    color = OnSurface.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@Composable
private fun GhostLink(text: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = if (isFocused) Purple else OnSurface.copy(alpha = 0.6f),
        modifier = Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    )
}

@Composable
private fun LoginCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .width(420.dp)
            .background(Surface.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun ServerStep(state: LoginUiState, viewModel: LoginViewModel, focusManager: FocusManager) {
    TvOutlinedTextField(
        value = state.serverUrl,
        onValueChange = viewModel::onServerUrlChange,
        label = "Server address",
        leadingIcon = Icons.Filled.Home,
        colors = fieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = {
            // Compose doesn't close the IME just because the action ran — clearing focus first
            // is what actually dismisses it (same as the wrapping field's own Back handling).
            focusManager.clearFocus(force = true)
            viewModel.continueFromServer()
        }),
        modifier = Modifier.fillMaxWidth()
    )

    ErrorText(state.error)

    if (state.isLoading) {
        CircularProgressIndicator(color = Purple)
    } else {
        Button(
            onClick = viewModel::continueFromServer,
            contentPadding = ButtonContentPadding,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue", fontSize = 16.sp)
        }
    }
}

// tv-material3's own default padding is sized for larger, icon-bearing buttons; this keeps a
// simple text button from ballooning the card.
private val ButtonContentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)

@Composable
private fun SignInStep(state: LoginUiState, viewModel: LoginViewModel, focusManager: FocusManager) {
    SignInTabs(
        selected = state.signInMethod,
        showQuickConnect = state.quickConnectAvailable,
        onSelect = viewModel::selectSignInMethod
    )

    when (state.signInMethod) {
        SignInMethod.PASSWORD -> {
            TvOutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = "Username",
                leadingIcon = Icons.Filled.Person,
                colors = fieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )
            TvOutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Password",
                leadingIcon = Icons.Filled.Lock,
                visualTransformation = PasswordVisualTransformation(),
                colors = fieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus(force = true)
                    viewModel.login()
                }),
                modifier = Modifier.fillMaxWidth()
            )

            ErrorText(state.error)

            if (state.isLoading) {
                CircularProgressIndicator(color = Purple)
            } else {
                Button(
                    onClick = viewModel::login,
                    contentPadding = ButtonContentPadding,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign in", fontSize = 16.sp)
                }
            }
        }
        SignInMethod.QUICK_CONNECT -> {
            QuickConnectPanel(code = state.quickConnectCode, isLoading = state.isLoading)
            ErrorText(state.error)
        }
    }
}

@Composable
private fun SignInTabs(selected: SignInMethod, showQuickConnect: Boolean, onSelect: (SignInMethod) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        TabItem("Sign in", selected == SignInMethod.PASSWORD) { onSelect(SignInMethod.PASSWORD) }
        if (showQuickConnect) {
            TabItem("Quick Connect", selected == SignInMethod.QUICK_CONNECT) { onSelect(SignInMethod.QUICK_CONNECT) }
        }
    }
}

@Composable
private fun TabItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val tintColor = when {
        isSelected -> Purple
        isFocused -> OnSurface
        else -> OnSurface.copy(alpha = 0.5f)
    }
    // The underline is drawn inside the Text's own layout bounds (drawBehind uses that draw
    // scope's own measured size) instead of as a separate sibling sized to match it — two earlier
    // attempts at cross-element width matching (Row's default fillMaxWidth leak, then
    // IntrinsicSize.Min mismeasuring wrappable Text) both broke in different ways. This can't.
    val underlineGap = 6.dp
    val underlineWidth = 2.dp
    Text(
        text = label,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = tintColor,
        maxLines = 1,
        modifier = Modifier
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(bottom = underlineGap + underlineWidth)
            .drawBehind {
                if (isSelected) {
                    val strokeWidthPx = underlineWidth.toPx()
                    drawLine(
                        color = Purple,
                        start = Offset(0f, size.height - strokeWidthPx / 2),
                        end = Offset(size.width, size.height - strokeWidthPx / 2),
                        strokeWidth = strokeWidthPx
                    )
                }
            }
    )
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) {
        Text(text = error, color = Color.Red, fontSize = 14.sp)
    }
}

@Composable
private fun fieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    // The outline is drawn once, by TvOutlinedTextField's wrapping Box — not here — since that
    // Box also has to show a highlight before the field is armed, when it isn't really focused.
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    focusedTextColor = OnSurface,
    unfocusedTextColor = OnSurface,
    cursorColor = Purple,
    focusedPlaceholderColor = OnSurface.copy(alpha = 0.5f),
    unfocusedPlaceholderColor = OnSurface.copy(alpha = 0.5f),
    focusedLeadingIconColor = Purple,
    unfocusedLeadingIconColor = OnSurface.copy(alpha = 0.5f),
    focusedContainerColor = Surface,
    unfocusedContainerColor = Surface
)

/**
 * An OutlinedTextField that only takes real focus (and therefore only opens the software
 * keyboard) once the user explicitly presses select on it. Compose otherwise opens the IME the
 * instant a text field gains focus, including from plain D-pad navigation — on Fire TV that
 * traps the user with a keyboard they have no way to close. D-pad arrival lands on a wrapping
 * Box instead; select moves real focus into the field, and Back moves it back out.
 */
@Composable
private fun TvOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    colors: TextFieldColors,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val focusManager = LocalFocusManager.current
    val fieldFocusRequester = remember { FocusRequester() }
    val boxInteractionSource = remember { MutableInteractionSource() }
    val fieldInteractionSource = remember { MutableInteractionSource() }
    val isBoxFocused by boxInteractionSource.collectIsFocusedAsState()
    val isFieldFocused by fieldInteractionSource.collectIsFocusedAsState()
    val isHighlighted = isBoxFocused || isFieldFocused

    Box(
        modifier = modifier
            .focusable(enabled = enabled, interactionSource = boxInteractionSource)
            .border(
                width = if (isHighlighted) 2.dp else 1.dp,
                color = if (isHighlighted) Purple else OnSurface.copy(alpha = 0.3f),
                shape = RoundedCornerShape(50)
            )
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    fieldFocusRequester.requestFocus()
                    true
                } else {
                    false
                }
            }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(label) },
            singleLine = true,
            enabled = enabled,
            shape = RoundedCornerShape(50),
            leadingIcon = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null) } },
            visualTransformation = visualTransformation,
            colors = colors,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = fieldInteractionSource,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(fieldFocusRequester)
                .onKeyEvent { event ->
                    when {
                        event.type != KeyEventType.KeyUp -> false
                        event.key == Key.Back -> {
                            focusManager.clearFocus(force = true)
                            true
                        }
                        event.key == Key.DirectionDown -> {
                            focusManager.moveFocus(FocusDirection.Down)
                            true
                        }
                        event.key == Key.DirectionUp -> {
                            focusManager.moveFocus(FocusDirection.Up)
                            true
                        }
                        else -> false
                    }
                }
        )
    }
}

@Composable
private fun QuickConnectPanel(code: String?, isLoading: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        if (code == null || isLoading) {
            CircularProgressIndicator(color = Purple)
        } else {
            Text(
                text = code,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Purple
            )
            Text(
                text = "Enter this code under your account's Quick Connect settings in Jellyfin, on a device you're already signed in on",
                fontSize = 13.sp,
                color = OnSurface.copy(alpha = 0.6f)
            )
        }
    }
}
