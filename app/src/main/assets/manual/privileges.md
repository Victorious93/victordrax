# Privilege modes

VictorDrax can run elevated shell commands (installer, VNC, diagnostics) through one of several backends. Choose the mode in **Settings → Privilege mode**.

## Modes
- **Auto** — prefer root (`su`) if available, then Shizuku, then unprivileged.
- **Root** — use `su -c` (requires a rooted device, uid 0).
- **Shizuku** — use a Shizuku-provided shell (ADB-level, no root needed).
- **None** — run unprivileged, no wrapper.

## Chroot needs uid 0
The ANDRAX chroot requires real root (uid 0). That means either `su`, or Shizuku backed by **Sui** (which runs as uid 0). Plain ADB-level Shizuku runs as uid 2000 and **cannot** chroot — VictorDrax detects this and blocks it with a clear message instead of failing silently.

## Active backend
The Settings screen shows the live active backend and whether chroot is available (`chroot OK` vs `chroot needs uid 0`).
