# VictorSuite integration

Victorious ANDRAX has **no embedded terminal**. Every shell, whether from "Open terminal" or a tool's
"Open in VictorSuite" button, opens as a session in [VictorSuite](https://github.com/Victorious93/VictorSuite).
VictorSuite is a separate, Termux-based app. The two apps only talk through Android intents. No VictorSuite
or Termux code is bundled or linked here.

## 1. What this app uses today (existing contract)

VictorSuite keeps the Termux applicationId `com.termux`. It also keeps Termux's `RUN_COMMAND` service unchanged
(see `app/src/main/AndroidManifest.xml` in the VictorSuite repo):

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
| `com.termux.RUN_COMMAND_COMMAND_LABEL` | tool name / `ANDRAX-NG` |

The `<script>` value depends on the setup:

* **The ANDRAX-NG environment is installed and `su` exists:**
  `su -c '<mount proc/dev/devpts/sys> && chroot <installPath> /usr/bin/env -i HOME=/root TERM=xterm-256color PATH=… /bin/bash -lc "<cmd>; exec /bin/bash -l"'`
* **Otherwise:** `<cmd>; exec bash -l` runs in VictorSuite's own shell.

Commands are limited to `[A-Za-z0-9 ._/:=,+@%-]`. The install path must pass `Validation.isSafePath`. Anything
else is rejected before an intent is sent. This rule is unit-tested in `ValidationTest`.

Implementation: `app/src/main/java/com/victorious/andrax/terminal/TerminalLauncher.kt`. The `TerminalLauncher`
interface is there so another terminal backend can be added later.

### Fallbacks

1. **VictorSuite is not installed:** a dialog links to <https://github.com/Victorious93/VictorSuite/releases>.
2. **The permission is not granted, or VictorSuite refuses the request** (for example, `allow-external-apps` is off):
   the app opens VictorSuite's launcher activity and copies the script to the clipboard. The user pastes it into a session.

### Setup

1. Install VictorSuite.
2. In VictorSuite, run `mkdir -p ~/.termux && echo "allow-external-apps = true" >> ~/.termux/termux.properties`,
   then restart the app.
3. In Victorious ANDRAX, go to **Terminal**, then **Grant RUN_COMMAND permission**.

## 2. Proposed VictorSuite-native contract (not implemented in VictorSuite yet)

VictorSuite's draft plugin API (`docs/PLUGIN_API.md`) defines `com.termux.victor.action.PLUGIN_LAUNCH`,
`PLUGIN_RESULT` and the `com.termux.victor.permission.PLUGIN` permission. It does not define how the drawer finds
installed **apk** plugins. This app proposes the contract below and already ships its side of it.

### 2a. Drawer discovery (shipped here as a proposal)

The idea is that VictorSuite queries `PackageManager.queryIntentActivities(Intent("com.termux.victor.action.PLUGIN_ENTRY"), GET_META_DATA)`
and lists every result in its drawer.

```xml
<activity android:name=".PluginEntryActivity" android:exported="true">
  <intent-filter>
    <action android:name="com.termux.victor.action.PLUGIN_ENTRY" />
    <category android:name="android.intent.category.DEFAULT" />
  </intent-filter>
  <meta-data android:name="com.termux.victor.plugin.manifest" android:resource="@raw/victor_plugin" />
  <meta-data android:name="com.termux.victor.plugin.id" android:value="com.victorious.andrax" />
</activity>
```

* `com.termux.victor.plugin.manifest` points to a `plugin.json` that follows VictorSuite's schema, with type `apk`.
  See `app/src/main/res/raw/victor_plugin.json`.
* When the drawer launches the activity, it may add the extra `action_id`. Accepted values are `dashboard`, `tools`,
  `terminal`, `engagements`, `install` and `desktop`. Unknown values fall back to `dashboard`. The activity only
  navigates inside the app; it never runs a command. The legal-notice gate still applies.
* VictorSuite would need `<queries><intent><action android:name="com.termux.victor.action.PLUGIN_ENTRY"/></intent></queries>`
  so it can see plugins on Android 11+. Before trusting a plugin, it should verify `apkSigningCertSha256`.
  That field is empty here because debug builds have no stable release certificate.

### 2b. Optional terminal action (future)

This would be a VictorSuite activity action, `com.termux.victor.action.OPEN_SESSION`, with these extras:
* `command` (String)
* `label` (String)
* `chroot` (String path, optional)

It would be guarded by `com.termux.victor.permission.PLUGIN`. The advantage over `RUN_COMMAND` is that users would
not need to enable `allow-external-apps`. If VictorSuite ever adds this action, only `VictorSuiteLauncher` would need to change.

## 3. Licensing

VictorSuite (a Termux fork) is GPLv3. This app is Apache-2.0. It is a separate program that talks to VictorSuite only
through documented intents, the same way third-party apps use Termux's `RUN_COMMAND`. This repo copies no code from
VictorSuite. *(This is an engineering note, not legal advice.)*

The VictorSuite repo itself was not changed.
