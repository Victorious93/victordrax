#!/bin/bash
# Host-side tests for the root module scripts. No device needed.
#   - stubbed tests (mount/umount/getprop faked) always run
#   - real mount tests run only with VDX_REAL_MOUNT=1 and passwordless sudo
# shellcheck disable=SC2016  # single quotes are intentional (literal metacharacters)
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
MOD=$(cd "$HERE/.." && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
PASS=0
FAIL=0

ok()   { PASS=$((PASS + 1)); echo "ok   - $1"; }
bad()  { FAIL=$((FAIL + 1)); echo "FAIL - $1"; }
check() { if "${@:2}"; then ok "$1"; else bad "$1"; fi; }
count_lines() { grep -c "$2" "$1" 2>/dev/null || true; }

# ---------- customize.sh ----------
run_customize() { # env assignments as args; prints output, returns exit code
  (
    env -i PATH="$PATH" MODPATH="$WORK/modpath" VDX_USER_DIR="$WORK/userdir" "$@" bash -c '
      ui_print() { echo "$1"; }
      abort() { echo "$1"; exit 1; }
      set_perm_recursive() { :; }
      set_perm() { :; }
      . "$0"
    ' "$MOD/customize.sh"
  )
}
rm -rf "$WORK/modpath" "$WORK/userdir"; mkdir -p "$WORK/modpath"
cp "$MOD/victordrax.conf" "$WORK/modpath/"

out=$(run_customize MAGISK_VER=27.0 ARCH=arm64); rc=$?
check "customize: Magisk arm64 succeeds" test $rc -eq 0
check "customize: prints Magisk" grep -q "Root manager: Magisk 27.0" <<<"$out"
check "customize: creates user config" test -f "$WORK/userdir/victordrax.conf"

echo "CHROOT_PATH=/data/local/custom" > "$WORK/userdir/victordrax.conf"
out=$(run_customize KSU=true KSU_VER=1.0.5 ARCH=arm64); rc=$?
check "customize: KernelSU succeeds" test $rc -eq 0
check "customize: prints KernelSU" grep -q "Root manager: KernelSU 1.0.5" <<<"$out"
check "customize: keeps existing user config" grep -q "/data/local/custom" "$WORK/userdir/victordrax.conf"

out=$(run_customize KSU=true APATCH=true APATCH_VER=10700 ARCH=arm64); rc=$?
check "customize: APatch (which also sets KSU) reported as APatch" grep -q "Root manager: APatch 10700" <<<"$out"

out=$(run_customize ARCH=arm64); rc=$?
check "customize: no root manager aborts" test $rc -ne 0
check "customize: unsupported env message" grep -q "Unsupported environment" <<<"$out"

out=$(run_customize MAGISK_VER=27.0 ARCH=x86); rc=$?
check "customize: x86 aborts" test $rc -ne 0
check "customize: unsupported arch message" grep -q "Unsupported architecture" <<<"$out"
out=$(run_customize MAGISK_VER=27.0 ARCH=x64); rc=$?
check "customize: x64 accepted" test $rc -eq 0

# ---------- common.sh: path validation and config parsing ----------
export VDX_MODDIR="$WORK/mod" VDX_USER_DIR="$WORK/user" VDX_LOG="$WORK/mod/victordrax.log"
export VDX_STATE="$WORK/mod/mounted_roots" VDX_PROC_MOUNTS="$WORK/proc_mounts"
mkdir -p "$WORK/mod" "$WORK/user"
cp "$MOD/common.sh" "$MOD/victordrax.conf" "$WORK/mod/"
: > "$VDX_PROC_MOUNTS"
# shellcheck source=/dev/null
. "$MOD/common.sh"

for p in /data/local/andrax /data/local/tmp/x1 /mnt/andrax-ng; do
  check "safe_path accepts $p" vdx_safe_path "$p"
done
for p in "" / relative /data /system /data/local/../x '/a b' '/a;b' '/a$(id)' /data/local//x '/a`id`'; do
  check "safe_path rejects '$p'" bash -c ". '$MOD/common.sh'; ! vdx_safe_path '$p'"
done

PWNED="$WORK/pwned"
cat > "$WORK/user/victordrax.conf" <<CONF
# comment
CHROOT_PATH=/data/local/mychroot/
AUTO_MOUNT=0
MOUNT_SDCARD=maybe
EVIL=\$(touch $PWNED)
SDCARD_SOURCE=/x;touch $PWNED
CONF
CHROOT_PATH=/data/local/andrax; AUTO_MOUNT=1; MOUNT_SDCARD=1
vdx_load_config
check "config: valid CHROOT_PATH applied, trailing slash stripped" test "$CHROOT_PATH" = "/data/local/mychroot"
check "config: AUTO_MOUNT=0 applied" test "$AUTO_MOUNT" = "0"
check "config: invalid MOUNT_SDCARD ignored" test "$MOUNT_SDCARD" = "1"
check "config: config content is never executed" test ! -e "$PWNED"
check "config: unsafe SDCARD_SOURCE ignored" test "$SDCARD_SOURCE" = "/storage/emulated/0"

# ---------- stubbed mount tests ----------
mkdir -p "$WORK/bin"
cat > "$WORK/bin/mount" <<'STUB'
#!/bin/sh
for a; do last="$a"; done
type=none; prev=
for a; do [ "$prev" = "-t" ] && type="$a"; prev="$a"; done
echo "stub $last $type" >> "$VDX_PROC_MOUNTS"
STUB
cat > "$WORK/bin/umount" <<'STUB'
#!/bin/sh
for a; do last="$a"; done
awk -v mp="$last" '{ l[NR] = $0 } $2 == mp { idx = NR } END { for (i = 1; i <= NR; i++) if (i != idx) print l[i] }' "$VDX_PROC_MOUNTS" > "$VDX_PROC_MOUNTS.tmp"
mv "$VDX_PROC_MOUNTS.tmp" "$VDX_PROC_MOUNTS"
STUB
cat > "$WORK/bin/getprop" <<'STUB'
#!/bin/sh
echo 1
STUB
chmod +x "$WORK/bin/"*
export PATH="$WORK/bin:$PATH"

CH="$WORK/chroot"
mkdir -p "$CH/bin" "$WORK/sdcard/Android"
: > "$CH/bin/sh"
printf 'CHROOT_PATH=%s\nSDCARD_SOURCE=%s\n' "$CH" "$WORK/sdcard" > "$WORK/user/victordrax.conf"
: > "$VDX_PROC_MOUNTS"; rm -f "$VDX_LOG"

vdx_mount_all; rc=$?
check "mount_all: succeeds with chroot present" test $rc -eq 0
for m in proc sys dev dev/pts sdcard; do
  check "mount_all: $m mounted once" test "$(count_lines "$VDX_PROC_MOUNTS" " $CH/$m ")" = "1"
done
vdx_mount_all; vdx_mount_all
check "mount_all: idempotent (still 5 mounts)" test "$(wc -l < "$VDX_PROC_MOUNTS")" = "5"
check "mount_all: logs already mounted" grep -q "already mounted" "$VDX_LOG"
check "mount_all: records root in state file" grep -qx "$CH" "$VDX_STATE"

echo "stub $CH/proc proc" >> "$VDX_PROC_MOUNTS"   # simulate stacked mount by the app
vdx_umount_all
check "umount_all: removes every mount incl. stacked" test "$(wc -l < "$VDX_PROC_MOUNTS")" = "0"
check "umount_all: chroot files untouched" test -e "$CH/bin/sh"

: > "$VDX_PROC_MOUNTS"; rm -rf "$CH"; rm -f "$VDX_LOG"
vdx_mount_all; rc=$?
check "mount_all: no chroot -> returns 2" test $rc -eq 2
check "mount_all: no chroot -> no mounts" test "$(wc -l < "$VDX_PROC_MOUNTS")" = "0"
check "mount_all: no chroot -> logged" grep -q "no ANDRAX-NG chroot" "$VDX_LOG"

# service.sh end-to-end (copy so MODDIR is isolated)
SVC="$WORK/svcmod"; mkdir -p "$SVC"; cp "$MOD/common.sh" "$MOD/service.sh" "$MOD/victordrax.conf" "$SVC/"
mkdir -p "$CH/bin"; : > "$CH/bin/sh"; : > "$VDX_PROC_MOUNTS"
( unset VDX_LOG VDX_STATE; sh "$SVC/service.sh" ); rc=$?
check "service.sh: exits 0" test $rc -eq 0
check "service.sh: mounted chroot" test "$(wc -l < "$VDX_PROC_MOUNTS")" = "5"
check "service.sh: wrote log under module dir" grep -q "boot completed" "$SVC/victordrax.log"

printf 'CHROOT_PATH=%s\nAUTO_MOUNT=0\n' "$CH" > "$WORK/user/victordrax.conf"; : > "$VDX_PROC_MOUNTS"
( unset VDX_LOG VDX_STATE; sh "$SVC/service.sh" )
check "service.sh: AUTO_MOUNT=0 mounts nothing" test "$(wc -l < "$VDX_PROC_MOUNTS")" = "0"

# uninstall.sh
UNI="$WORK/unimod"; mkdir -p "$UNI"; cp "$MOD/common.sh" "$MOD/uninstall.sh" "$MOD/victordrax.conf" "$UNI/"
printf 'CHROOT_PATH=%s\nSDCARD_SOURCE=%s\n' "$CH" "$WORK/sdcard" > "$WORK/user/victordrax.conf"
vdx_mount_all
( unset VDX_LOG VDX_STATE; sh "$UNI/uninstall.sh" ); rc=$?
check "uninstall.sh: exits 0" test $rc -eq 0
check "uninstall.sh: unmounted everything" test "$(wc -l < "$VDX_PROC_MOUNTS")" = "0"
check "uninstall.sh: removed user config dir" test ! -e "$WORK/user"
check "uninstall.sh: chroot untouched" test -e "$CH/bin/sh"
mkdir -p "$WORK/user"

# ---------- helper wrappers (non-root paths) ----------
if [ "$(id -u)" != "0" ]; then
  out=$(sh "$MOD/system/bin/victordrax-chroot" 2>&1); rc=$?
  check "victordrax-chroot: refuses without root" test $rc -eq 1
  check "victordrax-chroot: says root required" grep -q "root required" <<<"$out"
  out=$(sh "$MOD/system/bin/victordrax-mounts" mount 2>&1); rc=$?
  check "victordrax-mounts mount: refuses without root" test $rc -eq 1
fi
out=$(VDX_MODDIR="$WORK/mod" sh "$MOD/system/bin/victordrax-mounts" status 2>&1); rc=$?
check "victordrax-mounts status: works unprivileged" test $rc -eq 0
check "victordrax-mounts status: shows chroot path" grep -q "chroot path" <<<"$out"
out=$(VDX_MODDIR="$WORK/mod" sh "$MOD/system/bin/victordrax-mounts" bogus 2>&1); rc=$?
check "victordrax-mounts: bad usage exits 2" test $rc -eq 2
out=$(VDX_MODDIR="$WORK/nonexistent" sh "$MOD/system/bin/victordrax-chroot" 2>&1); rc=$?
check "victordrax-chroot: missing module files exits 1" test $rc -eq 1

# ---------- real mount tests (CI only) ----------
if [ "${VDX_REAL_MOUNT:-0}" = "1" ] && sudo -n true 2>/dev/null; then
  RWORK=$(mktemp -d)
  RCH="$RWORK/chroot"; RSD="$RWORK/sdcard"; RMOD="$RWORK/mod"; RUSER="$RWORK/user"
  mkdir -p "$RCH/bin" "$RSD/Android" "$RMOD" "$RUSER"
  : > "$RCH/bin/sh"
  cp "$MOD/common.sh" "$MOD/victordrax.conf" "$RMOD/"
  printf 'CHROOT_PATH=%s\nSDCARD_SOURCE=%s\n' "$RCH" "$RSD" > "$RUSER/victordrax.conf"
  rsudo() { sudo -n env PATH=/usr/sbin:/usr/bin:/sbin:/bin VDX_MODDIR="$RMOD" VDX_USER_DIR="$RUSER" "$@"; }
  rcount() { awk -v r="$RCH" 'index($2, r) == 1 { n++ } END { print n + 0 }' /proc/mounts; }

  rsudo sh "$MOD/system/bin/victordrax-mounts" mount; rc=$?
  check "real: mount succeeds" test $rc -eq 0
  n1=$(rcount)
  check "real: 5 mount points present" test "$n1" = "5"
  rsudo sh "$MOD/system/bin/victordrax-mounts" mount
  check "real: second mount run is idempotent" test "$(rcount)" = "$n1"
  check "real: /proc visible in chroot" test -e "$RCH/proc/self"
  rsudo sh "$MOD/system/bin/victordrax-mounts" umount
  check "real: umount removes all mounts" test "$(rcount)" = "0"
  check "real: chroot dir still intact" test -e "$RCH/bin/sh"
  sudo -n rm -rf "$RWORK"
else
  echo "skip - real mount tests (set VDX_REAL_MOUNT=1 with passwordless sudo)"
fi

echo "passed=$PASS failed=$FAIL"
[ "$FAIL" -eq 0 ]
