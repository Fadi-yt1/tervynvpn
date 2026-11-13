package com.tervyn.vpn.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import kotlin.math.cos
import kotlin.math.sin

/** Top brand bar used on secondary screens: back · logo · "Tervyn VPN" · optional settings shortcut. */
@Composable
fun BrandBar(onBack: (() -> Unit)?, onSettings: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val c = Tervyn.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp).clip(CircleShape).background(c.card).border(1.dp, c.cardBorder, CircleShape)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.text)
            }
            Spacer(Modifier.width(Space.s))
        }
        TervynLogo(40.dp)
        Spacer(Modifier.width(Space.s))
        Row(Modifier.weight(1f)) {
            Text("Tervyn ", style = MaterialTheme.typography.titleLarge, color = c.text, fontWeight = FontWeight.Bold)
            Text("VPN", style = MaterialTheme.typography.titleLarge, color = c.blue, fontWeight = FontWeight.Bold)
        }
        actions()
        if (onSettings != null) {
            IconButton(onClick = onSettings, modifier = Modifier.size(44.dp).clip(CircleShape).background(c.card).border(1.dp, c.cardBorder, CircleShape)) {
                Icon(Icons.Rounded.Settings, "Settings", tint = c.text)
            }
        }
    }
}

private fun shieldPath(w: Float, h: Float, ox: Float, oy: Float): Path = Path().apply {
    moveTo(ox + w * 0.5f, oy)
    cubicTo(ox + w * 0.68f, oy + h * 0.1f, ox + w * 0.86f, oy + h * 0.12f, ox + w, oy + h * 0.13f)
    cubicTo(ox + w, oy + h * 0.55f, ox + w * 0.86f, oy + h * 0.82f, ox + w * 0.5f, oy + h)
    cubicTo(ox + w * 0.14f, oy + h * 0.82f, ox, oy + h * 0.55f, ox, oy + h * 0.13f)
    cubicTo(ox + w * 0.14f, oy + h * 0.12f, ox + w * 0.32f, oy + h * 0.1f, ox + w * 0.5f, oy)
    close()
}

/**
 * Glowing shield illustration with orbit rings, used as the hero art of screens.
 * [icon] is drawn in the centre; [accent] tints the shield (cyan by default, green when protected).
 */
@Composable
fun GlowShield(icon: ImageVector?, size: Dp = 140.dp, accent: Color = Tervyn.colors.cyan, modifier: Modifier = Modifier, animated: Boolean = true, content: (@Composable () -> Unit)? = null) {
    val c = Tervyn.colors
    val t = rememberInfiniteTransition(label = "shield")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "spin")
    val glow by t.animateFloat(0.7f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    val g = if (animated) glow else 0.85f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f * g), Color.Transparent)), s / 2)
            // orbit rings
            rotate(if (animated) spin else 20f) {
                drawOval(Brush.sweepGradient(listOf(Color.Transparent, accent, c.purple, Color.Transparent)),
                    Offset(s * 0.02f, s * 0.36f), Size(s * 0.96f, s * 0.3f), style = Stroke(s * 0.012f, cap = StrokeCap.Round))
            }
            rotate(if (animated) -spin * 0.7f else -25f) {
                drawOval(Brush.sweepGradient(listOf(Color.Transparent, c.blue, accent.copy(alpha = .6f), Color.Transparent)),
                    Offset(s * 0.08f, s * 0.4f), Size(s * 0.84f, s * 0.22f), style = Stroke(s * 0.008f, cap = StrokeCap.Round))
            }
            val w = s * 0.56f; val h = s * 0.66f
            val ox = (s - w) / 2; val oy = (s - h) / 2
            val p = shieldPath(w, h, ox, oy)
            drawPath(p, Brush.verticalGradient(listOf(accent.copy(alpha = 0.95f), c.blue, Color(0xFF1A2BB8)), oy, oy + h))
            drawPath(p, Brush.linearGradient(listOf(Color.White.copy(alpha = .9f), accent, c.purple)), style = Stroke(s * 0.022f))
            val inner = shieldPath(w * 0.8f, h * 0.8f, ox + w * 0.1f, oy + h * 0.09f)
            drawPath(inner, Brush.verticalGradient(listOf(Color(0xFF0A2A7A), Color(0xFF07134A))))
            drawPath(inner, accent.copy(alpha = 0.6f), style = Stroke(s * 0.008f))
            // sparkles
            for (k in 0 until 5) {
                val a = Math.toRadians((spin * (if (animated) 1f else 0f) + k * 72).toDouble())
                drawCircle(accent.copy(alpha = .8f), s * 0.012f, Offset(s / 2 + (s * 0.46f * cos(a)).toFloat(), s / 2 + (s * 0.14f * sin(a)).toFloat()))
            }
        }
        when {
            content != null -> content()
            icon != null -> Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.28f))
        }
    }
}

