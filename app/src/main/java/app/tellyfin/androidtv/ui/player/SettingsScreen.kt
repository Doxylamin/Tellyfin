package app.tellyfin.androidtv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tellyfin.androidtv.R
import app.tellyfin.androidtv.ui.theme.AppColors

@Composable
fun SettingsScreen(
    serverUrl: String,
    username: String,
    currentBitrate: Int?,
    highlightedIndex: Int,
    modifier: Modifier = Modifier
) {
    val logoutIndex = BITRATE_OPTIONS.size

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

            Text(
                stringResource(R.string.settings_bandwidth),
                color = AppColors.OnSurface.copy(alpha = 0.55f),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BITRATE_OPTIONS.forEachIndexed { index, (bitrate, label) ->
                    val isSelected = bitrate == currentBitrate
                    val isFocused = index == highlightedIndex
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                when {
                                    isFocused -> Modifier
                                        .background(AppColors.Purple.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                        .border(1.dp, AppColors.Purple, RoundedCornerShape(8.dp))
                                    isSelected -> Modifier
                                        .background(AppColors.Surface, RoundedCornerShape(8.dp))
                                        .border(1.dp, AppColors.OnSurface.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    else -> Modifier.background(Color.Transparent, RoundedCornerShape(8.dp))
                                }
                            )
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Text(
                            label,
                            color = when {
                                isFocused -> Color.White
                                isSelected -> AppColors.Purple
                                else -> AppColors.OnSurface.copy(alpha = 0.7f)
                            },
                            fontSize = 15.sp,
                            fontWeight = if (isFocused || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Text("✓", color = AppColors.Purple, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Logout ───────────────────────────────────────────────────────
            val isLogoutFocused = highlightedIndex == logoutIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isLogoutFocused) Modifier
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
                    color = if (isLogoutFocused) AppColors.Red else AppColors.OnSurface.copy(alpha = 0.65f),
                    fontSize = 15.sp,
                    fontWeight = if (isLogoutFocused) FontWeight.SemiBold else FontWeight.Normal
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
