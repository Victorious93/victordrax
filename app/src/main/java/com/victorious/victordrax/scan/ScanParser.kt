package com.victorious.victordrax.scan

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/** Parses common scanner outputs into a normalized [ScanTable]. Pure; unit tested with fixtures. */
object ScanParser {

    fun detect(name: String, content: String): String {
        val n = name.lowercase()
        val head = content.take(400)
        return when {
            head.contains("<nmaprun") -> "nmap-xml"
            Regex("""Host:\s+\S+.*Ports:""").containsMatchIn(content) -> "nmap-grep"
            n.endsWith(".json") || head.trimStart().startsWith("{") || head.trimStart().startsWith("[") -> "json"
            else -> "csv"
        }
    }

    fun parse(name: String, content: String): ScanTable {
        val fmt = detect(name, content)
        val rows = when (fmt) {
            "nmap-xml" -> parseNmapXml(content)
            "nmap-grep" -> parseNmapGrepable(content)
            "json" -> parseJson(content)
            else -> parseCsv(content)
        }
        return ScanTable(fmt, rows)
    }

    fun parseNmapXml(xml: String): List<ScanRow> {
        val doc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false; isExpandEntityReferences = false
            // nmap output carries <!DOCTYPE nmaprun>; allow it but never resolve external entities (XXE).
            fun f(name: String, v: Boolean) = runCatching { setFeature(name, v) }
            f("http://xml.org/sax/features/external-general-entities", false)
            f("http://xml.org/sax/features/external-parameter-entities", false)
            f("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }.newDocumentBuilder().apply { setEntityResolver { _, _ -> InputSource(StringReader("")) } }.parse(InputSource(StringReader(xml)))
        val out = ArrayList<ScanRow>()
        val hosts = doc.getElementsByTagName("host")
        for (h in 0 until hosts.length) {
            val host = hosts.item(h) as Element
            var addr = ""; var hostname = ""
            val addrs = host.getElementsByTagName("address")
            for (a in 0 until addrs.length) {
                val el = addrs.item(a) as Element
                if (el.getAttribute("addrtype").startsWith("ip")) { addr = el.getAttribute("addr"); break }
            }
            val hns = host.getElementsByTagName("hostname")
            if (hns.length > 0) hostname = (hns.item(0) as Element).getAttribute("name")
            val st = host.getElementsByTagName("status")
            if (st.length > 0 && (st.item(0) as Element).getAttribute("state") == "down") continue
            val ports = host.getElementsByTagName("port")
            if (ports.length == 0) { out.add(ScanRow(host = addr, hostname = hostname, state = "up")); continue }
            for (p in 0 until ports.length) {
                val port = ports.item(p) as Element
                val stateEl = port.getElementsByTagName("state")
                val svcEl = port.getElementsByTagName("service")
                val svc = if (svcEl.length > 0) svcEl.item(0) as Element else null
                out.add(
                    ScanRow(
                        host = addr, hostname = hostname,
                        port = port.getAttribute("portid").toIntOrNull() ?: 0,
                        proto = port.getAttribute("protocol"),
                        state = if (stateEl.length > 0) (stateEl.item(0) as Element).getAttribute("state") else "",
                        service = svc?.getAttribute("name") ?: "",
                        product = svc?.getAttribute("product") ?: "",
                        version = svc?.getAttribute("version") ?: "",
                    )
                )
            }
        }
        return out
    }

    // Host: 10.0.0.1 (name)\tStatus: Up  /  Ports: 22/open/tcp//ssh//OpenSSH 8.9, 80/open/tcp//http//nginx
    fun parseNmapGrepable(text: String): List<ScanRow> {
        val out = ArrayList<ScanRow>()
        for (line in text.lines()) {
            if (!line.startsWith("Host:")) continue
            val hostMatch = Regex("""Host:\s+(\S+)\s+\(([^)]*)\)""").find(line) ?: continue
            val host = hostMatch.groupValues[1]; val hostname = hostMatch.groupValues[2]
            val portsPart = line.substringAfter("Ports:", "").substringBefore("Ignored State:")
            // Each host also has a separate "Status:" line; only "Ports:" lines carry rows.
            if (portsPart.isBlank()) continue
            for (entry in portsPart.split(',')) {
                val f = entry.trim().split('/')
                if (f.size < 3 || f[0].toIntOrNull() == null) continue
                out.add(
                    ScanRow(
                        host = host, hostname = hostname,
                        port = f[0].toInt(), state = f.getOrElse(1) { "" }, proto = f.getOrElse(2) { "" },
                        service = f.getOrElse(4) { "" }, version = f.getOrElse(6) { "" }.trim(),
                    )
                )
            }
        }
        return out
    }

    fun parseJson(text: String): List<ScanRow> {
        val el = kotlinx.serialization.json.Json.parseToJsonElement(text)
        val arr = when (el) {
            is kotlinx.serialization.json.JsonArray -> el
            is kotlinx.serialization.json.JsonObject ->
                (el["results"] ?: el["hosts"] ?: el["rows"] ?: el["ports"]) as? kotlinx.serialization.json.JsonArray
                    ?: kotlinx.serialization.json.JsonArray(emptyList())
            else -> kotlinx.serialization.json.JsonArray(emptyList())
        }
        fun str(o: kotlinx.serialization.json.JsonObject, vararg keys: String): String {
            for (k in keys) o[k]?.let { return (it as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "" }
            return ""
        }
        return arr.filterIsInstance<kotlinx.serialization.json.JsonObject>().map { o ->
            ScanRow(
                host = str(o, "host", "ip", "address"), hostname = str(o, "hostname", "name"),
                port = str(o, "port").toIntOrNull() ?: 0, proto = str(o, "proto", "protocol"),
                state = str(o, "state", "status"), service = str(o, "service", "svc"),
                product = str(o, "product"), version = str(o, "version", "ver"),
            )
        }
    }

    fun parseCsv(text: String): List<ScanRow> {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val header = splitCsv(lines.first()).map { it.trim().lowercase() }
        fun idx(vararg names: String) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1
        val hi = idx("host", "ip", "address"); val pi = idx("port"); val pri = idx("proto", "protocol")
        val sti = idx("state", "status"); val si = idx("service"); val vi = idx("version"); val pdi = idx("product")
        fun get(f: List<String>, i: Int) = if (i in f.indices) f[i].trim() else ""
        return lines.drop(1).map { splitCsv(it) }.map { f ->
            ScanRow(host = get(f, hi), port = get(f, pi).toIntOrNull() ?: 0, proto = get(f, pri),
                state = get(f, sti), service = get(f, si), product = get(f, pdi), version = get(f, vi))
        }.filter { it.host.isNotBlank() }
    }

    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var q = false
        for (c in line) when {
            c == '"' -> q = !q
            c == ',' && !q -> { out.add(sb.toString()); sb.clear() }
            else -> sb.append(c)
        }
        out.add(sb.toString()); return out
    }
}
