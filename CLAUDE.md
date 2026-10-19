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
  `tool/lighttool.toml`). The SDK's `LightQrCodeScanner` handles the permission flow and,
  given a `formats` list of `LightBarcodeFormats` constants, scans QR + 1D codes and
  reports the detected format via `onScannedBarcode`. See "How the tool is built ›
  Camera scanning" for how the tool uses it.
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
- **`serverPackage` stays `com.lightos` in git.** CI and Light's tool-library build server
  use it as-is, and the plugin rejects anything else unless
  `-DlightSdk.allowAltServerPackage=true` is passed. For the LightOS emulator, run
  `./scripts/install-emulator.sh`: it swaps in `com.thelightphone.sdk.emulator`, runs
  `:tool:installDebug` with the flag, and always restores the toml.
- Emulator gotchas: boot with `emulator -avd Light_Phone_3 -writable-system` **every
  time** (without it Android drops the LightOS emulator app, wiping its settings and
  resetting Home), and set LightOS Settings → Allowed Tools → **All Tools**, otherwise
  the Wallet is refused ("unable to request camera permission").
- Pre-submission checklist: `TEST_PLAN.md`.
- Sideload: `./scripts/install-emulator.sh` (see the `serverPackage` note below).
  **This only works against the LightOS emulator (AVD), not a real Light Phone III.**
- **Installing on real LP3 hardware is currently BLOCKED.** USB debugging / adb install
  onto the device is not available. Upstream now documents the intended path — the
  on-device **Tool Manager** over Wi-Fi (Dashboard developer mode → upload the signing-key
  fingerprint → upload the APK, or `./gradlew :tool:uploadTool -Pdevice.ip=… -Pdevice.token=…`)
  — see `docs/sideloading/README.md`. But per that doc it is **not yet rolled out to
  LightOS production builds** (as of 2026-08-25), so until it ships do the full dev loop
  on the LightOS emulator. Source: https://github.com/orgs/lightphone/discussions/70
  Re-check `docs/sideloading/` after each `/sync-upstream` in case it has shipped.
- Full-fidelity testing (permissions, service binding) therefore runs on the LightOS
  emulator set up as a system app — see `docs/system_app/`.

## How the tool is built (current architecture)

Everything lives in `tool/src/main/kotlin/com/thelightphone/wallet/`, split into screens
(top level), `data/` (Room persistence), and `code/` (code encoding + Canvas rendering).
This fork carries **no SDK edits** — `sdk/`, `plugin/` and the build files match upstream,
so all tool code builds against the public SDK API. Keep it that way.

### Storage — Room, one source of truth
- `LoyaltyCard` (`LoyaltyCard.kt`) is the UI model: `id` (stable row id), `name`, `code`,
  `codeType`. Rename / delete / selection key on `id`. Names are unique (trimmed,
  case-insensitive): `NameCardScreen` and `EditCardNameScreen` share `NameEditor.kt`, which
  checks `CardRepository.isNameTaken` (DAO `nameExists`, `COLLATE NOCASE`, excluding the
  card being renamed) and shows a `LightFullscreenModal` on a clash.
- `data/`: `CardEntity` (`@Entity "cards"`), `CardDao` (`observeCards(): Flow`, plus
  `suspend` `insert` / `rename` / `deleteByIds`), `WalletDatabase` (`@Database`), and
  `CardRepository` — a process singleton (`getInstance { … }`) that maps entity↔model and
  exposes `observeCards(): Flow<List<LoyaltyCard>>`. Follows the `examples/authenticator`
  pattern.
- **Schema changes & migrations.** Each DB version's structure is exported to
  `tool/schemas/com.thelightphone.wallet.data.WalletDatabase/<version>.json` (committed;
  `exportSchema = true` + the `ksp { room.schemaLocation }` arg in `tool/build.gradle.kts`).
  The SDK's `buildDatabase()` is `Room.databaseBuilder(...).build()` with no hook for
  `addMigrations`/fallbacks, so **hand-written `Migration`s can't be registered — use Room
  `@AutoMigration`**, which diffs these JSON files. An update that changes the schema
  without a migration makes Room throw on open (the tool crashes; data stays on disk).
  To change the schema:
  1. Edit the entity, bump `version` in `@Database`, and add
     `autoMigrations = [AutoMigration(from = N, to = N + 1)]` (with an `AutoMigrationSpec`
     for renames/deletes, e.g. `@RenameColumn`, `@DeleteColumn`).
  2. Build; Room writes `N+1.json`. Commit it next to the old one. Never edit or delete a
     released version's JSON.
  3. Test on the emulator: install the old release, add cards, install the new build over
     it (TEST_PLAN § 9 "Survives an update"), and check the cards are intact.
  4. Bump `versionCode` in `tool/lighttool.toml` as for any release.
- Wiring: `HomeScreen` builds the repository once in a field via
  `lightContext.buildDatabase(WalletDatabase::class.java, CardRepository.DATABASE_NAME)`
  and passes it to `HomeScreenViewModel`, which surfaces the list with
  `repository.observeCards().stateIn(...)`. Writes are fire-and-forget on
  `viewModelScope`; the Room `Flow` re-emits, so there is no in-memory list to keep in
  sync. No seed data — a fresh install starts empty (Browse shows an empty-state hint).

### Screens & navigation
- `HomeScreen` (`@InitialScreen`) — the card list (Search / SELECT / ＋ bottom bar). With no
  cards it shows a first-run empty state instead: a short centered explanation and a
  bottom bar with a single text button, ADD (the Authenticator pattern).
