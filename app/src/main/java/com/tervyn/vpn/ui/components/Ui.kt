package com.tervyn.vpn.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tervyn.vpn.R
import com.tervyn.vpn.ui.theme.Radius
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import com.tervyn.vpn.utils.Format

// ---- brand ---------------------------------------------------------------------------------

@Composable
fun BrandMark(size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Image(
        painterResource(R.drawable.ic_launcher_art), null,
        modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).semantics { contentDescription = "Tervyn logo" },
        contentScale = ContentScale.Crop,
    )
}

// ---- screen frame --------------------------------------------------------------------------

/**
 * Standard screen: a compact top bar (back button, title, optional actions) above scrollable content with
 * the app gutter. Top-level tab screens pass `onBack = null` and get a large title instead.
 */
@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollable: Boolean = true,
    gutter: Dp = Space.gutter,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Tervyn.colors
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        if (title.isNotEmpty() || onBack != null) TopBar(title, onBack, subtitle, actions)
        val inner = Modifier.fillMaxWidth().weight(1f)
        Column(if (scrollable) inner.verticalScroll(rememberScrollState()) else inner) {
            Column(Modifier.padding(horizontal = gutter)) {
                content()
                if (scrollable) Spacer(Modifier.height(Space.xxl))
            }
        }
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val c = Tervyn.colors
    if (onBack == null) {
        Row(Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.m, top = Space.l, bottom = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineLarge, color = c.text)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.text2)
            }
            actions()
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = c.text)
            }
            Column(Modifier.weight(1f).padding(start = Space.xs)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            actions()
        }
    }
}

// ---- surfaces ------------------------------------------------------------------------------

@Composable
fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(Space.l),
    highlighted: Boolean = false,
    tone: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Tervyn.colors
    val shape = RoundedCornerShape(Radius.l)
    val bg = tone?.copy(alpha = 0.10f) ?: c.card
    val border = when {
        tone != null -> BorderStroke(1.dp, tone.copy(alpha = 0.35f))
        highlighted -> BorderStroke(1.5.dp, c.accent)
        else -> BorderStroke(1.dp, c.border)
    }
    Column(
        modifier.fillMaxWidth().clip(shape).background(bg).border(border, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, loading: Boolean = false) {
    val c = Tervyn.colors
    val shape = RoundedCornerShape(Radius.m)
    Row(
        modifier.heightIn(min = 54.dp).clip(shape).background(c.accent).alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick).padding(horizontal = Space.xl, vertical = Space.m),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) { CircularProgressIndicator(Modifier.size(18.dp), color = c.onAccent, strokeWidth = 2.dp); Spacer(Modifier.width(Space.s)) }
        else if (icon != null) { Icon(icon, null, tint = c.onAccent, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(Space.s)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.onAccent, textAlign = TextAlign.Center)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, danger: Boolean = false) {
    val c = Tervyn.colors
    val shape = RoundedCornerShape(Radius.m)
    val color = if (danger) c.danger else c.text
    Row(
        modifier.heightIn(min = 54.dp).clip(shape).background(c.cardHigh).border(1.dp, if (danger) c.danger.copy(alpha = .4f) else c.border, shape)
            .alpha(if (enabled) 1f else 0.45f).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = Space.xl, vertical = Space.m),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(Space.s)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = color, textAlign = TextAlign.Center)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Tervyn.colors.accent2) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = color,
        modifier = modifier.clip(RoundedCornerShape(Radius.s)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = Space.m, vertical = Space.s),
    )
}

// ---- rows ----------------------------------------------------------------------------------

@Composable
fun IconBadge(icon: ImageVector, tint: Color = Tervyn.colors.accent, size: Dp = 38.dp, shape: RoundedCornerShape = RoundedCornerShape(12.dp)) {
    Box(Modifier.size(size).clip(shape).background(tint.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun SettingRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    tint: Color = Tervyn.colors.accent,
    subtitleColor: Color? = null,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Tervyn.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp)
            .then(if (onClick != null && enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.5f).padding(vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { IconBadge(icon, tint); Spacer(Modifier.width(Space.m)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor ?: c.text2)
        }
        Spacer(Modifier.width(Space.s))
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.text3)
        }
    }
}

@Composable
fun SwitchRow(icon: ImageVector?, title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, tint: Color = Tervyn.colors.accent, onChange: (Boolean) -> Unit) {
    SettingRow(icon, title, subtitle, onClick = { onChange(!checked) }, tint = tint, enabled = enabled) {
        TervynSwitch(checked, onChange, enabled)
    }
}

@Composable
fun TervynSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = Tervyn.colors
    Switch(
        checked = checked, onCheckedChange = onChange, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = c.accent, checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent,
            uncheckedTrackColor = c.cardHigh, uncheckedThumbColor = c.text2, uncheckedBorderColor = c.borderStrong,
        ),
    )
}

