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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import app.tellyfin.androidtv.diagnostics.CrashReporting
import app.tellyfin.androidtv.ui.theme.AppColors
import coil.compose.AsyncImage

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SplashScreen(
    status: String = "",
    serverName: String? = null,
    splashscreenUrl: String? = null,
    error: String? = null,
    onLogOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LaunchedEffect(status, error) {
        CrashReporting.addBreadcrumb("SplashScreen composed: status=\"$status\" error=\"$error\"", "ui")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background),
        contentAlignment = Alignment.Center
    ) {
        if (splashscreenUrl != null) {
            AsyncImage(
                model = splashscreenUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Scrim so the logo/status stay readable over whatever the server's splashscreen is.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.Background.copy(alpha = 0.72f))
            )
        }

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
                    (serverName?.firstOrNull() ?: 'T').uppercaseChar().toString(),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Purple
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                serverName ?: "Tellyfin",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(Modifier.height(32.dp))

            if (error != null) {
                val retryFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { retryFocusRequester.requestFocus() }

                Text(
                    error,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(320.dp)
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onLogOut,
                    modifier = Modifier
                        .width(200.dp)
                        .focusRequester(retryFocusRequester)
                ) {
                    Text("Sign in again", fontSize = 16.sp)
                }
            } else {
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
}
