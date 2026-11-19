package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AccretionAmber
import com.example.ui.theme.AstralCyan
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.NebulaSlate
import com.example.ui.theme.ObsidianVoid

@Composable
fun SplashOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(durationMillis = 750))
    ) {
        val inf = rememberInfiniteTransition(label = "cosmos_splash")
        val angle by inf.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
            label = "orbit_spin"
        )
        val pulse by inf.animateFloat(
            initialValue = 0.75f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
            label = "core_pulse"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF0D1734), Color(0xFF03050C), ObsidianVoid)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Canvas(modifier = Modifier.size(96.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val outerR = size.minDimension * 0.44f
                    drawCircle(
                        color = ChampagneGold.copy(alpha = 0.22f),
                        radius = outerR,
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                    rotate(angle, pivot = center) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(Color.Transparent, ChampagneGold, AstralCyan, Color.Transparent)
                            ),
                            startAngle = 0f,
                            sweepAngle = 210f,
                            useCenter = false,
                            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    rotate(-angle * 0.65f, pivot = center) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(Color.Transparent, AccretionAmber, Color.Transparent)
                            ),
                            startAngle = 90f,
                            sweepAngle = 140f,
                            useCenter = false,
                            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White, ChampagneGold, Color.Transparent),
                            center = center,
                            radius = 12.dp.toPx() * pulse
                        ),
                        radius = 12.dp.toPx() * pulse
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.brand_title),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 6.sp
                    ),
                    color = ChampagneGold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.brand_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = NebulaSlate
                )
            }
        }
    }
}
