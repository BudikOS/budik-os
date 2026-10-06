// SPDX-License-Identifier: GPL-3.0-only
package org.budikos.setup;

import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;

/**
 * Screen lock: PIN or password typed twice, or none. Saved through
 * LockPatternUtils (ACCESS_KEYGUARD_SECURE_STORAGE, signature|setup). Only
 * offered on a phone without a lock yet; changing an existing one stays in
 * Settings (it needs the old credential).
 */
final class LockStep extends Step {
    private static final int NONE = 0, PIN = 1, PASSWORD = 2;
    private static final int MIN_LENGTH = LockPatternUtils.MIN_LOCK_PASSWORD_SIZE;
    private static final String KEY_CHOICE = "lock_choice";

    private final LockPatternUtils mLockUtils;
    private int mChoice = -1;
    private String mFirst = "", mSecond = "";
    private boolean mSaving;
    private int mError;

    private EditText mFirstField, mSecondField;
    private TextView mNote;

    LockStep(SetupActivity activity) {
        super(activity);
        mLockUtils = new LockPatternUtils(activity);
    }

    static boolean isSupported(Context context) {
        try {
            return new LockPatternUtils(context).hasSecureLockScreen();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean alreadySecure() {
        try {
            return mLockUtils.isSecure(UserHandle.myUserId());
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    int titleRes() {
        return R.string.lock_title;
    }

    @Override
    int bodyRes() {
        return R.string.lock_body;
    }

    @Override
    boolean hideBarWithIme() {
        return false;
    }

    @Override
    View createView(LayoutInflater inflater, ViewGroup parent) {
        View v = inflater.inflate(R.layout.step_lock, parent, false);
        LinearLayout options = v.findViewById(R.id.lock_options);
        mNote = v.findViewById(R.id.lock_note);
        mFirstField = v.findViewById(R.id.lock_first);
        mSecondField = v.findViewById(R.id.lock_second);

        if (alreadySecure()) {
            options.setVisibility(View.GONE);
            mNote.setVisibility(View.VISIBLE);
            mNote.setText(R.string.lock_already);
            return v;
        }

        RowAdapter typefaces = new RowAdapter(mActivity);
        addOption(inflater, options, typefaces, PIN, R.string.lock_pin, R.string.lock_pin_sub);
        addOption(inflater, options, typefaces, PASSWORD, R.string.lock_password, R.string.lock_password_sub);
        addOption(inflater, options, typefaces, NONE, R.string.lock_none, R.string.lock_none_sub);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                mFirst = mFirstField.getText().toString();
                mSecond = mSecondField.getText().toString();
                mError = 0;
                updateNote();
                mActivity.refreshPrimary();
            }
        };
        mFirstField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        mSecondField.setOnEditorActionListener((x, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE && primaryEnabled()) {
                onPrimary();
                return true;
            }
            return false;
        });
        bindFields();
        mFirstField.addTextChangedListener(watcher);
        mSecondField.addTextChangedListener(watcher);
        updateNote();
        return v;
    }

    private void addOption(LayoutInflater inflater, LinearLayout options, RowAdapter typefaces,
            int choice, int title, int subtitle) {
        View row = inflater.inflate(R.layout.row_item, options, false);
        RowAdapter.Row r = new RowAdapter.Row(choice, mActivity.getText(title),
                mActivity.getText(subtitle));
        r.checked = mChoice == choice;
        RowAdapter.bind(row, r, typefaces.regular(), typefaces.bold());
        row.setTag(r);
        row.setOnClickListener(x -> {
            mChoice = choice;
            mFirst = mSecond = "";
            mError = 0;
            for (int i = 0; i < options.getChildCount(); i++) {
                View o = options.getChildAt(i);
                RowAdapter.Row or = (RowAdapter.Row) o.getTag();
                or.checked = or.key.equals(choice);
                RowAdapter.bind(o, or, typefaces.regular(), typefaces.bold());
            }
            bindFields();
            updateNote();
            mActivity.refreshPrimary();
            if (choice != NONE) mFirstField.requestFocus();
        });
        options.addView(row);
    }

    private void bindFields() {
        View fields = (View) mFirstField.getParent();
        boolean typed = mChoice == PIN || mChoice == PASSWORD;
        fields.setVisibility(typed ? View.VISIBLE : View.GONE);
        if (!typed) return;
        int type = mChoice == PIN
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        mFirstField.setInputType(type);
        mSecondField.setInputType(type);
        mFirstField.setHint(mChoice == PIN ? R.string.lock_enter_pin : R.string.lock_enter_password);
        mSecondField.setHint(mChoice == PIN ? R.string.lock_repeat_pin : R.string.lock_repeat_password);
        if (!mFirstField.getText().toString().equals(mFirst)) mFirstField.setText(mFirst);
        if (!mSecondField.getText().toString().equals(mSecond)) mSecondField.setText(mSecond);
    }

    private boolean valid() {
        if (mFirst.length() < MIN_LENGTH || !mFirst.equals(mSecond)) return false;
        return mChoice != PIN || mFirst.matches("[0-9]+");
    }

    private void updateNote() {
        if (mNote == null) return;
        int text = 0;
        if (mError != 0) {
            text = mError;
        } else if (mChoice == PIN || mChoice == PASSWORD) {
            if (!mSecond.isEmpty() && mSecond.length() >= mFirst.length() && !mFirst.equals(mSecond)) {
                text = R.string.lock_mismatch;
            } else if (valid()) {
                text = R.string.lock_remember;
            }
        }
        mNote.setVisibility(text != 0 ? View.VISIBLE : View.GONE);
        if (text != 0) mNote.setText(text);
    }

    @Override
    CharSequence primaryLabel() {
        if (alreadySecure() || mChoice == NONE) return mActivity.getText(R.string.next);
        if (mChoice == -1) return mActivity.getText(R.string.skip_step);
        return mActivity.getText(mSaving ? R.string.lock_saving : R.string.lock_set);
    }

    @Override
    boolean primaryEnabled() {
        if (mSaving) return false;
        if (alreadySecure()) return true;
        return (mChoice != PIN && mChoice != PASSWORD) || valid();
    }

    @Override
    void onPrimary() {
        if (alreadySecure() || (mChoice != PIN && mChoice != PASSWORD)) {
            mActivity.next();
            return;
        }
        final String code = mFirst;
        final boolean pin = mChoice == PIN;
        mSaving = true;
        mActivity.refreshPrimary();
        // Key derivation takes a moment: off the UI thread.
        new Thread(() -> {
            boolean ok;
            try (LockscreenCredential none = LockscreenCredential.createNone();
                 LockscreenCredential credential = pin
                         ? LockscreenCredential.createPin(code)
                         : LockscreenCredential.createPassword(code)) {
                ok = mLockUtils.setLockCredential(credential, none, UserHandle.myUserId());
            } catch (RuntimeException e) {
                Log.w(SetupActivity.TAG, "cannot set screen lock", e);
                ok = false;
            }
            final boolean saved = ok;
            mActivity.runOnUiThread(() -> {
                mSaving = false;
                if (saved) {
                    mFirst = mSecond = "";
                    mActivity.next();
                } else {
                    mError = R.string.lock_failed;
                    updateNote();
                    mActivity.refreshPrimary();
                }
            });
        }, "BudikSetup-lock").start();
    }

    @Override
    CharSequence summary() {
        int type;
        try {
            type = mLockUtils.getCredentialTypeForUser(UserHandle.myUserId());
        } catch (RuntimeException e) {
            type = LockPatternUtils.CREDENTIAL_TYPE_NONE;
        }
        int text;
        switch (type) {
            case LockPatternUtils.CREDENTIAL_TYPE_PIN: text = R.string.lock_pin; break;
            case LockPatternUtils.CREDENTIAL_TYPE_PASSWORD: text = R.string.lock_password; break;
            case LockPatternUtils.CREDENTIAL_TYPE_PATTERN: text = R.string.lock_pattern; break;
            default: text = R.string.lock_none; break;
        }
        return mActivity.getText(text);
    }

    @Override
    void save(Bundle out) {
        out.putInt(KEY_CHOICE, mChoice);
    }

    @Override
    void restore(Bundle in) {
        mChoice = in.getInt(KEY_CHOICE, -1);
    }
}
