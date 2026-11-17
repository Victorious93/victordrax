package com.victorious.victordrax.capture

/**
 * Captures the output of a tool/workflow run that VictorDrax hands to VictorSuite.
 *
 * VictorSuite sessions are interactive, so the app never sees stdout directly. Instead the launched
 * command is wrapped (see [com.victorious.victordrax.terminal.VictorSuiteLauncher.captureWrap]) so
 * VictorSuite tees stdout+stderr into a per-run log file and appends an exit-code sentinel line.
 * The app then reads that file and parses it here. Pure (no Android deps) so it is unit tested.
 */
object RunCapture {
    /** Sentinel the capture wrapper appends: "<marker> <exit code>" on its own line. */
    const val EXIT_MARKER = "__VDX_EXIT__"

    data class Parsed(
        /** Captured stdout+stderr with the trailing exit sentinel removed. */
        val output: String,
        /** Exit code parsed from the sentinel, or null if the run has not finished / no sentinel. */
        val exitCode: Int?,
        /** True once the sentinel line was found (the run completed). */
        val finished: Boolean,
    )

    /** Relative file name for a run's capture log. Stable per run id so re-reads find the same file. */
    fun fileName(runId: Long): String = "run-$runId.log"

    /**
     * Parse a captured log. Strips the last `__VDX_EXIT__ <n>` line (wherever it is) and returns the
     * remaining text plus the parsed exit code. Tolerates logs still being written (no sentinel yet).
     */
    fun parse(raw: String): Parsed {
        if (raw.isEmpty()) return Parsed("", null, false)
        val lines = raw.split("\n")
        // Find the last non-blank line; the sentinel is always appended last by the wrapper.
        var sentinelIdx = -1
        var code: Int? = null
        for (i in lines.indices.reversed()) {
            val t = lines[i].trim()
            if (t.isEmpty()) continue
            if (t.startsWith("$EXIT_MARKER ")) {
                sentinelIdx = i
                code = t.removePrefix("$EXIT_MARKER ").trim().toIntOrNull()
            }
            break
        }
        val body = if (sentinelIdx >= 0) {
            lines.filterIndexed { i, _ -> i != sentinelIdx }.joinToString("\n")
        } else raw
        return Parsed(body.trimEnd('\n'), code, finished = sentinelIdx >= 0)
    }
}
