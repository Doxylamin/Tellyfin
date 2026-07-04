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

// Focus map (highlightedIndex): SETTINGS_FOCUS_BANDWIDTH = Streaming card,
// SETTINGS_FOCUS_UPDATE = App card, SETTINGS_FOCUS_LOGOUT = Account card.
// Key handling lives in PlayerViewModel.handleSettingsKeys.

@Composable
fun SettingsScreen(
    serverUrl: String,
    username: String,
    currentBitrate: Int?,
    highlightedIndex: Int,
    updateStatus: UpdateStatus = UpdateStatus.Idle,
    appVersion: String = "",
    bitratePickerOpen: Boolean = false,
    bitratePickerIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = Modifier.width(860.dp)) {
            // Header: title left, signed-in identity right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.settings),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                if (username.isNotBlank()) {
                    Text(
                        username,
                        color = AppColors.OnSurface.copy(alpha = 0.45f),
                        fontSize = 13.sp
                    )
                }
            }

            // Top row: Streaming + App cards side by side
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SettingsCard(
                    title = stringResource(R.string.settings_section_streaming),
                    isActive = highlightedIndex == SETTINGS_FOCUS_BANDWIDTH,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    ActionRow(
                        label = stringResource(R.string.settings_bandwidth),
                        isFocused = highlightedIndex == SETTINGS_FOCUS_BANDWIDTH
                    ) {
                        val currentLabel = BITRATE_OPTIONS
                            .firstOrNull { it.first == currentBitrate }?.second
                            ?: BITRATE_OPTIONS.first().second
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                currentLabel,
                                color = if (highlightedIndex == SETTINGS_FOCUS_BANDWIDTH) AppColors.Purple
                                else AppColors.OnSurface.copy(alpha = 0.80f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "›",
                                color = if (highlightedIndex == SETTINGS_FOCUS_BANDWIDTH) AppColors.Purple
                                else AppColors.OnSurface.copy(alpha = 0.35f),
                                fontSize = 16.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.settings_bandwidth_hint),
                        color = AppColors.OnSurface.copy(alpha = 0.35f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                SettingsCard(
                    title = stringResource(R.string.settings_section_app),
                    isActive = highlightedIndex == SETTINGS_FOCUS_UPDATE,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    InfoLine(
                        label = stringResource(R.string.settings_version),
                        value = if (appVersion.isNotBlank()) "v$appVersion" else "—"
                    )
                    Spacer(Modifier.height(8.dp))
                    ActionRow(
                        label = stringResource(R.string.settings_update_label),
                        isFocused = highlightedIndex == SETTINGS_FOCUS_UPDATE
                    ) {
                        UpdateStatusValue(updateStatus)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Bottom: Account card, full width
            SettingsCard(
                title = stringResource(R.string.settings_section_account),
                isActive = highlightedIndex == SETTINGS_FOCUS_LOGOUT,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        InfoLine(
                            label = stringResource(R.string.settings_server),
                            value = serverUrl.ifBlank { "—" }
                        )
                        Spacer(Modifier.height(8.dp))
                        InfoLine(
                            label = stringResource(R.string.settings_username_label),
                            value = username.ifBlank { "—" }
                        )
                    }
                    SignOutButton(isFocused = highlightedIndex == SETTINGS_FOCUS_LOGOUT)
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                stringResource(R.string.settings_hint),
                color = AppColors.OnSurface.copy(alpha = 0.30f),
                fontSize = 11.sp
            )
        }

        if (bitratePickerOpen) {
            BitratePickerDialog(bitratePickerIndex)
        }
    }
}

// ── Building blocks ───────────────────────────────────────────────────────────

/** Section card; the whole card lifts slightly when its action is focused. */
@Composable
private fun SettingsCard(
    title: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) Color(0xFF1C1C30) else Color(0xFF151522))
            .border(
                1.dp,
                if (isActive) AppColors.Purple.copy(alpha = 0.35f)
                else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(12.dp)
            )
            .padding(20.dp)
    ) {
        Text(
            title,
            color = if (isActive) AppColors.Purple else AppColors.Purple.copy(alpha = 0.60f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(bottom = 14.dp)
        )
        content()
    }
}

/** Read-only label/value pair — visually flat so it can't be mistaken for a button. */
@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = AppColors.OnSurface.copy(alpha = 0.45f),
            fontSize = 13.sp
        )
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

/** Focusable label/value row — bordered pill so actionable rows stand out. */
@Composable
private fun ActionRow(
    label: String,
    isFocused: Boolean,
    value: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isFocused) Modifier
                    .background(AppColors.Purple.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                    .border(1.dp, AppColors.Purple, RoundedCornerShape(8.dp))
                else Modifier
                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = if (isFocused) Color.White else AppColors.OnSurface.copy(alpha = 0.70f),
            fontSize = 14.sp,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal
        )
        value()
    }
}

@Composable
private fun SignOutButton(isFocused: Boolean) {
    Box(
        modifier = Modifier
            .then(
                if (isFocused) Modifier
                    .background(AppColors.Red.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                    .border(1.dp, AppColors.Red.copy(alpha = 0.70f), RoundedCornerShape(8.dp))
                else Modifier
                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            )
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            stringResource(R.string.settings_logout),
            color = if (isFocused) AppColors.Red else AppColors.OnSurface.copy(alpha = 0.65f),
            fontSize = 14.sp,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal
        )
    }
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                    modifier = Modifier.width(120.dp).height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
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

@Composable
private fun BitratePickerDialog(bitratePickerIndex: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .background(AppColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, AppColors.OnSurface.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(vertical = 20.dp, horizontal = 20.dp)
        ) {
            Text(
                stringResource(R.string.settings_bandwidth),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 14.dp)
            )
            BITRATE_OPTIONS.forEachIndexed { idx, (_, label) ->
                val isHighlighted = idx == bitratePickerIndex
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
                    if (isHighlighted) {
                        Text("●", color = AppColors.Purple, fontSize = 8.sp)
                    }
                }
                if (idx < BITRATE_OPTIONS.size - 1) Spacer(Modifier.height(3.dp))
            }
        }
    }
}
