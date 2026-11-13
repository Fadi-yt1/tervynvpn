package com.tervyn.vpn.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tervyn.vpn.security.PinHasher
import com.tervyn.vpn.ui.MainViewModel
import com.tervyn.vpn.ui.components.AnimatedGlobe
import com.tervyn.vpn.ui.components.IconBadge
import com.tervyn.vpn.ui.components.NeonButton
import com.tervyn.vpn.ui.components.NotificationCard
import com.tervyn.vpn.ui.components.ProxyInputField
import com.tervyn.vpn.ui.components.TervynLogo
import com.tervyn.vpn.ui.components.Tone
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import kotlinx.coroutines.delay

/** Animated brand splash shown after the system splash on first launch. */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val c = Tervyn.colors
    val scale = remember { Animatable(0.7f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(450))
        scale.animateTo(1f, tween(500))
        delay(450)
        onDone()
    }
    Box(Modifier.fillMaxSize().background(c.backgroundGradient), contentAlignment = Alignment.Center) {
        AnimatedGlobe(Modifier.size(320.dp).alpha(0.6f), active = true)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(alpha.value).scale(scale.value)) {
            TervynLogo(112.dp)
            Spacer(Modifier.height(Space.l))
            Text("Tervyn VPN", style = MaterialTheme.typography.displaySmall, color = c.text)
            Text("Fast • Secure • Free", style = MaterialTheme.typography.bodyMedium, color = c.text2)
        }
    }
}

private data class Page(val title: String, val text: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val pages = listOf(
    Page("One tap. Real VPN.", "Tervyn finds working servers, tests them over verified HTTPS and connects you to the fastest one automatically.", Icons.Rounded.Bolt),
    Page("Only the sites you choose", "Route everything, or only selected websites and apps through the VPN while the rest stays on your normal connection.", Icons.Rounded.TrackChanges),
    Page("Free, no account, no ads", "Use free public servers or bring your own (VLESS, VMess, Trojan, Shadowsocks, SOCKS5, HTTP). Nothing to sign up for.", Icons.Rounded.Public),
)

@Composable
fun OnboardingScreen(vm: MainViewModel, connect: () -> Unit) {
    val c = Tervyn.colors
    var page by rememberSaveable { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().background(c.backgroundGradient).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = Space.gutter).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Space.xl))
        TervynLogo(72.dp)
        Spacer(Modifier.height(Space.xl))
        if (page < pages.size) {
            val p = pages[page]
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                AnimatedGlobe(Modifier.fillMaxSize(), active = true)
                IconBadge(p.icon, c.cyan, 84.dp)
            }
            Spacer(Modifier.height(Space.xl))
            Text(p.title, style = MaterialTheme.typography.headlineSmall, color = c.text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.s))
            Text(p.text, style = MaterialTheme.typography.bodyLarge, color = c.text2, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(pages.size + 1) { i ->
                    Box(Modifier.size(if (i == page) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(if (i == page) c.cyan else c.text3))
                }
            }
            Spacer(Modifier.height(Space.xl))
            NeonButton("Continue", { page++ }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.s))
            NeonButton("Skip", { page = pages.size }, Modifier.fillMaxWidth(), secondary = true)
        } else {
            Text("Before you start", style = MaterialTheme.typography.headlineSmall, color = c.text)
            Spacer(Modifier.height(Space.l))
            NotificationCard("Free servers are run by unknown operators",
                "Free public proxies can be slow, short-lived and untrusted. Tervyn verifies HTTPS certificates, but avoid banking and other sensitive logins over free servers. Use your own servers for anything important.",
                Tone.WARNING)
            Spacer(Modifier.height(Space.m))
            NotificationCard("What Android will ask",
                "Android shows a one-time VPN connection request. Tervyn also asks to show notifications for the connection status. No other permissions are needed.",
                Tone.INFO)
            Spacer(Modifier.height(Space.m))
            NotificationCard("No tracking", "No account, no ads, no analytics. Settings and connection history stay on this phone.", Tone.SUCCESS)
            Spacer(Modifier.height(Space.xl))
            NeonButton("Get started & connect", {
                vm.updateSettings { it.copy(onboardingDone = true) }
                connect()
            }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Shield)
            Spacer(Modifier.height(Space.s))
            NeonButton("Explore first", { vm.updateSettings { it.copy(onboardingDone = true) } }, Modifier.fillMaxWidth(), secondary = true)
        }
        Spacer(Modifier.height(Space.xl))
    }
}

@Composable
fun LockScreen(vm: MainViewModel, biometricEnabled: Boolean, onBiometric: () -> Unit, onUnlocked: () -> Unit) {
    val c = Tervyn.colors
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { if (biometricEnabled) onBiometric() }
    Column(
        Modifier.fillMaxSize().background(c.backgroundGradient).statusBarsPadding().navigationBarsPadding().padding(Space.gutter),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        TervynLogo(80.dp)
        Spacer(Modifier.height(Space.l))
        IconBadge(Icons.Rounded.Lock, c.cyan, 48.dp)
        Spacer(Modifier.height(Space.m))
        Text("Tervyn is locked", style = MaterialTheme.typography.titleLarge, color = c.text)
        Spacer(Modifier.height(Space.l))
        ProxyInputField(pin, { if (it.length <= 8 && it.all(Char::isDigit)) pin = it }, "PIN", keyboardType = KeyboardType.NumberPassword, secret = true, error = error)
        Spacer(Modifier.height(Space.m))
        NeonButton("Unlock", {
            if (attempts >= 5) { error = "Too many attempts. Wait a moment."; return@NeonButton }
            if (vm.verifyPin(pin)) onUnlocked() else { attempts++; error = "Wrong PIN"; pin = "" }
        }, Modifier.fillMaxWidth(), enabled = PinHasher.isValidPin(pin))
        if (biometricEnabled) {
            Spacer(Modifier.height(Space.s))
            NeonButton("Use fingerprint / face", onBiometric, Modifier.fillMaxWidth(), icon = Icons.Rounded.Fingerprint, secondary = true)
        }
        LaunchedEffect(attempts) { if (attempts >= 5) { delay(30_000); attempts = 0; error = null } }
        Spacer(Modifier.width(1.dp))
    }
}
