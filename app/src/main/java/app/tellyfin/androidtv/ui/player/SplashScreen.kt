package app.tellyfin.androidtv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tellyfin.androidtv.ui.theme.AppColors

@Composable
fun SplashScreen(status: String = "", modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(AppColors.Purple.copy(alpha = 0.15f))
                    .border(1.dp, AppColors.Purple.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "T",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Purple
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Tellyfin",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(Modifier.height(32.dp))

            CircularProgressIndicator(
                color = AppColors.Purple.copy(alpha = 0.60f),
                trackColor = Color.White.copy(alpha = 0.06f),
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )

            if (status.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    status,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.38f)
                )
            }
        }
    }
}
