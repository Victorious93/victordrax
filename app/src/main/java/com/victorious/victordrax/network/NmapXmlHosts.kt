package com.victorious.victordrax.network

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Host-centric parser for `nmap -oX` XML. Produces [DiscoveredHost] rows with status, ports,
 * services/versions and the best OS guess. XXE-hardened (external entities disabled). Pure; unit tested.
 *
 * Distinct from [com.victorious.victordrax.scan.ScanParser] (row-per-port for the scan importer)
 * and [com.victorious.victordrax.tools.parse.NmapParser] (plain -oN text).
 */
object NmapXmlHosts {

    fun parse(xml: String): List<DiscoveredHost> {
        val doc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false; isExpandEntityReferences = false
            fun f(name: String, v: Boolean) = runCatching { setFeature(name, v) }
            f("http://xml.org/sax/features/external-general-entities", false)
            f("http://xml.org/sax/features/external-parameter-entities", false)
            f("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }.parse(InputSource(StringReader(xml)))

        val out = ArrayList<DiscoveredHost>()
        val hosts = doc.getElementsByTagName("host")
        for (i in 0 until hosts.length) {
            val host = hosts.item(i) as Element
            var addr = ""
            var hostname = ""
            var status = "up"
            val st = host.getElementsByTagName("status")
            if (st.length > 0) status = (st.item(0) as Element).getAttribute("state").ifBlank { "up" }

            val addrs = host.getElementsByTagName("address")
            for (a in 0 until addrs.length) {
                val el = addrs.item(a) as Element
                if (el.getAttribute("addrtype").startsWith("ip")) { addr = el.getAttribute("addr"); break }
            }
            if (addr.isBlank()) continue

            val hns = host.getElementsByTagName("hostname")
            if (hns.length > 0) hostname = (hns.item(0) as Element).getAttribute("name")

            val ports = ArrayList<HostPort>()
            val portEls = host.getElementsByTagName("port")
            for (p in 0 until portEls.length) {
                val port = portEls.item(p) as Element
                val stateEl = port.getElementsByTagName("state")
                val svcEl = port.getElementsByTagName("service")
                val svc = if (svcEl.length > 0) svcEl.item(0) as Element else null
                ports += HostPort(
                    port = port.getAttribute("portid").toIntOrNull() ?: 0,
                    proto = port.getAttribute("protocol").ifBlank { "tcp" },
                    state = if (stateEl.length > 0) (stateEl.item(0) as Element).getAttribute("state") else "",
                    service = svc?.getAttribute("name") ?: "",
                    product = svc?.getAttribute("product") ?: "",
                    version = svc?.getAttribute("version") ?: "",
                )
            }

            // Best OS match by accuracy.
            var osGuess = ""; var osAcc = 0
            val matches = host.getElementsByTagName("osmatch")
            for (m in 0 until matches.length) {
                val el = matches.item(m) as Element
                val acc = el.getAttribute("accuracy").toIntOrNull() ?: 0
                if (acc >= osAcc) { osAcc = acc; osGuess = el.getAttribute("name") }
            }

            out += DiscoveredHost(
                address = addr, hostname = hostname, status = status,
                ports = ports, osGuess = osGuess, osAccuracy = osAcc,
            )
        }
        return out
    }
}
