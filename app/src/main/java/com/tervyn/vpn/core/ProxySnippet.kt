package com.tervyn.vpn.core

import com.tervyn.vpn.domain.ProxyEndpoint
import com.tervyn.vpn.domain.ProxyProtocol

/**
 * Extracts proxy endpoints from code samples that proxy providers (Webshare, Bright Data, Oxylabs,
 * IPRoyal, ...) publish. The input is treated purely as text: nothing is evaluated or executed.
 *
 * Supported:
 *  - cURL: `-x/--proxy URL`, `-U/--proxy-user user:pass`, `--socks5[-hostname] host:port`
 *  - Python: `proxies = {"http": "...", "https": "..."}`, simple string variables and f-strings,
 *    `"...".format(...)`-free concatenation with `+`
 *  - Node.js: axios `proxy: { protocol, host, port, auth: { username, password } }`,
 *    `new HttpsProxyAgent(url)`, `new SocksProxyAgent(url)`, template literals with `${var}`
 */
object ProxySnippet {

    data class Result(val proxies: List<ProxyEndpoint>, val detected: String)

    fun parse(snippet: String): Result {
        val text = snippet.replace("\r", "")
        val found = LinkedHashMap<String, ProxyEndpoint>()
        val kinds = mutableListOf<String>()
        fun add(ep: ProxyEndpoint?) { if (ep != null) found.putIfAbsent(ep.key, ep) }

        val vars = collectVariables(text)

        if (Regex("""(^|\s)curl\s""").containsMatchIn(text) || text.trimStart().startsWith("curl")) {
            parseCurl(text, vars).forEach(::add)
            kinds += "cURL"
        }
        val axios = parseAxiosObjects(text, vars)
        if (axios.isNotEmpty()) { axios.forEach(::add); kinds += "Axios" }

        // Any proxy URL anywhere (python dicts, agents, env vars), after variable substitution.
        val substituted = substitute(text, vars)
        val urlRegex = Regex("""(?i)\b(socks5h?|socks|https?)://[^\s'"`,)}\]]+""")
        for (m in urlRegex.findAll(substituted)) {
            val url = m.value.trimEnd('.', ';')
            // Skip obvious target URLs (with a path and no explicit port), e.g. requests.get("https://example.com/ip")
            val afterScheme = url.substringAfter("://")
            val hostPort = afterScheme.substringBefore('/')
            if (!hostPort.substringAfterLast('@').contains(':')) continue
            add(ProxyList.parseUrl(url))
        }
        if (text.contains("proxies") && text.contains("{")) kinds += "Python"
        if (text.contains("ProxyAgent")) kinds += "Node.js agent"
        if (kinds.isEmpty() && found.isNotEmpty()) kinds += "URL"
        return Result(found.values.toList(), kinds.distinct().joinToString(", ").ifEmpty { "unknown" })
    }

    // ---- variables -------------------------------------------------------------------------

    /** Collects `name = "value"`, `const name = 'value'`, `name = 1234` style assignments. */
    fun collectVariables(text: String): Map<String, String> {
        val vars = HashMap<String, String>()
        val strAssign = Regex("""(?m)^\s*(?:(?:const|let|var|export)\s+)?([A-Za-z_]\w*)\s*(?::\s*\w+\s*)?=\s*(?:[fF])?(["'`])((?:\\.|(?!\2).)*)\2\s*;?\s*$""")
        val numAssign = Regex("""(?m)^\s*(?:(?:const|let|var)\s+)?([A-Za-z_]\w*)\s*=\s*(\d{1,5})\s*;?\s*$""")
        val shellAssign = Regex("""(?m)^\s*(?:export\s+)?([A-Za-z_]\w*)=(["']?)([^\s"']+)\2\s*$""")
        // Iterate to resolve variables referencing earlier ones
        repeat(3) {
            strAssign.findAll(text).forEach { vars[it.groupValues[1]] = substitute(it.groupValues[3], vars) }
            numAssign.findAll(text).forEach { vars[it.groupValues[1]] = it.groupValues[2] }
            shellAssign.findAll(text).forEach { vars.putIfAbsent(it.groupValues[1], it.groupValues[3]) }
        }
        return vars
    }

    /** Replaces `{var}`, `${var}`, `$var` and `" + var + "` concatenations with known values. */
    fun substitute(text: String, vars: Map<String, String>): String {
        if (vars.isEmpty()) return text
        var out = text
        // "a" + var + "b"  ->  "a<val>b"
        out = Regex("""(["'])\s*\+\s*(?:str\()?([A-Za-z_]\w*)\)?\s*\+\s*(["'])""").replace(out) { m ->
            vars[m.groupValues[2]] ?: m.value
        }
        out = Regex("""(["'])\s*\+\s*(?:str\()?([A-Za-z_]\w*)\)?(?=\s*[,)}\n])""").replace(out) { m ->
            vars[m.groupValues[2]]?.let { it + m.groupValues[1] } ?: m.value
        }
        out = Regex("""\$\{\s*([A-Za-z_]\w*)\s*}""").replace(out) { m -> vars[m.groupValues[1]] ?: m.value }
        out = Regex("""\{\s*([A-Za-z_]\w*)\s*}""").replace(out) { m -> vars[m.groupValues[1]] ?: m.value }
        out = Regex("""\$([A-Za-z_]\w*)""").replace(out) { m -> vars[m.groupValues[1]] ?: m.value }
        return out
    }

