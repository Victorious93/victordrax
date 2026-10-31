# Kali Top 10 tool suite

> **Authorized use only.** Every run requires an active engagement with authorization on record,
> and network targets must be inside that engagement's scope. These screens only build a command
> line from a form and hand it to your own VictorSuite terminal inside the ANDRAX-NG chroot.

| Tool | Screen | Notes |
|---|---|---|
| Nmap | Full form | Presets, live preview, paste-and-parse results viewer |
| sqlmap | Full form | URL target scope-checked |
| THC Hydra | Full form | Host target scope-checked |
| John the Ripper | Full form | Works on local hash files (no network target) |
| Aircrack-ng | Full form | Works on local capture files (no network target) |
| OWASP ZAP | Full form | Headless `zap.sh -cmd -quickurl` |
| Metasploit | Resource builder | `msfconsole -q -x "use …; set RHOSTS …; run"`, RHOSTS scope-checked, console stays open |
| Burp Suite | Launch-only | Opens in the VNC desktop |
| Maltego | Launch-only | Opens in the VNC desktop |
| Wireshark | Launch-only | VNC desktop, or `dumpcap -D` helper for capture-to-file |

## How it works
- `tools/spec/ToolSpecs.kt`: one data spec per tool (fields → flags; text/number/toggle/dropdown/file).
- `tools/spec/CommandBuilder.kt`: validates each value by kind, enforces scope (`data/Scope.kt`) and returns an argv.
- `terminal/TerminalLauncher.kt`: argv is POSIX single-quote escaped and nested inside-out for
  `su -c '<chroot> /bin/bash -lc '…''`, then sent via the Termux `RUN_COMMAND` contract.
- Scope entries: exact host, domain (plus subdomains), `*.domain`, IPv4 CIDR. URLs/host:port are reduced to the host.
- RUN_COMMAND cannot return stdout, so parsed results come from pasting nmap output.
