# VictorSuite integration

VictorDrax has **no embedded terminal**. Every shell, whether from "Open terminal" or a tool's
"Open in VictorSuite" button, opens as a session in [VictorSuite](https://github.com/Victorious93/VictorSuite).
VictorSuite is a separate, Termux-based app. The two apps only talk through Android intents. No VictorSuite
or Termux code is bundled or linked here.

## 1. What this app uses today (existing contract)

VictorSuite keeps the Termux applicationId `com.termux`. It also keeps Termux's `RUN_COMMAND` service unchanged
(see VictorSuite branch `feat/andrax-drawer-entry`, `docs/PLUGIN_CONTRACT.md` §9.4):

| Item | Value |
|---|---|
| Package / service | `com.termux` / `com.termux.app.RunCommandService` |
| Action | `com.termux.RUN_COMMAND` |
| Caller permission | `com.termux.permission.RUN_COMMAND` (dangerous; the user grants it at runtime) |
| VictorSuite setting | `allow-external-apps = true` in `~/.termux/termux.properties` |
| `com.termux.RUN_COMMAND_PATH` | `/data/data/com.termux/files/usr/bin/bash` |
| `com.termux.RUN_COMMAND_ARGUMENTS` | `["-c", <script>]` |
| `com.termux.RUN_COMMAND_WORKDIR` | `/data/data/com.termux/files/home` |
| `com.termux.RUN_COMMAND_BACKGROUND` | `false` (foreground terminal session) |
| `com.termux.RUN_COMMAND_SESSION_ACTION` | `"0"` (open the new session and switch to it) |
| `com.termux.RUN_COMMAND_SHELL_NAME` | `VictorDrax` (session title) |
| `com.termux.RUN_COMMAND_COMMAND_LABEL` | tool name / `ANDRAX-NG` |

The `<script>` value depends on the setup:

* **The ANDRAX-NG environment is installed and `su` exists:**
  `su -c '<mount proc/dev/devpts/sys> && chroot <installPath> /usr/bin/env -i HOME=/root TERM=xterm-256color PATH=… /bin/bash -lc "<cmd>; exec /bin/bash -l"'`
* **Otherwise:** `<cmd>; exec bash -l` runs in VictorSuite's own shell.

Commands are limited to `[A-Za-z0-9 ._/:=,+@%-]`. The install path must pass `Validation.isSafePath`. Anything
else is rejected before an intent is sent. This rule is unit-tested in `ValidationTest`.

Implementation: `app/src/main/java/com/victorious/victordrax/terminal/TerminalLauncher.kt`. The `TerminalLauncher`
interface is there so another terminal backend can be added later.

### Fallbacks

1. **VictorSuite is not installed:** a dialog links to <https://github.com/Victorious93/VictorSuite/releases>.
2. **The permission is not granted, or VictorSuite refuses the request** (for example, `allow-external-apps` is off):
   the app opens VictorSuite's launcher activity and copies the script to the clipboard. The user pastes it into a session.

### Setup

1. Install VictorSuite.
2. In VictorSuite, run `mkdir -p ~/.termux && echo "allow-external-apps = true" >> ~/.termux/termux.properties`,
   then restart the app.
3. In VictorDrax, go to **Terminal**, then **Grant RUN_COMMAND permission**.


## 2. Drawer discovery

None is required. Per `docs/PLUGIN_CONTRACT.md` §9.5, VictorSuite adds no new action, extra, or
permission for ANDRAX; the existing `RUN_COMMAND` contract covers opening a titled foreground session. This
app therefore ships no exported plugin activity or `plugin.json`.

## 3. Licensing

VictorSuite (a Termux fork) is GPLv3. This app is Apache-2.0. It is a separate program that talks to VictorSuite only
through documented intents, the same way third-party apps use Termux's `RUN_COMMAND`. This repo copies no code from
VictorSuite. *(This is an engineering note, not legal advice.)*

The VictorSuite repo itself was not changed.

## Privilege backend for VictorSuite sessions

The launch contract above is unchanged. Only the chroot wrapper inside the `bash -c` script now follows **Settings → Privilege mode**, using the same `PrivilegeResolver` that in-app runs use:

| Resolved backend | Chroot session wrapper |
|---|---|
| Root (su) | `su -c '<chroot …>'` (same as before) |
| Shizuku via Sui (uid 0) | `rish -c '<chroot …>'` |
| Shizuku via ADB (uid 2000) | blocked with a clear message, because uid 2000 can't chroot |
| Unprivileged | host VictorSuite shell with no wrapper |

VictorSuite runs in its own process, so it can't use VictorDrax's in-process `ShizukuShell` binder. `rish` is Shizuku's supported terminal bridge. In the Shizuku app, use "Use Shizuku in terminal apps" → Export, then put `rish` and `rish_shizuku.dex` on VictorSuite's `$PATH`. If `rish` is missing, the session prints how to set it up and drops to a host shell.
