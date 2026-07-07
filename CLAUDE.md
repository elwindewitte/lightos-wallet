# CLAUDE.md — Light Wallet

## What this tool is (and is not)
Purpose: A Light Phone III tool that stores loyalty and membership cards and displays their barcodes / QR codes for scanning in-store.
In scope (v1): manually add a card (name + code value + code type); scan a physical
card with the camera to import its code (the SDK allows camera access and ships a
built-in `LightQrCodeScanner` — see Permissions below); store cards locally on the
device; list saved cards; open one to display its code full-screen, high contrast,
for scanning.
Out of scope (v1): cloud sync or accounts; payment/NFC cards; sharing.
If unsure whether something is in scope, ask me — do not add features.

## Design constraints (non-negotiable)
- The display renders black & white. Design for high contrast; never rely on color.
- One screen, one job. No feeds, no dashboards, no cross-linking.
- No notifications, badges, or any mechanic designed to pull me back to the tool.
- The scannable code is the whole point: when a card is open, render the barcode as
  large, bright, and unobstructed as the screen allows.

## Technical rules — from SDK source (fork of lightphone/light-sdk)
All facts below are read from this repo's source, not guessed. File references are
relative to the repo root. Re-verify after each `/sync-upstream` — the SDK is early
and changing fast.

### Where my code goes
- Write the tool in the `tool/` module: `tool/src/main/kotlin/`.
- Tool metadata (id, label, version, permissions) lives in `tool/lighttool.toml`,
  NOT in `build.gradle.kts`. You cannot set `applicationId`/`versionCode`/
  `versionName`/`namespace` in the build script — the plugin generates the
  AndroidManifest from `lighttool.toml` (`plugin/.../ManifestGenerator.kt`).
- The SDK library I build against is `sdk/client`; shared UI is in `sdk/ui`.

### Navigation
- A screen extends `LightScreen<ResultType, ViewModelType>` (or `SimpleLightScreen<ResultType>`)
  and implements `@Composable Content()`. Source: `sdk/client/.../LightScreen.kt`.
- Exactly one screen is annotated `@InitialScreen` (KSP fails the build otherwise).
- Move forward: `navigateTo(::NextScreen) { result -> ... }`. Go back: `goBack(result)`.
- The SDK owns the back stack and wires the hardware back button — do not hand-roll
  navigation. Optional VM hook `onBackPressed(): Boolean` (return true to consume).

### What I'm allowed to use (guardrail is a Gradle plugin)
The `com.thelightphone.light-sdk` plugin (`plugin/.../LightSdkPlugin.kt`) fails the
build if I pull in anything off the allow-list. Allowed deps include: Compose,
`activity-compose`, coroutines, `androidx.lifecycle`, **`androidx.datastore`**,
**`androidx.room`**, `androidx.work`, `androidx.startup`, okhttp, ktor,
kotlinx-serialization, kotlinx-io, `androidx.core:core-splashscreen`,
`org.unifiedpush.android:connector`, `com.thelightphone.lp3keyboard`.
Blocked: raw Android APIs — `android.content.Context`/`Intent`/`BroadcastReceiver`,
`LocalContext.current`, `startActivity`, `getSystemService`, reflection.
Use the `lightContext` wrapper instead of a raw Context.
- IMPORTANT for this tool: **no barcode/QR *generation* library is on the allow-list**
  (e.g. ZXing is not allowed). We render every code ourselves from dependency-free,
  plain-Kotlin encoders vendored as source under `tool/.../code/` — no Gradle
  dependency, no Android APIs, so they pass the plugin guardrails. See "Supported
  code types & rendering" below for the symbologies and how they're drawn.

### Permissions
- Declared in `tool/lighttool.toml` `permissions = [...]`; allow-list is in
  `plugin/.../LightToolMetadata.kt`.
- **Camera IS allowed** (`android.permission.CAMERA`), and the SDK ships a built-in
  `LightQrCodeScanner` composable that handles the permission flow. This is what
  backs the in-scope "scan a physical card to import it" feature. Note the built-in
  scanner targets QR codes — verify it reads the 1D barcodes many loyalty cards use,
  or plan a fallback, before relying on it.
- Runtime request: `rememberPermissionRequestLauncher(Manifest.permission.CAMERA)`
  then `.launch()`; check with `checkPermission(...)`.

