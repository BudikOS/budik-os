// SPDX-License-Identifier: GPL-3.0-only
package org.budikos.setup;

import android.app.Dialog;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** Budik dialog card (layout/dialog): title, text, optional password, two actions. */
final class BudikDialog {
    private final SetupActivity mActivity;
    private final Dialog mDialog;
    private final EditText mField;
    private final Button mPositive;
    private final TextView mNegative;
    private Predicate<String> mValid = s -> true;

    BudikDialog(SetupActivity activity, CharSequence title, CharSequence body) {
        mActivity = activity;
        mDialog = new Dialog(activity, R.style.Theme_BudikSetup_Dialog);
        mDialog.setContentView(R.layout.dialog);
        ((TextView) mDialog.findViewById(R.id.dialog_title)).setText(title);
        ((TextView) mDialog.findViewById(R.id.dialog_body)).setText(body);
        mField = mDialog.findViewById(R.id.dialog_field);
        mPositive = mDialog.findViewById(R.id.dialog_positive);
        mNegative = mDialog.findViewById(R.id.dialog_negative);
        mNegative.setVisibility(View.GONE);
        mPositive.setVisibility(View.GONE);
    }

    /** Password field + "show password"; positive gets the text. */
    BudikDialog password(Predicate<String> valid) {
        mValid = valid;
        mField.setVisibility(View.VISIBLE);
        mField.setHint(R.string.wifi_password_hint);
        CheckBox show = mDialog.findViewById(R.id.dialog_show);
        show.setVisibility(View.VISIBLE);
        show.setOnCheckedChangeListener((b, on) -> {
            int sel = mField.getSelectionEnd();
            mField.setInputType(InputType.TYPE_CLASS_TEXT | (on
                    ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            mField.setSelection(Math.max(0, sel));
        });
        mField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                updatePositive();
            }
        });
        mField.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE && mPositive.isEnabled()) {
                mPositive.performClick();
                return true;
            }
            return false;
        });
        return this;
    }

    BudikDialog positive(int label, Consumer<String> action) {
        mPositive.setText(label);
        mPositive.setVisibility(View.VISIBLE);
        mPositive.setOnClickListener(v -> {
            String text = mField.getText().toString();
            mDialog.dismiss();
            if (action != null) action.accept(text);
        });
        return this;
    }

    BudikDialog negative(int label, Runnable action) {
        mNegative.setText(label);
        mNegative.setVisibility(View.VISIBLE);
        mNegative.setOnClickListener(v -> {
            mDialog.dismiss();
            if (action != null) action.run();
        });
        return this;
    }

    private void updatePositive() {
        boolean ok = mValid.test(mField.getText().toString());
        mPositive.setEnabled(ok);
        mPositive.setAlpha(ok ? 1f : 0.4f);
    }

    void show() {
        Window w = mDialog.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (mField.getVisibility() == View.VISIBLE) {
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                        | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                mField.requestFocus();
            }
        }
        updatePositive();
        mActivity.present(mDialog);
    }
}
