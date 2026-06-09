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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tellyfin.androidtv.ui.theme.AppColors

@Composable
fun SettingsScreen(
    serverUrl: String,
    currentBitrate: Int?,
    highlightedIndex: Int,
    modifier: Modifier = Modifier
) {
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
                "Settings",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Text(
                "Max Streaming Bandwidth",
                color = AppColors.OnSurface.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
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

            Spacer(Modifier.height(32.dp))

            Text(
                "Server",
                color = AppColors.OnSurface.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                serverUrl.ifBlank { "—" },
                color = AppColors.OnSurface.copy(alpha = 0.8f),
                fontSize = 14.sp
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "Press BACK to close · OK to apply bandwidth",
                color = AppColors.OnSurface.copy(alpha = 0.35f),
                fontSize = 11.sp
            )
        }
    }
}
