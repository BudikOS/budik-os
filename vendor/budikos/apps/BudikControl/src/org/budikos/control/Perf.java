// SPDX-License-Identifier: GPL-3.0-only
package org.budikos.control;

import android.os.SystemProperties;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * API of vendor/budikos/perf/budik_perf.rc. The .rc does the actual work
 * (init writes /proc/sys/kernel/budik/*, stune, mmd props); we only set props.
 */
final class Perf {
    static final String PROP_PROFILE = "persist.sys.budik.perf_profile";
    static final String PROP_RAM_PLUS = "persist.sys.budik.ram_plus_gb";
    /** RAM Plus value in effect since boot (budik_perf.rc copies it once per boot) */
    static final String PROP_RAM_PLUS_BOOT = "sys.budik.ram_plus";

    static final String BATTERY = "battery";
    static final String BALANCED = "balanced";
    static final String PERFORMANCE = "performance";
    static final String[] PROFILES = {BATTERY, BALANCED, PERFORMANCE};

    static final int[] RAM_PLUS_GB = {0, 2, 4};

    private Perf() {}

    static String profile() {
        String p = SystemProperties.get(PROP_PROFILE, BALANCED);
        for (String known : PROFILES) {
            if (known.equals(p)) return p;
        }
        return BALANCED;
    }

    static void setProfile(String p) {
        SystemProperties.set(PROP_PROFILE, p);
    }

    static String next(String p) {
        for (int i = 0; i < PROFILES.length; i++) {
            if (PROFILES[i].equals(p)) return PROFILES[(i + 1) % PROFILES.length];
        }
        return BALANCED;
    }

    static int profileLabel(String p) {
        switch (p) {
            case BATTERY: return R.string.profile_battery;
            case PERFORMANCE: return R.string.profile_performance;
            default: return R.string.profile_balanced;
        }
    }

    static int profileSummary(String p) {
        switch (p) {
            case BATTERY: return R.string.profile_battery_summary;
            case PERFORMANCE: return R.string.profile_performance_summary;
            default: return R.string.profile_balanced_summary;
        }
    }

    static int ramPlus() {
        return SystemProperties.getInt(PROP_RAM_PLUS, 0);
    }

    static int ramPlusBoot() {
        return SystemProperties.getInt(PROP_RAM_PLUS_BOOT, 0);
    }

    static void setRamPlus(int gb) {
        SystemProperties.set(PROP_RAM_PLUS, Integer.toString(gb));
    }

    /** First line of a small sysfs/proc file, or null. */
    static String readLine(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path), 64)) {
            String l = r.readLine();
            return l == null ? null : l.trim();
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    /** Current frequency of the policy containing cpu, in kHz, or -1. */
    static int cpuKhz(int cpu) {
        String s = readLine("/sys/devices/system/cpu/cpu" + cpu + "/cpufreq/scaling_cur_freq");
        try {
            return s == null ? -1 : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
