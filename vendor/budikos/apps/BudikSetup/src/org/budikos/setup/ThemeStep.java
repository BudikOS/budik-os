// SPDX-License-Identifier: GPL-3.0-only
package org.budikos.setup;

import android.app.UiModeManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.time.LocalTime;
import java.util.Calendar;

/**
 * Light / dark / automatic. "Automatic" is a fixed night schedule
 * (MODE_NIGHT_CUSTOM): MODE_NIGHT_AUTO needs a location fix for sunset, which a
 * fresh phone without Google location usually does not have.
 */
final class ThemeStep extends Step {
    private static final LocalTime NIGHT_START = LocalTime.of(21, 0);
    private static final LocalTime NIGHT_END = LocalTime.of(7, 0);

    private final UiModeManager mUiMode;
    private TextView mLight, mDark, mAuto, mNote;

    ThemeStep(SetupActivity activity) {
        super(activity);
        mUiMode = activity.getSystemService(UiModeManager.class);
    }

    @Override
    int titleRes() {
        return R.string.theme_title;
    }

    @Override
    int bodyRes() {
        return R.string.theme_body;
    }

    @Override
    View createView(LayoutInflater inflater, ViewGroup parent) {
        View v = inflater.inflate(R.layout.step_theme, parent, false);
        mLight = v.findViewById(R.id.theme_light);
        mDark = v.findViewById(R.id.theme_dark);
        mAuto = v.findViewById(R.id.theme_auto);
        mNote = v.findViewById(R.id.theme_note);
        mLight.setOnClickListener(x -> choose(UiModeManager.MODE_NIGHT_NO));
        mDark.setOnClickListener(x -> choose(UiModeManager.MODE_NIGHT_YES));
        mAuto.setOnClickListener(x -> choose(UiModeManager.MODE_NIGHT_CUSTOM));
        bind(mode());
        return v;
    }

    private int mode() {
        int m = mUiMode != null ? mUiMode.getNightMode() : UiModeManager.MODE_NIGHT_NO;
        return m == UiModeManager.MODE_NIGHT_AUTO ? UiModeManager.MODE_NIGHT_CUSTOM : m;
    }

    private void bind(int mode) {
        mLight.setSelected(mode == UiModeManager.MODE_NIGHT_NO);
        mDark.setSelected(mode == UiModeManager.MODE_NIGHT_YES);
        mAuto.setSelected(mode == UiModeManager.MODE_NIGHT_CUSTOM);
        if (mode == UiModeManager.MODE_NIGHT_YES) {
            mNote.setText(R.string.theme_note_dark);
        } else if (mode == UiModeManager.MODE_NIGHT_CUSTOM) {
            mNote.setText(mActivity.getString(R.string.theme_note_auto,
                    time(NIGHT_START), time(NIGHT_END)));
        } else {
            mNote.setText(R.string.theme_note_light);
        }
    }

    private String time(LocalTime t) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, t.getHour());
        c.set(Calendar.MINUTE, t.getMinute());
        return DateFormat.getTimeFormat(mActivity).format(c.getTime());
    }

    // Light/dark changes uiMode: the activity is recreated in the new colors
    // (that is the preview) and comes back on this page from the saved state.
    private void choose(int mode) {
        bind(mode);
        if (mUiMode == null) return;
        try {
            if (mode == UiModeManager.MODE_NIGHT_CUSTOM) {
                mUiMode.setCustomNightModeStart(NIGHT_START);
                mUiMode.setCustomNightModeEnd(NIGHT_END);
            }
            mUiMode.setNightMode(mode);
        } catch (RuntimeException e) {
            Log.w(SetupActivity.TAG, "setNightMode failed", e);
        }
    }

    @Override
    CharSequence summary() {
        int m = mode();
        return mActivity.getText(m == UiModeManager.MODE_NIGHT_YES ? R.string.theme_dark
                : m == UiModeManager.MODE_NIGHT_CUSTOM ? R.string.theme_auto : R.string.theme_light);
    }
}
