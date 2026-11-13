package com.tervyn.vpn

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tervyn.vpn.core.Domains
import com.tervyn.vpn.ui.components.GlassCard
import com.tervyn.vpn.ui.components.NeonButton
import com.tervyn.vpn.ui.components.NotificationCard
import com.tervyn.vpn.ui.components.Tone
import com.tervyn.vpn.ui.components.TervynLogo
import com.tervyn.vpn.ui.theme.Tervyn
import com.tervyn.vpn.ui.theme.TervynTheme
import kotlinx.coroutines.launch

/**
 * Receives Share → "Add to Tervyn VPN" from browsers and other apps. The shared text is treated as
 * untrusted input: only an http(s) URL or bare host name is accepted, normalised and confirmed by the user.
 */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT)?.take(4096) else null
        val domain = text?.let { Domains.normalize(it) }
        val container = (application as TervynApp).container
        setContent {
            TervynTheme {
                val c = Tervyn.colors
                val scope = rememberCoroutineScope()
                var done by remember { mutableStateOf<String?>(null) }
                GlassCard(Modifier.padding(16.dp), highlight = true) {
                    Row { TervynLogo(40.dp); Spacer(Modifier.padding(6.dp)); Text("Add to Only My Sites", style = MaterialTheme.typography.titleMedium, color = c.text) }
                    Spacer(Modifier.height(12.dp))
                    when {
                        done != null -> {
                            NotificationCard("Added", done!!, Tone.SUCCESS)
                            Spacer(Modifier.height(12.dp))
                            NeonButton("Close", { finish() }, Modifier.fillMaxWidth())
                        }
                        domain == null -> {
                            NotificationCard("Not a website", "The shared content does not contain a valid web address.", Tone.ERROR)
                            Spacer(Modifier.height(12.dp))
                            NeonButton("Close", { finish() }, Modifier.fillMaxWidth(), secondary = true)
                        }
                        else -> {
                            Text(domain, style = MaterialTheme.typography.headlineSmall, color = c.cyan)
                            Text("Traffic to $domain and its subdomains will use Tervyn VPN when \"Only My Sites\" mode is on.",
                                style = MaterialTheme.typography.bodyMedium, color = c.text2)
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                NeonButton("Cancel", { finish() }, Modifier.weight(1f), secondary = true)
                                NeonButton("Add", {
                                    scope.launch {
                                        container.domains.add(domain)
                                        container.controller.reloadDomains()
                                        val mode = container.settings.current().mode
                                        done = "$domain was added." + if (mode != com.tervyn.vpn.domain.VpnMode.ONLY_MY_SITES) " Switch to Only My Sites mode to route only your sites." else ""
                                    }
                                }, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