@Composable
fun Divider() { Box(Modifier.fillMaxWidth().height(1.dp).background(Tervyn.colors.border)) }

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = Tervyn.colors
    Row(modifier.fillMaxWidth().padding(top = Space.xl, bottom = Space.s), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = c.text3, modifier = Modifier.weight(1f), letterSpacing = 1.2.sp)
        action?.invoke()
    }
}

// ---- small pieces --------------------------------------------------------------------------

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Text(
        text, color = if (filled) Color.Black else color, style = MaterialTheme.typography.labelSmall,
        modifier = modifier.clip(RoundedCornerShape(50)).background(if (filled) color else color.copy(alpha = .14f)).padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

@Composable
fun FlagTile(cc: String?, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val c = Tervyn.colors
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(c.cardHigh).border(1.dp, c.border, RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) { Text(Format.flag(cc), fontSize = (size.value * 0.5f).sp) }
}

/** Latency as a coloured value, or the test state when none is known. */
@Composable
fun LatencyBadge(latencyMs: Int?, alive: Boolean, tested: Boolean, testing: Boolean = false) {
    val c = Tervyn.colors
    when {
        testing -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(12.dp), color = c.accent2, strokeWidth = 1.5.dp)
            Spacer(Modifier.width(6.dp)); Text("Testing", style = MaterialTheme.typography.labelMedium, color = c.text2)
        }
        alive && latencyMs != null -> {
            val color = when { latencyMs < 150 -> c.success; latencyMs < 400 -> c.warning; else -> c.danger }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text("$latencyMs ms", style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
        tested -> Pill("Offline", c.danger)
        else -> Pill("Not tested", c.text3)
    }
}

enum class Tone { INFO, SUCCESS, WARNING, ERROR }

@Composable
fun Banner(title: String, text: String?, tone: Tone = Tone.INFO, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = Tervyn.colors
    val color = when (tone) { Tone.INFO -> c.accent2; Tone.SUCCESS -> c.success; Tone.WARNING -> c.warning; Tone.ERROR -> c.danger }
    val icon = when (tone) { Tone.INFO -> Icons.Rounded.Info; Tone.SUCCESS -> Icons.Rounded.CheckCircle; Tone.WARNING -> Icons.Rounded.WarningAmber; Tone.ERROR -> Icons.Rounded.ErrorOutline }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(color.copy(alpha = .10f)).border(1.dp, color.copy(alpha = .3f), RoundedCornerShape(Radius.m)).padding(Space.m),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp).padding(top = 1.dp))
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = color)
            if (text != null) Text(text, style = MaterialTheme.typography.bodySmall, color = c.text2)
            if (action != null) { Spacer(Modifier.height(Space.s)); action() }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = Tervyn.colors
    Column(modifier.fillMaxWidth().padding(vertical = Space.xxl, horizontal = Space.l), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(icon, c.accent, 64.dp, RoundedCornerShape(20.dp))
        Spacer(Modifier.height(Space.l))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xs))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.text2, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(Space.l)); action() }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Tervyn.colors.text) {
    val c = Tervyn.colors
    Column(modifier.clip(RoundedCornerShape(Radius.m)).background(c.card).border(1.dp, c.border, RoundedCornerShape(Radius.m)).padding(Space.m)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.text3, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun KeyValueRow(label: String, value: String, valueColor: Color = Tervyn.colors.text) {
    val c = Tervyn.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.text2, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Tervyn.colors
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(Radius.m)).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(Radius.s)).background(if (sel) c.accent else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }.padding(vertical = 12.dp)
                    .semantics { contentDescription = label + if (sel) ", selected" else "" },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = MaterialTheme.typography.labelMedium, color = if (sel) c.onAccent else c.text2, maxLines = 1) }
        }
    }
}

@Composable
fun TervynTextField(
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
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Tervyn.colors
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = c.text3) } },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine, minLines = minLines,
        isError = error != null,
        supportingText = error?.let { { Text(it, color = c.danger) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (secret && !visible) KeyboardType.Password else keyboardType, autoCorrectEnabled = false),
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = when {
            secret -> ({ IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (visible) "Hide" else "Show", tint = c.text2) } })
            trailing != null -> trailing
            else -> null
        },
        shape = RoundedCornerShape(Radius.m),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent, unfocusedBorderColor = c.borderStrong, focusedLabelColor = c.accent, unfocusedLabelColor = c.text2,
            cursorColor = c.accent, focusedTextColor = c.text, unfocusedTextColor = c.text, focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
            errorBorderColor = c.danger, errorLabelColor = c.danger,
        ),
    )
}

@Composable
fun Loading(text: String? = null, modifier: Modifier = Modifier) {
    val c = Tervyn.colors
    Column(modifier.fillMaxWidth().padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = c.accent, trackColor = c.cardHigh)
        if (text != null) { Spacer(Modifier.height(Space.m)); Text(text, color = c.text2, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
    }
}
