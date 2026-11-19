package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccretionAmber
import com.example.ui.theme.AstralCyan
import com.example.ui.theme.ChampagneBright
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.HairlineAmber
import com.example.ui.theme.HairlineCyan
import com.example.ui.theme.HairlineGold
import com.example.ui.theme.HairlineLight
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NebulaSlate
import com.example.ui.theme.ObsidianGlass
import com.example.ui.theme.ObsidianSheet
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.StarlightInk

enum class PillTone { NEUTRAL, GOLD, CYAN, AMBER }

@Composable
fun LuxuryGlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    borderColor: Color = HairlineLight,
    sheetTone: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable () -> Unit
) {
    val bgBrush = if (sheetTone) {
        Brush.verticalGradient(
            listOf(Color(0xF00C1327), ObsidianSheet)
        )
    } else {
        Brush.verticalGradient(
            listOf(Color(0xD40A1021), ObsidianGlass)
        )
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(bgBrush)
            .border(
                width = 0.85.dp,
                brush = Brush.verticalGradient(
                    listOf(borderColor.copy(alpha = (borderColor.alpha * 1.4f).coerceAtMost(1f)), borderColor.copy(alpha = 0.35f))
                ),
                shape = shape
            )
            .padding(contentPadding)
    ) {
        content()
    }
}

@Composable
fun LuxuryPillButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: PillTone = PillTone.GOLD,
    leadingDotColor: Color? = null,
    isBlackHoleDot: Boolean = false,
    minHeight: Dp = 36.dp,
    tag: String? = null
) {
    val activeBg = when (tone) {
        PillTone.GOLD -> Brush.linearGradient(listOf(ChampagneGold.copy(alpha = 0.28f), ChampagneGold.copy(alpha = 0.08f)))
        PillTone.CYAN -> Brush.linearGradient(listOf(AstralCyan.copy(alpha = 0.25f), AstralCyan.copy(alpha = 0.07f)))
        PillTone.AMBER -> Brush.linearGradient(listOf(AccretionAmber.copy(alpha = 0.30f), Color(0x1AFF5A28)))
        PillTone.NEUTRAL -> Brush.linearGradient(listOf(Color(0x1EFFFFFF), Color(0x0DFFFFFF)))
    }
    val inactiveBg = Brush.linearGradient(listOf(Color(0x0DFFFFFF), Color(0x05FFFFFF)))

    val border = when {
        selected && tone == PillTone.GOLD -> HairlineGold
        selected && tone == PillTone.CYAN -> HairlineCyan
        selected && tone == PillTone.AMBER -> HairlineAmber
        selected -> Color(0x4DFFFFFF)
        else -> Color(0x16FFFFFF)
    }

    val textColor = when {
        selected && tone == PillTone.GOLD -> ChampagneBright
        selected -> Color.White
        tone == PillTone.AMBER -> Color(0xFFFFBE82)
        else -> NebulaSlate
    }

    Row(
        modifier = modifier
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .defaultMinSize(minHeight = minHeight)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) activeBg else inactiveBg)
            .border(0.9.dp, border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (leadingDotColor != null) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (isBlackHoleDot) Brush.radialGradient(listOf(ObsidianVoid, ObsidianVoid))
                        else Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), leadingDotColor, leadingDotColor.copy(alpha = 0.4f)))
                    )
                    .then(
                        if (isBlackHoleDot) Modifier.border(1.5.dp, AccretionAmber, CircleShape)
                        else Modifier
                    )
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LuxurySectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        ),
        color = AstralCyan,
        modifier = modifier.padding(top = 12.dp, bottom = 6.dp)
    )
}

@Composable
fun LuxurySliderRow(
    title: String,
    subtitle: String? = null,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    accentColor: Color = ChampagneGold,
    tag: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = StarlightInk
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = NebulaSlate
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.14f),
                border = BorderStroke(0.6.dp, accentColor.copy(alpha = 0.38f))
            ) {
                Text(
                    text = valueLabel,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .then(if (tag != null) Modifier.testTag(tag) else Modifier),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = Color(0x28FFFFFF)
            )
        )
    }
}

@Composable
fun LuxuryToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color = AstralCyan,
    tag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = StarlightInk
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NebulaSlate
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor.copy(alpha = 0.85f),
                uncheckedThumbColor = NebulaSlate,
                uncheckedTrackColor = Color(0x22FFFFFF),
                uncheckedBorderColor = HairlineLight
            )
        )
    }
}
