# keystore/

`debug.keystore` is a **debug-only** signing key, committed on purpose so every
debug build (local and CI) has the same signature and can be installed over the
previous one.

- alias: `androiddebugkey`, store/key password: `android` (Android defaults)
- **Never** use it to sign release builds. It is public and offers no security.

Release keys must never be committed. `*.jks` and `keystore.properties` are git-ignored.
