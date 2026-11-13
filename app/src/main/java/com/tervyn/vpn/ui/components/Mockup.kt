package com.tervyn.vpn.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tervyn.vpn.R
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import com.tervyn.vpn.utils.Format

/** Navigation function made available to deeply nested components (e.g. the header's settings button). */
val LocalGo = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Artwork taken from the Tervyn design mockups. */
@Composable
fun Art(@DrawableRes res: Int, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Fit, description: String? = null) {
    Image(painterResource(res), description, modifier, contentScale = scale)
}

@Composable
fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit, size: Dp = 52.dp) {
    val c = Tervyn.colors
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF13214A), c.bg2)))
            .border(1.5.dp, Brush.linearGradient(listOf(c.blue.copy(alpha = .9f), c.purple.copy(alpha = .6f))), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.5f)) }
}

/** Top header exactly as in the mockups: [back] · logo · "Tervyn VPN" · gear. Includes the corner glow swoosh. */
@Composable
fun MockHeader(onBack: (() -> Unit)? = null, showSettings: Boolean = true, subtitle: String? = null, logoSize: Dp = 60.dp) {
    val c = Tervyn.colors
    val go = LocalGo.current
    Row(
        Modifier.fillMaxWidth()
            .drawBehind {
                drawCircle(Brush.radialGradient(listOf(c.blue.copy(alpha = .35f), Color.Transparent), Offset(size.width * 0.8f, -size.height * 0.2f), size.width * 0.55f),
                    size.width * 0.55f, Offset(size.width * 0.8f, -size.height * 0.2f))
            }
            .padding(horizontal = Space.gutter, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) { CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, 48.dp); Spacer(Modifier.width(Space.m)) }
        TervynLogo(logoSize)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Row {
                Text("Tervyn ", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = c.text)
                Text("VPN", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E7BFF))
            }
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.titleSmall, color = c.text2, fontWeight = FontWeight.Normal)
        }
        if (showSettings) CircleIconButton(Icons.Rounded.Settings, "Settings", { go("settings") })
    }
}

/** Second header row of "style A" screens: circular back button, big title and subtitle, optional action. */
@Composable
fun TitleRow(title: String, subtitle: String?, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    val c = Tervyn.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) { CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, 56.dp); Spacer(Modifier.width(Space.l)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = c.text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = c.text2)
        }
        actions()
    }
}

/** Pill-shaped outlined action such as "⟳ Refresh" in the Servers mockup. */
@Composable
fun PillButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Tervyn.colors
    Row(
        modifier.clip(RoundedCornerShape(50)).background(c.card).border(1.5.dp, c.blue.copy(alpha = .6f), RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = c.text, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = c.text, style = MaterialTheme.typography.titleSmall)
    }
}

/** Round flag: the emoji is drawn larger than the circle and clipped so the flag fills it like the mockups. */
@Composable
fun RoundFlag(cc: String?, size: Dp = 56.dp) {
    val c = Tervyn.colors
    Box(Modifier.size(size).clip(CircleShape).background(c.bg2), contentAlignment = Alignment.Center) {
        if (cc == null) Art(R.drawable.art_earth, Modifier.size(size), ContentScale.Crop)
        else Text(Format.flag(cc), fontSize = (size.value * 1.15f).sp, textAlign = TextAlign.Center, lineHeight = (size.value * 1.2f).sp)
    }
}

/** Green signal bars used next to latencies. */
@Composable
fun SignalBars(level: Int, color: Color = Tervyn.colors.green, height: Dp = 22.dp) {
    val c = Tervyn.colors
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..4) Box(Modifier.width(height / 4.4f).height(height * (0.35f + 0.65f * i / 4)).clip(RoundedCornerShape(2.dp))
            .background(if (i <= level) color else c.text3.copy(alpha = .3f)))
    }
}

fun barsFor(ms: Int?) = when { ms == null -> 0; ms < 120 -> 4; ms < 250 -> 3; ms < 500 -> 2; else -> 1 }

