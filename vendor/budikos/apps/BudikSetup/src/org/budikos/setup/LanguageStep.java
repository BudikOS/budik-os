// SPDX-License-Identifier: GPL-3.0-only
package org.budikos.setup;

import android.icu.util.ULocale;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.android.internal.app.LocaleHelper;
import com.android.internal.app.LocalePicker;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * First page: welcome + our own language list. Only languages the system is
 * really translated to (framework asset locales), each named in itself; Czech
 * and the neighbours on top. A tap switches the whole system at once.
 */
final class LanguageStep extends Step {
    private static final List<String> PINNED = Arrays.asList(
            "cs-CZ", "sk-SK", "en-US", "en-GB", "de-DE", "pl-PL");
    private static final String KEY_QUERY = "language_query";

    private String mQuery = "";
    private RowAdapter mAdapter;
    private TextView mEmpty;

    LanguageStep(SetupActivity activity) {
        super(activity);
    }

    @Override
    int titleRes() {
        return R.string.language_title;
    }

    @Override
    int bodyRes() {
        return R.string.language_body;
    }

    @Override
    CharSequence primaryLabel() {
        return mActivity.getText(R.string.start);
    }

    @Override
    boolean primaryAccent() {
        return true;
    }

    @Override
    View createView(LayoutInflater inflater, ViewGroup parent) {
        View v = inflater.inflate(R.layout.step_list, parent, false);
        ListView list = v.findViewById(R.id.list);
        mEmpty = v.findViewById(R.id.empty);
        mEmpty.setText(R.string.search_empty);
        mAdapter = new RowAdapter(mActivity);
        mAdapter.setRows(buildRows());
        list.setAdapter(mAdapter);
        list.setOnItemClickListener((p, row, pos, id) -> pick((Locale) mAdapter.getItem(pos).key));

        EditText search = v.findViewById(R.id.search);
        search.setVisibility(View.VISIBLE);
        search.setHint(R.string.language_search);
        search.setText(mQuery);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                mQuery = s.toString();
                filter();
            }
        });
        filter();
        int checked = mAdapter.checkedPosition();
        if (checked > 3) list.setSelection(checked);
        return v;
    }

    private void filter() {
        mAdapter.setQuery(mQuery);
        mEmpty.setVisibility(mAdapter.getCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private Locale current() {
        return mActivity.getResources().getConfiguration().getLocales().get(0);
    }

    private void pick(Locale locale) {
        if (locale.toLanguageTag().equals(current().toLanguageTag())) return;
        for (RowAdapter.Row r : mAdapter.rows()) r.checked = r.key == locale;
        mAdapter.notifyDataSetChanged();
        mActivity.setLocale(locale);
    }

    private List<RowAdapter.Row> buildRows() {
        Locale ui = current();
        List<Locale> locales = supportedLocales();

        // Several regions of one language (English US/UK): name the region too.
        Map<String, Integer> perLanguage = new HashMap<>();
        for (Locale l : locales) perLanguage.merge(languageKey(l), 1, Integer::sum);

        List<RowAdapter.Row> pinned = new ArrayList<>();
        List<RowAdapter.Row> rest = new ArrayList<>();
        RowAdapter.Row selected = null;
        for (Locale l : locales) {
            Locale named = perLanguage.get(languageKey(l)) > 1 ? l : languageOnly(l);
            String title = LocaleHelper.getDisplayName(named, l, true);
            String subtitle = LocaleHelper.getDisplayName(named, ui, true);
            if (subtitle.equals(title)) subtitle = "";
            RowAdapter.Row r = new RowAdapter.Row(l, title, subtitle);
            r.search = RowAdapter.normalize(title + " " + subtitle + " "
                    + named.getDisplayName(Locale.ENGLISH) + " " + l.toLanguageTag());
            if (l.toLanguageTag().equals(ui.toLanguageTag())) selected = r;
            (PINNED.contains(l.toLanguageTag()) ? pinned : rest).add(r);
        }
        pinned.sort((a, b) -> PINNED.indexOf(((Locale) a.key).toLanguageTag())
                - PINNED.indexOf(((Locale) b.key).toLanguageTag()));
        Collator collator = Collator.getInstance(ui);
        rest.sort((a, b) -> collator.compare(a.title.toString(), b.title.toString()));

        List<RowAdapter.Row> rows = new ArrayList<>(pinned);
        rows.addAll(rest);
        if (selected == null) {
            // e.g. system "en" without region: mark the first row of that language
            for (RowAdapter.Row r : rows) {
                if (((Locale) r.key).getLanguage().equals(ui.getLanguage())) {
                    selected = r;
                    break;
                }
            }
        }
        if (selected != null) selected.checked = true;
        return rows;
    }

    /** Locales the framework is translated to, each with a region (cs -> cs-CZ). */
    private static List<Locale> supportedLocales() {
        LinkedHashMap<String, Locale> out = new LinkedHashMap<>();
        for (String tag : LocalePicker.getSystemAssetLocales()) {
            if (tag == null || tag.isEmpty()) continue;
            Locale l = Locale.forLanguageTag(tag.startsWith("b+")
                    ? tag.substring(2).replace('+', '-') : tag);
            String region = l.getCountry();
            // skip pseudo-locales (en-XA, ar-XB, en-XC)
            if (l.getLanguage().isEmpty() || region.matches("X[ABC]")) continue;
            if (region.isEmpty()) {
                region = ULocale.addLikelySubtags(ULocale.forLocale(l)).getCountry();
                if (!region.isEmpty()) {
                    l = new Locale.Builder().setLocale(l).setRegion(region).build();
                }
            }
            out.putIfAbsent(l.toLanguageTag(), l);
        }
        return new ArrayList<>(out.values());
    }

    private static String languageKey(Locale l) {
        return l.getLanguage() + "-" + l.getScript();
    }

    private static Locale languageOnly(Locale l) {
        return new Locale.Builder().setLanguage(l.getLanguage()).setScript(l.getScript()).build();
    }

    @Override
    int summaryLabelRes() {
        return R.string.summary_language;
    }

    @Override
    CharSequence summary() {
        Locale l = current();
        return LocaleHelper.getDisplayName(l, l, true);
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
