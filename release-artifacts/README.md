# Release APK artifacts

Prebuilt release APKs from `./gradlew assembleRelease` (App Manager
`versionCode 445`, `versionName 4.0.5`, `minSdk 21`, `targetSdk 36`).

These binaries are committed here only so they can be downloaded directly from
this pull request. **Do not merge this folder** — APKs/build outputs are
normally git-ignored (`*.apk`, `build/`).

| File | Description | SHA-256 |
| --- | --- | --- |
| `app-release-unsigned.apk` | Canonical `assembleRelease` output. Unsigned (the repo defines no `release` signing config), so it cannot be installed as-is. | `5ef89e8934eb3a5296f2e48fa7f44be92cf592474a2ec4b16ddc65605cf65e93` |
| `app-release-debugkey-signed.apk` | Same APK, `zipalign`-ed and signed with the repo's bundled keystore (`app/dev_keystore.jks`, alias `key0`) so it is installable for testing. Verifies under v1/v2/v3 signature schemes. | `391ab0f1bf3e83bd19cf8f344e55e6f169150cac03b791aaee142c8b052d7389` |

To produce a production-signed release, re-sign `app-release-unsigned.apk` with
the maintainer's keystore (or run `./scripts/aab_to_apks.sh release`).
