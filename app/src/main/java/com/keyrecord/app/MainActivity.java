package com.keyrecord.app;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private LogManager logManager;

    private TextView tvStatusTitle;
    private TextView tvStatusSub;
    private Button btnEnable;
    private TextView tvStatKeys;
    private TextView tvStatApps;
    private TextView tvStatSince;
    private LinearLayout recentContainer;
    private TextView tvRecentEmpty;
    private TextView tvImeSub;
    private Button btnIme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        logManager = LogManager.getInstance(this);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);

        tvStatusTitle = findViewById(R.id.tvStatusTitle);
        tvStatusSub = findViewById(R.id.tvStatusSub);
        btnEnable = findViewById(R.id.btnEnable);
        tvStatKeys = findViewById(R.id.tvStatKeys);
        tvStatApps = findViewById(R.id.tvStatApps);
        tvStatSince = findViewById(R.id.tvStatSince);
        recentContainer = findViewById(R.id.recentContainer);
        tvRecentEmpty = findViewById(R.id.tvRecentEmpty);
        tvImeSub = findViewById(R.id.tvImeSub);
        btnIme = findViewById(R.id.btnIme);

        btnEnable.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        btnIme.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));

        findViewById(R.id.btnOpenLogs).setOnClickListener(v ->
                startActivity(new Intent(this, LogsActivity.class)));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.top_app_bar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.menu_about) {
            showAbout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean enabled = isServiceEnabled();
        if (enabled) {
            tvStatusTitle.setText(R.string.status_running);
            tvStatusSub.setText(R.string.status_running_sub);
            btnEnable.setText(R.string.open_settings);
        } else {
            tvStatusTitle.setText(R.string.status_stopped);
            tvStatusSub.setText(R.string.status_stopped_sub);
            btnEnable.setText(R.string.btn_enable);
        }

        refreshImeState();

        LogManager.Stats stats = logManager.getStats();
        tvStatKeys.setText(String.valueOf(stats.keystrokes));
        tvStatApps.setText(String.valueOf(stats.apps));
        tvStatSince.setText(stats.since > 0 ? formatDate(stats.since) : "—");

        buildRecent();
    }

    private void refreshImeState() {
        boolean imeEnabled = false;
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                if (getPackageName().equals(info.getPackageName())) {
                    imeEnabled = true;
                    break;
                }
            }
        }
        if (!imeEnabled) {
            tvImeSub.setText(R.string.ime_off);
            btnIme.setText(R.string.btn_enable_ime);
            return;
        }
        String current = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean selected = current != null && current.startsWith(getPackageName() + "/");
        tvImeSub.setText(selected ? R.string.ime_on_using : R.string.ime_on_idle);
        btnIme.setText(selected ? R.string.btn_enable_ime : R.string.btn_enable_ime);
    }

    private void buildRecent() {
        recentContainer.removeAllViews();
        List<LogManager.LogEntry> recent = logManager.getRecent(6);
        if (recent.isEmpty()) {
            tvRecentEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvRecentEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        boolean first = true;
        for (LogManager.LogEntry e : recent) {
            View row = inflater.inflate(R.layout.item_entry, recentContainer, false);
            if (first) {
                row.findViewById(R.id.divider).setVisibility(View.GONE);
                first = false;
            }
            ((TextView) row.findViewById(R.id.tvApp)).setText(AppLabels.getLabel(this, e.pkg));
            ((TextView) row.findViewById(R.id.tvTime)).setText(formatTime(e.time));
            ((TextView) row.findViewById(R.id.tvSnippet)).setText(e.text);
            recentContainer.addView(row);
        }
    }

    private boolean isServiceEnabled() {
        AccessibilityManager am =
                (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) {
            return false;
        }
        for (AccessibilityServiceInfo info
                : am.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getResolveInfo() != null
                    && getPackageName().equals(
                            info.getResolveInfo().serviceInfo.packageName)) {
                return true;
            }
        }
        return false;
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.about_text)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private String formatTime(long millis) {
        if (millis <= 0) {
            return "";
        }
        Calendar c = Calendar.getInstance();
        Calendar now = Calendar.getInstance();
        c.setTimeInMillis(millis);
        if (c.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                && c.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(millis));
        }
        return new SimpleDateFormat("MM/dd", Locale.getDefault()).format(new Date(millis));
    }

    private String formatDate(long millis) {
        if (millis <= 0) {
            return "—";
        }
        return new SimpleDateFormat("MM/dd", Locale.getDefault()).format(new Date(millis));
    }
}