### Local storage (all provided via `lightContext`)
- Key/value: `lightContext.dataStore` (`DataStore<Preferences>`, androidx.datastore).
- Structured/relational: Room via `lightContext.buildDatabase(MyDb::class.java, "name")`
  — see `examples/authenticator/` for a working Room example (closest analog to this tool).
- Files: `lightContext.filesDir`; share-to-LightOS via `lightContext.fileShare`.
- Background work: `androidx.work` wrapped as `LightWork` + `@LightJob`.

### Build & sideload
- Credentials: SDK libs come from GitHub Packages. Set `gpr.user`/`gpr.key` in
  `local.properties` (or env `GITHUB_ACTOR`/`GITHUB_TOKEN`).
- Build APK: `./gradlew :tool:assembleDebug` → `tool/build/outputs/apk/debug/tool-debug.apk`.
- Validate: `./gradlew check` (runs the plugin's dependency/source guardrails).
- Sideload: `adb install -r tool/build/outputs/apk/debug/tool-debug.apk`.
- Full-fidelity testing (permissions, service binding) needs either LP3 hardware or
  the LightOS emulator set up as a system app — see `docs/system_app/`.

### Supported code types & rendering (RESOLVED)
Rendering is the core of this tool, and it is done — no allow-list change was needed.
The generation risk is closed; only scanning of 1D barcodes remains open (see below).

**Supported symbologies** — the `CodeType` enum (`tool/.../code/CodeType.kt`) is the
source of truth. A card stores only its raw value + `CodeType`; nothing is stored as
an image. `CodeEncoder.encode(value, type)` turns that pair into drawable geometry at
display time.

| `CodeType` | Symbology            | Kind | Accepted input |
| ---------- | -------------------- | ---- | -------------- |
| `QR`       | QR Code              | 2D   | any UTF-8 string (byte mode) |
| `CODE_128` | Code 128             | 1D   | ASCII 32–126; auto Code C for digit runs, else Code B |
| `EAN_13`   | EAN-13               | 1D   | 12 digits (check digit appended) or 13 |
| `EAN_8`    | EAN-8                | 1D   | 7 digits (check digit appended) or 8 |
| `UPC_A`    | UPC-A                | 1D   | 11 digits (check digit appended) or 12 |
| `CODE_39`  | Code 39              | 1D   | uppercased `0-9 A-Z` and `- . space $ / + %` |
| `ITF`      | Interleaved 2 of 5   | 1D   | an even number of digits |

**How they're drawn** — all encoders are plain Kotlin, vendored as source under
`tool/.../code/`, no Gradle dependency and no Android APIs (passes the plugin
guardrails; see "What I'm allowed to use"):
- `QrEncoder.kt` — a trimmed MIT-licensed port of Nayuki's QR generator (byte mode,
  auto version + ECC boost, full mask selection). Emits a `CodeGeometry.QrMatrix`.
- `Barcode1DEncoders.kt` — the five 1D symbologies, each producing a
  `CodeGeometry.BarPattern` (run-length bar/space widths). GS1 for EAN/UPC, ISO/IEC
  15417 for Code 128, ISO/IEC 16388 for Code 39 and ITF.
- `CodeGeometry.kt` is the drawable model; `CardCodeView.kt` scales it to fill the
  screen on a Compose Canvas. Invalid input throws `Barcode1DEncoders.EncodeException`.

Growing the set: add an enum case, a `CodeEncoder.encode` branch, and an encoder.
The set is deliberately small for v1 and grows as real cards turn up types we don't
yet handle.

### Open technical risk — scanning 1D barcodes (UNRESOLVED)
Rendering (above) is done; **reading** codes with the camera is still open. Re-check
after each `/sync-upstream` in case the built-in scanner changes.
- The built-in `LightQrCodeScanner` targets QR codes, but many loyalty/membership
  cards use 1D barcodes (Code 128, UPC/EAN, Code 39). Unknown whether it decodes
  those. If it doesn't, options: manual entry only for 1D cards, or an allow-list
  request for a scanning library.
- **Practical fallback for v1** — Manual entry (type the number + pick the code type)
  needs neither camera nor a scanning library, so it ships independent of scanning.
  Camera import can follow once this question is settled.
