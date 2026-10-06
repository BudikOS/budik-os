// SPDX-License-Identifier: GPL-3.0-only
package org.budikos.setup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.NetworkSelectionStatus;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Our own Wi-Fi picker: scan, signal, lock, password dialog, live state.
 * Platform signature gives NETWORK_SETTINGS / NETWORK_SETUP_WIZARD, so scan
 * results come without location permission and WifiManager.connect() works.
 */
final class WifiStep extends Step {
    private static final long SCAN_INTERVAL_MS = 10_000;
    private static final long CONNECT_TIMEOUT_MS = 30_000;
    // a fresh connect() resets the old "wrong password" mark, give it a moment
    private static final long STALE_ERROR_MS = 4_000;
    private static final int SEC_UNSUPPORTED = -1;
    private static final String KEY_AUTO_ENABLED = "wifi_auto_enabled";

    private final WifiManager mWifi;
    private final ConnectivityManager mConnectivity;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Integer> mErrors = new HashMap<>();

    private boolean mRegistered;
    private boolean mAutoEnabled;
    private boolean mBinding;
    private String mConnecting;
    private long mConnectStart;
    private String mConnected;

    private RowAdapter mAdapter;
    private ListView mList;
    private TextView mState, mEmpty, mLabel;
    private Switch mSwitch;

    private static final class Net {
        String ssid;
        int level;
        int security;
        WifiConfiguration saved;
    }

    private final Runnable mRefresh = this::refresh;

    private final Runnable mScan = new Runnable() {
        @Override
        public void run() {
            try {
                if (mWifi.isWifiEnabled()) mWifi.startScan();
            } catch (RuntimeException e) {
                Log.w(SetupActivity.TAG, "scan failed", e);
            }
            refresh();
            mHandler.postDelayed(this, SCAN_INTERVAL_MS);
        }
    };

