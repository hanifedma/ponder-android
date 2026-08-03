package com.hanifedma.ponder.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.hanifedma.ponder.ui.components.GoogleButton
import com.hanifedma.ponder.ui.components.PonderCard
import com.hanifedma.ponder.ui.theme.PonderTheme

/**
 * The signed-out landing screen: sign in with Google, or carry on without an
 * account. Same two choices, and the same wording, as the web app.
 */
@Composable
fun LoginScreen(
    signingIn: Boolean,
    errorText: String?,
    onSignIn: () -> Unit,
    onUseLocal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PonderTheme.colors
    val tr = PonderTheme.tr

    Box(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(horizontal = 18.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        PonderCard(
            modifier = Modifier.widthIn(max = 400.dp),
            padding = PaddingValues(horizontal = 30.dp, vertical = 36.dp),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("❝", color = colors.accent, fontSize = 40.sp)

                Text(
                    text = tr("login.h1"),
                    color = colors.text,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
                )
                Text(
                    text = tr("login.sub"),
                    color = colors.muted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 22.dp),
                )

                GoogleButton(
                    text = tr("login.google"),
                    onClick = onSignIn,
                    enabled = !signingIn,
                )

                if (errorText != null) {
                    Text(
                        text = errorText,
                        color = colors.danger,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }

                Text(
                    text = tr("login.local"),
                    color = colors.muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onUseLocal)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        }
    }
}
