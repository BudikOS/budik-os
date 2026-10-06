// SPDX-License-Identifier: GPL-3.0-only
package org.budikos.control;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Insets;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

/**
 * Budik OS performance page. Plain framework views, no libraries; the live status
 * refreshes every 2 s only while the page is visible.
 */
public class PerfActivity extends Activity {
    private static final long REFRESH_MS = 2000;
    private static final int METER_BLOCKS = 16;
    /** first CPU of each cluster on ocean/SDM632; other devices: missing → hidden */
    private static final int[] CLUSTER_CPUS = {0, 4};

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mRefresh = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            mHandler.postDelayed(this, REFRESH_MS);
        }
    };

    private LinearLayout mProfiles;
    private LinearLayout mRamPlus;
    private View mReboot;
    private TextView mCpuValue, mTempValue, mMemValue, mSwapValue;
    private LinearLayout mMeter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perf);
        getWindow().setDecorFitsSystemWindows(false);

        View root = findViewById(R.id.root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });
        findViewById(R.id.back).setOnClickListener(v -> finish());

        mProfiles = findViewById(R.id.profiles);
        mRamPlus = findViewById(R.id.ram_plus);
        mReboot = findViewById(R.id.reboot);
        mReboot.setOnClickListener(v ->
                getSystemService(PowerManager.class).reboot(null));

        LayoutInflater inf = getLayoutInflater();
        for (String p : Perf.PROFILES) {
            View row = addRadioRow(inf, mProfiles, getString(Perf.profileLabel(p)),
                    getString(Perf.profileSummary(p)));
            row.setTag(p);
            row.setOnClickListener(v -> {
                Perf.setProfile((String) v.getTag());
                bindProfiles();
            });
        }
        for (int gb : Perf.RAM_PLUS_GB) {
            View row = addRadioRow(inf, mRamPlus,
                    gb == 0 ? getString(R.string.ram_plus_off) : getString(R.string.ram_plus_gb, gb),
                    getString(gb == 0 ? R.string.ram_plus_off_summary
                            : gb == 2 ? R.string.ram_plus_2_summary : R.string.ram_plus_4_summary));
            row.setTag(gb);
            row.setOnClickListener(v -> {
                Perf.setRamPlus((Integer) v.getTag());
                bindRamPlus();
            });
        }

        mCpuValue = initValueRow(R.id.st_cpu, R.string.status_cpu);
        mTempValue = initValueRow(R.id.st_temp, R.string.status_temp);
        mMemValue = findViewById(R.id.mem_value);
        mSwapValue = findViewById(R.id.swap_value);
        mMeter = findViewById(R.id.mem_meter);
        int gap = Math.round(4 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < METER_BLOCKS; i++) {
            View b = new View(this);
            b.setBackgroundResource(R.drawable.segment);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1);
            if (i > 0) lp.setMarginStart(gap);
            mMeter.addView(b, lp);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindProfiles();
        bindRamPlus();
        mHandler.post(mRefresh);
    }

    @Override
    protected void onPause() {
        mHandler.removeCallbacks(mRefresh);
        super.onPause();
    }

    private View addRadioRow(LayoutInflater inf, LinearLayout card, String title, String sub) {
        View row = inf.inflate(R.layout.row_radio, card, false);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        ((TextView) row.findViewById(R.id.subtitle)).setText(sub);
        card.addView(row);
        return row;
    }

    private TextView initValueRow(int id, int title) {
        View row = findViewById(id);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        return row.findViewById(R.id.value);
    }

    private static void check(LinearLayout card, Object selected) {
        for (int i = 0; i < card.getChildCount(); i++) {
            View row = card.getChildAt(i);
            boolean on = selected.equals(row.getTag());
            row.findViewById(R.id.radio).setActivated(on);
            row.setSelected(on);
        }
    }

    private void bindProfiles() {
        check(mProfiles, Perf.profile());
    }

    private void bindRamPlus() {
        int gb = Perf.ramPlus();
        check(mRamPlus, gb);
        mReboot.setVisibility(gb != Perf.ramPlusBoot() ? View.VISIBLE : View.GONE);
    }

    private void refreshStatus() {
        StringBuilder cpu = new StringBuilder();
        for (int c : CLUSTER_CPUS) {
            int khz = Perf.cpuKhz(c);
            if (khz == -1 && c != 0) continue;
            if (cpu.length() > 0) cpu.append(" · ");
            cpu.append(khz > 0 ? String.format(Locale.getDefault(), "%.2f", khz / 1e6)
                    : getString(R.string.status_cpu_off));
        }
        mCpuValue.setText(getString(R.string.status_ghz, cpu));

        Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int temp = bat == null ? Integer.MIN_VALUE
                : bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        mTempValue.setText(temp == Integer.MIN_VALUE ? "–" : getString(R.string.status_celsius,
                String.format(Locale.getDefault(), "%.1f", temp / 10f)));

        long[] m = readMeminfo();
        if (m == null) return;
        long total = m[0], used = m[0] - m[1];
        mMemValue.setText(getString(R.string.status_mem, gb(used), gb(total)));
        int on = total > 0 ? Math.round(METER_BLOCKS * used / (float) total) : 0;
        for (int i = 0; i < METER_BLOCKS; i++) mMeter.getChildAt(i).setActivated(i < on);
        if (m[2] > 0) {
            mSwapValue.setVisibility(View.VISIBLE);
            mSwapValue.setText(getString(R.string.status_swap, gb(m[2] - m[3]), gb(m[2])));
        } else {
            mSwapValue.setVisibility(View.GONE);
        }
    }

    private static String gb(long kb) {
        return String.format(Locale.getDefault(), "%.1f", kb / 1048576f);
    }

    /** {MemTotal, MemAvailable, SwapTotal, SwapFree} in kB, or null. */
    private static long[] readMeminfo() {
        long[] v = new long[4];
        String[] keys = {"MemTotal:", "MemAvailable:", "SwapTotal:", "SwapFree:"};
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"), 2048)) {
            String l;
            int found = 0;
            while ((l = r.readLine()) != null && found < keys.length) {
                for (int i = 0; i < keys.length; i++) {
                    if (l.startsWith(keys[i])) {
                        v[i] = Long.parseLong(l.replaceAll("[^0-9]", ""));
                        found++;
                    }
                }
            }
            return v;
        } catch (IOException | NumberFormatException | SecurityException e) {
            return null;
        }
    }
}
