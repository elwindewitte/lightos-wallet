#!/usr/bin/env bash
# Boot the LightOS emulator (AVD Light_Phone_3) if it isn't running yet, and wait
# until Android has finished booting.
#
# Always boots with -writable-system: without it Android drops the LightOS emulator
# app, wiping its settings (Allowed Tools) and resetting Home.
#
# The emulator is started in its own session so it keeps running after this script
# (or the Conductor run script that called it) exits. Its log goes to
# .context/emulator.log.
set -euo pipefail

cd "$(dirname "$0")/.."
AVD=${AVD:-Light_Phone_3}
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
EMULATOR=$SDK/emulator/emulator
ADB=$SDK/platform-tools/adb

if "$ADB" devices | grep -q '^emulator-.*device$'; then
  echo "Emulator already running."
else
  echo "Starting emulator ${AVD}..."
  mkdir -p .context
  # perl's setsid detaches the emulator from this script's process group.
  nohup perl -MPOSIX -e 'POSIX::setsid(); exec @ARGV' \
    "$EMULATOR" -avd "$AVD" -writable-system "$@" \
    > .context/emulator.log 2>&1 &
fi

"$ADB" wait-for-device
until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
  sleep 2
done
echo "Emulator ready."
