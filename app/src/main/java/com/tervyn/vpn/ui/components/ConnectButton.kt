package com.tervyn.vpn.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tervyn.vpn.ui.theme.Tervyn
import com.tervyn.vpn.vpn.Phase
import com.tervyn.vpn.vpn.VpnStatus

@Composable
fun phaseColor(phase: Phase): Color {
    val c = Tervyn.colors
    return when (phase) {
        Phase.CONNECTED -> c.success
        Phase.FAILED -> c.danger
        Phase.DISCONNECTED, Phase.DNS_ONLY -> c.accent
        Phase.RECONNECTING -> c.warning
        else -> c.accent2
    }
}

/**
 * The connect control: a ring that breathes while connected, sweeps while working, and a power glyph in a
 * disc. Colour follows the connection phase. The whole control is one accessible button.
 */
@Composable
fun ConnectButton(status: VpnStatus, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 196.dp) {
    val c = Tervyn.colors
    val color by animateColorAsState(phaseColor(status.phase), tween(450), label = "phaseColor")
    val busy = status.phase.busy
    // Infinite animations only run while there is something to animate (idle Home costs no frames).
    val sweep = if (busy) rememberInfiniteTransition(label = "sweep").animateFloat(0f, 360f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "sweep").value else 0f
    val breath = if (status.isConnected) rememberInfiniteTransition(label = "breath").animateFloat(0.92f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath").value else 1f
    val label = when (status.phase) {
        Phase.DISCONNECTED, Phase.FAILED, Phase.DNS_ONLY -> "Connect"
        Phase.CONNECTED -> "Disconnect"
        else -> "Cancel"
    }
    Box(
        modifier.size(size).semantics { contentDescription = label }.clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            val glow = if (status.isConnected) breath else 0.85f
            // halo
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.28f * glow), Color.Transparent), center, r), r)
            // track
            val ringR = r * 0.80f
            val stroke = r * 0.055f
            drawCircle(c.cardHigh, ringR, style = Stroke(stroke))
            if (busy) {
                rotate(sweep) {
                    drawArc(
                        Brush.sweepGradient(listOf(Color.Transparent, color.copy(alpha = .2f), color)),
                        0f, 300f, false, Offset(center.x - ringR, center.y - ringR), Size(ringR * 2, ringR * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            } else {
                drawCircle(color.copy(alpha = if (status.isConnected) 1f else 0.9f), ringR, style = Stroke(stroke))
            }
            // disc
            val discR = r * 0.64f
            drawCircle(Brush.radialGradient(listOf(c.cardHigh, c.surface), center, discR), discR)
            drawCircle(color.copy(alpha = 0.35f), discR, style = Stroke(1.5f))
        }
        if (status.isConnected) Icon(Icons.Rounded.Check, null, tint = color, modifier = Modifier.size(size * 0.36f))
        else Icon(Icons.Rounded.PowerSettingsNew, null, tint = if (busy) color else c.text, modifier = Modifier.size(size * 0.3f))
    }
}