/** Large outlined button with a glowing gradient border (e.g. "Cancel" on the connecting screen). */
@Composable
fun GlowOutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = Tervyn.colors
    val shape = RoundedCornerShape(50)
    Row(
        modifier.heightIn(min = 60.dp).clip(shape).background(c.bg2)
            .border(2.dp, Brush.horizontalGradient(listOf(c.blue, c.purple)), shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = Space.m),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = c.text, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Bottom navigation styled like the mockups: rounded tab cards, glowing selected tab with an underline. */
@Composable
fun TervynBottomBar(tabs: List<Triple<String, String, ImageVector>>, selected: String?, onSelect: (String) -> Unit) {
    val c = Tervyn.colors
    Row(Modifier.fillMaxWidth().background(c.bg).navigationBarsPadding().padding(horizontal = 2.dp)) {
        tabs.forEach { (route, label, icon) ->
            val sel = route == selected
            val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
            Column(
                Modifier.weight(1f).height(84.dp).clip(shape)
                    .background(if (sel) Brush.verticalGradient(listOf(Color(0xFF0F2259), c.bg2)) else Brush.verticalGradient(listOf(c.bg2, c.bg)))
                    .border(1.dp, if (sel) c.blue.copy(alpha = .55f) else c.cardBorder, shape)
                    .clickable(role = Role.Tab) { onSelect(route) }
                    .semantics { contentDescription = label },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, null, tint = if (sel) Color(0xFF29B6FF) else c.text2, modifier = Modifier.size(34.dp))
                Text(label, color = if (sel) Color(0xFF29B6FF) else c.text2, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(64.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (sel) Brush.horizontalGradient(listOf(c.cyan, c.blue)) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))))
            }
        }
    }
}

/** Logo for a preset app / site, using the artwork from the mockups when available. */
@DrawableRes
fun presetLogo(id: String): Int? = when (id) {
    "youtube" -> R.drawable.logo_app_youtube
    "instagram" -> R.drawable.logo_app_instagram
    "x" -> R.drawable.logo_app_x
    "discord" -> R.drawable.logo_app_discord
    "telegram" -> R.drawable.logo_app_telegram
    "chatgpt" -> R.drawable.logo_app_chatgpt
    "claude" -> R.drawable.logo_app_claude
    "netflix" -> R.drawable.logo_app_netflix
    "facebook" -> R.drawable.logo_app_facebook
    "google" -> R.drawable.logo_app_google
    else -> null
}

/** Site logo for a domain (falls back to null → globe icon). */
@DrawableRes
fun siteLogo(domain: String): Int? = when {
    domain.contains("google") -> R.drawable.logo_app_google
    domain.contains("facebook") || domain.contains("fb.") -> R.drawable.logo_app_facebook
    domain.contains("netflix") -> R.drawable.logo_app_netflix
    domain.contains("youtube") -> R.drawable.logo_app_youtube
    domain.contains("instagram") -> R.drawable.logo_app_instagram
    domain.contains("discord") -> R.drawable.logo_app_discord
    domain.contains("telegram") || domain == "t.me" -> R.drawable.logo_app_telegram
    domain.contains("openai") || domain.contains("chatgpt") -> R.drawable.logo_app_chatgpt
    domain.contains("claude") || domain.contains("anthropic") -> R.drawable.logo_app_claude
    domain == "x.com" || domain.contains("twitter") -> R.drawable.logo_app_x
    else -> null
}

/** Tile with a preset logo and label (App Presets / Quick Access grids). */
@Composable
fun LogoTile(label: String, @DrawableRes logo: Int?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, fallback: String = "🌐") {
    val c = Tervyn.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF0D1A3C), c.bg2)))
            .border(if (selected) 2.dp else 1.dp, if (selected) c.cyan else c.cardBorder, shape)
            .clickable(role = Role.Checkbox, onClick = onClick).padding(vertical = 10.dp, horizontal = 2.dp)
            .semantics { contentDescription = label + if (selected) ", added" else "" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (logo != null) Art(logo, Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)), ContentScale.Crop)
        else Text(fallback, fontSize = 34.sp)
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (selected) c.cyan else c.text, fontSize = 11.sp, maxLines = 1, softWrap = false, letterSpacing = (-0.2).sp)
    }
}
