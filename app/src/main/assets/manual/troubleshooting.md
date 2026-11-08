# Troubleshooting & FAQ

## The terminal doesn't open
Install **VictorSuite** — VictorDrax hands all commands to it. Without it, nothing runs.

## Shizuku says "not granted"
Open **Settings → Shizuku**, start the service (wireless debugging on non-rooted devices), then **Request permission**. See **Shizuku & wireless debugging**.

## "chroot needs uid 0"
Plain ADB-level Shizuku runs as uid 2000 and cannot chroot. Use `su` (rooted device) or Shizuku backed by **Sui** (uid 0). See **Privilege modes**.

## A tool refuses to run
The target is probably out of scope, or the engagement is not authorized. Check **Engagements & scope**.

## Tool output is empty in a finding
Expected — VictorSuite sessions are interactive and do not send stdout back, so runs record only the command.

## rish not found
Export `rish` and `rish_shizuku.dex` into VictorSuite's `bin`, and point `rish` at the `com.termux` package.

## Reset the legal notice
**Settings → Legal → Reset acceptance** re-shows it on next launch.
