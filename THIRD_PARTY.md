# Third-party notices

This repository contains only original code. No upstream files are copied.

| Project | Author | License | How we use it |
|---|---|---|---|
| ANDRAX (original) | Weidsom Nascimento / The Cracker Technology | — | Credited only |
| ANDRAX-NG | SNAKE Security — https://github.com/snakesec | see upstream | Linked from the app; users download from official sources |
| afos-ng | SNAKE Security — https://github.com/snakesec/afos-ng | Apache-2.0 | Credited and linked; no code copied |
| andrax-base-files | SNAKE Security — https://github.com/snakesec/andrax-base-files | No license published | **Link only.** Not copied or redistributed |

## Build dependencies (fetched by Gradle, not vendored)
- AndroidX / Jetpack Compose, Navigation Compose — Apache-2.0
- Kotlin — Apache-2.0
- Gradle wrapper — Apache-2.0
- Shizuku API + Provider (`dev.rikka.shizuku:api` / `provider`) — RikkaApps, Apache-2.0
  (https://github.com/RikkaApps/Shizuku-API). Runtime companion app: Shizuku / Sui — not bundled.

## VictorSuite (separate app, not bundled)

- https://github.com/Victorious93/VictorSuite is a Termux-based terminal app. This app only sends it Android intents
  (`com.termux.RUN_COMMAND`). No VictorSuite or Termux source or binaries are included here.
