# Victorious ANDRAX

An Android app (Kotlin + Jetpack Compose) that will help you set up and launch the
**ANDRAX-NG** penetration-testing environment from
[SNAKE Security](https://github.com/snakesec).

> ⚠️ **Authorized testing only.** Use this only on systems you own or have written
> permission to test. The app makes you accept this notice on first launch, and it
> stays visible in the About screen.

## What it does today (v0.1.0, early preview)
- **Status** – shows whether root (`su`) is detected, your CPU architecture, and free storage.
- **Install environment** – explains the requirements and links to the official
  upstream sources. *Automatic download/setup is not built yet (TODO).*
- **Settings** – choose an install path, reset the legal acceptance.
- **About** – legal notice, credits, and license.

## Build it
You need JDK 17 and the Android SDK (API 34).

```
./gradlew assembleDebug
```
The APK ends up in `app/build/outputs/apk/debug/`. Every push also builds it on
GitHub Actions; download it from the run's **Artifacts** (`victorious-andrax-debug-apk`).

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
