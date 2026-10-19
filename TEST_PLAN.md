# Wallet — Test Plan

Run this before every tool-library submission. Work top to bottom and tick each box.
If a check fails, write down what you saw next to it and fix it before you submit.

**Where to test:** the LightOS emulator (AVD `Light_Phone_3`). Real-LP3 install isn't
possible yet (see CLAUDE.md › Build & sideload). Once it is, run sections 3–9 again on
the phone, especially camera scanning (§4) and in-store scanning (§9).

---

## 0. Submission blockers — check these first

These come from Light's [Tool Guidelines](TOOL_GUIDELINES.md), [tool metadata rules](docs/tool_metadata/README.md)
and the "Submitting Your Tool" steps in upstream's README. Each one fails the
submission on its own, however well the app works.

- [x] **Tool id is yours and final:** `io.github.elwindewitte.wallet` (based on your GitHub
      account, so nobody else can claim it). **It can't change after the tool is registered.**
- [ ] **The GitHub repo is public.** Light builds from a public commit, and the
      repo is currently **private**.
- [x] **`serverPackage = "com.lightos"`** in `tool/lighttool.toml` on the commit you
      submit. CI enforces this.
- [x] **`versionCode` is higher than the last version you submitted** (start at `1`), and
      `versionName` is strict `major.minor.patch`, e.g. `1.0.0`.
- [x] **Only the permissions you need.** `lighttool.toml` declares only `CAMERA`. (The
      built app still lists `INTERNET` because SDK libraries — okhttp and the scanner's
      Google library — add it; worth a line in your submission notes.)
- [x] **No leftover template code.** `ToolEntryPoint.kt` is the bare required shell.
- [ ] **Built on the latest Light SDK.** Run `/sync-upstream` shortly before submitting.
- [ ] **At least one screenshot** ready for the Dashboard listing.
- [x] **Third-party code is credited.** `QrEncoder.kt` carries Nayuki's full MIT notice.

---

## 1. Automated checks (a few minutes, run on your computer)

- [x] `./gradlew check` passes **without** extra flags. This is what CI and Light's build
      server run, and it includes the guardrail plugin and the encoder golden tests
      (`EncoderGoldenTest`).
- [x] The PR's **PR Check** on GitHub is green.
- [x] `./gradlew :tool:assembleRelease` builds. Release builds shrink the code; a crash
      that only shows up in release usually means something got stripped.
- [x] The release build also **runs**: install it on the emulator once (temporarily set the
      emulator `serverPackage`, then `./gradlew :tool:installRelease
      -DlightSdk.allowAltServerPackage=true`, then restore the toml). Add a card, restart
      the tool and check the card is still there. Light's server builds this version.

## 2. Emulator setup (once per session)

- [x] Start the emulator **with the flag**:
      `~/Library/Android/sdk/emulator/emulator -avd Light_Phone_3 -writable-system`
- [x] Pressing Home shows the black LightOS tool list (not the Android home screen).
- [x] LightOS **Settings → Allowed Tools** is **All Tools**.
- [x] Install the current code: `./scripts/install-emulator.sh`
- [x] Afterwards, `git status` shows `tool/lighttool.toml` unchanged.
- [ ] After uninstalling or changing the tool id, restart LightOS so its tool list
      refreshes: `adb shell am force-stop com.thelightphone.sdk.emulator`, then press Home.
      (The emulator only refreshes the list for Tool Manager installs; a stale entry
      silently does nothing when tapped.)

## 3. First launch & empty state

Start from a clean slate: `adb uninstall io.github.elwindewitte.wallet`, then
reinstall.

- [x] "Wallet" appears in the LightOS tool list and opens.
- [x] The empty list shows "No cards yet. Tap + to add one."
- [x] No permission prompt appears until you try to scan.
- [x] The bottom bar shows Search, EDIT and ＋, and nothing overlaps.
- [ ] **EDIT on an empty list** opens "Select to delete…" with nothing to select.
      *Found 2026-10-19:* hide or disable EDIT (and Search?) while there are no cards.
- [ ] **Hardware back in select mode** returns to the list.
      *Found 2026-10-19:* it **exits the tool** instead. The SDK sends the hardware back key
      straight to its back stack and never calls `onBackPressed()`, and the guardrail
      blocks `androidx.activity` (so no `BackHandler`). Affects Search mode too.

## 4. Add a card — camera scan

You need a working emulator camera (the emulator uses your Mac's webcam; macOS must
allow it). Show the codes on a second screen or on paper.

- [ ] Tap ＋ and the first time, the camera permission prompt appears. **Allow** opens the
      viewfinder with a live picture.
- [ ] **Deny** instead: the tool explains it can't scan, and "Enter manually" still works.
      Then grant the permission again (LightOS settings, or reinstall).
- [ ] Scan one code of **each supported type**. Each should jump straight to "Card name"
      with no "is this QR or barcode?" question:
  - [ ] QR code
  - [ ] Code 128
  - [ ] EAN-13 (most supermarket cards, e.g. Albert Heijn)
  - [ ] EAN-8
  - [ ] UPC-A
  - [ ] Code 39
  - [ ] ITF
- [ ] **Unsupported types are ignored:** pointing at a PDF417, Aztec, Data Matrix or
      UPC-E code doesn't capture anything; the scanner keeps looking.
- [ ] Back (top-left arrow **and** the hardware back key) on the scanner returns to the
      list without adding a card.
- [ ] Back on "Card name" cancels: no card is added.
- [ ] Name it and SAVE. The card appears in the list.
- [ ] **Round trip:** open the saved card and scan it with a phone barcode app (or a
      second scanner). It must decode to **exactly** the same number the physical card
      has, including the EAN/UPC check digit.

