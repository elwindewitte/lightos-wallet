#!/usr/bin/env bash
# Replace every card in the Wallet on the LightOS emulator with the mock cards in
# scripts/mock-cards.sql (fake numbers, Dutch loyalty-card names). Also clears the
# "recently opened" list. Needs a debug build installed (./scripts/install-emulator.sh).
#
# Usage: ./scripts/load-mock-cards.sh            (uses $ANDROID_SERIAL if set)
set -euo pipefail

cd "$(dirname "$0")/.."
PKG=io.github.elwindewitte.wallet
ADB="${ADB:-adb}"
command -v "$ADB" >/dev/null || ADB="$HOME/Library/Android/sdk/platform-tools/adb"

if ! "$ADB" shell run-as "$PKG" ls databases/wallet.db >/dev/null 2>&1; then
  # Room creates the database on first launch.
  "$ADB" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 4
fi

"$ADB" shell am force-stop "$PKG"
"$ADB" shell "run-as $PKG sqlite3 databases/wallet.db \"$(grep -v '^--' scripts/mock-cards.sql | tr '\n' ' ')\""
"$ADB" shell run-as "$PKG" rm -rf files/datastore
"$ADB" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
echo "Loaded $("$ADB" shell "run-as $PKG sqlite3 databases/wallet.db 'select count(*) from cards'" | tr -d '\r') mock cards."