- `SearchScreen` (returns the tapped `LoyaltyCard`; Home opens it) and `SelectCardsScreen`
  (bulk delete; returns the ids to delete after `DeleteCardConfirmScreen`) are **separate
  screens, not modes of Home**. Reason: the SDK's hardware-back path
  (`LightActivity.handleOnBackPressed` → its own `goBack()`) pops the back stack without
  calling `LightViewModel.onBackPressed()`, and the guardrail blocks `androidx.activity`
  (no `BackHandler`). So don't rely on `onBackPressed()` for in-screen modes — give each
  "mode" its own screen and let the back stack handle it.
- `CardDetailScreen` — opens one card and renders its code full-screen (see rendering).
  A bottom bar carries two text buttons, **RENAME** and **DELETE**, each navigating
  straight to its own screen. Edits are pushed back to the list through
  `onRename(id, …)` / `onDelete(id)` callbacks (not screen results) so they survive the
  hardware back button, which pops without a result.
- Rename / delete: the detail screen's RENAME → `EditCardNameScreen` (title "Rename") and DELETE →
  `DeleteCardConfirmScreen` (confirm). Bulk delete runs from Home's Select mode through
  the same confirm screen.
- Add flow (camera): `HomeScreen` ＋ → `ScanCardScreen` → (scanner popped, per SDK camera
  guidance) → Home pushes `NameCardScreen` → save.
- Add flow (manual): `ScanCardScreen` "Enter manually" → `EnterCodeScreen` → NEXT pushes
  `PickCodeTypeScreen(code)` → pick pushes `NameCardScreen`. Each step stays on the stack
  so Back returns one step with input kept (`EnterCodeViewModel` holds the
  `TextFieldState`); the finished `ScannedCode(code, codeType, name)` bubbles back up via
  `goBack` and Home saves it directly because `name` is set.
- Input checks (`code/CodeInput.kt`, plain Kotlin): `typesFor(value)` filters the type
  picker to types that can encode the value; `check()` flags a full-length EAN/UPC with a
  wrong check digit (shown with the SDK's `LightFullscreenModal`, LightOS's error pattern);
  `normalize()` appends the EAN/UPC check digit and uppercases Code 39. Codes are stored
  normalized (`HomeScreenViewModel.addCard`) and displayed normalized (`CardCodeView`), so
  the printed number always matches the bars. Empty NEXT/SAVE does nothing.
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

### Camera scanning
- The tool cannot own the camera: the guardrail blocks `LocalContext`, and CameraX / ML
  Kit are `implementation`-only in `sdk/ui` (not on the tool's classpath). The camera
  lives in the SDK; the tool owns the *screen*.
- `ScanCardScreen` uses the SDK's `LightQrCodeScanner` (`sdk/client/.../LightClientUiUtils.kt`,
  wrapping `sdk/ui/.../LightCodeScanner.kt`) with `formats` = the `LightBarcodeFormats`
  constants we can render, and reads results from `onScannedBarcode` (`LightScannedBarcode`:
  `value`, `format` as an `Int`, `rawBytes`). Without `onScannedBarcode` it only reports QR.
  ML Kit auto-detects the symbology, so there is **no QR-vs-barcode prompt** — the detected
  format maps straight to a `CodeType`, and unrenderable formats are never enabled.
  (This replaced an earlier fork-only `LightBarcodeScanner` once upstream shipped the
  same capability.)

### Fonts
- LightOS's typeface is **Akkurat** (licensed). `sdk/ui/.../LightFont.kt` uses the LP3's
  system Akkurat, else `res/font/akkuratll_*` bundled in the APK, else Android's default.
  This repo bundles none, so **on the emulator the Wallet renders in Roboto** (bolder,
  wider buttons) while Light's own emulator APKs (Authenticator, LightOS emulator) bundle
  Akkurat. Not a bug — judge typography on real hardware. Never commit font files.

### Text input & the LP3 keyboard
- Name / code entry uses the SDK `LightTextInputEditor` (`EditCardNameScreen`,
  `NameCardScreen`, `EnterCodeScreen`). Each **must** be a `LightScreen` (owning its own
  ViewModelStore) with a **distinct editor title** — the SDK caches the keyboard view
  model by title, so a shared title leaves later editors unresponsive.
- Search uses the embedded keyboard directly (`SearchScreen` + `SearchKeyboardCallback`)
  because `LightTextInputEditor` has no slot for live results between field and keyboard.
  It builds an `EnQwertyLp3KeyboardViewModel` the same way `LightTextInputEditor` does
  (copy that file when the keyboard library changes); `SearchKeyboardCallback` mirrors the
  SDK's `internal` `TextInputKeyboardCallback`, except Return hides the keyboard (tap the
  search field to show it again). Default order is most-recently-opened; recents are
  ids persisted in the tool's DataStore (`data/RecentCardsStore.kt`, key
  `recent_card_ids`) by `HomeScreenViewModel`.

### Barcodes — RESOLVED
Both former open risks are closed. Codes are **rendered** by self-drawing on Canvas (see
"Supported code types & rendering"), and 1D + QR codes are **scanned** with the camera via
the SDK's multi-format `LightQrCodeScanner` (see "Camera scanning") — this supersedes the
earlier "scanning 1D barcodes unresolved" note. Manual entry (`EnterCodeScreen` →
`PickCodeTypeScreen`) remains as a camera-free fallback. One thing to keep honest: ML Kit's
`rawValue` must match what the `Barcode1DEncoders` expect so a scanned code re-renders
identically (e.g. EAN/UPC check digits) — verify when adding a symbology.
