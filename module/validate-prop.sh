#!/bin/sh
# Validate module.prop has the keys Magisk / KernelSU / APatch require.
# Usage: module/validate-prop.sh [path/to/module.prop]
set -eu
PROP="${1:-$(dirname "$0")/module.prop}"
fail=0

[ -f "$PROP" ] || { echo "FAIL: $PROP not found" >&2; exit 1; }

get() { sed -n "s/^$1=//p" "$PROP" | head -n 1; }

for key in id name version versionCode author description; do
  val=$(get "$key")
  if [ -z "$val" ]; then
    echo "FAIL: module.prop missing or empty key: $key" >&2
    fail=1
  fi
done

id=$(get id)
case "$id" in
  [A-Za-z]*) ;;
  *) echo "FAIL: id must start with a letter" >&2; fail=1 ;;
esac
case "$id" in
  *[!A-Za-z0-9._-]*) echo "FAIL: id has invalid characters" >&2; fail=1 ;;
esac
[ "$id" = "victordrax" ] || { echo "FAIL: id must be victordrax" >&2; fail=1; }

vc=$(get versionCode)
case "$vc" in
  ""|*[!0-9]*) echo "FAIL: versionCode must be an integer" >&2; fail=1 ;;
esac

if grep -q "$(printf '\r')" "$PROP"; then
  echo "FAIL: module.prop must use LF line endings" >&2
  fail=1
fi

if [ "$fail" -ne 0 ]; then exit 1; fi
echo "OK: module.prop valid (id=$id version=$(get version) versionCode=$vc)"
