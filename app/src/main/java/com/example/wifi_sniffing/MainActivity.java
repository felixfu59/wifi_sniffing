package com.example.wifi_sniffing;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private static final long UPDATE_INTERVAL_MS = 1000L;

    // Scan speed: position → delay between scans in ms
    private static final long[] SCAN_DELAYS = {0, 2000, 5000, 15000};
    private static final String[] SCAN_SPEEDS = {"极速", "快速", "标准", "省电"};

    private WifiManager wifiManager;
    private Handler handler = new Handler(Looper.getMainLooper());
    private long scanDelay = SCAN_DELAYS[0]; // default: fastest
    private List<ScanResult> scanResults = new ArrayList<>();
    private boolean updatingSpinner = false;
    private String selectedBssid = null;
    private ScanResult selectedScanResult = null;
    private String lastConnectedBssid = null;
    private String lastScanFingerprint = "";
    private boolean scanInProgress = false;
    private long lastScanStartedAt = 0;

    // RSSI history for trend/distance detection
    private static final int HISTORY_SIZE = 6;
    private int[] rssiHistory = new int[HISTORY_SIZE];
    private int rssiHistoryIndex = 0;
    private int rssiHistoryCount = 0;
    private int lastDisplayedRssi = Integer.MIN_VALUE;

    private Spinner wifiSpinner;
    private Spinner scanSpeedSpinner;
    private TextView tvSignalStrength;
    private TextView tvSignalLabel;
    private TextView tvProximity;
    private TextView tvTrendArrow;
    private TextView tvTrendDelta;
    private View circleBackground;
    private View circleFill;
    private View bar1, bar2, bar3, bar4, bar5;
    private TextView tvSsid;
    private TextView tvBssid;
    private TextView tvFrequency;
    private TextView tvSecurity;
    private TextView tvLinkSpeed;
    private TextView tvIpAddress;

    private ScanResultAdapter spinnerAdapter;

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateWiFiInfo();
            handler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        initViews();
        registerReceiver(scanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    private void initViews() {
        wifiSpinner = findViewById(R.id.wifiSpinner);
        scanSpeedSpinner = findViewById(R.id.scanSpeedSpinner);
        tvSignalStrength = findViewById(R.id.tvSignalStrength);
        tvSignalLabel = findViewById(R.id.tvSignalLabel);
        tvProximity = findViewById(R.id.tvProximity);
        tvTrendArrow = findViewById(R.id.tvTrendArrow);
        tvTrendDelta = findViewById(R.id.tvTrendDelta);
        circleBackground = findViewById(R.id.circleBackground);
        circleFill = findViewById(R.id.circleFill);
        bar1 = findViewById(R.id.bar1);
        bar2 = findViewById(R.id.bar2);
        bar3 = findViewById(R.id.bar3);
        bar4 = findViewById(R.id.bar4);
        bar5 = findViewById(R.id.bar5);
        tvSsid = findViewById(R.id.tvSsid);
        tvBssid = findViewById(R.id.tvBssid);
        tvFrequency = findViewById(R.id.tvFrequency);
        tvSecurity = findViewById(R.id.tvSecurity);
        tvLinkSpeed = findViewById(R.id.tvLinkSpeed);
        tvIpAddress = findViewById(R.id.tvIpAddress);

        // WiFi network spinner
        spinnerAdapter = new ScanResultAdapter(this, new ArrayList<>());
        wifiSpinner.setAdapter(spinnerAdapter);

        wifiSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (updatingSpinner) return;
                ScanResult selected = spinnerAdapter.getItem(position);
                if (selected != null) {
                    selectedBssid = selected.BSSID;
                    selectedScanResult = selected;
                    showScanResultDetails(selected);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Scan speed spinner
        ArrayAdapter<String> speedAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, SCAN_SPEEDS) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(0xFFFFFFFF);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(0xFFFFFFFF);
                view.setBackgroundColor(0xFF2C2C2C);
                return view;
            }
        };
        speedAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        scanSpeedSpinner.setAdapter(speedAdapter);

        scanSpeedSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                scanDelay = SCAN_DELAYS[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasLocationPermission()) {
            startMonitoring();
        } else {
            requestLocationPermission();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(updateRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(scanReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startMonitoring();
            }
        }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            new AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage(getString(R.string.location_permission_required))
                    .setPositiveButton("Grant", (dialog, which) ->
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                    LOCATION_PERMISSION_REQUEST))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        }
    }

    private void startMonitoring() {
        handler.post(updateRunnable);
        triggerScan();

        // One-time hint about scan throttling
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        if (!prefs.getBoolean("throttle_hint_shown", false)) {
            prefs.edit().putBoolean("throttle_hint_shown", true).apply();
            android.widget.Toast.makeText(this,
                    "Developer options → Wi-Fi scan throttling → OFF for fastest updates",
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void triggerScan() {
        // Safety timeout: if scan stuck for >5s, reset and retry
        if (scanInProgress && System.currentTimeMillis() - lastScanStartedAt > 5000) {
            scanInProgress = false;
        }
        if (scanInProgress) return;
        if (wifiManager.startScan()) {
            scanInProgress = true;
            lastScanStartedAt = System.currentTimeMillis();
        }
    }

    private void updateWiFiInfo() {
        if (!wifiManager.isWifiEnabled()) {
            tvSignalStrength.setText("OFF");
            tvSignalLabel.setText("WiFi is disabled");
            setCircleColor(R.color.signal_bad);
            return;
        }

        // Stall detection: if no scan started for scanDelay + 5s, force restart loop
        if (!scanInProgress && System.currentTimeMillis() - lastScanStartedAt > scanDelay + 5000) {
            triggerScan();
        }

        // Poll latest cached scan results and refresh UI if changed
        refreshScanResults();

        WifiInfo wifiInfo = wifiManager.getConnectionInfo();

        if (wifiInfo.getNetworkId() == -1) {
            tvSignalStrength.setText("--");
            tvSignalLabel.setText(getString(R.string.no_wifi_connected));
            setCircleColor(R.color.signal_bad);
        } else {
            // Only update center display + details for the connected WiFi,
            // unless user has selected a different network from spinner
            if (selectedBssid == null || selectedBssid.equals(wifiInfo.getBSSID())) {
                int rssi = getReliableRssi(wifiInfo);
                tvSignalStrength.setText(String.valueOf(rssi));
                tvSignalLabel.setText("dBm");
                updateSignalColor(rssi);
                trackRssi(rssi);
                showConnectionDetails(wifiInfo);
            }
        }
    }

    private void showConnectionDetails(WifiInfo wifiInfo) {
        String ssid = wifiInfo.getSSID();
        tvSsid.setText(ssid != null ? ssid.replace("\"", "") : "--");
        tvBssid.setText(wifiInfo.getBSSID() != null ? wifiInfo.getBSSID() : "--");
        tvFrequency.setText(formatFrequency(wifiInfo.getFrequency()));
        tvSecurity.setText(getSecurityType(wifiInfo.getBSSID()));
        tvLinkSpeed.setText(wifiInfo.getLinkSpeed() > 0 ? wifiInfo.getLinkSpeed() + " Mbps" : "--");
        tvIpAddress.setText(formatIpAddress(wifiInfo.getIpAddress()));
    }

    private void showScanResultDetails(ScanResult result) {
        String ssid = result.SSID;
        tvSsid.setText(ssid != null && !ssid.isEmpty() ? ssid : "--");
        tvBssid.setText(result.BSSID);
        tvFrequency.setText(formatFrequency(result.frequency));
        tvSecurity.setText(getSecurityFromCapabilities(result.capabilities));
        tvLinkSpeed.setText("--");
        tvIpAddress.setText("--");
        tvSignalStrength.setText(String.valueOf(result.level));
        tvSignalLabel.setText("dBm");
        updateSignalColor(result.level);
        trackRssi(result.level);
    }

    private void updateSignalBars(int rssi) {
        int bars = 0;
        if (rssi > -55) bars = 5;
        else if (rssi > -62) bars = 4;
        else if (rssi > -70) bars = 3;
        else if (rssi > -80) bars = 2;
        else if (rssi > -90) bars = 1;

        int activeColor;
        if (bars >= 4) activeColor = 0xFF4CAF50;
        else if (bars >= 3) activeColor = 0xFFFFC107;
        else if (bars >= 2) activeColor = 0xFFFF9800;
        else activeColor = 0xFFF44336;

        int dimColor = 0x22FFFFFF;
        bar1.setBackgroundColor(bars >= 1 ? activeColor : dimColor);
        bar2.setBackgroundColor(bars >= 2 ? activeColor : dimColor);
        bar3.setBackgroundColor(bars >= 3 ? activeColor : dimColor);
        bar4.setBackgroundColor(bars >= 4 ? activeColor : dimColor);
        bar5.setBackgroundColor(bars >= 5 ? activeColor : dimColor);
    }

    private void updateSignalColor(int rssi) {
        int colorRes;
        if (rssi > -50) {
            colorRes = R.color.signal_excellent;
        } else if (rssi > -60) {
            colorRes = R.color.signal_good;
        } else if (rssi > -70) {
            colorRes = R.color.signal_fair;
        } else if (rssi > -80) {
            colorRes = R.color.signal_poor;
        } else {
            colorRes = R.color.signal_bad;
        }
        updateSignalBars(rssi);
        setCircleColor(colorRes);
    }

    // Poll latest scan results every second; only update UI when data actually changed
    @SuppressWarnings("MissingPermission")
    private void refreshScanResults() {
        if (!hasLocationPermission()) return;
        List<ScanResult> latest = wifiManager.getScanResults();
        if (latest.isEmpty()) return;

        // Fast fingerprint: concat BSSID+level of all results to detect changes
        StringBuilder sb = new StringBuilder();
        for (ScanResult r : latest) {
            sb.append(r.BSSID).append(':').append(r.level).append(';');
        }
        String fp = sb.toString();
        if (fp.equals(lastScanFingerprint)) return; // No change, skip UI update
        lastScanFingerprint = fp;

        scanResults.clear();
        scanResults.addAll(latest);
        updateSpinner();
    }

    private int getReliableRssi(WifiInfo wifiInfo) {
        int rssi = wifiInfo.getRssi();
        // On some devices, getRssi() returns 0 even when connected.
        // Fall back to scan results for the connected BSSID.
        if (rssi >= 0 && wifiInfo.getBSSID() != null) {
            for (ScanResult result : scanResults) {
                if (wifiInfo.getBSSID().equals(result.BSSID)) {
                    return result.level;
                }
            }
        }
        return rssi;
    }

    // Track RSSI in history ring buffer for trend detection
    private void trackRssi(int rssi) {
        if (rssi == lastDisplayedRssi) return;
        lastDisplayedRssi = rssi;
        rssiHistory[rssiHistoryIndex] = rssi;
        rssiHistoryIndex = (rssiHistoryIndex + 1) % HISTORY_SIZE;
        if (rssiHistoryCount < HISTORY_SIZE) rssiHistoryCount++;
        updateTrendDisplay();
        updateProximityDisplay(rssi);
    }

    // Show trend arrow and delta based on RSSI history
    private void updateTrendDisplay() {
        if (rssiHistoryCount < 2) {
            tvTrendArrow.setText("●");
            tvTrendArrow.setTextColor(0xFFFFFFFF);
            tvTrendDelta.setText("");
            return;
        }

        // Compare average of last half vs first half of history
        int mid = rssiHistoryCount / 2;
        int recentSum = 0, olderSum = 0;
        for (int i = 0; i < rssiHistoryCount; i++) {
            int idx = (rssiHistoryIndex - 1 - i + HISTORY_SIZE) % HISTORY_SIZE;
            if (i < mid) recentSum += rssiHistory[idx];
            else olderSum += rssiHistory[idx];
        }
        int recentAvg = recentSum / mid;
        int olderAvg = olderSum / (rssiHistoryCount - mid);
        int delta = recentAvg - olderAvg;

        // Get latest reading for instant delta display
        int latestIdx = (rssiHistoryIndex - 1 + HISTORY_SIZE) % HISTORY_SIZE;
        int prevIdx = (rssiHistoryIndex - 2 + HISTORY_SIZE) % HISTORY_SIZE;
        int instantDelta = rssiHistory[latestIdx] - rssiHistory[prevIdx];

        if (delta > 1) {
            tvTrendArrow.setText("▲");
            tvTrendArrow.setTextColor(0xFF4CAF50); // Green: getting closer
        } else if (delta < -1) {
            tvTrendArrow.setText("▼");
            tvTrendArrow.setTextColor(0xFFF44336); // Red: getting further
        } else {
            tvTrendArrow.setText("●");
            tvTrendArrow.setTextColor(0xFFFFFFFF); // White: stable
        }

        tvTrendDelta.setText(String.format(Locale.US, "%+d", instantDelta));
        tvTrendDelta.setTextColor(delta > 0 ? 0xFF4CAF50 : delta < 0 ? 0xFFF44336 : 0x80FFFFFF);
    }

    // Show approximate distance zone based on RSSI
    private void updateProximityDisplay(int rssi) {
        String zone;
        int color;
        if (rssi > -40) {
            zone = "Very Near";
            color = 0xFF4CAF50;
        } else if (rssi > -55) {
            zone = "Near";
            color = 0xFF8BC34A;
        } else if (rssi > -70) {
            zone = "Medium";
            color = 0xFFFFC107;
        } else if (rssi > -85) {
            zone = "Far";
            color = 0xFFFF9800;
        } else {
            zone = "Very Far";
            color = 0xFFF44336;
        }
        tvProximity.setText(zone);
        tvProximity.setTextColor(color);
    }

    private void setCircleColor(int colorRes) {
        if (circleBackground.getBackground() instanceof GradientDrawable) {
            GradientDrawable drawable = (GradientDrawable) circleBackground.getBackground();
            drawable.setStroke(8, ContextCompat.getColor(this, colorRes));
        }
    }

    private final Runnable triggerScanRunnable = new Runnable() {
        @Override
        public void run() {
            triggerScan();
        }
    };

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            scanInProgress = false;
            if (intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)) {
                if (hasLocationPermission()) {
                    @SuppressWarnings("MissingPermission")
                    List<ScanResult> results = wifiManager.getScanResults();
                    scanResults.clear();
                    scanResults.addAll(results);
                    updateSpinner();
                }
            }
            // Start next scan after configured delay based on scan speed
            handler.removeCallbacks(triggerScanRunnable);
            handler.postDelayed(triggerScanRunnable, scanDelay);
        }
    };

    private void updateSpinner() {
        Collections.sort(scanResults, (a, b) -> Integer.compare(b.level, a.level));

        updatingSpinner = true;

        spinnerAdapter.clear();
        spinnerAdapter.addAll(scanResults);

        // If user selected a WiFi that's no longer in range, keep it in the list
        boolean selectedInRange = false;
        if (selectedBssid != null) {
            for (int i = 0; i < scanResults.size(); i++) {
                if (selectedBssid.equals(scanResults.get(i).BSSID)) {
                    selectedInRange = true;
                    break;
                }
            }
            if (!selectedInRange && selectedScanResult != null) {
                spinnerAdapter.add(selectedScanResult);
            }
        }

        spinnerAdapter.notifyDataSetChanged();

        if (selectedBssid != null) {
            int idx = -1;
            for (int i = 0; i < spinnerAdapter.getCount(); i++) {
                ScanResult item = spinnerAdapter.getItem(i);
                if (item != null && selectedBssid.equals(item.BSSID)) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0) {
                wifiSpinner.setSelection(idx, false);

                if (selectedInRange) {
                    // In range: show latest scan data
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    if (!selectedBssid.equals(wifiInfo.getBSSID())) {
                        showScanResultDetails(spinnerAdapter.getItem(idx));
                    }
                } else {
                    // Out of range: mark as lost, keep last data
                    tvSignalLabel.setText("dBm (失联)");
                    setCircleColor(R.color.signal_bad);
                    tvTrendArrow.setText("●");
                    tvTrendArrow.setTextColor(0x80FFFFFF);
                    tvTrendDelta.setText("");
                    tvProximity.setText("Lost");
                    tvProximity.setTextColor(0xFFF44336);
                }
            }
        }

        updatingSpinner = false;
    }

    private String getSecurityType(String bssid) {
        if (bssid == null) return "--";
        for (ScanResult result : scanResults) {
            if (bssid.equals(result.BSSID)) {
                return getSecurityFromCapabilities(result.capabilities);
            }
        }
        return "--";
    }

    private String getSecurityFromCapabilities(String capabilities) {
        if (capabilities == null) return "--";
        if (capabilities.contains("WPA3")) return "WPA3";
        if (capabilities.contains("SAE")) return "WPA3-SAE";
        if (capabilities.contains("WPA2")) return "WPA2";
        if (capabilities.contains("WPA")) return "WPA";
        if (capabilities.contains("WEP")) return "WEP";
        if (capabilities.contains("EAP")) return "802.1X EAP";
        if (capabilities.contains("OWE")) return "OWE";
        return "Open";
    }

    private String formatFrequency(int frequency) {
        if (frequency <= 0) return "--";
        int mhz = frequency;
        double ghz = frequency / 1000.0;
        String band;
        if (frequency >= 5900) {
            band = "6 GHz";
        } else if (frequency >= 5000) {
            band = "5 GHz";
        } else if (frequency >= 2400) {
            band = "2.4 GHz";
        } else {
            band = "";
        }
        return String.format(Locale.US, "%d MHz (%.1f GHz %s)", mhz, ghz, band);
    }

    private String formatIpAddress(int ip) {
        if (ip == 0) return "--";
        return String.format(Locale.US, "%d.%d.%d.%d",
                ip & 0xFF,
                (ip >> 8) & 0xFF,
                (ip >> 16) & 0xFF,
                (ip >> 24) & 0xFF);
    }

    // Custom adapter to display SSID + signal level in the spinner
    private boolean isLostNetwork(ScanResult item) {
        if (item == null || selectedBssid == null) return false;
        if (!selectedBssid.equals(item.BSSID)) return false;
        for (ScanResult r : scanResults) {
            if (item.BSSID.equals(r.BSSID)) return false;
        }
        return true;
    }

    private class ScanResultAdapter extends ArrayAdapter<ScanResult> {

        ScanResultAdapter(Context context, List<ScanResult> items) {
            super(context, android.R.layout.simple_spinner_item, items);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            TextView view = (TextView) super.getView(position, convertView, parent);
            view.setTextColor(0xFFFFFFFF);

            ScanResult item = getItem(position);
            if (item != null) {
                String ssid = item.SSID != null && !item.SSID.isEmpty() ? item.SSID : "<hidden>";
                String suffix = isLostNetwork(item) ? " (失联)" : " (" + item.level + " dBm)";
                view.setText(ssid + suffix);
            } else {
                view.setText(R.string.scanning);
            }
            return view;
        }

        @Override
        public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
            TextView view = (TextView) super.getDropDownView(position, convertView, parent);
            view.setTextColor(0xFFFFFFFF);
            view.setBackgroundColor(0xFF2C2C2C);

            ScanResult item = getItem(position);
            if (item != null) {
                String ssid = item.SSID != null && !item.SSID.isEmpty() ? item.SSID : "<hidden>";
                String suffix = isLostNetwork(item) ? " (失联)" : " (" + item.level + " dBm)";
                view.setText(ssid + suffix);
            } else {
                view.setText(R.string.scanning);
            }
            return view;
        }
    }
}
