package com.tervyn.vpn.net

import com.tervyn.vpn.vpn.Packets

/** Minimal DNS wire-format support (RFC 1035) for the local resolver. */
object DnsMessage {
    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    const val TYPE_SVCB = 64
    const val TYPE_HTTPS = 65
    const val RCODE_NOERROR = 0
    const val RCODE_SERVFAIL = 2
    const val RCODE_NXDOMAIN = 3
    const val RCODE_REFUSED = 5

    data class Query(val id: Int, val flags: Int, val name: String, val qtype: Int, val qclass: Int, val questionEnd: Int)

    /** Parses the first question of a standard query. Returns null for responses or malformed input. */
    fun parseQuery(msg: ByteArray): Query? {
        if (msg.size < 12) return null
        val flags = Packets.u16(msg, 2)
        if ((flags and 0x8000) != 0) return null // is a response
        if (((flags ushr 11) and 0xF) != 0) return null // opcode must be QUERY
        if (Packets.u16(msg, 4) < 1) return null
        val sb = StringBuilder()
        var i = 12
        var labels = 0
        while (true) {
            if (i >= msg.size) return null
            val len = Packets.u8(msg, i)
            if (len == 0) { i++; break }
            if ((len and 0xC0) != 0) return null // compression is not used in questions
            if (len > 63 || i + 1 + len > msg.size || ++labels > 127) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (k in 0 until len) {
                val c = msg[i + 1 + k].toInt().toChar()
                sb.append(c.lowercaseChar())
            }
            i += 1 + len
        }
        if (i + 4 > msg.size || sb.length > 253) return null
        return Query(Packets.u16(msg, 0), flags, sb.toString(), Packets.u16(msg, i), Packets.u16(msg, i + 2), i + 4)
    }

    private fun header(q: Query, query: ByteArray, rcode: Int, answers: Int): ByteArray {
        val rd = q.flags and 0x0100
        val out = ByteArray(q.questionEnd)
        System.arraycopy(query, 0, out, 0, q.questionEnd)
        Packets.put16(out, 2, 0x8000 or rd or 0x0080 or rcode)
        Packets.put16(out, 4, 1)
        Packets.put16(out, 6, answers)
        Packets.put16(out, 8, 0)
        Packets.put16(out, 10, 0)
        return out
    }

    /** Builds a response with A records for [ipv4s] (may be empty for a NODATA answer). */
    fun answerA(query: ByteArray, q: Query, ipv4s: List<Int>, ttl: Int): ByteArray {
        val head = header(q, query, RCODE_NOERROR, ipv4s.size)
        val out = head.copyOf(head.size + ipv4s.size * 16)
        var o = head.size
        for (ip in ipv4s) {
            Packets.put16(out, o, 0xC00C)           // pointer to the question name
            Packets.put16(out, o + 2, TYPE_A)
            Packets.put16(out, o + 4, 1)            // IN
            Packets.put32(out, o + 6, ttl)
            Packets.put16(out, o + 10, 4)
            Packets.put32(out, o + 12, ip)
            o += 16
        }
        return out
    }

    fun empty(query: ByteArray, q: Query, rcode: Int = RCODE_NOERROR): ByteArray = header(q, query, rcode, 0)

    /** Builds a query (used by diagnostics). */
    fun buildQuery(id: Int, name: String, qtype: Int): ByteArray {
        val labels = name.trimEnd('.').split('.')
        val size = 12 + labels.sumOf { it.length + 1 } + 1 + 4
        val b = ByteArray(size)
        Packets.put16(b, 0, id); Packets.put16(b, 2, 0x0100); Packets.put16(b, 4, 1)
        var o = 12
        for (l in labels) { b[o++] = l.length.toByte(); l.toByteArray(Charsets.US_ASCII).copyInto(b, o); o += l.length }
        b[o++] = 0
        Packets.put16(b, o, qtype); Packets.put16(b, o + 2, 1)
        return b
    }

    /** Extracts A records from a response (used by diagnostics/tests). Handles name compression. */
    fun parseAnswersA(msg: ByteArray): List<Int> {
        if (msg.size < 12) return emptyList()
        val qd = Packets.u16(msg, 4)
        val an = Packets.u16(msg, 6)
        var i = 12
        fun skipName() {
            while (i < msg.size) {
                val l = Packets.u8(msg, i)
                if (l == 0) { i++; return }
                if ((l and 0xC0) == 0xC0) { i += 2; return }
                i += 1 + l
            }
        }
        repeat(qd) { skipName(); i += 4 }
        val out = mutableListOf<Int>()
        repeat(an) {
            skipName()
            if (i + 10 > msg.size) return out
            val type = Packets.u16(msg, i)
            val rdlen = Packets.u16(msg, i + 8)
            i += 10
            if (type == TYPE_A && rdlen == 4 && i + 4 <= msg.size) out += Packets.i32(msg, i)
            i += rdlen
        }
        return out
    }

    fun rcode(msg: ByteArray): Int = if (msg.size < 4) -1 else Packets.u16(msg, 2) and 0xF
}
