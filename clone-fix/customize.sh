#!/system/bin/sh
SKIPUNZIP=0
ui_print "Installing separate, disabled candidate. Existing bridge is preserved."
ui_print "No clone deletion or creation is performed during installation."
touch "$MODPATH/disable"
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/bin/noa-clonectl" 0 0 0755
set_perm "$MODPATH/service.sh" 0 0 0755
