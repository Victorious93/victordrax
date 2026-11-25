# Terminal (VictorSuite)

VictorDrax does not embed its own terminal. It hands commands to the **VictorSuite** app (a separate Termux-based terminal) through Android intents.

## How hand-off works
Commands are sent via the Termux `RUN_COMMAND` contract (a foreground service), with your command passed as `["-c", cmd]`. VictorSuite opens a session and runs it.

## Privilege mode is respected
The wrapper around your command depends on the active **privilege mode**:

- **Root** → `su -c <cmd>`
- **Shizuku** → run through Shizuku's `rish` bridge
- **None** → no wrapper, run as the app user

ADB-level Shizuku (uid 2000) cannot chroot, so those sessions are blocked with a clear message. Sui (uid 0) behaves like root.

## If nothing happens
Make sure VictorSuite is installed. For Shizuku sessions, make sure `rish` (and `rish_shizuku.dex`) are exported into VictorSuite's `bin` and that `rish` points at the `com.termux` package.
