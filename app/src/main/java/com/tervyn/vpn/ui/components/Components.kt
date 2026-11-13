package com.tervyn.vpn.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tervyn.vpn.R
import com.tervyn.vpn.domain.Server
import com.tervyn.vpn.domain.VpnMode
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import com.tervyn.vpn.utils.Format
import com.tervyn.vpn.vpn.Phase
import com.tervyn.vpn.vpn.VpnStatus
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ---- brand ---------------------------------------------------------------------------------

@Composable
fun TervynLogo(size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val c = Tervyn.colors
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.radialGradient(listOf(Color(0xFF0B2A66), c.bg2, c.bg)))
            .border(1.dp, Brush.linearGradient(listOf(c.cyan.copy(alpha = .8f), c.purple.copy(alpha = .8f))), RoundedCornerShape(size * 0.28f))
            .semantics { contentDescription = "Tervyn logo" },
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(size * 1.35f))
    }
}

@Composable
fun BrandHeader(modifier: Modifier = Modifier, subtitle: String? = "Fast • Secure • Free", trailing: @Composable RowScope.() -> Unit = {}) {
    val c = Tervyn.colors
    Row(modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
        TervynLogo(48.dp)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Row {
                Text("Tervyn ", style = MaterialTheme.typography.headlineSmall, color = c.text)
                Text("VPN", style = MaterialTheme.typography.headlineSmall, color = c.blue)
            }
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.text2)
        }
        trailing()
    }
}

// ---- surfaces ------------------------------------------------------------------------------

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(Space.l),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Tervyn.colors
    val shape = RoundedCornerShape(Space.radius)
    val border = if (highlight) BorderStroke(1.5.dp, c.neonGradient) else BorderStroke(1.dp, c.cardBorder)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c.card.copy(alpha = 0.92f), c.bg2.copy(alpha = 0.85f))))
            .border(border, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    secondary: Boolean = false,
    danger: Boolean = false,
) {
    val c = Tervyn.colors
    val shape = RoundedCornerShape(16.dp)
    val bg = when {
        danger -> Brush.horizontalGradient(listOf(c.red, Color(0xFFB0124A)))
        secondary -> Brush.horizontalGradient(listOf(c.card, c.bg2))
        else -> c.primaryGradient
    }
    Row(
        modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (secondary) c.cardBorder else c.cyan.copy(alpha = .45f), shape)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.xl, vertical = Space.m),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = c.text, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(Space.s)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.text, textAlign = TextAlign.Center)
    }
}

// ---- power button & status ----------------------------------------------------------------

@Composable
fun phaseColor(phase: Phase): Color {
    val c = Tervyn.colors
    return when (phase) {
        Phase.CONNECTED -> c.green
        Phase.FAILED -> c.red
        Phase.DISCONNECTED -> c.blue
        Phase.RECONNECTING -> c.amber
        else -> c.cyan
    }
}

@Composable
fun VPNPowerButton(status: VpnStatus, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 220.dp) {
    val c = Tervyn.colors
    val color by animateColorAsState(phaseColor(status.phase), tween(500), label = "powerColor")
    val busy = status.phase.busy
    val t = rememberInfiniteTransition(label = "power")
    val rotation by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (busy) 1400 else 9000, easing = LinearEasing)), label = "rot")
    val pulse by t.animateFloat(0.85f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val glow = if (status.isConnected) pulse else if (busy) 0.9f else 0.6f
    val label = when (status.phase) {
        Phase.DISCONNECTED, Phase.FAILED -> "Connect"
        Phase.CONNECTED -> "Disconnect"
        else -> "Cancel connection"
    }
    Box(
        modifier
            .size(size)
            .semantics { contentDescription = label }
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = min(this.size.width, this.size.height) / 2
            // Outer glow
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.35f * glow), Color.Transparent), center, r), r)
            // Orbit ring (rotating sweep)
            rotate(rotation) {
                drawCircle(
                    Brush.sweepGradient(listOf(Color.Transparent, c.cyan.copy(alpha = .9f), c.purple, Color.Transparent)),
                    r * 0.86f, style = Stroke(width = r * 0.045f, cap = StrokeCap.Round),
                )
            }
            // Inner disc
            drawCircle(Brush.radialGradient(listOf(Color(0xFF13245A), Color(0xFF060E28)), center, r * 0.72f), r * 0.72f)
            drawCircle(Brush.sweepGradient(listOf(color, c.purple, color)), r * 0.72f, style = Stroke(width = r * 0.035f))
        }
        Icon(Icons.Rounded.PowerSettingsNew, null, tint = Color.White, modifier = Modifier.size(size * 0.3f))
    }
}

