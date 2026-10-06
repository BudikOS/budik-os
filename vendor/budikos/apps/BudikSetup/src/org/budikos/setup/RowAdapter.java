// SPDX-License-Identifier: GPL-3.0-only
package org.budikos.setup;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Rows of a list card (layout/row_item) with an accent- and case-blind filter. */
final class RowAdapter extends BaseAdapter {

    static final class Row {
        final Object key;
        final CharSequence title;
        final CharSequence subtitle;
        boolean checked;
        boolean lock;
        boolean chevron;
        /** Wi-Fi signal 0-4, -1 = none. */
        int level = -1;
        String search;

        Row(Object key, CharSequence title, CharSequence subtitle) {
            this.key = key;
            this.title = title;
            this.subtitle = subtitle;
        }

        String search() {
            if (search == null) search = normalize(title + " " + subtitle);
            return search;
        }
    }

    private final LayoutInflater mInflater;
    private final Typeface mRegular;
    private final Typeface mBold;
    private List<Row> mAll = new ArrayList<>();
    private List<Row> mShown = mAll;
    private String mQuery = "";

    RowAdapter(Context context) {
        mInflater = LayoutInflater.from(context);
        Typeface base = Typeface.create("instrument-sans", Typeface.NORMAL);
        mRegular = Typeface.create(base, 500, false);
        mBold = Typeface.create(base, 700, false);
    }

    void setRows(List<Row> rows) {
        mAll = rows;
        applyFilter();
    }

    List<Row> rows() {
        return mAll;
    }

    void setQuery(String query) {
        mQuery = normalize(query).trim();
        applyFilter();
    }

    /** Position of the first checked row in the filtered list, or -1. */
    int checkedPosition() {
        for (int i = 0; i < mShown.size(); i++) {
            if (mShown.get(i).checked) return i;
        }
        return -1;
    }

    private void applyFilter() {
        if (mQuery.isEmpty()) {
            mShown = mAll;
        } else {
            mShown = new ArrayList<>();
            for (Row r : mAll) {
                if (r.search().contains(mQuery)) mShown.add(r);
            }
        }
        notifyDataSetChanged();
    }

    static String normalize(CharSequence s) {
        if (s == null) return "";
        String d = Normalizer.normalize(s, Normalizer.Form.NFD);
        return d.replaceAll("\\p{Mn}+", "").toLowerCase(Locale.ROOT);
    }

    @Override
    public int getCount() {
        return mShown.size();
    }

    @Override
    public Row getItem(int position) {
        return mShown.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView != null ? convertView
                : mInflater.inflate(R.layout.row_item, parent, false);
        bind(v, getItem(position), mRegular, mBold);
        return v;
    }

    /** Also used for rows outside a ListView (lock options, summary). */
    static void bind(View v, Row r, Typeface regular, Typeface bold) {
        TextView title = v.findViewById(R.id.row_title);
        TextView subtitle = v.findViewById(R.id.row_subtitle);
        ImageView lock = v.findViewById(R.id.row_lock);
        ImageView end = v.findViewById(R.id.row_end);

        title.setText(r.title);
        title.setTypeface(r.checked ? bold : regular);
        subtitle.setText(r.subtitle);
        subtitle.setVisibility(TextUtils.isEmpty(r.subtitle) ? View.GONE : View.VISIBLE);
        lock.setVisibility(r.lock ? View.VISIBLE : View.GONE);

        if (r.level >= 0) {
            end.setImageResource(R.drawable.wifi_level);
            end.setImageLevel(r.level);
            end.setVisibility(View.VISIBLE);
        } else if (r.checked) {
            end.setImageResource(R.drawable.ic_check);
            end.setVisibility(View.VISIBLE);
        } else if (r.chevron) {
            end.setImageResource(R.drawable.ic_chevron_right);
            end.setVisibility(View.VISIBLE);
        } else {
            end.setVisibility(View.GONE);
        }
        v.setSelected(r.checked);
    }

    Typeface regular() {
        return mRegular;
    }

    Typeface bold() {
        return mBold;
    }
}
