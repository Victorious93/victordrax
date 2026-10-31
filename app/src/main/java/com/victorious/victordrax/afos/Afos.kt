package com.victorious.victordrax.afos

/**
 * afos-ng (ANDRAX-NG package manager, https://github.com/snakesec/afos-ng, Apache-2.0) integration.
 * CLI (binary `afos`): -i/--install <pkg>, -u/--update, -a/--update-all, -l/--list.
 * Commands run inside the ANDRAX-NG chroot via VictorSuite; parsing helpers are pure and unit tested.
 */
data class AfosPackage(
    val name: String,
    val version: String = "",
    val description: String = "",
    val categories: List<String> = emptyList(),
    val repoUrl: String = "",
    val installed: Boolean = false,
)

object Afos {
    const val BIN = "afos"
    private val SAFE_PKG = Regex("^[A-Za-z0-9][A-Za-z0-9._+-]{0,80}$")
    fun isSafePackage(p: String) = SAFE_PKG.matches(p)

    fun installCommand(pkg: String): String { require(isSafePackage(pkg)); return "$BIN -i $pkg" }
    fun updateCommand(pkg: String): String { require(isSafePackage(pkg)); return "$BIN -i $pkg" } // -i installs or updates a single pkg
    fun checkUpdatesCommand(): String = "$BIN -u"
    fun updateAllCommand(): String = "$BIN -a"
    fun listInstalledCommand(): String = "$BIN -l"

    /**
     * Minimal parser for the bundled afos.yaml repository (a flat list of package maps).
     * Only the top-level fields AFOS uses are read; this is not a general YAML parser.
     */
    fun parseRepository(yaml: String): List<AfosPackage> {
        val out = ArrayList<AfosPackage>()
        var name: String? = null; var version = ""; var desc = ""; var repo = ""
        val cats = ArrayList<String>(); var inCats = false
        fun flush() { name?.let { out.add(AfosPackage(it, version, desc, cats.toList(), repo)) } }
        for (rawLine in yaml.lines()) {
            val line = rawLine.trimEnd()
            when {
                line.startsWith("- name:") -> {
                    flush(); name = line.substringAfter("- name:").trim(); version = ""; desc = ""; repo = ""; cats.clear(); inCats = false
                }
                line.trimStart().startsWith("version:") -> { version = line.substringAfter("version:").trim(); inCats = false }
                line.trimStart().startsWith("description:") -> { desc = line.substringAfter("description:").trim(); inCats = false }
                line.trimStart().startsWith("repo_url:") -> { repo = line.substringAfter("repo_url:").trim(); inCats = false }
                line.trimStart().startsWith("original_repo_url:") -> inCats = false
                line.trimStart() == "categories:" -> inCats = true
                inCats && line.trimStart().startsWith("- ") -> cats.add(line.trimStart().removePrefix("- ").trim())
            }
        }
        flush()
        return out.filter { it.name.isNotBlank() }
    }

    fun filter(pkgs: List<AfosPackage>, query: String, category: String?, installedOnly: Boolean): List<AfosPackage> {
        val q = query.trim().lowercase()
        return pkgs.filter { p ->
            (category == null || p.categories.any { it.equals(category, true) }) &&
                (!installedOnly || p.installed) &&
                (q.isEmpty() || p.name.lowercase().contains(q) || p.description.lowercase().contains(q))
        }.sortedBy { it.name.lowercase() }
    }

    fun categoriesOf(pkgs: List<AfosPackage>): List<String> =
        pkgs.flatMap { it.categories }.distinct().sorted()

    /**
     * Parse `afos -l` output. The CLI prints one line per package shaped like:
     * `[ name ] [ version ] [ desc ] [ cat1, cat2 ]` optionally followed by `( == NOT INSTALLED == )`.
     * ANSI color codes are stripped first. Returns the set of installed package names.
     */
    fun parseInstalled(listOutput: String): Set<String> {
        val ansi = Regex("\u001B\\[[0-9;]*m")
        val out = HashSet<String>()
        for (raw in listOutput.lines()) {
            val line = raw.replace(ansi, "")
            val m = Regex("""\[\s*([A-Za-z0-9._+-]+)\s*]""").find(line) ?: continue
            val name = m.groupValues[1]
            if (!isSafePackage(name)) continue
            if (!line.contains("NOT INSTALLED")) out.add(name)
        }
        return out
    }

    fun merge(repo: List<AfosPackage>, installedNames: Set<String>): List<AfosPackage> =
        repo.map { it.copy(installed = it.name in installedNames) }
}
