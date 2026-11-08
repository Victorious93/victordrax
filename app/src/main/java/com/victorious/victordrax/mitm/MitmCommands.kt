package com.victorious.victordrax.mitm

import com.victorious.victordrax.data.Scope
import com.victorious.victordrax.data.Validation
import com.victorious.victordrax.db.Engagement
import com.victorious.victordrax.workflow.RunGuard

/**
 * Pure builders + authorization guard for the MITM / traffic proxy console.
 *
 * Frames the proxy as intercepting the **operator's own authorized test traffic** only —
 * no credential harvesting, no third-party interception automation.
 */
object MitmCommands {

    /** Gate: must have an authorized engagement. */
    fun guard(engagement: Engagement?): RunGuard = when {
        engagement == null -> RunGuard.Blocked("Select an engagement first.")
        !engagement.isAuthorized -> RunGuard.Blocked("The engagement has no authorization record. Record written authorization before running a proxy.")
        else -> RunGuard.Ok
    }

    /** Flag whether a captured flow's host sits inside the engagement scope (advisory). */
    fun flowInScope(engagement: Engagement?, flow: MitmFlow): Boolean {
        if (engagement == null || flow.host.isBlank()) return false
        return Scope.inScope(flow.host, engagement.scopeList()) || Scope.targetInScope(flow.host, engagement.scopeList())
    }

    fun start(cfg: MitmConfig): String {
        require(cfg.isValid) { "invalid listen host/port" }
        require(Validation.isSafePath(cfg.flowPath)) { "unsafe flow path" }
        // Writes a binary flow file; operator later exports to text/JSONL for the viewer.
        return "mitmdump -p ${cfg.listenPort} -w ${cfg.flowPath} --set block_global=false"
    }

    fun stop(): String = "pkill -f mitmdump || echo 'no mitmdump process found'"

    /**
     * Re-read a saved flow file as a human-readable log the in-app text parser accepts.
     * Output is also tee'd to [MitmConfig.jsonlPath] so the operator can import the file.
     */
    fun exportReadable(cfg: MitmConfig): String {
        require(Validation.isSafePath(cfg.flowPath)) { "unsafe flow path" }
        require(Validation.isSafePath(cfg.jsonlPath)) { "unsafe jsonl path" }
        return "mitmdump -n -r ${cfg.flowPath} --flow-detail 1 2>&1 | tee ${cfg.jsonlPath}"
    }

    /**
     * Optional: dump each flow as one JSON object per line (needs mitmproxy's Python API).
     * Kept as a separate, explicit step so a missing addon never blocks the readable export.
     */
    fun exportJsonlScript(cfg: MitmConfig): String {
        require(Validation.isSafePath(cfg.flowPath)) { "unsafe flow path" }
        require(Validation.isSafePath(cfg.jsonlPath)) { "unsafe jsonl path" }
        return "mitmdump -n -r ${cfg.flowPath} -s \$PREFIX/share/victordrax/mitm_jsonl.py --set jsonl_path=${cfg.jsonlPath}"
    }

    fun showCaPath(): String =
        "ls -la ~/.mitmproxy/ 2>/dev/null || echo 'No CA yet — start mitmdump once to generate ~/.mitmproxy/mitmproxy-ca-cert.pem'"

    /** CA install steps shown in the UI (never automated). */
    val caInstallSteps: List<Pair<String, String>> = listOf(
        "Start the proxy once" to "mitmdump creates ~/.mitmproxy/mitmproxy-ca-cert.pem on first run.",
        "Copy the CA to the test device" to "Share or adb-push mitmproxy-ca-cert.cer / .pem onto the device under test.",
        "Install as a user CA" to "Android: Settings → Security → Encryption & credentials → Install a certificate → CA certificate. Browsers/OS: import the PEM into the trust store.",
        "Point only authorized clients at the proxy" to "Set HTTP(S) proxy to this device's LAN IP and the listen port. Do not route third-party traffic without consent.",
        "Android 7+ user-CA trust" to "Debug builds need a network_security_config that trusts user CAs; many production apps pin certificates — out of scope here.",
    )
}
