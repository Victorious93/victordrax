#!/bin/sh
# Build the universal root module zip deterministically.
# Usage: module/build.sh [output-dir]   (default: module/build)
# Output: <output-dir>/victordrax-root-module-<version>.zip
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/.." && pwd)
OUT="${1:-$HERE/build}"
EPOCH_DATE="2020-01-01 00:00:00"
TZ=UTC
export TZ

command -v zip >/dev/null 2>&1 || { echo "build.sh: zip is required" >&2; exit 1; }

"$HERE/validate-prop.sh" "$HERE/module.prop"
VERSION=$(sed -n 's/^version=//p' "$HERE/module.prop" | head -n 1)

STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

# Explicit allowlist: nothing outside files.txt (plus LICENSE/NOTICE) is shipped.
LC_ALL=C sort "$HERE/files.txt" > "$STAGE/.list"
while IFS= read -r f; do
  [ -f "$HERE/$f" ] || { echo "build.sh: missing $f" >&2; exit 1; }
  mkdir -p "$STAGE/pkg/$(dirname "$f")"
  cp "$HERE/$f" "$STAGE/pkg/$f"
done < "$STAGE/.list"
cp "$ROOT/LICENSE" "$STAGE/pkg/LICENSE"
cp "$ROOT/NOTICE" "$STAGE/pkg/NOTICE"

# Normalize modes and timestamps so identical sources give identical zips.
find "$STAGE/pkg" -type f -exec chmod 0644 {} +
chmod 0755 "$STAGE/pkg/META-INF/com/google/android/update-binary" \
  "$STAGE/pkg/customize.sh" "$STAGE/pkg/service.sh" "$STAGE/pkg/uninstall.sh" \
  "$STAGE/pkg/system/bin/victordrax-chroot" "$STAGE/pkg/system/bin/victordrax-mounts"
find "$STAGE/pkg" -exec touch -h -d "$EPOCH_DATE" {} +

(cd "$STAGE/pkg" && find . -type f | sed 's|^\./||' | LC_ALL=C sort > "$STAGE/.all")

mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)
ZIP="$OUT/victordrax-root-module-$VERSION.zip"
rm -f "$ZIP"
(cd "$STAGE/pkg" && zip -X -D -9 -q "$ZIP" -@ < "$STAGE/.all")

echo "$ZIP"
