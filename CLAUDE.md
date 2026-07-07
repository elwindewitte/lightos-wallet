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
- **Camera IS allowed** (`android.permission.CAMERA`, now declared in
  `tool/lighttool.toml`). The SDK ships `LightQrCodeScanner` (QR only); this fork adds
  `LightBarcodeScanner`, which scans a chosen set of `LightBarcodeFormat` (QR + 1D) and
  reports the detected format. Both handle the permission flow. See "How the tool is
  built › Camera scanning" for how the tool uses it.
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

## How the tool is built (current architecture)

Everything lives in `tool/src/main/kotlin/com/thelightphone/wallet/`, split into screens
(top level), `data/` (Room persistence), and `code/` (code encoding + Canvas rendering).
Re-verify the SDK fork edits noted below after each `/sync-upstream`.

### Storage — Room, one source of truth
- `LoyaltyCard` (`LoyaltyCard.kt`) is the UI model: `id` (stable row id), `name`, `code`,
  `codeType`. Rename / delete / selection key on `id`, so two cards may share a name.
- `data/`: `CardEntity` (`@Entity "cards"`), `CardDao` (`observeCards(): Flow`, plus
  `suspend` `insert` / `rename` / `deleteByIds`), `WalletDatabase` (`@Database`), and
  `CardRepository` — a process singleton (`getInstance { … }`) that maps entity↔model and
  exposes `observeCards(): Flow<List<LoyaltyCard>>`. Follows the `examples/authenticator`
  pattern.
- Wiring: `HomeScreen` builds the repository once in a field via
  `lightContext.buildDatabase(WalletDatabase::class.java, CardRepository.DATABASE_NAME)`
  and passes it to `HomeScreenViewModel`, which surfaces the list with
  `repository.observeCards().stateIn(...)`. Writes are fire-and-forget on
  `viewModelScope`; the Room `Flow` re-emits, so there is no in-memory list to keep in
  sync. No seed data — a fresh install starts empty (Browse shows an empty-state hint).

### Screens & navigation
- `HomeScreen` (`@InitialScreen`) — the card list. One screen, three modes via the
  `HomeMode` enum: **Browse** (list + Search / EDIT / ＋ bottom bar), **Search**, and
  **Select** (bulk-delete). `onBackPressed()` consumes hardware back to leave
  Search/Select rather than exit the tool.
- `CardDetailScreen` — opens one card and renders its code full-screen (see rendering).
  The ⋯ button opens `CardOptionsScreen` (Edit / Delete). Edits are pushed back to the
  list through `onRename(id, …)` / `onDelete(id)` callbacks (not screen results) so they
  survive the hardware back button, which pops without a result.
- Edit / delete: `CardOptionsScreen` → `EditCardNameScreen` (rename) or
  `DeleteCardConfirmScreen` (confirm). Bulk delete runs from Home's Select mode through
  the same confirm screen.
- Add flow: `HomeScreen` ＋ → `ScanCardScreen` → `NameCardScreen` → save. The scanner is
  popped before the name screen is pushed (SDK camera guidance: `goBack` then the caller
  `navigateTo`s). `ScanCardScreen`'s "Enter manually" fallback runs `EnterCodeScreen` →
  `PickCodeTypeScreen` and converges on the same `NameCardScreen`; both routes yield a
  `ScannedCode(code, codeType)`.
- Conventions: go forward with `navigateTo(screenFactory = { … }, resultCallback = { … })`;
  `goBack(result)` returns a value, while `goBack(null)` / `goBack()` delivers no result (a
  cancel) — a `resultCallback` only fires for a non-null result.

### Supported code types & rendering (RESOLVED)
Rendering is the core of this tool, and it is done — no allow-list change was needed. A
card stores only its raw value + `CodeType` (`tool/.../code/CodeType.kt`, the source of
truth); nothing is stored as an image. `CodeEncoder.encode(value, type)` turns that pair
into drawable geometry at display time.

