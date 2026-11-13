package com.tervyn.vpn

import android.net.VpnService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tervyn.vpn.domain.ProxyEndpoint
import com.tervyn.vpn.domain.ProxyProtocol
import com.tervyn.vpn.domain.ServerOrigin
import com.tervyn.vpn.net.ProxyDialer
import com.tervyn.vpn.net.ProxyTester
import com.tervyn.vpn.vpn.Phase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * On-device tests. Run with: ./gradlew connectedDebugAndroidTest
 *
 * Tests that start the VPN require the VPN permission to be granted once beforehand, e.g.
 *   adb shell appops set com.tervyn.vpn ACTIVATE_VPN allow
 * They are skipped otherwise. TEST_PROXY (instrumentation arg, e.g. -e proxy socks5://host:port)
 * enables the real end-to-end connection test.
 */
@RunWith(AndroidJUnit4::class)
class VpnServiceInstrumentedTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = ctx.applicationContext as TervynApp
    private val args get() = InstrumentationRegistry.getArguments()

    @Test fun permissionStateIsReported() {
        val needs = VpnService.prepare(ctx) != null
        assertEquals(needs, app.container.controller.needsPermission() != null)
    }

    @Test fun xrayBinaryIsPackagedAndExecutable() {
        assertTrue("libxray.so missing for this ABI", app.container.xray.available)
        val v = app.container.xray.version()
        assertTrue("unexpected version: $v", v?.contains("26.9.30") == true)
    }

    @Test fun keystoreEncryptionRoundTrip() {
        val c = app.container.cipher
        val enc = c.encrypt("p@ssw0rd")
        assertTrue(enc.startsWith("k1:") && !enc.contains("p@ssw0rd"))
        assertEquals("p@ssw0rd", c.decrypt(enc))
    }

    @Test fun socksDialerOnDevice() {
        // Local SOCKS5 server on the device, no auth, connecting to a local echo socket.
        val echo = ServerSocket(0)
        thread(isDaemon = true) { echo.accept().use { s -> val b = ByteArray(4); s.getInputStream().read(b); s.getOutputStream().write(b) } }
        val socks = ServerSocket(0)
        thread(isDaemon = true) {
            socks.accept().use { c ->
                val i = c.getInputStream(); val o = c.getOutputStream()
                i.read(ByteArray(3)); o.write(byteArrayOf(5, 0))
                val req = ByteArray(10); i.read(req); o.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 0))
                Socket("127.0.0.1", echo.localPort).use { up ->
                    val b = ByteArray(4); i.read(b); up.getOutputStream().write(b)
                    val r = ByteArray(4); up.getInputStream().read(r); o.write(r)
                }
            }
        }
        ProxyDialer.connect(ProxyEndpoint(ProxyProtocol.SOCKS5, "127.0.0.1", socks.localPort), "127.0.0.1", echo.localPort).use { s ->
            s.getOutputStream().write("ping".toByteArray())
            val b = ByteArray(4); s.getInputStream().read(b)
            assertEquals("ping", String(b))
        }
    }

    /** Full connect → verify → traffic → disconnect against a real proxy given as instrumentation arg `proxy`. */
    @Test fun connectThroughRealProxyAndDisconnect() = runBlocking {
        val proxyArg = args.getString("proxy")
        assumeTrue("pass -e proxy socks5://host:port", proxyArg != null)
        assumeTrue("grant VPN permission first", VpnService.prepare(ctx) == null)
        val ep = com.tervyn.vpn.core.ProxyList.parseLine(proxyArg!!)!!
        val c = app.container
        val server = c.servers.endpointServer(ep, ServerOrigin.CUSTOM, "Instrumented test")
        c.servers.save(server)
        c.settings.update { it.copy(onboardingDone = true, autoSwitch = false, selectedServerId = server.id) }
        c.controller.connect()
        withTimeout(90_000) { c.controller.status.first { it.phase == Phase.CONNECTED || it.phase == Phase.FAILED } }
        assertEquals(c.controller.status.value.error, Phase.CONNECTED, c.controller.status.value.phase)
        // The exit IP seen through the tunnel equals the proxy's verified exit IP.
        val direct = ProxyTester().test(ep)
        assertEquals(direct.exitIp, c.controller.status.value.exitIp)
        c.controller.disconnect()
        withTimeout(20_000) { c.controller.status.first { it.phase == Phase.DISCONNECTED } }
    }
}
