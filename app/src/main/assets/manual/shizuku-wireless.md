# Shizuku & wireless debugging

[Shizuku](https://shizuku.rikka.app/) lets apps run ADB-level commands without full root. VictorDrax can use it as its privileged backend.

## Install & start Shizuku
1. Install the Shizuku app (Settings shows a **Get Shizuku** button when it is missing).
2. Start the Shizuku service. On a non-rooted device the usual way is **wireless debugging**.
3. Grant VictorDrax permission when prompted (**Request permission** in Settings).

Status shown in Settings: *not installed / not running / not granted / granted*. When granted it also shows the server uid — `uid=0` means root/Sui, otherwise it is the ADB shell (not full root).

## Wireless debugging pairing helper
Open **Settings → Wireless debugging pairing helper** (route `wireless`). It walks you through:

1. Enable Developer options and **Wireless debugging**.
2. Enter the pairing **host:port** and the 6-digit pairing code.
3. Enter the connect **host:port**.
4. It runs `adb pair`, `adb connect`, then the Shizuku starter through VictorSuite.

The pairing code is never saved — it is masked and cleared after use. Only the last host IP is remembered.

## rish for terminal sessions
VictorSuite runs in its own process and cannot reach VictorDrax's in-process Shizuku binder, so terminal sessions use Shizuku's `rish` bridge. If `rish` is not on VictorSuite's PATH the session prints setup steps and drops to a normal shell.
