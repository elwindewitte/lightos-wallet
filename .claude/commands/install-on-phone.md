---
description: "[BLOCKED — parked] Build + sideload the Wallet tool onto a real Light Phone III. Real-hardware install is not possible yet; revive this once Light ships Dashboard install."
---

> **⚠️ PARKED — DO NOT RUN YET. Real-hardware install is currently BLOCKED.**
>
> There is no sideloading path to a physical Light Phone III today — USB debugging /
> `adb install` onto the device is not available, and the LP3's LightOS settings do
> not expose a developer-mode / USB-debugging toggle. The Light team has announced
> they are building an option to install tools onto the phone through the
> **Dashboard**, but it has not shipped. Source (2026):
> https://github.com/orgs/lightphone/discussions/70
>
> Until Dashboard install ships, do the full dev loop on the LightOS emulator
> instead. The steps below assume `adb install` to real hardware works and are kept
> only as a starting point to revive once the feature lands — **re-verify every step
> against Light's shipped instructions before trusting them; the real flow will
> likely be Dashboard-based, not adb.** Re-check the discussion above (and after each
> `/sync-upstream`) to see if it's shipped. See `CLAUDE.md` → "Build & sideload".

Build and install this tool onto my real Light Phone III over USB. The only
LP3-specific code change is the `serverPackage` in `tool/lighttool.toml`: on real
hardware it must be `com.lightos`, but on the emulator it must stay
`com.thelightphone.sdk.emulator`. If the wrong one ships, the tool fails on device
with "unable to request camera permission!". So this command flips it to
`com.lightos` only for the build, then **flips it back** afterward — the APK on the
phone is the correct LP3 build, and the repo is left in its normal emulator state so
I never accidentally commit the hardware value.

I am new to this, so keep me safe and explain things in plain language. Follow these
steps in order and stop if something looks wrong rather than guessing.

Two environment facts for this Mac (from setup notes — verify before relying):
- Terminal Gradle MUST run on JDK 17 or the `:plugin` module fails to configure.
  Prefix Gradle with
  `JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"`.
- `adb` is not on the non-interactive shell PATH. Use the absolute path
  `/Users/elwindewitte/Library/Android/sdk/platform-tools/adb` (or `$ANDROID_HOME/platform-tools/adb`).

1. **Check for unsaved work in the tool config.** Run `git status`. Look
   specifically at `tool/lighttool.toml`: if it already has uncommitted changes,
   STOP and show me what they are — I need to know, because this command edits and
   then reverts that file, and I don't want to lose or commit an unrelated edit by
   accident. Other uncommitted files elsewhere are fine; only the toml matters here.

2. **Confirm the phone is connected and trusted.** Run
   `/Users/elwindewitte/Library/Android/sdk/platform-tools/adb devices`.
   - If nothing is listed, tell me to plug the LP3 in via USB-C and make sure USB
     debugging is on (Settings → About → tap build number 7×, then Developer
     options → USB debugging).
   - If it shows `unauthorized`, tell me to accept the "Allow USB debugging?" prompt
     on the phone's screen (and check "always allow from this computer").
   - Only continue once exactly one device shows as `device`. If more than one
     device is listed, ask me which is the phone before going on.

3. **Flip serverPackage to hardware.** In `tool/lighttool.toml`, set
   `serverPackage = "com.lightos"` (comment out or replace the
   `com.thelightphone.sdk.emulator` line). Show me the one-line change. Remember the
   original value — you will restore it in step 7 no matter what happens.

4. **Build the APK.** Run:
   `JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" ./gradlew :tool:assembleDebug`
   The result is `tool/build/outputs/apk/debug/tool-debug.apk`. If the build fails,
   STOP, show me the error in plain language, and go straight to step 7 to restore
   the emulator value — do not leave the repo flipped to hardware.

5. **Install onto the phone.** Run:
   `/Users/elwindewitte/Library/Android/sdk/platform-tools/adb install -r tool/build/outputs/apk/debug/tool-debug.apk`
   The `-r` reinstalls over any existing copy so I can run this repeatedly. Confirm
   the output says `Success`. If it fails (e.g. a signature mismatch from a
   previously installed build), tell me plainly; the usual fix is to uninstall first
   with `adb uninstall io.github.elwindewitte.wallet` and install again — but ask me before
   uninstalling, since that clears any cards already saved on the phone.

6. **Tell me it worked.** Confirm the tool is installed and that I should find
   "Wallet" in the LightOS tools list on the phone to open it.

7. **Restore the emulator default (ALWAYS do this).** Set `serverPackage` in
   `tool/lighttool.toml` back to `com.thelightphone.sdk.emulator`. Run `git status`
   / `git diff tool/lighttool.toml` and confirm to me that the file is back to its
   original state with no lingering change. This runs whether the build/install
   succeeded or failed — the repo must never be left pointing at `com.lightos`. If I
   want to iterate on the phone again, just re-run this command.
