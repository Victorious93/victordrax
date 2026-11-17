#!/system/bin/sh
# Shared helpers for the VictorDrax root module. Sourced, never executed directly.
# POSIX sh / busybox ash only. No network access, no downloads, no SELinux changes.

VDX_MODDIR="${VDX_MODDIR:-${MODDIR:-/data/adb/modules/victordrax}}"
VDX_USER_DIR="${VDX_USER_DIR:-/data/adb/victordrax}"
VDX_USER_CONF="$VDX_USER_DIR/victordrax.conf"
VDX_LOG="${VDX_LOG:-$VDX_MODDIR/victordrax.log}"
VDX_STATE="${VDX_STATE:-$VDX_MODDIR/mounted_roots}"
VDX_PROC_MOUNTS="${VDX_PROC_MOUNTS:-/proc/mounts}"
VDX_LOG_MAX_BYTES=65536

CHROOT_PATH=/data/local/andrax
# shellcheck disable=SC2034  # read by callers that source this file
AUTO_MOUNT=1
MOUNT_SDCARD=1
SDCARD_SOURCE=/storage/emulated/0

vdx_log() {
  _lg_tag="${VDX_TAG:-module}"
  if [ -f "$VDX_LOG" ]; then
    _lg_size=$(wc -c < "$VDX_LOG" 2>/dev/null || echo 0)
    if [ "$_lg_size" -gt "$VDX_LOG_MAX_BYTES" ]; then
      mv -f "$VDX_LOG" "$VDX_LOG.old" 2>/dev/null
    fi
  fi
  echo "$(date '+%Y-%m-%d %H:%M:%S') [$_lg_tag] $*" >> "$VDX_LOG" 2>/dev/null
  return 0
}

# Accept only plain absolute paths: no "..", no spaces or shell metacharacters,
# and never a top-level system location.
vdx_safe_path() {
  case "$1" in
    ""|/) return 1 ;;
    /*) ;;
    *) return 1 ;;
  esac
  case "$1" in
    *[!A-Za-z0-9._/-]*) return 1 ;;
    */../*|*/..|*//*) return 1 ;;
  esac
  case "${1%/}" in
    ""|/data|/data/local|/system|/vendor|/product|/dev|/proc|/sys|/sdcard|/storage|/storage/emulated|/storage/emulated/0|/mnt|/cache|/apex|/etc|/bin|/sbin|/lib)
      return 1 ;;
  esac
  return 0
}

# Parse KEY=VALUE lines from $1 without executing them. Unknown keys and
# invalid values are ignored (and logged).
vdx_parse_conf() {
  [ -f "$1" ] || return 0
  while IFS= read -r _pc_line || [ -n "$_pc_line" ]; do
    case "$_pc_line" in
      ""|"#"*) continue ;;
    esac
    _pc_key="${_pc_line%%=*}"
    _pc_val="${_pc_line#*=}"
    case "$_pc_key" in
      CHROOT_PATH)
        if vdx_safe_path "$_pc_val"; then CHROOT_PATH="${_pc_val%/}"; else vdx_log "config: ignoring unsafe CHROOT_PATH"; fi ;;
      SDCARD_SOURCE)
        if vdx_safe_path "$_pc_val" || [ "$_pc_val" = "/storage/emulated/0" ]; then SDCARD_SOURCE="${_pc_val%/}"; else vdx_log "config: ignoring unsafe SDCARD_SOURCE"; fi ;;
      AUTO_MOUNT|MOUNT_SDCARD)
        case "$_pc_val" in
          0|1) eval "$_pc_key=\$_pc_val" ;;
          *) vdx_log "config: ignoring invalid $_pc_key" ;;
        esac ;;
      *) vdx_log "config: ignoring unknown key" ;;
    esac
  done < "$1"
  return 0
}

vdx_load_config() {
  vdx_parse_conf "$VDX_MODDIR/victordrax.conf"
  vdx_parse_conf "$VDX_USER_CONF"
  return 0
}

# A usable chroot has a shell and the mount points we need.
vdx_chroot_present() {
  [ -d "$CHROOT_PATH" ] && [ -e "$CHROOT_PATH/bin/sh" ]
}

