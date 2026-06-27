# AGENTS.md

## Cursor Cloud specific instructions

App Manager is a **single Android application** (Java/C++ via Gradle). There is no
web frontend/backend or external database — every Gradle module compiles into one
APK. The on-device "server" (`am.jar`/`main.jar`, built from `:server`) is a
privileged root/ADB helper, not a network service. Standard commands live in
`BUILDING.rst` and the CI workflows under `.github/workflows/`.

### Environment (already provisioned in the VM snapshot)
- **JDK 21** (system) and the **Gradle 9.0.0** wrapper (`./gradlew`) drive everything.
- **Android SDK** is installed at `$HOME/android-sdk` with: `platforms;android-36`,
  `build-tools;36.1.0`, `platform-tools`, `ndk;27.0.12077973`, `cmake;3.22.1`.
  `ANDROID_HOME`/`ANDROID_SDK_ROOT`/`PATH` are exported from `~/.bashrc`, and
  `local.properties` (git-ignored) holds `sdk.dir=$HOME/android-sdk`. The startup
  update script re-creates `local.properties` and refreshes git submodules, so you
  normally don't need to touch SDK setup.

### Build / test / lint / run
- Lint: `./gradlew lint` (report: `app/build/reports/lint-results-debug.html`).
  Note `app/build.gradle` sets `abortOnError false`, so lint does not fail the build.
- Test: `./gradlew test` (Robolectric/JUnit; runs real app code). Reports in
  `app/build/reports/tests/testDebugUnitTest/`. Scope a class with
  `./gradlew :app:testDebugUnitTest --tests "<fqcn>"`.
- Build APK: `./gradlew packageDebugUniversalApk` → universal APK at
  `app/build/outputs/apk_from_bundle/debug/app-debug-universal.apk`. This triggers
  the native CMake build (`libam.so`) and `:server:build` (generates
  `app/src/main/assets/{am,main}.jar` via `d8`).

### Non-obvious gotchas
- The **first** Gradle invocation downloads the Gradle distribution plus all
  dependencies (and Robolectric's `android-all` jars at test time). It can take well
  over 20 minutes — run long Gradle commands in the background and poll the log
  rather than blocking. Subsequent runs are ~1 minute thanks to the build cache.
- `:app` `preBuild` depends on `:docs:buildDocs`, which runs `scripts/make_docs.sh`.
  That script only needs `bash`+`sed` (it copies CSS/images/HTML into
  `docs/src/main/res`); the heavier pandoc/LaTeX/mdBook toolchain is only for the
  full documentation site and is **not** required to build/test/lint the app.
- Git submodules under `scripts/` (`android-libraries`, `android-debloat-list`) are
  data for maintenance PHP scripts only; the app builds without them, but CI checks
  them out recursively.
- **Running the GUI app requires an Android emulator/device, which is not available
  here**: the VM has no `/dev/kvm`, so a hardware-accelerated emulator cannot run.
  Demonstrate app behavior by building the APK and running the Robolectric tests
  (which execute real App Manager code, e.g. `apk.parser.ManifestParserTest`), and
  inspect the built APK with `$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer`.
