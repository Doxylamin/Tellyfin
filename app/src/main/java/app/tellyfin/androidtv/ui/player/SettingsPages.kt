package app.tellyfin.androidtv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tellyfin.androidtv.R
import app.tellyfin.androidtv.ui.theme.AppColors
import java.util.Locale

// Contents of each settings page. Focusable rows must appear in exactly the order
// settingsRows() lists them — SettingsNavigator's row index points into that list.

@Composable
fun SettingsPageContent(
    page: SettingsPage,
    state: PlayerUiState,
    context: SettingsContext,
    focusedRow: SettingsRow?,
    serverUrl: String,
    appVersion: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (page) {
            SettingsPage.STREAMING -> StreamingPage(state, focusedRow)
            SettingsPage.KEYBINDS -> KeybindsPage(state.keybinds, focusedRow)
            SettingsPage.APP -> AppPage(state.updateStatus, appVersion, context, focusedRow)
            SettingsPage.ACCOUNT -> AccountPage(serverUrl, state.username, focusedRow)
            SettingsPage.ADVANCED -> AdvancedPage(state, context, focusedRow)
        }
    }
}

@Composable
private fun StreamingPage(state: PlayerUiState, focusedRow: SettingsRow?) {
    val bandwidthFocused = focusedRow == SettingsRow.Bandwidth
    SettingsRowItem(stringResource(R.string.settings_bandwidth), bandwidthFocused) {
        ValueText(bitrateLabel(state.maxBitrate), bandwidthFocused, chevron = true)
    }
    Hint(stringResource(R.string.settings_bandwidth_hint))

    val prebufferFocused = focusedRow == SettingsRow.PrebufferToggle
    SettingsRowItem(stringResource(R.string.settings_prebuffer), prebufferFocused) {
        ValueText(onOff(state.prebufferEnabled), prebufferFocused)
    }
    Hint(
        stringResource(
            if (state.prebufferAutoDisabled) R.string.settings_prebuffer_auto_off_hint
            else R.string.settings_prebuffer_hint
        ),
        isWarning = state.prebufferAutoDisabled
    )
}

@Composable
private fun KeybindsPage(keybinds: Keybinds, focusedRow: SettingsRow?) {
    Hint(stringResource(R.string.keybinds_intro))
    Spacer(Modifier.height(4.dp))
    KeyAction.entries.forEach { action ->
        SettingsRowItem(stringResource(action.labelRes), focusedRow == SettingsRow.Keybind(action)) {
            KeyChips(action, keybinds)
        }
    }
}

@Composable
private fun AppPage(updateStatus: UpdateStatus, appVersion: String, context: SettingsContext, focusedRow: SettingsRow?) {
    InfoLine(stringResource(R.string.settings_version), if (appVersion.isNotBlank()) "v$appVersion" else "—")
    if (context.selfUpdateEnabled) {
        Spacer(Modifier.height(6.dp))
        SettingsRowItem(stringResource(R.string.settings_update_label), focusedRow == SettingsRow.Update) {
            UpdateStatusValue(updateStatus)
        }
    }
}

@Composable
private fun AccountPage(serverUrl: String, username: String, focusedRow: SettingsRow?) {
    InfoLine(stringResource(R.string.settings_server), serverUrl.ifBlank { "—" })
    InfoLine(stringResource(R.string.settings_username_label), username.ifBlank { "—" })
    Spacer(Modifier.height(10.dp))
    SettingsRowItem(stringResource(R.string.settings_logout), focusedRow == SettingsRow.SignOut, danger = true)
}

@Composable
private fun AdvancedPage(state: PlayerUiState, context: SettingsContext, focusedRow: SettingsRow?) {
    GroupLabel(stringResource(R.string.settings_group_switching))
    val delayFocused = focusedRow == SettingsRow.PrebufferDelay
    SettingsRowItem(stringResource(R.string.settings_prebuffer_delay), delayFocused) {
        ValueText(formatSeconds(state.prebufferDelayMs), delayFocused, chevron = true)
    }
    Hint(stringResource(R.string.settings_prebuffer_delay_hint))
    val countdownFocused = focusedRow == SettingsRow.Countdown
    SettingsRowItem(stringResource(R.string.settings_countdown), countdownFocused) {
        ValueText(countdownLabel(state.countdownSettingMs, state.prebufferEnabled), countdownFocused, chevron = true)
    }
    Hint(stringResource(R.string.settings_countdown_hint))

    if (context.diagnosticsAvailable) {
        GroupLabel(stringResource(R.string.settings_group_diagnostics))
        val diagnosticsFocused = focusedRow == SettingsRow.Diagnostics
        SettingsRowItem(stringResource(R.string.settings_diagnostics), diagnosticsFocused) {
            ValueText(onOff(state.diagnosticsEnabled), diagnosticsFocused)
        }
        Hint(stringResource(R.string.settings_diagnostics_hint))
    }

    Spacer(Modifier.height(8.dp))
    SettingsRowItem(stringResource(R.string.settings_restore_defaults), focusedRow == SettingsRow.RestoreDefaults, danger = true)
    Hint(stringResource(R.string.settings_restore_defaults_hint))
}

// ── Labels ────────────────────────────────────────────────────────────────────