| `CodeType` | Symbology            | Kind | Accepted input |
| ---------- | -------------------- | ---- | -------------- |
| `QR`       | QR Code              | 2D   | any UTF-8 string (byte mode) |
| `CODE_128` | Code 128             | 1D   | ASCII 32–126; auto Code C for digit runs, else Code B |
| `EAN_13`   | EAN-13               | 1D   | 12 digits (check digit appended) or 13 |
| `EAN_8`    | EAN-8                | 1D   | 7 digits (check digit appended) or 8 |
| `UPC_A`    | UPC-A                | 1D   | 11 digits (check digit appended) or 12 |
| `CODE_39`  | Code 39              | 1D   | uppercased `0-9 A-Z` and `- . space $ / + %` |
| `ITF`      | Interleaved 2 of 5   | 1D   | an even number of digits |

`CodeType` also carries `is2d` and `displayLabel` (labels for the manual type picker).

**How they're drawn** — all encoders are plain Kotlin, vendored as source under
`tool/.../code/`, no Gradle dependency and no Android APIs (passes the plugin
guardrails; see "What I'm allowed to use"):
- `QrEncoder.kt` — a trimmed MIT-licensed port of Nayuki's QR generator (byte mode,
  auto version + ECC boost, full mask selection). Emits a `CodeGeometry.QrMatrix`.
- `Barcode1DEncoders.kt` — the five 1D symbologies, each producing a
  `CodeGeometry.BarPattern` (run-length bar/space widths). GS1 for EAN/UPC, ISO/IEC
  15417 for Code 128, ISO/IEC 16388 for Code 39 and ITF.
- `CodeGeometry.kt` is the drawable model; `CardCodeView.kt` scales it to fill the
  screen on a Compose Canvas — black on white with a quiet-zone border, snapped to whole
  pixels for crisp scanning. Invalid input throws `Barcode1DEncoders.EncodeException`.

Growing the set: add an enum case, a `CodeEncoder.encode` branch, and an encoder. The set
is deliberately small for v1 and grows as real cards turn up types we don't yet handle.

### Camera scanning (SDK fork edit — re-verify after `/sync-upstream`)
- The tool cannot own the camera: the guardrail blocks `LocalContext`, and CameraX / ML
  Kit are `implementation`-only in `sdk/ui` (not on the tool's classpath). The camera
  lives in the SDK; the tool owns the *screen*.
- This fork generalizes `sdk/ui/.../LightQrCodeScanner.kt` and adds a public
  `LightBarcodeScanner` (+ a permission-wrapping twin in
  `sdk/client/.../LightClientUiUtils.kt`). It scans a caller-chosen
  `Set<LightBarcodeFormat>` and reports `(value, LightBarcodeFormat)`. ML Kit auto-detects
  the symbology, so there is **no QR-vs-barcode prompt** — `ScanCardScreen` maps the
  detected format straight to a `CodeType` and only enables the formats the tool can
  render. The original QR-only `LightQrCodeScanner` is left intact (still used by the
  examples), so this is an additive change.

### Text input & the LP3 keyboard
- Name / code entry uses the SDK `LightTextInputEditor` (`EditCardNameScreen`,
  `NameCardScreen`, `EnterCodeScreen`). Each **must** be a `LightScreen` (owning its own
  ViewModelStore) with a **distinct editor title** — the SDK caches the keyboard view
  model by title, so a shared title leaves later editors unresponsive.
- Search uses the embedded keyboard directly (`SearchContent` + `SearchKeyboardCallback`);
  its default order is most-recently-opened, with recents tracked in-memory by `id`.

### Barcodes — RESOLVED
Both former open risks are closed. Codes are **rendered** by self-drawing on Canvas (see
"Supported code types & rendering"), and 1D + QR codes are **scanned** with the camera via
the fork's multi-format `LightBarcodeScanner` (see "Camera scanning") — this supersedes the
earlier "scanning 1D barcodes unresolved" note. Manual entry (`EnterCodeScreen` →
`PickCodeTypeScreen`) remains as a camera-free fallback. One thing to keep honest: ML Kit's
`rawValue` must match what the `Barcode1DEncoders` expect so a scanned code re-renders
identically (e.g. EAN/UPC check digits) — verify when adding a symbology.
