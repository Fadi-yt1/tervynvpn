package com.tervyn.vpn.xray

import com.tervyn.vpn.core.XrayOutbound
import com.tervyn.vpn.domain.ProxyEndpoint
import com.tervyn.vpn.domain.ProxyProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * One running Xray-core process exposing a password-protected SOCKS5 endpoint on 127.0.0.1.
 * The binary is packaged as `libxray.so` so Android extracts it to the (executable) native library dir.
 */
class XrayProcess private constructor(
    val endpoint: ProxyEndpoint,
    private val process: Process,
    private val log: ArrayDeque<String>,
) {
    val isAlive get() = process.isAlive
    fun recentLog(): List<String> = synchronized(log) { log.toList() }

    fun stop() {
        process.destroy()
        if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
    }

    companion object {
        private val random = SecureRandom()

        private fun token(n: Int): String {
            val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
            return (1..n).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        }

        private fun freePort(): Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

        suspend fun start(binary: File, workDir: File, outbound: XrayOutbound, readyTimeoutMs: Long = 8_000): XrayProcess =
            withContext(Dispatchers.IO) {
                if (!binary.canExecute()) throw IOException("Xray binary is missing for this device ABI")
                workDir.mkdirs()
                val local = XrayConfig.LocalSocks(freePort(), "t" + token(8), token(24))
                val cfg = File(workDir, "xray-${local.port}.json")
                cfg.writeText(XrayConfig.build(outbound, local))
                cfg.setReadable(false, false); cfg.setReadable(true, true)
                val pb = ProcessBuilder(binary.absolutePath, "run", "-c", cfg.absolutePath)
                    .directory(workDir).redirectErrorStream(true)
                pb.environment()["XRAY_LOCATION_ASSET"] = workDir.absolutePath
                val proc = pb.start()
                val log = ArrayDeque<String>()
                thread(isDaemon = true, name = "xray-log") {
                    runCatching {
                        proc.inputStream.bufferedReader().forEachLine { line ->
                            synchronized(log) { if (log.size > 60) log.removeFirst(); log.addLast(line.take(300)) }
                        }
                    }
                }
                val deadline = System.currentTimeMillis() + readyTimeoutMs
                var ready = false
                while (System.currentTimeMillis() < deadline && proc.isAlive) {
                    ready = runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", local.port), 200) } }.isSuccess
                    if (ready) break
                    delay(100)
                }
                cfg.delete() // the config contains server credentials; Xray has already read it
                if (!ready) {
                    proc.destroyForcibly()
                    val tail = synchronized(log) { log.takeLast(3).joinToString(" | ") }
                    throw IOException("Xray failed to start" + if (tail.isNotEmpty()) ": $tail" else "")
                }
                XrayProcess(ProxyEndpoint(ProxyProtocol.SOCKS5, "127.0.0.1", local.port, local.user, local.pass), proc, log)
            }

        /** Runs `xray version` (used by diagnostics and About). */
        fun version(binary: File): String? = runCatching {
            val p = ProcessBuilder(binary.absolutePath, "version").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor(3, TimeUnit.SECONDS)
            out.lineSequence().firstOrNull()?.trim()
        }.getOrNull()
    }
}

/** Keeps the active Xray instance and provides short-lived instances for testing. */
class XrayManager(private val binary: File, private val workDir: File) {
    private val active = ConcurrentHashMap<String, XrayProcess>()
    private val testSlots = Semaphore(4) // each instance costs ~20-40 MB RAM

    val available get() = binary.exists()

    suspend fun startActive(serverId: String, outbound: XrayOutbound): ProxyEndpoint {
        active[serverId]?.let { if (it.isAlive) return it.endpoint else active.remove(serverId) }
        val p = XrayProcess.start(binary, workDir, outbound)
        active[serverId] = p
        return p.endpoint
    }

    fun isRunning(serverId: String) = active[serverId]?.isAlive == true

    fun stop(serverId: String) { active.remove(serverId)?.stop() }

    fun stopAllExcept(keep: String?) {
        active.keys.filter { it != keep }.forEach { stop(it) }
    }

    suspend fun <T> withTemporary(outbound: XrayOutbound, block: suspend (ProxyEndpoint) -> T): T = testSlots.withPermit {
        val p = XrayProcess.start(binary, workDir, outbound)
        try { block(p.endpoint) } finally { p.stop() }
    }

    fun version(): String? = if (available) XrayProcess.version(binary) else null

    fun cleanupStaleConfigs() { workDir.listFiles { f -> f.name.startsWith("xray-") }?.forEach { it.delete() } }
}