    private final Runnable mTimeout = () -> {
        if (mConnecting != null && !mConnecting.equals(mConnected)) {
            mErrors.put(mConnecting, R.string.wifi_state_failed);
            mConnecting = null;
            refresh();
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(intent.getAction())
                    && intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, 0)
                            == WifiManager.ERROR_AUTHENTICATING
                    && mConnecting != null) {
                mErrors.put(mConnecting, R.string.wifi_state_wrong_password);
                mConnecting = null;
            }
            mHandler.removeCallbacks(mRefresh);
            mHandler.postDelayed(mRefresh, 200);
        }
    };

    WifiStep(SetupActivity activity) {
        super(activity);
        mWifi = activity.getSystemService(WifiManager.class);
        mConnectivity = activity.getSystemService(ConnectivityManager.class);
    }

    @Override
    int titleRes() {
        return R.string.wifi_title;
    }

    @Override
    int bodyRes() {
        return R.string.wifi_body;
    }

    @Override
    CharSequence primaryLabel() {
        return mActivity.getText(mConnected != null ? R.string.next : R.string.skip_step);
    }

    @Override
    View createView(LayoutInflater inflater, ViewGroup parent) {
        View v = inflater.inflate(R.layout.step_list, parent, false);
        ViewGroup header = v.findViewById(R.id.header);
        header.setVisibility(View.VISIBLE);
        inflater.inflate(R.layout.header_wifi, header, true);
        mState = header.findViewById(R.id.wifi_state);
        mSwitch = header.findViewById(R.id.wifi_switch);
        mSwitch.setOnCheckedChangeListener((b, on) -> {
            if (!mBinding) setWifiEnabled(on);
        });
        header.findViewById(R.id.wifi_toggle_row).setOnClickListener(x -> mSwitch.toggle());

        mLabel = v.findViewById(R.id.label);
        mLabel.setText(R.string.wifi_networks);
        mEmpty = v.findViewById(R.id.empty);
        mList = v.findViewById(R.id.list);
        mAdapter = new RowAdapter(mActivity);
        mList.setAdapter(mAdapter);
        mList.setOnItemClickListener((p, row, pos, id) -> onNetworkClicked((Net) mAdapter.getItem(pos).key));
        refresh();
        return v;
    }

    @Override
    void onShown() {
        if (!mRegistered) {
            IntentFilter f = new IntentFilter();
            f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            f.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            f.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
            f.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            mActivity.registerReceiver(mReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            mRegistered = true;
        }
        // Coming to this page means the user wants Wi-Fi: switch it on once.
        if (!mAutoEnabled && !mWifi.isWifiEnabled()) {
            mAutoEnabled = true;
            setWifiEnabled(true);
        }
        mHandler.removeCallbacks(mScan);
        mHandler.post(mScan);
        if (mConnecting != null) {
            mHandler.removeCallbacks(mTimeout);
            mHandler.postDelayed(mTimeout, CONNECT_TIMEOUT_MS);
        }
    }

    @Override
    void onHidden() {
        if (mRegistered) {
            mActivity.unregisterReceiver(mReceiver);
            mRegistered = false;
        }
        mHandler.removeCallbacks(mScan);
        mHandler.removeCallbacks(mRefresh);
        mHandler.removeCallbacks(mTimeout);
    }

    private void setWifiEnabled(boolean on) {
        try {
            mWifi.setWifiEnabled(on);
        } catch (RuntimeException e) {
            Log.w(SetupActivity.TAG, "setWifiEnabled failed", e);
        }
        mHandler.removeCallbacks(mRefresh);
        mHandler.postDelayed(mRefresh, 300);
    }

    /** SSID we are associated to, or null. */
    private String associatedSsid() {
        WifiInfo info = mWifi.getConnectionInfo();
        if (info == null || info.getNetworkId() == -1
                || info.getSupplicantState() != SupplicantState.COMPLETED) {
            return null;
        }
        String ssid = unquote(info.getSSID());
        return ssid == null || WifiManager.UNKNOWN_SSID.equals(ssid) ? null : ssid;
    }

    @SuppressWarnings("deprecation")
    private boolean hasWifiNetwork() {
        for (Network n : mConnectivity.getAllNetworks()) {
            NetworkCapabilities nc = mConnectivity.getNetworkCapabilities(n);
            if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return true;
        }
        return false;
    }

    private void refresh() {
        boolean on;
        String associated;
        boolean online;
        List<Net> nets;
        try {
            int state = mWifi.getWifiState();
            on = state == WifiManager.WIFI_STATE_ENABLED || state == WifiManager.WIFI_STATE_ENABLING;
            associated = on ? associatedSsid() : null;
            online = associated != null && hasWifiNetwork();
            nets = on ? collect() : new ArrayList<>();
        } catch (RuntimeException e) {
            Log.w(SetupActivity.TAG, "wifi state unavailable", e);
            return;
        }

        String before = mConnected;
        mConnected = online ? associated : null;
        if (mConnected != null) {
            mErrors.remove(mConnected);
            if (mConnected.equals(mConnecting)) {
                mConnecting = null;
                mHandler.removeCallbacks(mTimeout);
            }
        }
        // wrong password reported through the saved network
        if (mConnecting != null
                && SystemClock.elapsedRealtime() - mConnectStart > STALE_ERROR_MS) {
            for (Net n : nets) {
                if (n.ssid.equals(mConnecting) && wrongPassword(n.saved)) {
                    mErrors.put(mConnecting, R.string.wifi_state_wrong_password);
                    mConnecting = null;
                    mHandler.removeCallbacks(mTimeout);
                    break;
                }
            }
        }
        if (mList == null) return;

        mBinding = true;
        mSwitch.setChecked(on);
        mBinding = false;
        if (!on) {
            mState.setText(R.string.wifi_off);
        } else if (mConnected != null) {
            mState.setText(mActivity.getString(R.string.wifi_connected_to, mConnected));
        } else if (mConnecting != null) {
            mState.setText(mActivity.getString(R.string.wifi_connecting_to, mConnecting));
        } else {
            mState.setText(R.string.wifi_not_connected);
        }

        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Net n : nets) {
            RowAdapter.Row r = new RowAdapter.Row(n, n.ssid, mActivity.getText(status(n, associated)));
            r.lock = n.security != WifiInfo.SECURITY_TYPE_OPEN
                    && n.security != WifiInfo.SECURITY_TYPE_OWE;
            r.level = n.level;
            r.checked = n.ssid.equals(mConnected);
            rows.add(r);
        }
        mAdapter.setRows(rows);
        boolean empty = rows.isEmpty();
        mList.setVisibility(empty ? View.GONE : View.VISIBLE);
        mLabel.setVisibility(empty ? View.GONE : View.VISIBLE);
        mEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        mEmpty.setText(on ? R.string.wifi_none_found : R.string.wifi_off_body);
        if (before == null ? mConnected != null : !before.equals(mConnected)) {
            mActivity.refreshPrimary();
        }
    }

    private int status(Net n, String associated) {
        if (n.ssid.equals(mConnected)) return R.string.wifi_state_connected;
        if (n.ssid.equals(associated) || n.ssid.equals(mConnecting)) {
            return R.string.wifi_state_connecting;
        }
        Integer error = mErrors.get(n.ssid);
        if (error != null) return error;
        if (wrongPassword(n.saved)) return R.string.wifi_state_wrong_password;
        if (n.saved != null) return R.string.wifi_state_saved;
        if (!supported(n)) return R.string.wifi_unsupported;
        if (n.security == WifiInfo.SECURITY_TYPE_OPEN
                || n.security == WifiInfo.SECURITY_TYPE_OWE) {
            return R.string.wifi_open;
        }
        return R.string.wifi_secured;
    }

    /** Scan results, one entry per SSID (strongest AP), connected/saved/strong first. */
    @SuppressWarnings("deprecation")
    private List<Net> collect() {
        Map<String, WifiConfiguration> saved = new HashMap<>();
        List<WifiConfiguration> configs = mWifi.getConfiguredNetworks();
        if (configs != null) {
            for (WifiConfiguration c : configs) {
                String ssid = unquote(c.SSID);
                if (ssid != null) saved.put(ssid, c);
            }
        }
        int max = Math.max(1, mWifi.getMaxSignalLevel());
        Map<String, Net> bySsid = new HashMap<>();
        for (ScanResult r : mWifi.getScanResults()) {
            String ssid = r.SSID;
            if (ssid == null || ssid.isEmpty() || ssid.indexOf('\0') >= 0) continue;
            int level = Math.round(mWifi.calculateSignalLevel(r.level) * 4f / max);
            Net n = bySsid.get(ssid);
            if (n == null) {
                n = new Net();
                n.ssid = ssid;
                n.level = level;
                n.security = security(r.getSecurityTypes());
                n.saved = saved.get(ssid);
                bySsid.put(ssid, n);
            } else if (level > n.level) {
                n.level = level;
            }
        }
        List<Net> out = new ArrayList<>(bySsid.values());
        out.sort((a, b) -> {
            int c = Boolean.compare(b.ssid.equals(mConnected), a.ssid.equals(mConnected));
            if (c == 0) c = Boolean.compare(b.saved != null, a.saved != null);
            if (c == 0) c = Integer.compare(b.level, a.level);
            return c != 0 ? c : a.ssid.compareToIgnoreCase(b.ssid);
        });
        return out;
    }

    private static int security(int[] types) {
        boolean psk = false, sae = false, owe = false, open = false, eap = false;
        for (int t : types) {
            switch (t) {
                case WifiInfo.SECURITY_TYPE_PSK: psk = true; break;
                case WifiInfo.SECURITY_TYPE_SAE: sae = true; break;
                case WifiInfo.SECURITY_TYPE_OWE: owe = true; break;
                case WifiInfo.SECURITY_TYPE_OPEN: open = true; break;
                case WifiInfo.SECURITY_TYPE_EAP:
                case WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE:
                case WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT:
                    eap = true;
                    break;
                default:
                    break;
            }
        }
        if (psk) return WifiInfo.SECURITY_TYPE_PSK;
        if (sae) return WifiInfo.SECURITY_TYPE_SAE;
        if (owe) return WifiInfo.SECURITY_TYPE_OWE;
        if (open) return WifiInfo.SECURITY_TYPE_OPEN;
        if (eap) return WifiInfo.SECURITY_TYPE_EAP;
        return SEC_UNSUPPORTED; // WEP, WAPI, ...
    }

    private static boolean supported(Net n) {
        return n.security == WifiInfo.SECURITY_TYPE_PSK || n.security == WifiInfo.SECURITY_TYPE_SAE
                || n.security == WifiInfo.SECURITY_TYPE_OWE || n.security == WifiInfo.SECURITY_TYPE_OPEN;
    }

    private static boolean wrongPassword(WifiConfiguration c) {
        return c != null && c.getNetworkSelectionStatus().getNetworkSelectionDisableReason()
                == NetworkSelectionStatus.DISABLED_BY_WRONG_PASSWORD;
    }

    private void onNetworkClicked(Net n) {
        if (n.ssid.equals(mConnected)) {
            new BudikDialog(mActivity, n.ssid, mActivity.getText(R.string.wifi_connected_body))
                    .negative(R.string.close, null)
                    .positive(R.string.wifi_forget, t -> forget(n))
                    .show();
        } else if (!supported(n)) {
            new BudikDialog(mActivity, n.ssid, mActivity.getText(R.string.wifi_unsupported_body))
                    .positive(R.string.close, null)
                    .show();
        } else if (n.saved != null && !wrongPassword(n.saved) && !mErrors.containsKey(n.ssid)) {
            connect(n.ssid, null, n.saved.networkId);
        } else if (n.security == WifiInfo.SECURITY_TYPE_OPEN
                || n.security == WifiInfo.SECURITY_TYPE_OWE) {
            connect(n.ssid, config(n, null), WifiConfiguration.INVALID_NETWORK_ID);
        } else {
            int min = n.security == WifiInfo.SECURITY_TYPE_PSK ? 8 : 1;
            new BudikDialog(mActivity, n.ssid, mActivity.getText(R.string.wifi_password_body))
                    .password(p -> p.length() >= min && p.length() <= 63 || isHexKey(p))
                    .negative(R.string.cancel, null)
                    .positive(R.string.wifi_connect,
                            p -> connect(n.ssid, config(n, p), WifiConfiguration.INVALID_NETWORK_ID))
                    .show();
        }
    }

    private static boolean isHexKey(String p) {
        return p.length() == 64 && p.matches("[0-9A-Fa-f]+");
    }

    private static WifiConfiguration config(Net n, String password) {
        WifiConfiguration c = new WifiConfiguration();
        c.SSID = "\"" + n.ssid + "\"";
        switch (n.security) {
            case WifiInfo.SECURITY_TYPE_PSK:
                c.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
                break;
            case WifiInfo.SECURITY_TYPE_SAE:
                c.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
                break;
            case WifiInfo.SECURITY_TYPE_OWE:
                c.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OWE);
                break;
            default:
                c.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);
                break;
        }
        if (password != null) {
            c.preSharedKey = isHexKey(password) ? password : "\"" + password + "\"";
        }
        return c;
    }

    private void connect(String ssid, WifiConfiguration config, int networkId) {
        mErrors.remove(ssid);
        mConnecting = ssid;
        mConnectStart = SystemClock.elapsedRealtime();
        WifiManager.ActionListener listener = new WifiManager.ActionListener() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailure(int reason) {
                mHandler.post(() -> failed(ssid));
            }
        };
        try {
            if (config != null) {
                mWifi.connect(config, listener);
            } else {
                mWifi.connect(networkId, listener);
            }
        } catch (RuntimeException e) {
            Log.w(SetupActivity.TAG, "connect failed", e);
            failed(ssid);
            return;
        }
        mHandler.removeCallbacks(mTimeout);
        mHandler.postDelayed(mTimeout, CONNECT_TIMEOUT_MS);
        refresh();
    }

    private void failed(String ssid) {
        if (!ssid.equals(mConnecting)) return;
        mConnecting = null;
        mHandler.removeCallbacks(mTimeout);
        mErrors.put(ssid, R.string.wifi_state_failed);
        refresh();
    }

    private void forget(Net n) {
        if (n.saved == null) return;
        try {
            mWifi.forget(n.saved.networkId, null);
        } catch (RuntimeException e) {
            Log.w(SetupActivity.TAG, "forget failed", e);
        }
        mHandler.postDelayed(mRefresh, 300);
    }

    private static String unquote(String s) {
        if (s == null) return null;
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    @Override
    CharSequence summary() {
        String ssid;
        try {
            ssid = associatedSsid();
        } catch (RuntimeException e) {
            ssid = null;
        }
        return ssid != null ? ssid : mActivity.getText(R.string.wifi_not_connected);
    }

    @Override
    void save(Bundle out) {
        out.putBoolean(KEY_AUTO_ENABLED, mAutoEnabled);
    }

    @Override
    void restore(Bundle in) {
        mAutoEnabled = in.getBoolean(KEY_AUTO_ENABLED, false);
    }
}
