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
- [x] The empty list shows the first-run screen: "No cards yet. Add your first one.",
      and a bottom bar with only **ADD**. Tapping ADD starts adding a card.
- [x] No permission prompt appears until you try to scan.
- [x] Once there's a card, the bottom bar shows Search, SELECT and ＋, and nothing overlaps.
- [x] Deleting the last card brings the first-run screen back.
- [x] **Hardware back from Search and from select mode** returns to the list (doesn't exit
      the tool). *Fixed 2026-10-19 by making both their own screens.* (The LP3 has no
      hardware back key, but the emulator does.)

## 4. Add a card — camera scan

> **Status 2026-10-19:** not yet run end to end. Scanning worked in earlier testing; the
> all-types run needs the printed test sheet (`.context/testsheet/index.html`) and an
> emulator started from Terminal so it can use the camera.

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

- [x] ＋ (or ADD) → ENTER MANUALLY → "Card number" opens with the keyboard.
- [x] NEXT with nothing typed does nothing (stays on "Card number").
- [x] Type a number → NEXT → "Code type" lists **only the types that can encode it**
      (e.g. a 13-digit number: QR, Code 128, EAN-13, Code 39; `ABC`: QR, Code 128,
      Code 39).
- [x] Back on "Code type" returns to "Card number" with the number still there.
- [x] Pick a type → "Card name". SAVE with nothing typed does nothing. Back returns to
      "Code type". Type a name → SAVE → the card appears in the list.
- [x] **Check digit typo:** 13 digits for EAN-13 with a wrong last digit (e.g.
      `8710398527701`) → EAN-13 shows "This number doesn’t look right. The last digit
      should be 7, check the number on your card." → ✕ returns to "Card number" with
      the number kept. Same check for EAN-8 and UPC-A.
- [x] **Check digit added:** EAN-13 with 12 digits (`871039852770`) shows
      `8710398527707` under the bars, identical to typing all 13. Same for EAN-8 (7/8)
      and UPC-A (11/12). *(Unit tests: `CodeInputTest`.)*
- [x] **Code 39:** lowercase input is saved and shown as uppercase. *(Unit test.)*
- [x] **Long input:** a 200-character QR value still renders. Over ~2,950 characters no
      type is offered ("No code type fits this number.").
- [ ] Known SDK limits (can't fix in the Wallet): "Card number" opens on the letter
      keyboard, and in 123 mode NEXT is hidden until you close the number keys.

## 6. View a card (the core job)

Do this for at least one QR card and one 1D barcode card.

- [x] The code fills the screen width, black on a white panel, with a white margin
      (quiet zone) all around.
- [x] Bars and modules are crisp, with no blurry or grey edges (zoom the screenshot in).
- [x] The number is shown below the code.
- [x] Nothing (title, buttons) overlaps the code.
- [x] **It scans:** a phone barcode app reads it straight off the emulator screen.
- [x] Long Code 128 / Code 39 values (20+ characters) still scan; thin bars stay at
      least 1 pixel wide.
- [ ] **Brightness:** on a real LP3, check the code scans in a dim room. The tool **can't**
      raise brightness itself: that needs the activity's window, which the SDK doesn't
      expose and the guardrail blocks (`android.app.`, `LocalView`, Activity casts).
      Only an SDK API from Light could add it.

**Results 2026-10-19:** one card per type plus 25-character Code 128 and 20-character
Code 39, added through the app and decoded from emulator screenshots with ZXing
(`.context/zxing`, run with `java -cp 'zxing/*' com.google.zxing.client.j2se.CommandLineRunner <png>`).
All 9 decode to exactly the entered value (after normalising). Thinnest bar: 2px (20-char
Code 39).
- *Fixed:* QR codes didn't decode. Centring put the code on a half pixel, so every module
  edge anti-aliased into grey seams. The origin is now pixel-snapped and dark runs are
  drawn as one rect; 0 grey pixels in any code now.
- [x] *Decided:* white margins stay as they are, although some are below the symbology
  specs (QR top 2 modules vs 4; EAN-13 6 vs 11/7; ITF 4 vs 10; short Code 128 8 vs 10).
  Bigger bars matter more on the small screen, and ZXing reads them all. If a till ever
  refuses one, revisit this first.

## 7. Rename & delete

- [x] Card → RENAME → "Rename" shows the current name. Change it → SAVE → the new
      name shows on the card and in the list.
- [x] RENAME → hardware back: the name is unchanged.
- [x] Card → DELETE → "This can't be undone." → CANCEL: the card is still there.
- [x] Card → DELETE → DELETE: back on the list, and the card is gone.
- [x] **Names are unique** (ignoring capitals and surrounding spaces). Adding or renaming
      to a name another card has shows "A card called “…” already exists. Choose another
      name." ✕ returns to the name with the typing kept. Renaming a card to its own name,
      or only changing its capitals, is allowed.
- [x] Cards are still keyed by id, so rename / delete only ever touch the card you opened
      (tested before names were made unique: two `dup` cards stayed independent).
- [x] **Bulk delete:** list → SELECT → select mode. "Select to delete…" counts up
      ("2 selected"). DELETE → confirm removes exactly those cards. CANCEL leaves
      everything.
- [x] Hardware back in select mode leaves select mode (it doesn't exit the tool).

**Results 2026-10-19** (scripted on the emulator, `.context/s7*.py`): rename shows the
current name and updates the card and list; Back from RENAME keeps the old name; DELETE →
CANCEL keeps the card; DELETE → DELETE removes it and returns to the list. Two cards named
`dup`: renaming one left the other, and deleting one left the other with its own code.
Duplicate names are now blocked (`ors` vs `Ors` on add, `dup` on rename; `gitf` → `Gitf`
allowed).
SELECT counts up ("2 selected"), CANCEL keeps everything, DELETE asks "Delete 2 cards?"
and removes exactly those two.

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
      Authenticator and Weather). **Compare fonts on a real LP3 only:** LightOS uses
      Akkurat, a licensed font. On the emulator the Wallet falls back to Android's default
      font (Roboto), so its text, especially bottom-bar buttons, looks bolder and wider
      than Authenticator, whose emulator build has Akkurat bundled. On an LP3, Akkurat is
      a system font and the SDK picks it up automatically. Never add the font files to
      this repo.
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
