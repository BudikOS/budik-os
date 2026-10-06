// SPDX-License-Identifier: GPL-3.0-only
package org.budikos.setup;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/** Last page: what was set, each row leads back to its page. */
final class DoneStep extends Step {

    DoneStep(SetupActivity activity) {
        super(activity);
    }

    @Override
    int titleRes() {
        return R.string.done_title;
    }

    @Override
    int bodyRes() {
        return R.string.done_body;
    }

    @Override
    CharSequence primaryLabel() {
        return mActivity.getText(R.string.finish);
    }

    @Override
    boolean primaryAccent() {
        return true;
    }

    @Override
    void onPrimary() {
        mActivity.finishSetup();
    }

    @Override
    View createView(LayoutInflater inflater, ViewGroup parent) {
        View v = inflater.inflate(R.layout.step_done, parent, false);
        LinearLayout summary = v.findViewById(R.id.summary);
        RowAdapter typefaces = new RowAdapter(mActivity);
        for (Step s : mActivity.steps()) {
            CharSequence value = s == this ? null : s.summary();
            if (value == null) continue;
            View row = inflater.inflate(R.layout.row_item, summary, false);
            RowAdapter.Row r = new RowAdapter.Row(s, mActivity.getText(s.summaryLabelRes()), value);
            r.chevron = true;
            RowAdapter.bind(row, r, typefaces.regular(), typefaces.bold());
            row.setOnClickListener(x -> mActivity.goTo(s));
            summary.addView(row);
        }
        return v;
    }
}