val KeyAction.labelRes: Int
    get() = when (this) {
        KeyAction.QUICK_MENU -> R.string.keybind_quick_menu
        KeyAction.CHANNEL_INFO -> R.string.keybind_channel_info
        KeyAction.LIVE_GUIDE -> R.string.keybind_live_guide
        KeyAction.SEARCH -> R.string.keybind_search
        KeyAction.CHANNEL_UP -> R.string.keybind_channel_up
        KeyAction.CHANNEL_DOWN -> R.string.keybind_channel_down
    }

fun formatSeconds(ms: Long): String =
    if (ms % 1000 == 0L) "${ms / 1000} s" else String.format(Locale.getDefault(), "%.1f s", ms / 1000f)

@Composable
fun countdownLabel(settingMs: Long, prebufferEnabled: Boolean): String =
    if (settingMs == Prebuffer.COUNTDOWN_AUTO)
        stringResource(R.string.settings_countdown_auto_value, formatSeconds(Prebuffer.countdownMs(prebufferEnabled)))
    else formatSeconds(settingMs)

private fun bitrateLabel(bitrate: Int?): String =
    BITRATE_OPTIONS.firstOrNull { it.first == bitrate }?.second ?: BITRATE_OPTIONS.first().second

@Composable
private fun onOff(enabled: Boolean): String =
    stringResource(if (enabled) R.string.settings_on else R.string.settings_off)

// ── Building blocks ───────────────────────────────────────────────────────────

/** Focusable row — bordered pill; purple when focused, red for destructive actions. */
@Composable
fun SettingsRowItem(
    label: String,
    focused: Boolean,
    danger: Boolean = false,
    value: @Composable () -> Unit = {}
) {
    val accent = if (danger) AppColors.Red else AppColors.Purple
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focused) Modifier.background(accent.copy(alpha = 0.20f), shape).border(1.dp, accent, shape)
                else Modifier
                    .background(Color.White.copy(alpha = 0.04f), shape)
                    .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = when {
                focused && danger -> AppColors.Red
                focused -> Color.White
                else -> AppColors.OnSurface.copy(alpha = 0.75f)
            },
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal
        )
        value()
    }
}

@Composable
private fun ValueText(text: String, focused: Boolean, chevron: Boolean = false) {
    Text(
        if (chevron) "$text  ›" else text,
        color = if (focused) AppColors.Purple else AppColors.OnSurface.copy(alpha = 0.60f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun Hint(text: String, isWarning: Boolean = false) {
    Text(
        text,
        color = if (isWarning) AppColors.Red.copy(alpha = 0.75f) else AppColors.OnSurface.copy(alpha = 0.35f),
        fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        color = AppColors.Purple.copy(alpha = 0.80f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

/** Read-only label/value pair — visually flat so it can't be mistaken for a button. */
@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AppColors.OnSurface.copy(alpha = 0.45f), fontSize = 13.sp)
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            color = AppColors.OnSurface.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The buttons that trigger an action: grey = built in, purple = added by the user. */
@Composable
fun KeyChips(action: KeyAction, keybinds: Keybinds) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyChip(keyLabel(action.defaultKeyCode))
        when (action) {
            KeyAction.QUICK_MENU -> KeyChip(stringResource(R.string.keybind_hold_ok))
            KeyAction.CHANNEL_UP -> KeyChip("▲")
            KeyAction.CHANNEL_DOWN -> KeyChip("▼")
            else -> Unit
        }
        keybinds.extras[action]?.let { KeyChip(keyLabel(it), added = true) }
    }
}

@Composable
private fun KeyChip(label: String, added: Boolean = false) {
    Text(
        label,
        color = if (added) Color(0xFFC9B3FF) else AppColors.OnSurface.copy(alpha = 0.70f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(
                if (added) AppColors.Purple.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

@Composable
private fun UpdateStatusValue(updateStatus: UpdateStatus) {
    when (updateStatus) {
        UpdateStatus.Idle ->
            Text("—", color = AppColors.OnSurface.copy(alpha = 0.55f), fontSize = 13.sp)
        UpdateStatus.UpToDate ->
            Text(
                stringResource(R.string.settings_update_up_to_date),
                color = AppColors.OnSurface.copy(alpha = 0.55f), fontSize = 13.sp
            )
        UpdateStatus.Checking ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(
                    color = AppColors.Purple.copy(alpha = 0.60f),
                    trackColor = Color.Transparent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    stringResource(R.string.settings_update_checking),
                    color = AppColors.OnSurface.copy(alpha = 0.45f), fontSize = 12.sp
                )
            }
        is UpdateStatus.Available ->
            Text(
                stringResource(R.string.settings_update_available, updateStatus.version),
                color = AppColors.Purple, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
        is UpdateStatus.Downloading ->
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.settings_update_downloading, updateStatus.progress),
                    color = AppColors.Purple.copy(alpha = 0.80f), fontSize = 12.sp
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { updateStatus.progress / 100f },
                    modifier = Modifier.width(120.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = AppColors.Purple,
                    trackColor = Color.White.copy(alpha = 0.12f)
                )
            }
        UpdateStatus.ReadyToInstall ->
            Text(
                stringResource(R.string.settings_update_ready),
                color = AppColors.Purple, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
        is UpdateStatus.Error ->
            Text(
                stringResource(R.string.settings_update_error),
                color = AppColors.Red.copy(alpha = 0.80f), fontSize = 12.sp
            )
    }
}
