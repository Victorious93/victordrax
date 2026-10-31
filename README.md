# VictorDrax

An Android app (Kotlin + Jetpack Compose) that will help you set up and launch the
**ANDRAX-NG** penetration-testing environment from
[SNAKE Security](https://github.com/snakesec).

> ⚠️ **Authorized testing only.** Use this only on systems you own or have written
> permission to test. The app makes you accept this notice on first launch, and it
> stays visible in the About screen.

## What it does today (early preview)
- **Status** – shows whether root (`su`) is detected, your CPU architecture, and free storage.
- **Install environment** – explains the requirements and links to the official
  upstream sources. *Automatic download/setup is not built yet (TODO).*
- **Settings** – choose an install path, reset the legal acceptance.
- **About** – legal notice, credits, and license.

## Batch 2 features (v0.2.0)

All offensive actions are tied to an engagement with a recorded authorization (who authorized, date, document) and a scope; nothing runs against targets outside that scope.

- **Scan viewer** — import nmap XML (`-oX`, XXE-hardened parser), grepable (`-oG`), JSON or CSV into an engagement; sortable, filterable table with open-only and per-host filters.
- **Workflows** — playbooks stored in Room, with `{variables}`. A run requires an authorized engagement and a target inside its scope; each step opens a VictorSuite session. Built-in passive/low-impact playbooks: Nmap service scan, Passive host recon (whois/dig), Top-ports TCP sweep.
- **Report builder** — findings with severity and a CVSS 3.1 calculator (spec rounding), executive summary (auto or custom), branded neon PDF and Markdown export.
- **afos-ng packages** — browse/search the bundled afos-ng catalog; install/update/list run `afos` inside the ANDRAX-NG chroot via VictorSuite (needs a real rooted device with the environment installed). Installed state is marked by pasting `afos -l` output.
- **Encrypted backup/restore** — Storage Access Framework file, AES-256-GCM, key from PBKDF2-HMAC-SHA256 (210k iterations, random salt/IV). Restore can merge or replace. A wrong passphrase changes nothing.

Screenshots (rendered headlessly with Robolectric, not on a device): [`docs/screenshots/`](docs/screenshots/). Logo: [`docs/brand/`](docs/brand/).

## Build it
You need JDK 17 and the Android SDK (API 34).

```
./gradlew testDebugUnitTest assembleDebug
```
The APK ends up in `app/build/outputs/apk/debug/`. Every push also builds it on
GitHub Actions; download it from the run's **Artifacts** (`victordrax-debug-apk`).

## Signing
`keystore/debug.keystore` is a **debug-only** key (passwords `android`/`android`),
committed so debug builds always share a signature. It is not secret and must never
sign a release. No release keys or secrets are in this repo.

## Credits
- ANDRAX – original by Weidsom Nascimento / The Cracker Technology
- ANDRAX-NG – SNAKE Security
- afos-ng – SNAKE Security, Apache-2.0

Not affiliated with SNAKE Security. Upstream files are linked, not copied — see
[NOTICE](NOTICE) and [THIRD_PARTY.md](THIRD_PARTY.md).

## License
Our code is released under the **Apache License 2.0** (same as afos-ng). See [LICENSE](LICENSE).

## Terminal: VictorSuite

This app has no built-in terminal. "Open terminal" and every tool launch open a session in
[VictorSuite](https://github.com/Victorious93/VictorSuite) through its Termux-compatible `RUN_COMMAND` intent.
When the ANDRAX-NG chroot is installed, the session enters it. If VictorSuite has not allowed external apps, this
app opens VictorSuite and copies the command to the clipboard instead. See [docs/victorsuite-integration.md](docs/victorsuite-integration.md).
