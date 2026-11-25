# Running tools

The **Tools** tab is the Kali tool library. Tools are grouped by category and marked as Kali **Top 10** where applicable. Search and filter by name, favorites or category.

## Three kinds of tool
- **GUI form tools** — tools with a full data-driven form (for example nmap). You fill fields, VictorDrax builds a safe, argv-escaped command, and runs it through the terminal. The target must be in scope.
- **Metasploit builder** — builds an `msfconsole` resource script from your inputs.
- **Launch-only desktop tools** — heavy Java/desktop GUIs (for example Burp, Wireshark) that open through the **VNC desktop** rather than a form.

## Safety
Every form validates its target against the active engagement's scope before it will run. Commands are argv-escaped so inputs cannot break out into extra shell commands.

## Tool details
Each tool page shows its description, usage, a safe default (help) command, and a documentation link upstream.