    // ---- cURL -------------------------------------------------------------------------------

    fun parseCurl(text: String, vars: Map<String, String> = emptyMap()): List<ProxyEndpoint> {
        val joined = substitute(text.replace("\\\n", " "), vars)
        val out = mutableListOf<ProxyEndpoint>()
        for (line in joined.lines()) {
            if (!line.contains("curl")) continue
            val tokens = shellTokens(line.substring(line.indexOf("curl")))
            var proxy: String? = null
            var socksHost: String? = null
            var user: String? = null
            var i = 0
            while (i < tokens.size) {
                val t = tokens[i]
                val next = tokens.getOrNull(i + 1)
                when {
                    t == "-x" || t == "--proxy" -> { proxy = next; i++ }
                    t.startsWith("--proxy=") -> proxy = t.substringAfter('=')
                    t.startsWith("-x") && t.length > 2 -> proxy = t.substring(2)
                    t == "-U" || t == "--proxy-user" -> { user = next; i++ }
                    t.startsWith("--proxy-user=") -> user = t.substringAfter('=')
                    t == "--socks5" || t == "--socks5-hostname" -> { socksHost = next; i++ }
                }
                i++
            }
            var ep: ProxyEndpoint? = when {
                socksHost != null -> ProxyList.parseLine(socksHost.removePrefix("socks5://").removePrefix("socks5h://"), ProxyProtocol.SOCKS5)
                    ?.copy(protocol = ProxyProtocol.SOCKS5)
                proxy != null -> if (proxy.contains("://")) ProxyList.parseUrl(proxy) else ProxyList.parseLine(proxy, ProxyProtocol.HTTP)
                else -> null
            }
            if (ep != null && user != null && !ep.hasAuth) {
                ep = ep.copy(username = user.substringBefore(':'), password = user.substringAfter(':', ""))
            }
            ep?.let(out::add)
        }
        return out
    }

    /** Minimal POSIX-ish tokenizer supporting single and double quotes. */
    fun shellTokens(s: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        var any = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                quote != null -> if (c == quote) quote = null else if (c == '\\' && quote == '"' && i + 1 < s.length) { cur.append(s[++i]) } else cur.append(c)
                c == '\'' || c == '"' -> { quote = c; any = true }
                c.isWhitespace() -> { if (cur.isNotEmpty() || any) { out += cur.toString(); cur.clear(); any = false } }
                c == '\\' && i + 1 < s.length -> cur.append(s[++i])
                else -> cur.append(c)
            }
            i++
        }
        if (cur.isNotEmpty() || any) out += cur.toString()
        return out
    }

    // ---- axios ------------------------------------------------------------------------------

    fun parseAxiosObjects(text: String, vars: Map<String, String>): List<ProxyEndpoint> {
        val out = mutableListOf<ProxyEndpoint>()
        val start = Regex("""proxy\s*:\s*\{""")
        for (m in start.findAll(text)) {
            val body = balancedBlock(text, m.range.last) ?: continue
            fun field(name: String): String? {
                val r = Regex("""\b$name\s*:\s*(?:(["'`])((?:\\.|(?!\1).)*)\1|([A-Za-z_]\w*|\d+))""")
                val mm = r.find(body) ?: return null
                val literal = mm.groups[2]?.value
                return if (literal != null) substitute(literal, vars) else mm.groupValues[3].let { v -> v.toIntOrNull()?.toString() ?: vars[v] }
            }
            val host = field("host") ?: continue
            val port = field("port")?.toIntOrNull() ?: continue
            val protoName = field("protocol")?.removeSuffix(":") ?: "http"
            val proto = ProxyList.protocolForScheme(protoName) ?: ProxyProtocol.HTTP
            val user = field("username")
            val pass = field("password")
            if (!ProxyList.isValidHost(host) || !ProxyList.validPort(port)) continue
            out += ProxyEndpoint(proto, host, port, user?.ifEmpty { null }, if (user.isNullOrEmpty()) null else pass ?: "")
        }
        return out
    }

    private fun balancedBlock(text: String, openIdx: Int): String? {
        var depth = 0
        for (i in openIdx until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return text.substring(openIdx + 1, i) }
            }
        }
        return null
    }
}