@Composable
fun ConnectionStatus(status: VpnStatus, modifier: Modifier = Modifier) {
    val c = Tervyn.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val title = when (status.phase) {
            Phase.DISCONNECTED -> "Tap to Connect"
            Phase.CONNECTED -> "Connected"
            else -> status.phase.label
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = if (status.phase == Phase.DISCONNECTED) c.text else phaseColor(status.phase))
        val sub = when {
            status.phase == Phase.TESTING && status.testTotal > 0 -> "Tested ${status.tested}/${status.testTotal} · ${status.working} working"
            status.phase == Phase.FAILED -> status.error
            status.phase == Phase.CONNECTED -> status.server?.let { "${Format.flag(status.countryCode)} ${it.name}" }
            else -> status.message
        }
        if (!sub.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = c.text2, textAlign = TextAlign.Center, maxLines = 3)
        }
    }
}

// ---- server / country ----------------------------------------------------------------------

@Composable
fun CountryFlag(cc: String?, size: Dp = 40.dp) {
    val c = Tervyn.colors
    Box(Modifier.size(size).clip(CircleShape).background(c.bg2).border(1.dp, c.cardBorder, CircleShape), contentAlignment = Alignment.Center) {
        Text(Format.flag(cc), fontSize = (size.value * 0.55f).sp)
    }
}

@Composable
fun PingIndicator(ms: Int?, modifier: Modifier = Modifier) {
    val c = Tervyn.colors
    val color = when {
        ms == null -> c.text3
        ms < 150 -> c.green
        ms < 400 -> c.amber
        else -> c.red
    }
    val bars = when { ms == null -> 0; ms < 150 -> 4; ms < 300 -> 3; ms < 600 -> 2; else -> 1 }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.Bottom) {
            for (i in 1..4) {
                Box(Modifier.padding(end = 2.dp).width(3.dp).height((4 + i * 3).dp).clip(RoundedCornerShape(1.dp)).background(if (i <= bars) color else c.text3.copy(alpha = .3f)))
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(if (ms != null) "$ms ms" else "—", color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun ServerCard(
    server: Server,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFavorite: (() -> Unit)? = null,
) {
    val c = Tervyn.colors
    GlassCard(modifier, highlight = selected, onClick = onClick, padding = PaddingValues(horizontal = Space.l, vertical = Space.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CountryFlag(server.countryCode)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(server.name, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Format.countryName(server.countryCode).takeIf { server.countryCode != null } ?: "Unverified location"} · ${server.protocolLabel}",
                    style = MaterialTheme.typography.bodySmall, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                PingIndicator(if (server.alive) server.latencyMs else null)
                Text(if (server.alive) "Verified ${Format.ago(server.lastChecked)}" else if (server.lastChecked > 0) "Offline" else "Not tested",
                    style = MaterialTheme.typography.labelSmall, color = if (server.alive) c.text3 else c.red.copy(alpha = .8f))
            }
            if (onFavorite != null) {
                IconButton(onClick = onFavorite) {
                    Icon(if (server.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        if (server.favorite) "Remove from favorites" else "Add to favorites", tint = if (server.favorite) c.amber else c.text3)
                }
            }
        }
    }
}

// ---- gauges / globe ------------------------------------------------------------------------

@Composable
fun SpeedGauge(value: Double?, max: Double, label: String, unit: String, modifier: Modifier = Modifier, size: Dp = 150.dp) {
    val c = Tervyn.colors
    val fraction by animateFloatAsState(((value ?: 0.0) / max).toFloat().coerceIn(0f, 1f), tween(600), label = "gauge")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.08f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(c.text3.copy(alpha = .25f), 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Brush.sweepGradient(listOf(c.cyan, c.blue, c.purple, c.cyan)), 135f, 270f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value?.let { String.format(java.util.Locale.US, if (it >= 100) "%.0f" else "%.1f", it) } ?: "—", style = MaterialTheme.typography.headlineSmall, color = c.text)
            Text(unit, style = MaterialTheme.typography.labelSmall, color = c.text2)
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.cyan)
        }
    }
}

