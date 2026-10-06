#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Design export icons/{statusbar,qs,nav} -> BudikSystemIconsOverlay (SystemUI), and the
# Wi-Fi icons into BudikFrameworkOverlay. Names checked against the Android 17 source.
# Mobile signal = SettingsLib ic_mobile_* vectors (gen-signal-icons.py); the battery is
# drawn in code (UnifiedBattery), see the frameworks/base patches.
# Usage: tools/gen-system-icons.sh <design export dir>
set -euo pipefail
E="${1:?usage: $0 <design export dir>}/icons"
here="$(dirname "$(readlink -f "$0")")"
V="$(dirname "$here")/vendor/budikos"
O=$V/overlay/BudikSystemIconsOverlay
rm -rf "$O"; mkdir -p "$O/res/drawable"
m(){ cp "$E/$1.xml" "$O/res/drawable/$2.xml"; }
# status bar (SystemUI / SettingsLib resources live in the SystemUI package)
m statusbar/stat_sys_airplane stat_sys_airplane_mode
m statusbar/stat_sys_alarm stat_sys_alarm
m statusbar/stat_sys_bluetooth stat_sys_data_bluetooth_connected
m statusbar/stat_sys_dnd stat_sys_dnd
m statusbar/stat_sys_hotspot stat_sys_hotspot
m statusbar/stat_sys_vpn stat_sys_vpn_ic
m statusbar/stat_sys_data_lte ic_lte_mobiledata
m statusbar/stat_sys_data_4g ic_4g_mobiledata
# quick settings: on = filled, off = outline
for t in airplane:airplane flashlight:flashlight dnd:dnd location:location battery_saver:battery_saver hotspot:hotspot rotation:auto_rotate screen_record:screen_record bluetooth:bluetooth; do
  s=${t%%:*}; d=${t##*:}
  m "qs/ic_qs_${s}_on" "qs_${d}_icon_on"; m "qs/ic_qs_${s}_off" "qs_${d}_icon_off"
done
m qs/ic_qs_nfc_on ic_qs_nfc
# mobile signal levels (SettingsLib ic_mobile_level_list)
python3 "$here/gen-signal-icons.py" "$E/statusbar" "$O/res/drawable"
# 3-button navigation
m nav/ic_nav_back ic_sysbar_back; m nav/ic_nav_home ic_sysbar_home; m nav/ic_nav_recents ic_sysbar_recent
cat > "$O/AndroidManifest.xml" <<M
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="org.budikos.overlay.systemicons">
    <overlay android:targetPackage="com.android.systemui" android:isStatic="true" android:priority="902" />
    <application android:hasCode="false" />
</manifest>
M
# Wi-Fi signal icons are framework resources (used by SystemUI WifiIcons and Settings)
F=$V/overlay/BudikFrameworkOverlay/res/drawable; mkdir -p "$F"
for i in 0 1 2 3 4; do cp "$E/statusbar/stat_sys_wifi_$i.xml" "$F/ic_wifi_signal_$i.xml"; done
echo "OK $(find "$O/res/drawable" -type f | wc -l) SystemUI icons, 5 framework Wi-Fi icons"
