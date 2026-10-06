// SPDX-License-Identifier: GPL-3.0-only
package org.budikos.setup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * One wizard page. The step object lives for the whole wizard; its view is
 * created again after a language or orientation change (createView), so all
 * state worth keeping sits in fields, not in views.
 */
abstract class Step {
    final SetupActivity mActivity;

    Step(SetupActivity activity) {
        mActivity = activity;
    }

    abstract int titleRes();

    int bodyRes() {
        return 0;
    }

    abstract View createView(LayoutInflater inflater, ViewGroup parent);

    /** Step is visible and the activity started: register listeners. */
    void onShown() {
    }

    /** Step left or activity stopped: unregister everything from onShown. */
    void onHidden() {
    }

    CharSequence primaryLabel() {
        return mActivity.getText(R.string.next);
    }

    boolean primaryAccent() {
        return false;
    }

    boolean primaryEnabled() {
        return true;
    }

    void onPrimary() {
        mActivity.next();
    }

    /** Hide the bottom buttons while the keyboard is up (search fields). */
    boolean hideBarWithIme() {
        return true;
    }

    void save(Bundle out) {
    }

    void restore(Bundle in) {
    }

    /** Current value for the summary on the last page, null = not listed. */
    CharSequence summary() {
        return null;
    }

    int summaryLabelRes() {
        return titleRes();
    }
}