# Exact-match check against /proc/mounts (paths contain no spaces by validation).
vdx_is_mounted() {
  while read -r _im_dev _im_mp _im_rest; do
    [ "$_im_mp" = "$1" ] && return 0
  done < "$VDX_PROC_MOUNTS"
  return 1
}

# vdx_mount_one <description> <mountpoint> <mount args...>
vdx_mount_one() {
  _mo_desc="$1"; _mo_mp="$2"; shift 2
  if vdx_is_mounted "$_mo_mp"; then
    vdx_log "$_mo_desc: already mounted at $_mo_mp"
    return 0
  fi
  mkdir -p "$_mo_mp" 2>/dev/null
  if mount "$@" "$_mo_mp" 2>>"$VDX_LOG"; then
    vdx_log "$_mo_desc: mounted at $_mo_mp"
  else
    vdx_log "$_mo_desc: mount FAILED at $_mo_mp"
    return 1
  fi
}

vdx_record_root() {
  [ -f "$VDX_STATE" ] && while IFS= read -r _rr_line; do
    [ "$_rr_line" = "$1" ] && return 0
  done < "$VDX_STATE"
  echo "$1" >> "$VDX_STATE" 2>/dev/null
  return 0
}

# Idempotent: safe to run any number of times.
vdx_mount_all() {
  vdx_load_config
  if ! vdx_chroot_present; then
    vdx_log "no ANDRAX-NG chroot at $CHROOT_PATH (missing or no /bin/sh); nothing to mount"
    return 2
  fi
  _rc=0
  vdx_record_root "$CHROOT_PATH"
  vdx_mount_one "proc"   "$CHROOT_PATH/proc"    -t proc proc          || _rc=1
  vdx_mount_one "sysfs"  "$CHROOT_PATH/sys"     -t sysfs sysfs        || _rc=1
  vdx_mount_one "dev"    "$CHROOT_PATH/dev"     -o bind /dev          || _rc=1
  vdx_mount_one "devpts" "$CHROOT_PATH/dev/pts" -t devpts devpts      || _rc=1
  if [ "$MOUNT_SDCARD" = "1" ]; then
    if [ -d "$SDCARD_SOURCE" ]; then
      vdx_mount_one "sdcard" "$CHROOT_PATH/sdcard" -o bind "$SDCARD_SOURCE" || _rc=1
    else
      vdx_log "sdcard source $SDCARD_SOURCE not found; skipping sdcard mount"
    fi
  fi
  return "$_rc"
}

# Unmount one mount point, including stacked duplicates (the VictorDrax app
# also mounts pseudo filesystems when it enters the chroot).
vdx_umount_one() {
  _uo_n=0
  while vdx_is_mounted "$1" && [ "$_uo_n" -lt 8 ]; do
    if ! umount "$1" 2>/dev/null; then
      umount -l "$1" 2>/dev/null || { vdx_log "could not unmount $1"; return 1; }
    fi
    _uo_n=$((_uo_n + 1))
  done
  [ "$_uo_n" -gt 0 ] && vdx_log "unmounted $1"
  return 0
}

vdx_umount_root() {
  vdx_umount_one "$1/sdcard"
  vdx_umount_one "$1/dev/pts"
  vdx_umount_one "$1/dev"
  vdx_umount_one "$1/sys"
  vdx_umount_one "$1/proc"
}

# Unmount for the configured root and every root this module ever mounted.
vdx_umount_all() {
  vdx_load_config
  _rc=0
  vdx_safe_path "$CHROOT_PATH" && vdx_umount_root "$CHROOT_PATH"
  if [ -f "$VDX_STATE" ]; then
    while IFS= read -r _ua_root; do
      if vdx_safe_path "$_ua_root" && [ "$_ua_root" != "$CHROOT_PATH" ]; then
        vdx_umount_root "$_ua_root"
      fi
    done < "$VDX_STATE"
  fi
  return "$_rc"
}

vdx_status() {
  vdx_load_config
  echo "chroot path : $CHROOT_PATH"
  if vdx_chroot_present; then echo "chroot      : present"; else echo "chroot      : not found"; fi
  for _m in proc sys dev dev/pts sdcard; do
    if vdx_is_mounted "$CHROOT_PATH/$_m"; then echo "mount $_m: yes"; else echo "mount $_m: no"; fi
  done
}
