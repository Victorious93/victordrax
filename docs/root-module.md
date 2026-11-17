# VictorDrax Root Module (optional)

> **Authorized testing only.** Use this only on devices and systems you own or have
> written permission to test. The module does not weaken device security.

## What it is

A small, **optional** flashable module for **Magisk**, **KernelSU** and **APatch**
(one universal zip). The VictorDrax app works exactly the same without it. The
module only automates things you could do by hand with `su`:

- At boot (after `sys.boot_completed`), if an ANDRAX-NG chroot exists, it sets up the
  bind mounts the chroot needs: `/proc`, `/sys`, `/dev`, `/dev/pts` and (optionally)
  shared storage at `<chroot>/sdcard`. Mounting is idempotent: existing mounts are
  never duplicated.
- It adds two helpers on `PATH` (via the module's `system/bin`):
  - `victordrax-chroot [command ...]` – enter the chroot (or run one command in it).
  - `victordrax-mounts [status|mount|umount]` – inspect or manage the mounts.
- If no chroot is installed it does nothing and logs that fact.

What it deliberately does **not** do: set SELinux permissive, disable verity, change
any security setting, download anything, make network calls, or contain secrets.
It never deletes or modifies the chroot contents.

Requirements: Magisk 20.4+, KernelSU or APatch; architecture `arm64` or `x64`.
The installer aborts cleanly on anything else.

## Install

1. Get `victordrax-root-module-<version>.zip` from the `victordrax-root-module`
   artifact of the CI run (or build it yourself: `sh module/build.sh`, output in
   `module/build/`).
2. Open your root manager and flash the zip:
   - **Magisk**: Modules -> Install from storage -> select the zip.
   - **KernelSU**: Module -> Install -> select the zip.
   - **APatch**: Module -> Install -> select the zip.
3. The installer prints which root manager it detected. Reboot.

If `victordrax-chroot` is not found on `PATH`, your manager may not mount module
`system/` overlays (newer KernelSU needs a mount metamodule). The same scripts are
always available at `/data/adb/modules/victordrax/system/bin/`.

## Usage

```
su
victordrax-mounts status     # shows chroot path, presence and mount state
victordrax-chroot            # mount (if needed) and open a shell in the chroot
victordrax-chroot nmap -V    # run a single command inside the chroot
victordrax-mounts umount     # unmount everything the module set up
```

## Configuration

On install the module creates `/data/adb/victordrax/victordrax.conf` (kept across
module updates). It holds plain `KEY=VALUE` lines; it is parsed, never executed, and
invalid values are ignored.

| Key | Default | Meaning |
| --- | --- | --- |
| `CHROOT_PATH` | `/data/local/andrax` | Chroot location. Must match **Settings -> Install path** in the app. |
| `AUTO_MOUNT` | `1` | `0` disables boot-time mounting (helpers still work manually). |
| `MOUNT_SDCARD` | `1` | Bind-mount shared storage at `<CHROOT_PATH>/sdcard`. |
| `SDCARD_SOURCE` | `/storage/emulated/0` | Source directory for that bind mount. |

`CHROOT_PATH` must be a plain absolute path (letters, digits, `.`, `_`, `-`, `/`; no
`..`) and not a top-level system directory. Changes apply at the next boot or after
`victordrax-mounts mount`.

The app also mounts the pseudo filesystems when it opens a terminal session. That is
harmless alongside the module; `victordrax-mounts umount` removes stacked duplicates too.

## Logs

`/data/adb/modules/victordrax/victordrax.log` (rotated at about 64 KiB to
`victordrax.log.old`). It records boot-time steps, each mount, "no chroot found" and
any mount failure.

## Uninstall

1. Optional, to unmount right away: `su -c victordrax-mounts umount`.
2. Remove the module in your root manager and reboot.

`uninstall.sh` unmounts anything left over and deletes `/data/adb/victordrax`
(including your config). The chroot itself is never touched; remove it from the app.

## Troubleshooting

- **Installer says "Unsupported environment/architecture"** – flash only from Magisk
  20.4+, KernelSU or APatch on an `arm64`/`x64` device.
- **Nothing mounted after reboot** – check the log. "no ANDRAX-NG chroot" means
  `CHROOT_PATH` does not contain `/bin/sh`; fix the path in the config.
- **`victordrax-chroot: root required`** – run it through `su`.
- **A mount "FAILED" in the log** – the kernel may lack that filesystem, or a path is
  busy; run `victordrax-mounts status` and mount again.
- **Cannot delete the chroot** – run `victordrax-mounts umount` first; never delete a
  chroot while `/dev` or `/sdcard` is still bind-mounted into it.
- **Module misbehaves** – disable or remove it in your manager; the app keeps working.

## Development

```
sh module/validate-prop.sh
sh module/build.sh                  # deterministic zip in module/build/
sh module/verify-zip.sh module/build/*.zip
bash module/tests/run-tests.sh      # VDX_REAL_MOUNT=1 adds real mounts (needs sudo)
```

Licensed under Apache-2.0 like the rest of the repo. ANDRAX is by Weidsom Nascimento /
The Cracker Technology; ANDRAX-NG is by SNAKE Security. This module is independent and
not affiliated with or endorsed by them.
