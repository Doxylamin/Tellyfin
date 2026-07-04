package app.tellyfin.androidtv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** Centered dialog shown on start when a newer app version is available. */
@Composable
fun UpdatePromptOverlay(
    visible: Boolean,
    version: String,
    currentVersion: String,
    updateStatus: UpdateStatus,
    buttonIndex: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.Surface)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(28.dp)
            ) {
                Text(
                    stringResource(R.string.update_prompt_title),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.update_prompt_message, version, currentVersion),
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )

                Spacer(Modifier.height(20.dp))

                when (updateStatus) {
                    is UpdateStatus.Downloading -> {
                        Text(
                            stringResource(R.string.settings_update_downloading, updateStatus.progress),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { updateStatus.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = AppColors.Purple,
                            trackColor = Color.White.copy(alpha = 0.12f)
                        )
                    }
                    is UpdateStatus.Error -> {
                        Text(
                            updateStatus.message,
                            fontSize = 12.sp,
                            color = AppColors.Red
                        )
                        Spacer(Modifier.height(12.dp))
                        PromptButtons(buttonIndex)
                    }
                    else -> PromptButtons(buttonIndex)
                }
            }
        }
    }
}

@Composable
private fun PromptButtons(buttonIndex: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PromptButton(
            label = stringResource(R.string.update_install),
            isFocused = buttonIndex == 0,
            modifier = Modifier.weight(1f)
        )
        PromptButton(
            label = stringResource(R.string.update_later),
            isFocused = buttonIndex == 1,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PromptButton(
    label: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) AppColors.Purple else Color.White.copy(alpha = 0.06f)
            )
            .then(
                if (isFocused) Modifier.border(1.dp, AppColors.Purple, RoundedCornerShape(8.dp))
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.70f)
        )
    }
}
