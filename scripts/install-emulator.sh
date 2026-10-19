#!/usr/bin/env bash
# Build the Wallet and install it on the running LightOS emulator.
#
# tool/lighttool.toml is committed with serverPackage = "com.lightos" — the value
# CI and Light's tool-library build server require. The LightOS *emulator* app is
# a different package, so this script swaps it in for the duration of the build,
# then always restores the committed value (even if the build fails).
set -euo pipefail

cd "$(dirname "$0")/.."
TOML=tool/lighttool.toml
LIGHTOS='serverPackage = "com.lightos"'
EMULATOR='serverPackage = "com.thelightphone.sdk.emulator"'

if ! grep -qxF "$LIGHTOS" "$TOML"; then
  echo "error: expected the line '$LIGHTOS' in $TOML" >&2
  exit 1
fi

backup=$(mktemp)
cp "$TOML" "$backup"
trap 'cp "$backup" "$TOML"; rm -f "$backup"' EXIT

sed -i.bak "s|^$LIGHTOS\$|$EMULATOR|" "$TOML" && rm -f "$TOML.bak"
./gradlew :tool:installDebug -DlightSdk.allowAltServerPackage=true "$@"
