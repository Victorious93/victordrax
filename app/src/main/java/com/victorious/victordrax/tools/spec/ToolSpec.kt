package com.victorious.victordrax.tools.spec

/**
 * Data-driven tool-screen framework. Each supported Kali Top-10 tool is described by a
 * [ToolSpec]: a list of form [FieldSpec]s that map user input to command-line flags. The
 * generic form screen renders the fields, the [CommandBuilder] turns them into an argv, and
 * everything is pure Kotlin so command construction, scope enforcement and escaping are unit
 * tested without an emulator.
 */
enum class FieldType { TEXT, NUMBER, TOGGLE, DROPDOWN, FILE }

/** How a field's value is validated. Values are always shell-escaped later; this only blocks nonsense input. */
enum class ValueKind { GENERIC, HOST, URL, PARAM, PORTS, INT, PATH, USERNAME }

data class Option(val value: String, val label: String = value)

data class FieldSpec(
    val key: String,
    val label: String,
    val type: FieldType,
    /** The flag this field emits, e.g. "-p" or "--level". null => positional argument. */
    val flag: String? = null,
    val default: String = "",
    val options: List<Option> = emptyList(),
    val required: Boolean = false,
    /** Exactly one field per spec should set this; its value is scope-checked against the engagement. */
    val isTarget: Boolean = false,
    val kind: ValueKind = ValueKind.GENERIC,
    val help: String = "",
    val placeholder: String = "",
    /** For NUMBER/DROPDOWN flags written as "flag=value" instead of two tokens. */
    val joinWithEquals: Boolean = false,
)

/** A one-tap set of field values. */
data class Preset(val name: String, val values: Map<String, String>)

data class ToolSpec(
    val id: String,
    val name: String,
    /** Base command, e.g. ["nmap"] or ["sqlmap"]. */
    val program: List<String>,
    val fields: List<FieldSpec>,
    val presets: List<Preset> = emptyList(),
    /** nmap output is parseable; drives the results viewer. */
    val parseOutput: Boolean = false,
) {
    fun defaults(): Map<String, String> =
        fields.associate { it.key to (if (it.type == FieldType.TOGGLE) it.default.ifBlank { "false" } else it.default) }
}