/** Hero header: shield art on the left, two-tone title and description on the right. */
@Composable
fun HeroHeader(title: String, accentWord: String?, subtitle: String?, icon: ImageVector, accent: Color = Tervyn.colors.cyan) {
    val c = Tervyn.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
        GlowShield(icon, 132.dp, accent)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(twoTone(title, accentWord, c.text, c.cyan), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 30.sp), fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.text2)
            }
        }
    }
}

fun twoTone(title: String, accent: String?, base: Color, hi: Color) = buildAnnotatedString {
    if (accent == null || !title.contains(accent)) { withStyle(SpanStyle(color = base)) { append(title) }; return@buildAnnotatedString }
    val i = title.indexOf(accent)
    withStyle(SpanStyle(color = base)) { append(title.substring(0, i)) }
    withStyle(SpanStyle(color = hi)) { append(accent) }
    withStyle(SpanStyle(color = base)) { append(title.substring(i + accent.length)) }
}

/** Circular progress ring with a centre slot (used by the connecting screen). */
@Composable
fun ProgressRing(progress: Float, size: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Tervyn.colors
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring")
    val t = rememberInfiniteTransition(label = "ringglow")
    val head by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "head")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val stroke = s * 0.045f
            drawCircle(Brush.radialGradient(listOf(c.blue.copy(alpha = .3f), Color.Transparent)), s / 2)
            drawArc(c.text3.copy(alpha = .2f), 0f, 360f, false, Offset(stroke, stroke), Size(s - 2 * stroke, s - 2 * stroke), style = Stroke(stroke))
            drawArc(Brush.sweepGradient(listOf(c.cyan, c.blue, c.purple, c.cyan)), -90f, 360f * p, false,
                Offset(stroke, stroke), Size(s - 2 * stroke, s - 2 * stroke), style = Stroke(stroke, cap = StrokeCap.Round))
            rotate(head) {
                drawArc(c.cyan.copy(alpha = .35f), 0f, 30f, false, Offset(stroke * 2.6f, stroke * 2.6f), Size(s - 5.2f * stroke, s - 5.2f * stroke), style = Stroke(stroke * 0.4f, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** Speedometer-style gauge with ticks, labels and a needle. [value] null shows an idle needle. */
@Composable
fun ArcGauge(value: Double?, max: Double, unit: String, label: String, size: Dp = 280.dp, modifier: Modifier = Modifier) {
    val c = Tervyn.colors
    val frac by animateFloatAsState(((value ?: 0.0) / max).toFloat().coerceIn(0f, 1f), tween(500), label = "needle")
    Box(modifier.size(size, size), contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.size(size)) {
            val s = this.size.width
            val stroke = s * 0.05f
            val rect = Size(s - 2 * stroke, s - 2 * stroke)
            val tl = Offset(stroke, stroke)
            drawCircle(Brush.radialGradient(listOf(c.blue.copy(alpha = .25f), Color.Transparent), Offset(s / 2, s / 2), s / 2), s / 2, Offset(s / 2, s / 2))
            drawArc(c.text3.copy(alpha = .2f), 150f, 240f, false, tl, rect, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Brush.sweepGradient(listOf(c.purple, c.cyan, c.blue, c.purple), Offset(s / 2, s / 2)), 150f, 240f * frac, false, tl, rect, style = Stroke(stroke, cap = StrokeCap.Round))
            val cx = s / 2; val cy = s / 2
            for (i in 0..20) {
                val a = Math.toRadians((150 + 240.0 * i / 20))
                val r1 = s / 2 - stroke * 1.9f
                val r2 = r1 - if (i % 5 == 0) stroke * 0.9f else stroke * 0.45f
                drawLine(c.text2.copy(alpha = if (i % 5 == 0) .9f else .4f), Offset(cx + (r1 * cos(a)).toFloat(), cy + (r1 * sin(a)).toFloat()),
                    Offset(cx + (r2 * cos(a)).toFloat(), cy + (r2 * sin(a)).toFloat()), strokeWidth = s * 0.006f)
            }
            val na = Math.toRadians(150 + 240.0 * frac)
            val nl = s / 2 - stroke * 3.2f
            drawLine(Brush.linearGradient(listOf(c.blue, c.cyan)), Offset(cx, cy), Offset(cx + (nl * cos(na)).toFloat(), cy + (nl * sin(na)).toFloat()), strokeWidth = s * 0.018f, cap = StrokeCap.Round)
            drawCircle(c.bg2, s * 0.04f, Offset(cx, cy)); drawCircle(c.cyan, s * 0.04f, Offset(cx, cy), style = Stroke(s * 0.012f))
        }
        Column(Modifier.padding(top = size * 0.56f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value?.let { String.format(java.util.Locale.US, if (it >= 100) "%.0f" else "%.1f", it) } ?: "0.0", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = c.text)
            Text(unit, style = MaterialTheme.typography.titleMedium, color = c.cyan)
            Spacer(Modifier.height(4.dp))
            Text(label, color = c.cyan, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, c.cyan.copy(alpha = .6f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 4.dp))
        }
    }
}

/** Icon · label · value row used in information tables. */
@Composable
fun InfoRow(icon: ImageVector, label: String, value: String, valueColor: Color = Tervyn.colors.text, trailing: (@Composable () -> Unit)? = null) {
    val c = Tervyn.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.cyan, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(Space.m))
        Text(label, color = c.text2, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        trailing?.invoke()
        Text(value, color = valueColor, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.2f))
    }
}

