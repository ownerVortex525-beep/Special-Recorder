package com.ownervortex.nyxrecorder.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ownervortex.nyxrecorder.ui.theme.NixAccent
import com.ownervortex.nyxrecorder.ui.theme.NixAmber
import com.ownervortex.nyxrecorder.ui.theme.NixBackground
import com.ownervortex.nyxrecorder.ui.theme.NixPrimary
import com.ownervortex.nyxrecorder.ui.theme.NixRed
import com.ownervortex.nyxrecorder.ui.theme.NixStroke
import com.ownervortex.nyxrecorder.ui.theme.NixSurface
import com.ownervortex.nyxrecorder.ui.theme.NixSurfaceHigh
import com.ownervortex.nyxrecorder.ui.theme.NixText
import com.ownervortex.nyxrecorder.ui.theme.NixTextDim

/** Elevated dark card used across the app. */
@Composable
fun NixCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color = NixStroke,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val base = Modifier
        .then(modifier)
        .clip(shape)
        .background(NixSurface)
        .border(1.dp, accent, shape)
    val clickableMod = if (onClick != null) base.clickable(onClick = onClick) else base
    Surface(
        modifier = clickableMod,
        color = Color.Transparent
    ) {
        content()
    }
}

/** Primary filled button with press scale feedback. */
@Composable
fun NixButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = NixPrimary,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    if (enabled) listOf(color, color.copy(alpha = 0.75f))
                    else listOf(Color(0xFF33364A), Color(0xFF2A2C3D))
                )
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                color = if (enabled) Color.White else NixTextDim,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }
    }
}

/** Small selectable chip. */
@Composable
fun NixChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = NixPrimary
) {
    val bg by animateColorAsState(
        if (selected) accent.copy(alpha = 0.25f) else NixSurfaceHigh,
        label = "chip"
    )
    val border by animateColorAsState(
        if (selected) accent else NixStroke,
        label = "chipBorder"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            color = if (selected) NixText else NixTextDim,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** Row with label + trailing content, used in settings. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = NixText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, color = NixTextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** Section header. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        color = NixTextDim,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp
    )
}

/** Recording pulse dot. */
@Composable
fun PulseDot(color: Color = NixRed, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseScale"
    )
    Box(
        modifier = modifier
            .size(10.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(color)
    )
}

/** Gradient accent bar. */
@Composable
fun AccentBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(NixPrimary, NixAccent, NixAmber)
                )
            )
    )
}

/** Empty-state placeholder. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = NixSurfaceHigh,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(title, color = NixText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            color = NixTextDim,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

/** Truncating single-line text helper. */
@Composable
fun TruncText(text: String, modifier: Modifier = Modifier, fontSize: androidx.compose.ui.unit.TextUnit = 14.sp) {
    Text(
        text,
        modifier = modifier,
        color = NixText,
        fontSize = fontSize,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** App-level background. */
@Composable
fun NixBackgroundBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(NixBackground)
    ) {
        content()
    }
}
