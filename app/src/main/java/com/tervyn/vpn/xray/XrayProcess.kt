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
    /** One password-protected 127.0.0.1 SOCKS5 endpoint per configured server, in input order. */
    val endpoints: List<ProxyEndpoint>,
    private val process: Process,
    private val log: ArrayDeque<String>,
) {
    val endpoint: ProxyEndpoint get() = endpoints.first()
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

        private fun freePorts(n: Int): List<Int> {
            // Hold all sockets open while picking so the ports are distinct.
            val sockets = (1..n).map { ServerSocket(0, 1, InetAddress.getLoopbackAddress()) }
            return sockets.map { it.localPort }.also { sockets.forEach { s -> s.close() } }
        }

        private fun newLocal(port: Int) = XrayConfig.LocalSocks(port, "t" + token(8), token(24))

        suspend fun start(binary: File, workDir: File, outbound: XrayOutbound, readyTimeoutMs: Long = 8_000): XrayProcess =
            startMulti(binary, workDir, listOf(outbound), readyTimeoutMs)

        /** Starts one Xray process exposing a separate authenticated local SOCKS5 endpoint for each outbound. */
        suspend fun startMulti(binary: File, workDir: File, outbounds: List<XrayOutbound>, readyTimeoutMs: Long = 10_000): XrayProcess =
            withContext(Dispatchers.IO) {
                require(outbounds.isNotEmpty())
                if (!binary.canExecute()) throw IOException("Xray binary is missing for this device ABI")
                workDir.mkdirs()
                val locals = freePorts(outbounds.size).map(::newLocal)
                val cfg = File(workDir, "xray-${locals.first().port}.json")
                cfg.writeText(if (outbounds.size == 1) XrayConfig.build(outbounds[0], locals[0]) else XrayConfig.buildMulti(outbounds.zip(locals)))
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
                fun listening(port: Int) = runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 200) } }.isSuccess
                val deadline = System.currentTimeMillis() + readyTimeoutMs
                var ready = false
                while (System.currentTimeMillis() < deadline && proc.isAlive) {
                    // Xray opens all inbounds during startup; first and last listening means it is up.
                    ready = listening(locals.first().port) && listening(locals.last().port)
                    if (ready) break
                    delay(100)
                }
                cfg.delete() // the config contains server credentials; Xray has already read it
                if (!ready) {
                    proc.destroyForcibly()
                    val tail = synchronized(log) { log.takeLast(3).joinToString(" | ") }
                    throw IOException("Xray failed to start" + if (tail.isNotEmpty()) ": $tail" else "")
                }
                XrayProcess(locals.map { ProxyEndpoint(ProxyProtocol.SOCKS5, "127.0.0.1", it.port, it.user, it.pass) }, proc, log)
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

    private val batchSlots = Semaphore(1)

    /**
     * Starts the given servers in as few Xray processes as possible (chunks of [chunk]) and passes one endpoint per
     * server to [block] (null for a server Xray refused to start). If a chunk fails to start because of one bad
     * config, the chunk is split in half recursively so the remaining servers still get tested.
     */
    suspend fun <T> withBatch(outbounds: List<XrayOutbound>, chunk: Int = 120, block: suspend (List<ProxyEndpoint?>) -> T): T = batchSlots.withPermit {
        val procs = mutableListOf<XrayProcess>()
        val eps = arrayOfNulls<ProxyEndpoint>(outbounds.size)
        suspend fun startRange(idx: List<Int>) {
            if (idx.isEmpty()) return
            try {
                val p = XrayProcess.startMulti(binary, workDir, idx.map { outbounds[it] })
                procs += p
                idx.forEachIndexed { k, i -> eps[i] = p.endpoints[k] }
            } catch (e: IOException) {
                if (idx.size == 1) return
                startRange(idx.subList(0, idx.size / 2))
                startRange(idx.subList(idx.size / 2, idx.size))
            }
        }
        try {
            outbounds.indices.toList().chunked(chunk).forEach { startRange(it) }
            block(eps.toList())
        } finally {
            procs.forEach { it.stop() }
        }
    }

    fun version(): String? = if (available) XrayProcess.version(binary) else null

    fun cleanupStaleConfigs() { workDir.listFiles { f -> f.name.startsWith("xray-") }?.forEach { it.delete() } }
}
