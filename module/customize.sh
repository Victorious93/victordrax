# shellcheck shell=sh
# Sourced by the Magisk / KernelSU / APatch installer after the zip is extracted.
# Provided by the manager: MODPATH, ARCH, ui_print, abort, set_perm_recursive,
# and one of: MAGISK_VER, KSU, APATCH.

ui_print "- VictorDrax Root Module (optional helper)"
ui_print "- Authorized testing only"

if [ "$APATCH" = "true" ]; then
  ROOT_MANAGER="APatch ${APATCH_VER:-unknown}"
elif [ "$KSU" = "true" ]; then
  ROOT_MANAGER="KernelSU ${KSU_VER:-unknown}"
elif [ -n "$MAGISK_VER" ]; then
  ROOT_MANAGER="Magisk $MAGISK_VER"
else
  abort "! Unsupported environment: Magisk, KernelSU or APatch not detected"
fi
ui_print "- Root manager: $ROOT_MANAGER"

case "$ARCH" in
  arm64|x64)
    ui_print "- Architecture: $ARCH"
    ;;
  *)
    abort "! Unsupported architecture: ${ARCH:-unknown} (supported: arm64, x64)"
    ;;
esac

set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm_recursive "$MODPATH/system/bin" 0 2000 0755 0755 u:object_r:system_file:s0
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
set_perm "$MODPATH/common.sh" 0 0 0644

# Keep a user-editable config outside the module dir so it survives updates.
USER_DIR="${VDX_USER_DIR:-/data/adb/victordrax}"
mkdir -p "$USER_DIR"
if [ ! -f "$USER_DIR/victordrax.conf" ]; then
  cp "$MODPATH/victordrax.conf" "$USER_DIR/victordrax.conf"
  ui_print "- Created $USER_DIR/victordrax.conf (edit CHROOT_PATH if needed)"
else
  ui_print "- Keeping existing $USER_DIR/victordrax.conf"
fi

ui_print "- Helpers: victordrax-chroot, victordrax-mounts (run via su after reboot)"
ui_print "- Reboot to activate"
