#!/system/bin/sh
# Runs when the module is removed. Undoes everything this module created.
MODDIR="${0%/*}"
export VDX_MODDIR="$MODDIR"
VDX_TAG=uninstall
export VDX_TAG
# shellcheck source=/dev/null
. "$MODDIR/common.sh"

vdx_log "uninstalling"
vdx_umount_all

# Remove only the module's own user data. The chroot itself is never touched.
if [ -d "$VDX_USER_DIR" ]; then
  rm -rf "$VDX_USER_DIR"
fi
exit 0