@Composable
fun AnimatedGlobe(modifier: Modifier = Modifier, active: Boolean = false) {
    val c = Tervyn.colors
    val t = rememberInfiniteTransition(label = "globe")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (active) 9000 else 24000, easing = LinearEasing)), label = "spin")
    Canvas(modifier) {
        val r = size.minDimension / 2 * 0.95f
        val ctr = center
        drawCircle(Brush.radialGradient(listOf(c.blue.copy(alpha = .22f), Color.Transparent), ctr, r * 1.2f), r * 1.2f)
        val line = c.blue.copy(alpha = if (active) 0.45f else 0.28f)
        drawCircle(line, r, style = Stroke(1.2f))
        // latitudes
        for (i in -3..3) {
            val y = ctr.y + r * i / 4f
            val w = r * kotlin.math.sqrt(1f - (i / 4f) * (i / 4f))
            drawOval(line, Offset(ctr.x - w, y - w * 0.08f), Size(w * 2, w * 0.16f), style = Stroke(1f))
        }
        // rotating meridians
        for (k in 0 until 6) {
            val a = ((phase + k / 6f) % 1f) * Math.PI.toFloat()
            val w = (r * cos(a)).let { kotlin.math.abs(it) }
            drawOval(line, Offset(ctr.x - w, ctr.y - r), Size(w * 2, r * 2), style = Stroke(1f))
        }
        // dots (nodes)
        for (k in 0 until 7) {
            val a = (phase * 2 * Math.PI + k * 0.9).toFloat()
            val p = Offset(ctr.x + r * 0.8f * cos(a), ctr.y + r * 0.45f * sin(a * 1.3f))
            drawCircle(c.cyan.copy(alpha = 0.7f), 3f, p)
        }
    }
}

// ---- settings rows -------------------------------------------------------------------------

@Composable
fun IconBadge(icon: ImageVector, tint: Color = Tervyn.colors.cyan, size: Dp = 40.dp) {
    val c = Tervyn.colors
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    tint: Color = Tervyn.colors.cyan,
    valueColor: Color? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Tervyn.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { IconBadge(icon, tint); Spacer(Modifier.width(Space.m)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = valueColor ?: c.text2)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.text3)
        }
    }
}

@Composable
fun ToggleSetting(icon: ImageVector?, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true, tint: Color = Tervyn.colors.cyan) {
    val c = Tervyn.colors
    SettingsRow(icon, title, subtitle, onClick = if (enabled) ({ onChange(!checked) }) else null, tint = tint) {
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = c.blue, checkedThumbColor = Color.White, uncheckedTrackColor = c.bg2, uncheckedThumbColor = c.text2, uncheckedBorderColor = c.cardBorder),
        )
    }
}

@Composable
fun ProxyInputField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    error: String? = null,
) {
    val c = Tervyn.colors
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = c.text3) } },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine, minLines = minLines,
        isError = error != null,
        supportingText = error?.let { { Text(it, color = c.red) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (secret && !visible) KeyboardType.Password else keyboardType, autoCorrectEnabled = false),
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (secret) ({
            IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (visible) "Hide" else "Show", tint = c.text2) }
        }) else null,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.cyan, unfocusedBorderColor = c.cardBorder, focusedLabelColor = c.cyan, unfocusedLabelColor = c.text2,
            cursorColor = c.cyan, focusedTextColor = c.text, unfocusedTextColor = c.text, focusedContainerColor = c.card, unfocusedContainerColor = c.card,
        ),
    )
}

