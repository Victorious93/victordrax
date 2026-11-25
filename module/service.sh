#!/system/bin/sh
# Runs late in boot (Magisk / KernelSU / APatch late_start service stage).
MODDIR="${0%/*}"
export VDX_MODDIR="$MODDIR"
VDX_TAG=service
export VDX_TAG
# shellcheck source=/dev/null
. "$MODDIR/common.sh"

until [ "$(getprop sys.boot_completed)" = "1" ]; do
  sleep 5
done

vdx_log "boot completed"
vdx_load_config

if [ "$AUTO_MOUNT" != "1" ]; then
  vdx_log "AUTO_MOUNT=0; skipping boot-time mounts"
  exit 0
fi

# Shared storage may appear slightly after boot_completed.
_waited=0
while [ "$MOUNT_SDCARD" = "1" ] && [ ! -d "$SDCARD_SOURCE/Android" ] && [ "$_waited" -lt 60 ]; do
  sleep 5
  _waited=$((_waited + 5))
done

vdx_mount_all
vdx_log "service finished (rc=$?)"
exit 0
