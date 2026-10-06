// SPDX-License-Identifier: GPL-3.0-only
package org.budikos.setup;

import android.app.AlarmManager;
import android.icu.text.TimeZoneNames;
import android.icu.util.TimeZone.SystemTimeZoneType;
import android.icu.util.ULocale;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextClock;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Date & time: the time comes from the network (auto time stays on), the user
 * only checks the time zone. Default Europe/Prague (persist.sys.timezone in
 * sysui.mk); picking another one turns automatic time zone off.
 */
final class TimeStep extends Step {
    private static final String DEFAULT_ZONE = "Europe/Prague";
    private static final String KEY_QUERY = "zone_query";

    private String mQuery = "";
    private String mPicked;
    private List<RowAdapter.Row> mRows;
    private Locale mRowsLocale;
    private int mGeneration;

    private RowAdapter mAdapter;
    private ListView mList;
    private TextView mEmpty, mZoneNow;

    TimeStep(SetupActivity activity) {
        super(activity);
    }

    @Override
    int titleRes() {
        return R.string.time_title;
    }

    @Override
    int bodyRes() {
        return R.string.time_body;
    }

    @Override
    View createView(LayoutInflater inflater, ViewGroup parent) {
        Locale locale = locale();
        View v = inflater.inflate(R.layout.step_list, parent, false);
        ViewGroup header = v.findViewById(R.id.header);
        header.setVisibility(View.VISIBLE);
        inflater.inflate(R.layout.header_time, header, true);
        TextClock date = header.findViewById(R.id.date);
        String pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMMyyyy");
        date.setFormat12Hour(pattern);
        date.setFormat24Hour(pattern);
        mZoneNow = header.findViewById(R.id.zone_now);
        mZoneNow.setText(describe(currentZone(), locale));

        TextView label = v.findViewById(R.id.label);
        label.setVisibility(View.VISIBLE);
        label.setText(R.string.time_zone_label);
        mEmpty = v.findViewById(R.id.empty);
        mEmpty.setText(R.string.search_empty);
        mList = v.findViewById(R.id.list);
        mAdapter = new RowAdapter(mActivity);
        mList.setAdapter(mAdapter);
        mList.setOnItemClickListener((p, row, pos, id) -> pick((String) mAdapter.getItem(pos).key));

        EditText search = v.findViewById(R.id.search);
        search.setVisibility(View.VISIBLE);
        search.setHint(R.string.time_zone_search);
        search.setText(mQuery);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                mQuery = s.toString();
                filter();
            }
        });

        if (mRows != null && locale.equals(mRowsLocale)) {
            show(mRows);
        } else {
            load(locale);
        }
        return v;
    }

    private Locale locale() {
        return mActivity.getResources().getConfiguration().getLocales().get(0);
    }

    private String currentZone() {
        return mPicked != null ? mPicked : TimeZone.getDefault().getID();
    }

    /** ~400 zones with localized city names: built off the UI thread. */
    private void load(Locale locale) {
        final int gen = ++mGeneration;
        mEmpty.setVisibility(View.GONE);
        new Thread(() -> {
            List<RowAdapter.Row> rows = buildRows(locale);
            mActivity.runOnUiThread(() -> {
                if (gen != mGeneration || mActivity.isDestroyed()) return;
                mRows = rows;
                mRowsLocale = locale;
                show(rows);
            });
        }, "BudikSetup-zones").start();
    }

    private void show(List<RowAdapter.Row> rows) {
        String current = currentZone();
        for (RowAdapter.Row r : rows) r.checked = r.key.equals(current);
        mAdapter.setRows(rows);
        filter();
    }

    private void filter() {
        if (mAdapter == null || mRows == null) return;
        mAdapter.setQuery(mQuery);
        mEmpty.setVisibility(mAdapter.getCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private List<RowAdapter.Row> buildRows(Locale locale) {
        TimeZoneNames names = TimeZoneNames.getInstance(ULocale.forLocale(locale));
        long now = System.currentTimeMillis();
        String current = currentZone();
        List<RowAdapter.Row> pinned = new ArrayList<>();
        List<RowAdapter.Row> rest = new ArrayList<>();
        for (String id : android.icu.util.TimeZone.getAvailableIDs(
                SystemTimeZoneType.CANONICAL_LOCATION, null, null)) {
            String city = city(names, id);
            String region = "";
            try {
                region = android.icu.util.TimeZone.getRegion(id);
            } catch (IllegalArgumentException ignored) {
            }
            Locale country = new Locale("", region);
            String countryName = region.isEmpty() || "001".equals(region)
                    ? "" : country.getDisplayCountry(locale);
            String offset = gmt(TimeZone.getTimeZone(id).getOffset(now));
            RowAdapter.Row r = new RowAdapter.Row(id, city,
                    countryName.isEmpty() ? offset : countryName + " · " + offset);
            r.search = RowAdapter.normalize(city + " " + countryName + " "
                    + country.getDisplayCountry(Locale.ENGLISH) + " " + id + " " + offset);
            if (id.equals(current) || id.equals(DEFAULT_ZONE)) {
                pinned.add(id.equals(current) ? 0 : pinned.size(), r);
            } else {
                rest.add(r);
            }
        }
        // by country, then city
        Collator collator = Collator.getInstance(locale);
        rest.sort((a, b) -> {
            int c = collator.compare(country(a), country(b));
            return c != 0 ? c : collator.compare(a.title.toString(), b.title.toString());
        });
        pinned.addAll(rest);
        return pinned;
    }

    private static String country(RowAdapter.Row r) {
        String s = r.subtitle.toString();
        int dot = s.lastIndexOf(" · ");
        return dot > 0 ? s.substring(0, dot) : "";
    }

    private static String city(TimeZoneNames names, String id) {
        String city = null;
        try {
            city = names.getExemplarLocationName(id);
        } catch (RuntimeException ignored) {
        }
        if (city == null || city.isEmpty()) {
            city = id.substring(id.lastIndexOf('/') + 1).replace('_', ' ');
        }
        return city;
    }

    private static String gmt(int offsetMs) {
        int minutes = offsetMs / 60_000;
        if (minutes == 0) return "GMT";
        String sign = minutes > 0 ? "+" : "−";
        minutes = Math.abs(minutes);
        return minutes % 60 == 0
                ? String.format(Locale.ROOT, "GMT%s%d", sign, minutes / 60)
                : String.format(Locale.ROOT, "GMT%s%d:%02d", sign, minutes / 60, minutes % 60);
    }

    private static String describe(String id, Locale locale) {
        TimeZoneNames names = TimeZoneNames.getInstance(ULocale.forLocale(locale));
        return city(names, id) + " · " + gmt(TimeZone.getTimeZone(id).getOffset(System.currentTimeMillis()));
    }

    private void pick(String id) {
        if (id.equals(currentZone())) return;
        try {
            // a manual zone must not be overwritten by network detection
            Settings.Global.putInt(mActivity.getContentResolver(), Settings.Global.AUTO_TIME_ZONE, 0);
            mActivity.getSystemService(AlarmManager.class).setTimeZone(id);
        } catch (RuntimeException e) {
            Log.w(SetupActivity.TAG, "cannot set time zone " + id, e);
            return;
        }
        mPicked = id;
        for (RowAdapter.Row r : mAdapter.rows()) r.checked = r.key.equals(id);
        mAdapter.notifyDataSetChanged();
        mZoneNow.setText(describe(id, locale()));
    }

    @Override
    int summaryLabelRes() {
        return R.string.summary_time;
    }

    @Override
    CharSequence summary() {
        return describe(currentZone(), locale());
    }

    @Override
    void save(Bundle out) {
        out.putString(KEY_QUERY, mQuery);
    }

    @Override
    void restore(Bundle in) {
        mQuery = in.getString(KEY_QUERY, "");
    }
}