## 5. Add a card — manual entry

- [ ] ＋ → ENTER MANUALLY → "Card number" opens with the keyboard.
- [ ] Type a number → NEXT → "Code type" lists QR, Code 128, EAN-13, EAN-8, UPC-A,
      Code 39 and ITF.
- [ ] Pick a type → "Card name" → SAVE → the card appears in the list.
- [ ] Back at each step goes back exactly one step, and nothing is saved.
- [ ] **Check digits:** EAN-13 with 12 digits renders with a 13th (check) digit; with
      13 digits it renders exactly as typed. Same for EAN-8 (7/8) and UPC-A (11/12).
- [ ] **Code 39:** lowercase input is shown and encoded as uppercase.
- [ ] **Invalid input (known gap):** enter letters for EAN-13, an odd number of digits
      for ITF, or `é` for Code 39. Today the card saves and the detail screen shows only
      the number, with **no barcode**. Decide whether to block this at entry before
      submitting.
- [ ] **Long input:** a 200-character QR value still renders and scans.
- [ ] **Empty input:** NEXT / SAVE with nothing typed doesn't create a broken card.

## 6. View a card (the core job)

Do this for at least one QR card and one 1D barcode card.

- [ ] The code fills the screen width, black on a white panel, with a white margin
      (quiet zone) all around.
- [ ] Bars and modules are crisp, with no blurry or grey edges (zoom the screenshot in).
- [ ] The number is shown below the code.
- [ ] Nothing (title, buttons) overlaps the code.
- [ ] **It scans:** a phone barcode app reads it straight off the emulator screen.
- [ ] Long Code 128 / Code 39 values (20+ characters) still scan; thin bars stay at
      least 1 pixel wide.
- [ ] **Brightness:** on a real LP3, check the code scans in a dim room. (The tool
      doesn't force maximum brightness — decide whether that's acceptable.)

## 7. Edit & delete

- [ ] Card → EDIT → "Edit name" shows the current name. Change it → SAVE → the new
      name shows on the card and in the list.
- [ ] EDIT → hardware back: the name is unchanged.
- [ ] Card → DELETE → "This can't be undone." → CANCEL: the card is still there.
- [ ] Card → DELETE → DELETE: back on the list, and the card is gone.
- [ ] **Two cards with the same name:** renaming or deleting one leaves the other
      untouched.
- [ ] **Bulk delete:** list → EDIT → select mode. "Select to delete…" counts up
      ("2 selected"). DELETE → confirm removes exactly those cards. CANCEL leaves
      everything.
- [ ] Hardware back in select mode leaves select mode (it doesn't exit the tool).

## 8. Search

- [ ] Search → keyboard opens and the field shows a cursor.
- [ ] With nothing typed it shows recently opened cards, or "Type to search your cards"
      if you haven't opened any.
- [ ] Typing filters live, without pressing Enter (e.g. "he" → Albert Heijn, Shell).
- [ ] Matching ignores case ("SHELL" finds Shell).
- [ ] Gibberish shows "No cards found".
- [ ] Backspace deletes one character; **long-press** backspace deletes a word.
- [ ] Return doesn't add a new line.
- [ ] Emoji and accent keyboards show a close button; the main layouts don't.
- [ ] Tapping a result opens that card; back returns to the list.
- [ ] The ✕ and hardware back both leave search without exiting the tool.
- [ ] Recents: open card A, then B. Search with nothing typed shows B first, then A.

## 9. Durability & real-world use

- [ ] **Data survives:** add 3 cards, force-stop the tool
      (`adb shell am force-stop io.github.elwindewitte.wallet`) and reopen. All 3 are still there.
- [ ] **Survives a reboot** of the emulator (started with `-writable-system`).
- [ ] **Survives an update:** bump `versionCode`, reinstall over the top, and the cards
      are still there. (This is what users will see when you ship v1.0.1.)
- [ ] **Many cards:** with 25+ cards the list scrolls, the scrollbar appears and the
      last card can be reached and opened.
- [ ] **Long names** (40+ characters) wrap or truncate cleanly in the list, on the
      card title and in search.
- [ ] **Airplane mode / no network:** everything still works. The tool is fully offline.
- [ ] **Leaving and coming back:** press Home on a card, then reopen the tool. No crash,
      and it still shows the card or the list.
- [ ] **In-store test (real LP3, once possible):** scan a real card at a real till.

## 10. Design & guideline review (LightOS fit)

From CLAUDE.md's design constraints and Light's Tool Guidelines.

- [ ] Black and white only: no screen relies on color to make sense.
- [ ] One screen, one job: no feeds, dashboards or links between features.
- [ ] No notifications, badges, or anything that pulls you back into the tool.
- [ ] No analytics, tracking or ads, and no data leaves the device.
- [ ] Text uses the SDK styles and spacing matches other LightOS tools (compare with
      Authenticator and Weather on the emulator).
- [ ] Every screen can be left with the hardware back key, and back never exits the
      tool unexpectedly.
- [ ] Turn on dark/light theme in LightOS settings (if available) and check every
      screen above again in both.

## 11. Submit

- [ ] Everything above is ticked, or consciously accepted and written down.
- [ ] Merge to `main`, then note the commit or tag you're submitting (e.g. tag `v1.0.0`).
- [ ] Dashboard → Developer Account → Manage Custom Tools → Submit New Tool → submit a
      build with that git ref and a short changelog.
- [ ] Download the signed APK from the Dashboard and re-run sections 3, 4, 6 and 9 on it.
      This is the exact build users will get.
