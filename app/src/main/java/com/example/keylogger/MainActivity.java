package com.example.keylogger;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 首屏：展示合法使用声明 + 授权勾选；引导开启无障碍服务；查看/导出/清空本地日志。
 * 合规要点：未勾选授权确认前，禁止引导开启服务；不申请任何网络权限。
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "kl_prefs";
    private static final String KEY_AGREED = "agreed";

    private CheckBox cbAgree;
    private TextView tvLog;
    private LogManager logManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        logManager = LogManager.getInstance(this);

        TextView tvDecl = findViewById(R.id.tvDeclaration);
        tvDecl.setText(getString(R.string.legal_declaration));

        cbAgree = findViewById(R.id.cbAgree);
        tvLog = findViewById(R.id.tvLog);

        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        cbAgree.setChecked(sp.getBoolean(KEY_AGREED, false));
        cbAgree.setOnCheckedChangeListener((buttonView, isChecked) ->
                sp.edit().putBoolean(KEY_AGREED, isChecked).apply());

        findViewById(R.id.btnEnable).setOnClickListener(v -> {
            if (!cbAgree.isChecked()) {
                Toast.makeText(this, "请先勾选合法使用确认", Toast.LENGTH_SHORT).show();
                return;
            }
            // 跳转到系统无障碍设置，由用户手动开启本服务
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });

        findViewById(R.id.btnView).setOnClickListener(v ->
                tvLog.setText(logManager.read()));

        findViewById(R.id.btnExport).setOnClickListener(v -> {
            java.io.File f = logManager.export();
            Toast.makeText(this, "已导出到:\n" + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
        });

        findViewById(R.id.btnClear).setOnClickListener(v -> {
            logManager.clear();
            tvLog.setText("");
            Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show();
        });

        updateStatus();
    }

    private void updateStatus() {
        boolean enabled = isServiceEnabled();
        TextView tvStatus = findViewById(R.id.tvStatus);
        tvStatus.setText(enabled
                ? "状态：键盘记录服务已开启 ✅"
                : "状态：键盘记录服务未开启 ⚠️（请勾选确认后点击上方按钮开启）");
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(getPackageName() + "/.KeyLoggerService");
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }
}
