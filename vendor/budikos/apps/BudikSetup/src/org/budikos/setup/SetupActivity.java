// SPDX-License-Identifier: GPL-3.0-only
/*
 * Budik OS first-boot wizard: language -> Wi-Fi -> date & time -> appearance ->
 * screen lock -> done. Everything happens inside this activity (never jumps to
 * Settings). Plain framework views, no libraries (cheap on RAM). On finish it
 * marks the device provisioned and disables itself, like AOSP Provision, so it
 * never runs again. "Skip setup" is on every page and any crash also finishes
 * setup, so a phone can never get stuck in the wizard.
 *
 * Language, orientation and font size changes are handled here (configChanges)
 * by rebuilding the views in place: no activity restart, no flash. Light/dark
 * goes through a normal recreate (the theme must change); the page is kept in
 * the saved state.
 */
package org.budikos.setup;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.os.Bundle;
import android.os.LocaleList;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import com.android.internal.app.LocalePicker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SetupActivity extends Activity {
    static final String TAG = "BudikSetup";
    private static final String KEY_STEP = "step";
    // Settings.Secure.USER_SETUP_COMPLETE is @hide; same string.
    private static final String USER_SETUP_COMPLETE = "user_setup_complete";
    // Changes that only need new views (see manifest configChanges).
    private static final int REBUILD_CHANGES = ActivityInfo.CONFIG_LOCALE
            | ActivityInfo.CONFIG_LAYOUT_DIRECTION | ActivityInfo.CONFIG_ORIENTATION
            | ActivityInfo.CONFIG_SCREEN_SIZE | ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE
            | ActivityInfo.CONFIG_SCREEN_LAYOUT | ActivityInfo.CONFIG_DENSITY
            | ActivityInfo.CONFIG_FONT_SCALE;

    private static boolean sCrashGuardInstalled;

    private final List<Step> mSteps = new ArrayList<>();
    private int mIndex;
    private Step mShown;
    private boolean mStarted;
    private Configuration mConfig;
    private Dialog mDialog;

    private LinearLayout mProgress;
    private TextView mTitle, mBody, mSkip;
    private FrameLayout mContent;
    private View mBottomBar;
    private Button mPrimary;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        installCrashGuard(getApplicationContext());

        // Already set up (e.g. system update over a used phone): just get out of the way.
        if (isSetupComplete()) {
            finishSetup();
            return;
        }

        mSteps.add(new LanguageStep(this));
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            mSteps.add(new WifiStep(this));
        }
        mSteps.add(new TimeStep(this));
        mSteps.add(new ThemeStep(this));
        if (LockStep.isSupported(this)) mSteps.add(new LockStep(this));
        mSteps.add(new DoneStep(this));

        if (state != null) {
            for (Step s : mSteps) s.restore(state);
            mIndex = Math.max(0, Math.min(mSteps.size() - 1, state.getInt(KEY_STEP, 0)));
        }
        mConfig = new Configuration(getResources().getConfiguration());

        getWindow().setDecorFitsSystemWindows(false);
        buildUi();

        // Back = previous page; on the first page it is swallowed (we are HOME).
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::back);
    }

    private void buildUi() {
        setContentView(R.layout.activity_setup);
        View root = findViewById(R.id.root);
        mProgress = findViewById(R.id.progress);
        mTitle = findViewById(R.id.title);
        mBody = findViewById(R.id.body);
        mContent = findViewById(R.id.content);
        mBottomBar = findViewById(R.id.bottom_bar);
        mPrimary = findViewById(R.id.primary);
        mSkip = findViewById(R.id.skip);

        float dp = getResources().getDisplayMetrics().density;
        for (int i = 0; i < mSteps.size(); i++) {
            View seg = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i < mSteps.size() - 1) lp.setMarginEnd(Math.round(4 * dp));
            seg.setBackgroundResource(R.drawable.segment);
            mProgress.addView(seg, lp);
        }

        // Edge-to-edge: pad for the bars and the keyboard ourselves.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsets.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            boolean imeUp = insets.isVisible(WindowInsets.Type.ime());
            boolean hide = imeUp && mShown != null && mShown.hideBarWithIme();
            mBottomBar.setVisibility(hide ? View.GONE : View.VISIBLE);
            return WindowInsets.CONSUMED;
        });

        // Dark icons on the light background, light icons on the dark one.
        WindowInsetsController bars = getWindow().getInsetsController();
        if (bars != null) {
            int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            boolean light = getResources().getBoolean(R.bool.budik_light_bars);
            bars.setSystemBarsAppearance(light ? mask : 0, mask);
        }

        mPrimary.setOnClickListener(v -> {
            if (mShown != null && mShown.primaryEnabled()) mShown.onPrimary();
        });
        mSkip.setOnClickListener(v -> confirmSkip());
        showStep();
    }

    private void showStep() {
        if (mShown != null && mStarted) mShown.onHidden();
        mShown = mSteps.get(mIndex);

        for (int i = 0; i < mProgress.getChildCount(); i++) {
            mProgress.getChildAt(i).setActivated(i <= mIndex);
        }
        mTitle.setText(mShown.titleRes());
        int body = mShown.bodyRes();
        mBody.setVisibility(body != 0 ? View.VISIBLE : View.GONE);
        if (body != 0) mBody.setText(body);
        mSkip.setVisibility(mShown instanceof DoneStep ? View.GONE : View.VISIBLE);

        mContent.removeAllViews();
        mContent.addView(mShown.createView(LayoutInflater.from(this), mContent));
        refreshPrimary();
        mBottomBar.setVisibility(View.VISIBLE);
        if (mStarted) mShown.onShown();
        mTitle.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }

    /** Steps call this when their button label or enabled state changes. */
    void refreshPrimary() {
        if (mShown == null || mPrimary == null) return;
        boolean accent = mShown.primaryAccent();
        mPrimary.setBackgroundResource(accent ? R.drawable.keycap_accent : R.drawable.keycap_primary);
        mPrimary.setTextColor(getColor(accent ? R.color.budik_on_accent : R.color.budik_on_primary));
        mPrimary.setText(mShown.primaryLabel());
        boolean enabled = mShown.primaryEnabled();
        mPrimary.setEnabled(enabled);
        mPrimary.setAlpha(enabled ? 1f : 0.4f);
    }

    void next() {
        if (mIndex < mSteps.size() - 1) {
            mIndex++;
            showStep();
        } else {
            finishSetup();
        }
    }

    private void back() {
        if (mIndex > 0) {
            mIndex--;
            showStep();
        }
    }

    void goTo(Step step) {
        int i = mSteps.indexOf(step);
        if (i >= 0) {
            mIndex = i;
            showStep();
        }
    }

    List<Step> steps() {
        return mSteps;
    }

    /** System language; the config change comes back through onConfigurationChanged. */
    void setLocale(Locale locale) {
        // The system is busy for a moment while it switches; keep the UI thread free.
        new Thread(() -> {
            try {
                LocalePicker.updateLocales(new LocaleList(locale));
            } catch (RuntimeException e) {
                Log.w(TAG, "cannot set locale " + locale, e);
            }
        }, "BudikSetup-locale").start();
    }

    void present(Dialog dialog) {
        if (mDialog != null && mDialog.isShowing()) mDialog.dismiss();
        mDialog = dialog;
        if (!isFinishing() && !isDestroyed()) dialog.show();
    }

    private void confirmSkip() {
        new BudikDialog(this, getText(R.string.skip_confirm_title), getText(R.string.skip_confirm_body))
                .negative(R.string.cancel, null)
                .positive(R.string.skip_confirm_action, t -> finishSetup())
                .show();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int diff = mConfig != null ? mConfig.diff(newConfig) : REBUILD_CHANGES;
        mConfig = new Configuration(newConfig);
        if (mSteps.isEmpty() || (diff & REBUILD_CHANGES) == 0) return;
        // New language / size: same page, fresh views.
        if (mShown != null && mStarted) mShown.onHidden();
        mShown = null;
        buildUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mStarted = true;
        if (mShown != null) mShown.onShown();
    }

    @Override
    protected void onStop() {
        if (mShown != null) mShown.onHidden();
        mStarted = false;
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(KEY_STEP, mIndex);
        for (Step s : mSteps) s.save(out);
    }

    @Override
    protected void onDestroy() {
        if (mDialog != null && mDialog.isShowing()) mDialog.dismiss();
        super.onDestroy();
    }

    private boolean isSetupComplete() {
        ContentResolver cr = getContentResolver();
        return Settings.Global.getInt(cr, Settings.Global.DEVICE_PROVISIONED, 0) == 1
                && Settings.Secure.getInt(cr, USER_SETUP_COMPLETE, 0) == 1;
    }

    void finishSetup() {
        markSetupDone(getApplicationContext());
        finish();
    }

    // Provisioned + disable our only component: the launcher becomes HOME and this
    // app never starts again (no RAM, no background work).
    private static void markSetupDone(Context ctx) {
        try {
            ContentResolver cr = ctx.getContentResolver();
            Settings.Global.putInt(cr, Settings.Global.DEVICE_PROVISIONED, 1);
            Settings.Secure.putInt(cr, USER_SETUP_COMPLETE, 1);
        } catch (RuntimeException e) {
            Log.e(TAG, "cannot write provisioning state", e);
        }
        Log.i(TAG, "setup done, disabling wizard");
        ctx.getPackageManager().setComponentEnabledSetting(
                new ComponentName(ctx, SetupActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    // A wizard that crashes would be relaunched as HOME forever. Never let that
    // happen: on any crash finish setup first, then let the process die normally.
    private static void installCrashGuard(Context ctx) {
        if (sCrashGuardInstalled) return;
        sCrashGuardInstalled = true;
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Log.e(TAG, "crash in wizard, finishing setup so the phone is usable", e);
            try {
                markSetupDone(ctx);
            } catch (Throwable ignored) {
            }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }
}
