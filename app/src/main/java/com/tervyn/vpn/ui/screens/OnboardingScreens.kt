package com.tervyn.vpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tervyn.vpn.core.BuiltinServers
import com.tervyn.vpn.ui.MainViewModel
import com.tervyn.vpn.ui.components.BrandMark
import com.tervyn.vpn.ui.components.GhostButton
import com.tervyn.vpn.ui.components.IconBadge
import com.tervyn.vpn.ui.components.PrimaryButton
import com.tervyn.vpn.ui.components.SecondaryButton
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import kotlinx.coroutines.launch

private data class Page(val title: String, val text: String, val points: List<Triple<ImageVector, String, String>>)

private val pages = listOf(
    Page(
        "Private browsing, no strings", "A free VPN with no account, no ads and no tracking.",
        listOf(
            Triple(Icons.Rounded.Public, "${BuiltinServers.all.size} countries built in", "Community servers verified on your phone before use"),
            Triple(Icons.Rounded.VerifiedUser, "Nothing fake", "A server is shown as working only after a real HTTPS check"),
            Triple(Icons.Rounded.VpnKey, "Bring your own", "VLESS, VMess, Trojan, Shadowsocks, Hysteria2, SOCKS5, HTTP"),
        ),
    ),
    Page(
        "Two independent shields", "Use the VPN, Secure DNS, or both — they work separately.",
        listOf(
            Triple(Icons.Rounded.Bolt, "VPN", "Connect a server and your apps' traffic goes through it"),
            Triple(Icons.Rounded.Dns, "Secure DNS", "Encrypts lookups while no server is connected; pauses while one is"),
            Triple(Icons.Rounded.Language, "Only my sites", "Route just the websites you choose, keep everything else direct"),
        ),
    ),
    Page(
        "Before you connect", "Android asks for your consent once.",
        listOf(
            Triple(Icons.Rounded.VpnKey, "VPN permission", "Needed for the tunnel — both for servers and for Secure DNS"),
            Triple(Icons.Rounded.Notifications, "Notifications", "A status notification is required while the tunnel runs"),
            Triple(Icons.Rounded.Lock, "Honest limits", "TCP only (no UDP games/VoIP), free servers may go away — the app tells you"),
        ),
    ),
)

@Composable
fun OnboardingScreen(vm: MainViewModel, onDone: () -> Unit) {
    val c = Tervyn.colors
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    fun finish() { vm.updateSettings { it.copy(onboardingDone = true) }; vm.refreshServers(); onDone() }
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(36.dp); Spacer(Modifier.width(Space.m))
            Text("Tervyn", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.weight(1f))
            if (pager.currentPage < pages.lastIndex) GhostButton("Skip", ::finish, color = c.text2)
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val p = pages[i]
            Column(Modifier.fillMaxSize().padding(horizontal = Space.gutter), verticalArrangement = Arrangement.Center) {
                Text(p.title, style = MaterialTheme.typography.displaySmall, color = c.text)
                Spacer(Modifier.height(Space.s))
                Text(p.text, style = MaterialTheme.typography.bodyLarge, color = c.text2)
                Spacer(Modifier.height(Space.xxl))
                p.points.forEach { (icon, t, d) ->
                    Row(Modifier.padding(vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(icon, c.accent2, 48.dp)
                        Spacer(Modifier.width(Space.l))
                        Column { Text(t, style = MaterialTheme.typography.titleMedium, color = c.text); Text(d, style = MaterialTheme.typography.bodySmall, color = c.text2) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                Box(Modifier.padding(4.dp).size(if (i == pager.currentPage) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(if (i == pager.currentPage) c.accent else c.cardHigh))
            }
        }
        Spacer(Modifier.height(Space.l))
        Row(Modifier.padding(horizontal = Space.gutter, vertical = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            if (pager.currentPage == pages.lastIndex) PrimaryButton("Get started", ::finish, Modifier.weight(1f))
            else PrimaryButton("Continue", { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, Modifier.weight(1f))
        }
    }
}

/** PIN lock shown when app lock is enabled. Biometrics (if enabled) are offered via [onBiometric]. */
@Composable
fun LockScreen(vm: MainViewModel, biometricEnabled: Boolean, onBiometric: () -> Unit, onUnlocked: () -> Unit) {
    val c = Tervyn.colors
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (biometricEnabled) onBiometric() }
    fun submit() { if (vm.verifyPin(pin)) onUnlocked() else { error = true; pin = "" } }
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(Space.gutter), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        BrandMark(72.dp)
        Spacer(Modifier.height(Space.xl))
        Text("Enter your PIN", style = MaterialTheme.typography.headlineSmall, color = c.text)
        Text(if (error) "Wrong PIN, try again" else "Tervyn is locked", style = MaterialTheme.typography.bodyMedium, color = if (error) c.danger else c.text2)
        Spacer(Modifier.height(Space.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(maxOf(4, pin.length)) { i -> Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < pin.length) c.accent else c.cardHigh)) }
        }
        Spacer(Modifier.height(Space.xxl))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", if (biometricEnabled) "bio" else "", "0", "del")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.l), modifier = Modifier.padding(vertical = Space.s)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(if (k.isEmpty()) c.bg else c.card)
                            .then(if (k.isEmpty()) Modifier else Modifier.clickable(role = Role.Button) {
                                error = false
                                when (k) {
                                    "del" -> pin = pin.dropLast(1)
                                    "bio" -> onBiometric()
                                    else -> if (pin.length < 8) { pin += k; if (pin.length >= 4 && vm.verifyPin(pin)) onUnlocked() }
                                }
                            })
                            .semantics { contentDescription = when (k) { "del" -> "Delete"; "bio" -> "Use biometrics"; else -> k } },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (k) {
                            "del" -> Icon(Icons.AutoMirrored.Rounded.Backspace, null, tint = c.text2)
                            "bio" -> Icon(Icons.Rounded.Fingerprint, null, tint = c.accent2)
                            "" -> Unit
                            else -> Text(k, style = MaterialTheme.typography.headlineSmall, color = c.text)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.xl))
        SecondaryButton("Unlock", ::submit, Modifier.fillMaxWidth(), enabled = pin.length >= 4)
        @Suppress("unused") val align = TextAlign.Center
    }
}
