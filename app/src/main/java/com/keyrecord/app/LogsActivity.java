package com.keyrecord.app;

import android.app.Activity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Minimal local viewer for recorded keystrokes. Read-only display of the
 * private log file, with export and clear actions. No network access.
 */
public class LogsActivity extends Activity {

    private final SimpleDateFormat fmt =
            new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault());
    private TextView body;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("键盘记录（仅本机）");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        body = new TextView(this);
        body.setTextSize(13);
        body.setMovementMethod(new ScrollingMovementMethod());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(body, lp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        Button export = new Button(this);
        export.setText("导出");
        export.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                java.io.File f = LogManager.getInstance(LogsActivity.this).export();
                Toast.makeText(LogsActivity.this,
                        "已导出：" + (f != null ? f.getAbsolutePath() : "失败"),
                        Toast.LENGTH_LONG).show();
            }
        });

        Button clear = new Button(this);
        clear.setText("清空");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LogManager.getInstance(LogsActivity.this).clear();
                refresh();
                Toast.makeText(LogsActivity.this, "已清空", Toast.LENGTH_SHORT).show();
            }
        });

        Button close = new Button(this);
        close.setText("关闭");
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        buttons.addView(export);
        buttons.addView(clear);
        buttons.addView(close);
        root.addView(buttons);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        LogManager lm = LogManager.getInstance(this);
        LogManager.Stats s = lm.getStats();
        StringBuilder sb = new StringBuilder();
        sb.append("按键记录：").append(s.keystrokes).append(" 次\n");
        sb.append("涉及应用：").append(s.apps).append(" 个\n");
        if (s.since > 0) {
            sb.append("起始时间：").append(fmt.format(new Date(s.since))).append("\n");
        }
        sb.append("————————————————\n");
        List<LogManager.LogEntry> recent = lm.getRecent(200);
        if (recent.isEmpty()) {
            sb.append("（暂无记录。请先在系统设置里开启本服务的无障碍权限，并用本输入法打字。）\n");
        } else {
            for (LogManager.LogEntry e : recent) {
                sb.append(fmt.format(new Date(e.time))).append("  ")
                        .append(e.pkg).append("\n    ")
                        .append(e.text).append("\n");
            }
        }
        body.setText(sb.toString());
    }
}