/** Selectable card with a radio button (2-column option grids). */
@Composable
fun OptionCard(title: String, subtitle: String?, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Tervyn.colors.cyan) {
    val c = Tervyn.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.clip(shape)
            .background(if (selected) Brush.linearGradient(listOf(c.blue.copy(alpha = .35f), c.card)) else Brush.linearGradient(listOf(c.card, c.bg2)))
            .border(if (selected) 2.dp else 1.dp, if (selected) Brush.linearGradient(listOf(c.cyan, c.purple)) else Brush.linearGradient(listOf(c.cardBorder, c.cardBorder)), shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, style = MaterialTheme.typography.labelLarge, maxLines = 2)
            if (subtitle != null) Text(subtitle, color = c.text2, style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }
        RadioButton(selected, onClick, colors = RadioButtonDefaults.colors(selectedColor = c.cyan, unselectedColor = c.purple.copy(alpha = .7f)), modifier = Modifier.size(28.dp))
    }
}

/** Highlighted status banner (e.g. "Your Connection is Secure"). */
@Composable
fun StatusBanner(title: String, text: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val c = Tervyn.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(Brush.horizontalGradient(listOf(color.copy(alpha = .18f), c.card)))
            .border(1.5.dp, color.copy(alpha = .8f), shape).padding(Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(color.copy(alpha = .2f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(title, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text, color = c.text2, style = MaterialTheme.typography.bodySmall)
        }
        trailing?.invoke()
    }
}

/** Segmented tab row (e.g. Manual Entry · Import List · From Code). */
@Composable
fun SegmentedTabs(options: List<Pair<String, ImageVector>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Tervyn.colors
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).border(1.dp, c.cardBorder, RoundedCornerShape(22.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, (label, icon) ->
            val sel = i == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(if (sel) c.primaryGradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable(role = Role.Tab) { onSelect(i) }.padding(vertical = 10.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, null, tint = if (sel) Color.White else c.cyan, modifier = Modifier.size(24.dp))
                Text(label, color = if (sel) Color.White else c.text2, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

/** Smooth line chart with filled area and value dots. */
@Composable
fun LineChart(values: List<Pair<String, Double>>, unit: String, modifier: Modifier = Modifier) {
    val c = Tervyn.colors
    val max = (values.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(0.001)
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            Canvas(Modifier.fillMaxSize().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)) {
                val n = values.size
                if (n < 2) return@Canvas
                for (k in 0..3) drawLine(c.text3.copy(alpha = .15f), Offset(0f, size.height * k / 3), Offset(size.width, size.height * k / 3), 1f)
                val pts = values.mapIndexed { i, (_, v) -> Offset(size.width * i / (n - 1), size.height - (v / max * size.height * 0.9).toFloat()) }
                val line = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until n) {
                        val p0 = pts[i - 1]; val p1 = pts[i]; val mx = (p0.x + p1.x) / 2
                        cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
                    }
                }
                val area = Path().apply { addPath(line); lineTo(pts.last().x, size.height); lineTo(pts[0].x, size.height); close() }
                drawPath(area, Brush.verticalGradient(listOf(c.blue.copy(alpha = .45f), Color.Transparent)))
                drawPath(line, Brush.horizontalGradient(listOf(c.cyan, c.blue, c.purple)), style = Stroke(5f, cap = StrokeCap.Round))
                pts.forEach { drawCircle(c.bg, 8f, it); drawCircle(c.cyan, 8f, it, style = Stroke(3f)) }
            }
        }
        Row(Modifier.fillMaxWidth()) { values.forEach { (l, _) -> Text(l, color = c.text2, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) } }
        Text("max ${String.format(java.util.Locale.US, "%.1f", max)} $unit", color = c.text3, style = MaterialTheme.typography.labelSmall)
    }
}
