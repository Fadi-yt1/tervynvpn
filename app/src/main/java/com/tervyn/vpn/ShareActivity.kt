package com.tervyn.vpn

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tervyn.vpn.core.Domains
import com.tervyn.vpn.core.Links
import com.tervyn.vpn.domain.VpnMode
import com.tervyn.vpn.ui.components.Banner
import com.tervyn.vpn.ui.components.BrandMark
import com.tervyn.vpn.ui.components.Card
import com.tervyn.vpn.ui.components.PrimaryButton
import com.tervyn.vpn.ui.components.SecondaryButton
import com.tervyn.vpn.ui.components.Tone
import com.tervyn.vpn.ui.theme.Tervyn
import com.tervyn.vpn.ui.theme.TervynTheme
import kotlinx.coroutines.launch

/**
 * Receives Share → "Add to Tervyn VPN" from browsers and other apps. The shared text is untrusted input:
 * a website address is normalised and confirmed by the user before it is added to *Only my sites*; a
 * server share link is handed to the import screen.
 */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT)?.take(4096) else null
        val link = text?.lineSequence()?.map { it.trim() }?.firstOrNull { Links.isShareLink(it) }
        if (link != null) {
            PendingActions.importLink.value = link
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
            return
        }
        val domain = text?.let { Domains.normalize(it) }
        val container = (application as TervynApp).container
        setContent {
            TervynTheme {
                val c = Tervyn.colors
                val scope = rememberCoroutineScope()
                var done by remember { mutableStateOf<String?>(null) }
                Card(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandMark(36.dp); Spacer(Modifier.width(12.dp))
                        Text("Add to Only my sites", style = MaterialTheme.typography.titleMedium, color = c.text)
                    }
                    Spacer(Modifier.height(16.dp))
                    when {
                        done != null -> {
                            Banner("Added", done!!, Tone.SUCCESS)
                            Spacer(Modifier.height(12.dp))
                            PrimaryButton("Close", { finish() }, Modifier.fillMaxWidth())
                        }
                        domain == null -> {
                            Banner("Not a website", "The shared content does not contain a valid web address or server link.", Tone.ERROR)
                            Spacer(Modifier.height(12.dp))
                            SecondaryButton("Close", { finish() }, Modifier.fillMaxWidth())
                        }
                        else -> {
                            Text(domain, style = MaterialTheme.typography.headlineSmall, color = c.accent2)
                            Spacer(Modifier.height(4.dp))
                            Text("Traffic to $domain and its subdomains will use the server when \"Only my sites\" is on.", style = MaterialTheme.typography.bodyMedium, color = c.text2)
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SecondaryButton("Cancel", { finish() }, Modifier.weight(1f))
                                PrimaryButton("Add", {
                                    scope.launch {
                                        container.domains.add(domain)
                                        container.controller.reloadDomains()
                                        val mode = container.settings.current().mode
                                        done = "$domain was added." + if (mode != VpnMode.ONLY_MY_SITES) " Switch to Only my sites in the app to route only your sites." else ""
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
