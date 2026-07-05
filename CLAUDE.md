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
  (e.g. ZXing is not allowed). Rendering a card's code will need either a custom
  Compose/Canvas implementation or an allow-list request to Light. Verify before
  committing to an approach.

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

### Usage measurement (open counts)
- We record how many times each card is opened, to enable a future "most used"
  sort. Implemented in `tool/.../CardStatsStore.kt` (DataStore/Preferences), wired
  through `HomeScreenViewModel.recordOpened()`; exposed as
  `HomeScreenViewModel.openCounts` (`Map<name, count>`).
- Not consumed for sorting yet — we're only collecting the data. Search still
  defaults to recents order (in-memory), then alphabetical.
- Keyed by card **name** (the only stable id the v1 `LoyaltyCard` has), so a
  rename starts a fresh count. Revisit when cards get a stable id / real
  persistence. Counts persist across sessions; the recents order does not.
- Stays within the design constraints: this is local, silent measurement — no
  telemetry, notifications, or re-engagement mechanics.

### Build & sideload
- Credentials: SDK libs come from GitHub Packages. Set `gpr.user`/`gpr.key` in
  `local.properties` (or env `GITHUB_ACTOR`/`GITHUB_TOKEN`).
- Build APK: `./gradlew :tool:assembleDebug` → `tool/build/outputs/apk/debug/tool-debug.apk`.
- Validate: `./gradlew check` (runs the plugin's dependency/source guardrails).
- Sideload: `adb install -r tool/build/outputs/apk/debug/tool-debug.apk`.
- Full-fidelity testing (permissions, service binding) needs either LP3 hardware or
  the LightOS emulator set up as a system app — see `docs/system_app/`.

### Open technical risks — barcodes (UNRESOLVED)
Barcodes are the core of this tool, and both directions have an open question against
the SDK's constraints. Resolve these before committing to an implementation; re-check
after each `/sync-upstream` in case the allow-list or scanner changes.
- **Rendering (generating) codes** — No barcode/QR generation library is on the
  dependency allow-list (e.g. ZXing is blocked; see "What I'm allowed to use"). Likely
  paths: (a) draw the code myself with Compose Canvas from the stored value, or
  (b) ask Light to add a generation library to the allow-list. QR is a simpler
  self-draw than most 1D symbologies.
- **Reading (scanning) codes** — The built-in `LightQrCodeScanner` targets QR codes,
  but many loyalty/membership cards use 1D barcodes (Code128, UPC/EAN, Code39). Unknown
  whether it decodes those. If it doesn't, options: manual entry only for 1D cards, or
  an allow-list request for a scanning library.
- **Practical fallback for v1** — Manual entry (type the number + pick the code type)
  needs neither camera nor a scanning library, so it can ship independent of the above.
  Camera import and on-device rendering can follow once the two questions are settled.