@Composable
fun ProtocolCard(title: String, description: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, badge: String? = null) {
    val c = Tervyn.colors
    GlassCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, c.cyan, 44.dp)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
                    if (badge != null) { Spacer(Modifier.width(6.dp)); Pill(badge, c.green) }
                }
                Text(description, style = MaterialTheme.typography.bodySmall, color = c.text2)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.text3)
        }
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .15f)).border(1.dp, color.copy(alpha = .5f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
}

enum class Tone { INFO, SUCCESS, WARNING, ERROR }

@Composable
fun NotificationCard(title: String, text: String, tone: Tone = Tone.INFO, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = Tervyn.colors
    val color = when (tone) { Tone.INFO -> c.cyan; Tone.SUCCESS -> c.green; Tone.WARNING -> c.amber; Tone.ERROR -> c.red }
    val icon = when (tone) { Tone.INFO, Tone.SUCCESS -> Icons.Rounded.Info; Tone.WARNING -> Icons.Rounded.Warning; Tone.ERROR -> Icons.Rounded.ErrorOutline }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = .09f)).border(1.dp, color.copy(alpha = .35f), RoundedCornerShape(16.dp)).padding(Space.m),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = color)
            Text(text, style = MaterialTheme.typography.bodySmall, color = c.text2)
            if (action != null) { Spacer(Modifier.height(Space.s)); action() }
        }
    }
}

@Composable
fun LoadingIndicator(text: String? = null, modifier: Modifier = Modifier) {
    val c = Tervyn.colors
    Column(modifier.fillMaxWidth().padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = c.cyan, trackColor = c.bg2)
        if (text != null) { Spacer(Modifier.height(Space.m)); Text(text, color = c.text2, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = Tervyn.colors
    Column(modifier.fillMaxWidth().padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(icon, c.blue, 64.dp)
        Spacer(Modifier.height(Space.m))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.text2, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(Space.l)); action() }
    }
}

@Composable
fun ErrorState(text: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    EmptyState(Icons.Rounded.ErrorOutline, "Something went wrong", text, modifier, onRetry?.let { { NeonButton("Try again", it) } })
}

// ---- layout --------------------------------------------------------------------------------

@Composable
fun SectionHeader(title: String, icon: ImageVector? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = Tervyn.colors
    Row(modifier.fillMaxWidth().padding(top = Space.l, bottom = Space.s), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = c.amber, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(Space.s)) }
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Standard secondary screen: back button, title, subtitle, scrolling content with gutters. */
@Composable
fun ScreenScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Tervyn.colors
    Column(Modifier.fillMaxSize().background(c.backgroundGradient).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.padding(end = 4.dp).clip(CircleShape).background(c.card)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.text)
                }
            } else Spacer(Modifier.width(Space.s))
            Column(Modifier.weight(1f).padding(start = Space.s)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.text2, maxLines = 2)
            }
            actions()
        }
        val inner = Modifier.fillMaxWidth().weight(1f).padding(horizontal = Space.gutter)
        Column(if (scrollable) inner.verticalScroll(rememberScrollState()) else inner) {
            content()
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
fun StatTile(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, tint: Color = Tervyn.colors.cyan) {
    val c = Tervyn.colors
    GlassCard(modifier, padding = PaddingValues(Space.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = c.text2, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ModeToggle(mode: VpnMode, onChange: (VpnMode) -> Unit, modifier: Modifier = Modifier) {
    val c = Tervyn.colors
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(c.card).border(1.dp, c.cardBorder, RoundedCornerShape(50)).padding(4.dp)) {
        listOf(VpnMode.ALL_APPS to Icons.Rounded.Apps, VpnMode.ONLY_MY_SITES to Icons.Rounded.TrackChanges).forEach { (m, icon) ->
            val sel = m == mode
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (sel) c.primaryGradient else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable(role = Role.Tab) { onChange(m) }
                    .padding(vertical = 12.dp)
                    .semantics { contentDescription = "${m.label} mode" + if (sel) ", selected" else "" },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = if (sel) Color.White else c.text2, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(m.label, color = if (sel) Color.White else c.text2, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun AppTile(label: String, emoji: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Tervyn.colors
    Column(
        modifier.widthIn(min = 72.dp).clip(RoundedCornerShape(16.dp)).background(c.card)
            .border(1.dp, if (selected) c.cyan else c.cardBorder, RoundedCornerShape(16.dp))
            .clickable(role = Role.Checkbox, onClick = onClick).padding(vertical = 10.dp, horizontal = 8.dp)
            .semantics { contentDescription = "$label" + if (selected) ", added" else "" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 26.sp)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) c.cyan else c.text, maxLines = 1)
    }
}
