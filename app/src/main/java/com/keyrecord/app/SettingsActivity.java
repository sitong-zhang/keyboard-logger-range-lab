package com.keyrecord.app;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends AppCompatActivity {

    private LogManager logManager;
    private MaterialSwitch swService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        logManager = LogManager.getInstance(this);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        configureRow(R.id.rowService, R.drawable.ic_keyboard, R.string.row_service,
                R.string.row_service_summary, true);
        configureRow(R.id.rowExport, R.drawable.ic_share, R.string.row_export,
                R.string.row_export_summary, false);
        configureRow(R.id.rowClear, R.drawable.ic_delete, R.string.row_clear,
                R.string.row_clear_summary, false);
        configureRow(R.id.rowAbout, R.drawable.ic_info, R.string.row_about,
                R.string.about_text, false);
    }

    private void configureRow(int rowId, int iconRes, int titleRes, int summaryRes, boolean isService) {
        View row = findViewById(rowId);
        ((ImageView) row.findViewById(R.id.ivIcon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.tvTitle)).setText(titleRes);
        ((TextView) row.findViewById(R.id.tvSummary)).setText(summaryRes);
        MaterialSwitch sw = row.findViewById(R.id.swToggle);

        if (isService) {
            sw.setVisibility(View.VISIBLE);
            sw.setClickable(false);
            swService = sw;
            row.setOnClickListener(v ->
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        } else if (rowId == R.id.rowExport) {
            row.setOnClickListener(v -> {
                java.io.File f = logManager.export();
                Toast.makeText(this,
                        getString(R.string.exported_toast, f.getAbsolutePath()), Toast.LENGTH_LONG).show();
            });
        } else if (rowId == R.id.rowClear) {
            row.setOnClickListener(v -> confirmClear());
        } else if (rowId == R.id.rowAbout) {
            row.setOnClickListener(v -> showAbout());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (swService != null) {
            swService.setChecked(isServiceEnabled());
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.clear_confirm_title)
                .setMessage(R.string.clear_confirm_msg)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear, (d, w) -> {
                    logManager.clear();
                    Toast.makeText(this, R.string.clear, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.about_text)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(getPackageName() + "/.KeyLoggerService");
    }
}
