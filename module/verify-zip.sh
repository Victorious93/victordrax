#!/bin/sh
# Verify a built module zip contains every expected file (unzip -l based).
# Usage: module/verify-zip.sh <zip>
set -eu
ZIP="${1:?usage: verify-zip.sh <zip>}"
HERE=$(cd "$(dirname "$0")" && pwd)

[ -f "$ZIP" ] || { echo "FAIL: $ZIP not found" >&2; exit 1; }

LISTING=$(unzip -l "$ZIP")
fail=0
for f in $(cat "$HERE/files.txt") LICENSE NOTICE; do
  if ! printf '%s\n' "$LISTING" | awk -v f="$f" '$NF == f { found = 1 } END { exit !found }'; then
    echo "FAIL: zip is missing $f" >&2
    fail=1
  fi
done

expected=$(( $(wc -l < "$HERE/files.txt") + 2 ))
actual=$(unzip -Z1 "$ZIP" | wc -l)
if [ "$actual" -ne "$expected" ]; then
  echo "FAIL: zip has $actual entries, expected $expected" >&2
  fail=1
fi

unzip -tq "$ZIP" >/dev/null || { echo "FAIL: zip integrity test failed" >&2; fail=1; }

# Shell scripts must be shipped executable.
for f in customize.sh service.sh uninstall.sh system/bin/victordrax-chroot system/bin/victordrax-mounts META-INF/com/google/android/update-binary; do
  if ! unzip -Z "$ZIP" "$f" | grep -q '^-rwx'; then
    echo "FAIL: $f is not executable in the zip" >&2
    fail=1
  fi
done

[ "$fail" -eq 0 ] || exit 1
echo "OK: $ZIP contains all $expected expected files"
