package com.tervyn.vpn.xray

import com.tervyn.vpn.core.Links
import com.tervyn.vpn.core.XrayOutbound
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Validates the parser and generated Xray configs against real subscription files.
 * Runs only when TERVYN_SUBS_DIR points at downloaded fastest.txt / podpiska.txt (never committed).
 */
class RealSubscriptionTest {
    private val dir = System.getenv("TERVYN_SUBS_DIR")?.let(::File)
    private val bin = File(System.getProperty("xray.host.binary") ?: "../xray/host/xray")

    private fun xrayAccepts(items: List<XrayOutbound>, work: File): Boolean {
        val f = File(work, "m.json")
        f.writeText(XrayConfig.buildMulti(items.mapIndexed { i, o -> o to XrayConfig.LocalSocks(20000 + i, "u", "p") }))
        val p = ProcessBuilder(bin.absolutePath, "run", "-test", "-c", f.absolutePath).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(60, TimeUnit.SECONDS)
        if (p.exitValue() != 0 && items.size == 1) println("REJECTED ${items[0].protocol} ${items[0].network}/${items[0].security}: " + out.lines().last { it.isNotBlank() }.take(220))
        return p.exitValue() == 0
    }

    private fun bisect(items: List<XrayOutbound>, work: File): Int {
        if (items.isEmpty() || xrayAccepts(items, work)) return 0
        if (items.size == 1) return 1
        return bisect(items.subList(0, items.size / 2), work) + bisect(items.subList(items.size / 2, items.size), work)
    }

    @Test fun realSubscriptions() {
        assumeTrue("set TERVYN_SUBS_DIR", dir != null && dir.isDirectory)
        assumeTrue(bin.canExecute())
        val work = kotlin.io.path.createTempDirectory("realsubs").toFile()
        for (name in listOf("fastest.txt", "podpiska.txt")) {
            val sub = Links.parseSubscription(File(dir, name).readText())
            println("== $name: title=${sub.title} interval=${sub.updateIntervalHours}h parsed=${sub.links.size} skipped=${sub.skipped}")
            println("   protocols=" + sub.links.groupingBy { it.second.displayProtocol.substringBefore(" ·") }.eachCount())
            println("   sample names=" + sub.links.take(3).map { Links.cleanName(it.second.name) })
            File(dir, name).readText().lines().filter { it.contains("://") && !it.startsWith("#") }
                .mapNotNull { l -> runCatching { Links.parse(l) }.exceptionOrNull()?.let { l.substringBefore("://") + ": " + it.message } }
                .filter { "not supported by Xray" !in it }
                .filter { "no TLS" !in it && "no encryption" !in it }
                .groupingBy { it }.eachCount().forEach { (k, v) -> println("   INVALID x$v $k") }
            val rejected = sub.links.map { it.second }.chunked(200).sumOf { bisect(it, work) }
            println("   xray rejected=$rejected of ${sub.links.size}")
        }
        work.deleteRecursively()
    }
}
