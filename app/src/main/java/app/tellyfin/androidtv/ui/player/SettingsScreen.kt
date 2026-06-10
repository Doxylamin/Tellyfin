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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tellyfin.androidtv.R
import app.tellyfin.androidtv.ui.theme.AppColors

// highlightedIndex: 0 = bandwidth, 1 = update, 2 = logout

@Composable
fun SettingsScreen(
    serverUrl: String,
    username: String,
    currentBitrate: Int?,
    highlightedIndex: Int,
    updateStatus: UpdateStatus = UpdateStatus.Idle,
    appVersion: String = "",
    modifier: Modifier = Modifier
) {
    val currentLabel = BITRATE_OPTIONS.firstOrNull { it.first == currentBitrate }?.second
        ?: BITRATE_OPTIONS.first().second

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(560.dp)
                .padding(vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                stringResource(R.string.settings),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 36.dp)
            )

            // ── Account section ──────────────────────────────────────────────
            SectionHeader(stringResource(R.string.settings_section_account))

            InfoRow(
                label = stringResource(R.string.settings_server),
                value = serverUrl.ifBlank { "—" }
            )
            Spacer(Modifier.height(6.dp))
            InfoRow(
                label = stringResource(R.string.settings_username_label),
                value = username.ifBlank { "—" }
            )

            Spacer(Modifier.height(32.dp))

            // ── Streaming section ────────────────────────────────────────────
            SectionHeader(stringResource(R.string.settings_section_streaming))

            // Bandwidth selector row
            val bandwidthFocused = highlightedIndex == 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (bandwidthFocused) Modifier
                            .background(AppColors.Purple.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                            .border(1.dp, AppColors.Purple, RoundedCornerShape(8.dp))
                        else Modifier
                            .background(AppColors.Surface, RoundedCornerShape(8.dp))
                            .border(1.dp, AppColors.OnSurface.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.settings_bandwidth),
                    color = if (bandwidthFocused) Color.White else AppColors.OnSurface.copy(alpha = 0.70f),
                    fontSize = 14.sp,
                    fontWeight = if (bandwidthFocused) FontWeight.SemiBold else FontWeight.Normal
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "◀",
                        color = if (bandwidthFocused) AppColors.Purple else AppColors.OnSurface.copy(alpha = 0.30f),
                        fontSize = 12.sp
                    )
                    Text(
                        currentLabel,
                        color = if (bandwidthFocused) AppColors.Purple else AppColors.OnSurface.copy(alpha = 0.80f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.widthIn(min = 72.dp),
                    )
                    Text(
                        "▶",
                        color = if (bandwidthFocused) AppColors.Purple else AppColors.OnSurface.copy(alpha = 0.30f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── App section ──────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.settings_section_app))

            InfoRow(
                label = stringResource(R.string.settings_version),
                value = if (appVersion.isNotBlank()) "v$appVersion" else "—"
            )

            Spacer(Modifier.height(6.dp))

            // Update row
            val updateFocused = highlightedIndex == 1
            val isUpdateActionable = updateStatus is UpdateStatus.Available ||
                                     updateStatus == UpdateStatus.ReadyToInstall
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        when {
                            updateFocused && isUpdateActionable -> Modifier
                                .background(AppColors.Purple.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                                .border(1.dp, AppColors.Purple, RoundedCornerShape(8.dp))
                            updateFocused -> Modifier
                                .background(AppColors.Surface, RoundedCornerShape(8.dp))
                                .border(1.dp, AppColors.Purple.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            else -> Modifier
                                .background(AppColors.Surface, RoundedCornerShape(8.dp))
                                .border(1.dp, AppColors.OnSurface.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        }
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val labelColor = when {
                    updateFocused && isUpdateActionable -> Color.White
                    updateFocused -> Color.White.copy(alpha = 0.80f)
                    else -> AppColors.OnSurface.copy(alpha = 0.55f)
                }
                val valueColor = when {
                    updateStatus is UpdateStatus.Available -> AppColors.Purple
                    updateStatus == UpdateStatus.ReadyToInstall -> AppColors.Purple
                    updateStatus is UpdateStatus.Error -> AppColors.Red.copy(alpha = 0.80f)
                    else -> AppColors.OnSurface.copy(alpha = 0.55f)
                }

                Text(
                    "Update",
                    color = labelColor,
                    fontSize = 14.sp,
                    fontWeight = if (updateFocused) FontWeight.SemiBold else FontWeight.Normal
                )

                when (updateStatus) {
                    UpdateStatus.Idle, UpdateStatus.UpToDate ->
                        Text(
                            if (updateStatus == UpdateStatus.UpToDate)
                                stringResource(R.string.settings_update_up_to_date)
                            else "—",
                            color = valueColor, fontSize = 13.sp
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
                            color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
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
                            color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    is UpdateStatus.Error ->
                        Text(
                            stringResource(R.string.settings_update_error),
                            color = valueColor, fontSize = 12.sp
                        )
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Logout ───────────────────────────────────────────────────────
            val logoutFocused = highlightedIndex == 2
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (logoutFocused) Modifier
                            .background(AppColors.Red.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                            .border(1.dp, AppColors.Red.copy(alpha = 0.70f), RoundedCornerShape(8.dp))
                        else Modifier
                            .background(AppColors.Surface, RoundedCornerShape(8.dp))
                            .border(1.dp, AppColors.OnSurface.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(
                    stringResource(R.string.settings_logout),
                    color = if (logoutFocused) AppColors.Red else AppColors.OnSurface.copy(alpha = 0.65f),
                    fontSize = 15.sp,
                    fontWeight = if (logoutFocused) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                stringResource(R.string.settings_hint),
                color = AppColors.OnSurface.copy(alpha = 0.30f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = AppColors.Purple,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = AppColors.OnSurface.copy(alpha = 0.55f),
            fontSize = 13.sp
        )
        Text(
            value,
            color = AppColors.OnSurface.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
