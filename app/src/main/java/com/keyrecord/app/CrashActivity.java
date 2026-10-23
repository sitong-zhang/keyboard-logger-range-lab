package com.keyrecord.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Minimal crash log viewer. Built entirely from code with no theme or
 * library dependencies so that it still works when everything else is broken.
 */
public class CrashActivity extends Activity {

    private TextView body;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            buildUi();
        } catch (Throwable t) {
            // Absolute fallback: a bare text view.
            TextView tv = new TextView(this);
            tv.setText("崩溃日志页面初始化失败：" + t);
            setContentView(tv);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("崩溃日志");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        ScrollView sc = new ScrollView(this);
        body = new TextView(this);
        body.setTextSize(12);
        body.setTextIsSelectable(true);
        sc.addView(body);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(sc, lp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        Button copy = new Button(this);
        copy.setText("复制");
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    String text = body.getText().toString();
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("crash", text));
                    Toast.makeText(CrashActivity.this, "已复制", Toast.LENGTH_SHORT).show();
                } catch (Throwable t) {
                    Toast.makeText(CrashActivity.this, "复制失败", Toast.LENGTH_SHORT).show();
                }
            }
        });

        Button clear = new Button(this);
        clear.setText("清空");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CrashMonitor.clear(CrashActivity.this);
                refresh();
                Toast.makeText(CrashActivity.this, "已清空", Toast.LENGTH_SHORT).show();
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

        buttons.addView(copy);
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
        String log = CrashMonitor.read(this);
        if (log == null || log.trim().isEmpty()) {
            body.setText("（暂无崩溃记录）\n\n"
                    + "如果某个界面打不开或白屏，先打开一次那个界面，\n"
                    + "然后回到这里查看，把内容复制发给开发者即可。");
        } else {
            body.setText(log);
        }
    }
}
