# CLAUDE.md — Light Wallet

## What this tool is (and is not)
Purpose: A Light Phone III tool that stores loyalty and membership cards and displays their barcodes / QR codes for scanning in-store.
In scope (v1): manually add a card (name + code value + code type); store it locally
on the device; list saved cards; open one to display its code full-screen, high
contrast, for scanning.
Out of scope (v1): scanning a physical card to import it (needs camera access —
UNVERIFIED whether the SDK allows it); cloud sync or accounts; payment/NFC cards; sharing.
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
  `LightQrCodeScanner` composable that handles the permission flow. So the v1
  "out of scope" note about camera being UNVERIFIED is now resolved: scanning is
  *technically possible*. It's still out of scope until I say otherwise — flag it,
  don't build it.
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
