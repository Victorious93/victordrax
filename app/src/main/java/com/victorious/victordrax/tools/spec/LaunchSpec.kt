package com.victorious.victordrax.tools.spec

/**
 * Launch-only tools: heavy desktop / Java GUI apps (Burp Suite, Maltego, Wireshark) that are not
 * driven by a form. Each gets a tile with presets and a clear note that it opens in the ANDRAX-NG
 * VNC desktop session. Wireshark additionally offers a headless capture-to-file via dumpcap/tshark.
 */
data class LaunchOption(
    val label: String,
    /** Command run inside the chroot/VNC session; null => just open the desktop. */
    val command: String?,
    val note: String,
    val desktop: Boolean = true,
)

data class LaunchSpec(
    val id: String,
    val name: String,
    val options: List<LaunchOption>,
)

object LaunchSpecs {
    private val burpsuite = LaunchSpec("burpsuite", "Burp Suite", listOf(
        LaunchOption("Open Burp Suite (desktop)", "burpsuite", "Burp Suite is a Java desktop GUI; it opens in the ANDRAX-NG VNC desktop."),
    ))
    private val maltego = LaunchSpec("maltego", "Maltego", listOf(
        LaunchOption("Open Maltego (desktop)", "maltego", "Maltego is a Java desktop GUI; it opens in the ANDRAX-NG VNC desktop."),
    ))
    private val wireshark = LaunchSpec("wireshark", "Wireshark", listOf(
        LaunchOption("Open Wireshark (desktop)", "wireshark", "Wireshark is a GTK desktop GUI; it opens in the ANDRAX-NG VNC desktop."),
        LaunchOption("Capture to file (dumpcap)", null, "Headless capture with dumpcap/tshark to a .pcap you can open later. Set the interface and output path in the terminal.", desktop = false),
    ))

    val all = listOf(burpsuite, maltego, wireshark)
    fun byId(id: String) = all.firstOrNull { it.id == id }
    fun isLaunchOnly(id: String) = all.any { it.id == id }
}
