package app.tellyfin.androidtv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tellyfin.androidtv.R
import app.tellyfin.androidtv.ui.theme.AppColors

// Paged settings, modelled on Android TV's own: a rail of pages on the left, the selected
// page's rows on the right. Key handling lives in SettingsNavigator; this only draws state.

@Composable
fun SettingsScreen(
    state: PlayerUiState,
    context: SettingsContext,
    serverUrl: String,
    appVersion: String,
    modifier: Modifier = Modifier
) {
    val settings = state.settings
    val focusedRow = if (settings.inPane) settingsRows(settings.page, context).getOrNull(settings.row) else null

    Box(modifier = modifier.fillMaxSize().background(AppColors.Background)) {
        Row(modifier = Modifier.fillMaxSize()) {
            SettingsRail(
                selected = settings.page,
                railFocused = !settings.inPane,
                modifier = Modifier.width(320.dp).fillMaxHeight()
            )
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.06f)))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 48.dp, vertical = 40.dp)
            ) {
                Text(
                    stringResource(settings.page.titleRes),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(24.dp))
                Box(modifier = Modifier.weight(1f)) {
                    SettingsPageContent(settings.page, state, context, focusedRow, serverUrl, appVersion)
                }
                Text(
                    stringResource(if (settings.inPane) R.string.settings_hint_pane else R.string.settings_hint_rail),
                    color = AppColors.OnSurface.copy(alpha = 0.30f),
                    fontSize = 11.sp
                )
            }
        }

        settings.picker?.let { PickerDialog(it, state.prebufferEnabled) }
        settings.capture?.let { CaptureDialog(it, state.keybinds) }
    }
}

private val SettingsPage.titleRes: Int
    get() = when (this) {
        SettingsPage.STREAMING -> R.string.settings_section_streaming
        SettingsPage.KEYBINDS -> R.string.settings_section_keybinds
        SettingsPage.APP -> R.string.settings_section_app
        SettingsPage.ACCOUNT -> R.string.settings_section_account
        SettingsPage.ADVANCED -> R.string.settings_section_advanced
    }

private val SettingsPage.summaryRes: Int
    get() = when (this) {
        SettingsPage.STREAMING -> R.string.settings_summary_streaming
        SettingsPage.KEYBINDS -> R.string.settings_summary_keybinds
        SettingsPage.APP -> R.string.settings_summary_app
        SettingsPage.ACCOUNT -> R.string.settings_summary_account
        SettingsPage.ADVANCED -> R.string.settings_summary_advanced
    }

private val SettingsPage.iconRes: Int
    get() = when (this) {
        SettingsPage.STREAMING -> R.drawable.ic_settings_streaming
        SettingsPage.KEYBINDS -> R.drawable.ic_settings_remote
        SettingsPage.APP -> R.drawable.ic_settings_app
        SettingsPage.ACCOUNT -> R.drawable.ic_settings_account
        SettingsPage.ADVANCED -> R.drawable.ic_settings_advanced
    }

@Composable
private fun SettingsRail(selected: SettingsPage, railFocused: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 40.dp)) {
        Text(
            stringResource(R.string.settings),
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp, bottom = 28.dp)
        )
        SettingsPage.entries.forEach { page ->
            RailItem(page, isSelected = page == selected, isFocused = railFocused && page == selected)
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** Focused (rail has focus) = purple highlight; selected while the pane has focus = subtle. */
@Composable
private fun RailItem(page: SettingsPage, isSelected: Boolean, isFocused: Boolean) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    isFocused -> Modifier
                        .background(AppColors.Purple.copy(alpha = 0.14f), shape)
                        .border(1.dp, AppColors.Purple.copy(alpha = 0.50f), shape)
                    isSelected -> Modifier.background(Color.White.copy(alpha = 0.05f), shape)
                    else -> Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    if (isFocused) AppColors.Purple.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(page.iconRes),
                contentDescription = null,
                tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                stringResource(page.titleRes),
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp,
                fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                stringResource(page.summaryRes),
                color = Color.White.copy(alpha = 0.38f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun DialogFrame(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .background(AppColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, AppColors.OnSurface.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(20.dp),
            content = content
        )
    }
}

@Composable
private fun PickerDialog(picker: PickerState, prebufferEnabled: Boolean) {
    val title = stringResource(
        when (picker.kind) {
            PickerKind.BANDWIDTH -> R.string.settings_bandwidth
            PickerKind.PREBUFFER_DELAY -> R.string.settings_prebuffer_delay
            PickerKind.COUNTDOWN -> R.string.settings_countdown
        }
    )
    val options = when (picker.kind) {
        PickerKind.BANDWIDTH -> BITRATE_OPTIONS.map { it.second }
        PickerKind.PREBUFFER_DELAY -> Prebuffer.START_DELAY_OPTIONS.map(::formatSeconds)
        PickerKind.COUNTDOWN -> Prebuffer.COUNTDOWN_OPTIONS.map { countdownLabel(it, prebufferEnabled) }
    }
    DialogFrame {
        Text(
            title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 14.dp)
        )
        options.forEachIndexed { idx, label ->
            PickerOption(label, isHighlighted = idx == picker.index)
            if (idx < options.lastIndex) Spacer(Modifier.height(3.dp))
        }
    }
}

@Composable
private fun PickerOption(label: String, isHighlighted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isHighlighted) Modifier
                    .background(AppColors.Purple.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                    .border(1.dp, AppColors.Purple, RoundedCornerShape(6.dp))
                else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = if (isHighlighted) AppColors.Purple else AppColors.OnSurface.copy(alpha = 0.75f),
            fontSize = 14.sp,
            fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal
        )
        if (isHighlighted) Text("●", color = AppColors.Purple, fontSize = 8.sp)
    }
}

@Composable
private fun CaptureDialog(capture: CaptureState, keybinds: Keybinds) {
    DialogFrame {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_settings_remote),
                contentDescription = null,
                tint = AppColors.Purple,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.keybind_capture_title),
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(capture.action.labelRes),
                color = AppColors.Purple,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            KeyChips(capture.action, keybinds)
            capture.message?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(
                    when (message) {
                        CaptureMessage.Reserved -> stringResource(R.string.keybind_capture_reserved)
                        is CaptureMessage.Conflict -> stringResource(
                            R.string.keybind_capture_conflict,
                            keyLabel(message.keyCode),
                            stringResource(message.owner.labelRes)
                        )
                    },
                    color = AppColors.Red,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.keybind_capture_not_passed),
                color = AppColors.OnSurface.copy(alpha = 0.45f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.keybind_capture_keys),
                color = AppColors.OnSurface.copy(alpha = 0.60f),
                fontSize = 11.sp
            )
        }
    }
}
