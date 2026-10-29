#!/usr/bin/env bash
# Copies the web connectors from a Trackhop checkout into this repository, unchanged but for the
# app's name. Trackhop is where the connectors are developed: its :bridge library (WebView sign-ins,
# players, BridgeCore) and its :ytm module (the YouTube Music client without Android) are built
# into its GitHub/F-Droid build, and this app serves the same code to its Google Play build through
# BridgeProvider. Never edit android/bridge or android/ytm here: fix them in Trackhop, then run
#
#   scripts/sync-from-trackhop.sh [PATH_TO_TRACKHOP]     # default: ../Trackhop
#
# and commit the result together with the updated android/UPSTREAM.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
src=$(cd "${1:-$root/../Trackhop}" && pwd)
[ -f "$src/android/bridge/build.gradle" ] && [ -f "$src/android/ytm/build.gradle" ] ||
  { echo "$src is not a Trackhop checkout (android/bridge, android/ytm missing)" >&2; exit 1; }

for m in bridge ytm; do
  rm -rf "$root/android/$m"
  mkdir -p "$root/android/$m"
  (cd "$src/android/$m" && git ls-files -z . | xargs -0 cp --parents -t "$root/android/$m")
done

# The one difference: messages that send the user to this app name it as it is called here.
# (grep finds nothing once Trackhop itself says "Trackhop Bridge": not an error under pipefail.)
{ grep -rlZ 'Songport Bridge' "$root/android/bridge" "$root/android/ytm" || true; } | xargs -0 -r sed -i 's/Songport Bridge/Trackhop Bridge/g'

# The last Trackhop commit that touched the two modules, the one to compare against next time.
rev=$(git -C "$src" log -1 --format=%H -- android/bridge android/ytm)
cat > "$root/android/UPSTREAM" <<NOTE
android/bridge and android/ytm are copies of Trackhop's modules of the same name, made by
scripts/sync-from-trackhop.sh. Do not edit them here: change Trackhop, then sync again.

Source: https://github.com/xlollx/Trackhop
Last change: $rev ($(git -C "$src" log -1 --format=%cs "$rev"))
Change: "Songport Bridge" reads "Trackhop Bridge" in the copied messages.
NOTE
echo "Synced android/bridge and android/ytm from $src at $rev"
