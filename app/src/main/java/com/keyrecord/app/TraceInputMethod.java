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
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Built-in keyboard inspired by the iOS system keyboard:
 *  - rounded gray keys, long space bar, globe / 123 / comma / space / period / return bar
 *  - shift capitalization, 123 / #+= symbol pages, iOS key-pop animation
 *  - Chinese pinyin input with an iOS-style candidate bar (联想条)
 *  - long-press popups for accented letters and alternate punctuation
 *  - globe key switches between 中文拼音 (PINYIN) and English (LATIN)
 *
 * Every committed character / word is written locally via LogManager at commit
 * time (real-time, works in floating windows / split screen). No network is used.
 */
public class TraceInputMethod extends InputMethodService {

    private static final long WORD_FLUSH_DELAY_MS = 900;
    private static final long LONG_PRESS_MS = 350;
    private static final int LETTERS = 0;
    private static final int NUMBERS = 1;
    private static final int SYMBOLS = 2;
    private static final int PINYIN = 0;
    private static final int LATIN = 1;

    // long-press alternates (iOS style)
    private static final Map<Character, String> LETTER_ALTS = new HashMap<>();
    private static final Map<Character, String> PUNCT_ALTS = new HashMap<>();
    static {
        LETTER_ALTS.put('a', "áàâäãåāǎ");
        LETTER_ALTS.put('e', "éèêëēě");
        LETTER_ALTS.put('i', "íìîïīǐ");
        LETTER_ALTS.put('o', "óòôöõøōǒ");
        LETTER_ALTS.put('u', "úùûüūǔǖǘǚǜ");
        LETTER_ALTS.put('c', "çćč");
        LETTER_ALTS.put('n', "ñń");
        LETTER_ALTS.put('s', "śš");
        LETTER_ALTS.put('z', "źž");
        LETTER_ALTS.put('y', "ýÿ");
        LETTER_ALTS.put('d', "ð");
        LETTER_ALTS.put('g', "ĝ");
        LETTER_ALTS.put('l', "ł");
        LETTER_ALTS.put('r', "ř");
        LETTER_ALTS.put('t', "þ");
        PUNCT_ALTS.put('.', "…•");
        PUNCT_ALTS.put(',', "、");
        PUNCT_ALTS.put('?', "¿");
        PUNCT_ALTS.put('!', "¡");
        PUNCT_ALTS.put('$', "€£¥¢");
        PUNCT_ALTS.put('"', "‘’");
        PUNCT_ALTS.put('\'', "«»");
        PUNCT_ALTS.put('(', "[");
        PUNCT_ALTS.put(')', "]");
        PUNCT_ALTS.put('-', "—–");
        PUNCT_ALTS.put('/', "\\");
        PUNCT_ALTS.put('%', "‰");
        PUNCT_ALTS.put('+', "±");
        PUNCT_ALTS.put('*', "×÷");
    }

    private PinyinEngine engine;
    private LogManager logManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private LinearLayout keyboardView;
    private HorizontalScrollView candidateBar;
    private LinearLayout candRow;
    private TextView tapPop;

    private int page = LETTERS;
    private int inputMode = PINYIN;
    private int shiftState = 0; // 0 off, 1 one-shot, 2 caps lock

    private final StringBuilder composeBuffer = new StringBuilder();
    private final StringBuilder wordBuffer = new StringBuilder();
    private final Runnable flushRunnable = this::flushWord;
    private List<PinyinEngine.Candidate> currentCandidates;

    // long-press alt popup state
    private Runnable longPressRunnable;
    private boolean longPressing = false;
    private View altRow;
    private final List<TextView> altCells = new ArrayList<>();
    private final List<String> altCharList = new ArrayList<>();
    private int altLeft = 0;
    private int cellW = 0;
    private int altHover = -1;
    private String lpBase = "";
    private String lpAlts = "";
    private View lpKey = null;

    @Override
    public void onCreate() {
        super.onCreate();
        logManager = LogManager.getInstance(this);
        engine = new PinyinEngine();
        engine.load(this);
    }

    @Override
    public View onCreateInputView() {
        root = new FrameLayout(this);
        root.setBackgroundColor(color(R.color.ime_bg));

        // candidate bar (联想条)
        candidateBar = new HorizontalScrollView(this);
        candidateBar.setHorizontalScrollBarEnabled(false);
        candidateBar.setBackgroundColor(color(R.color.ime_bar_bg));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        cp.gravity = Gravity.TOP;
        candidateBar.setLayoutParams(cp);
        candRow = new LinearLayout(this);
        candRow.setOrientation(LinearLayout.HORIZONTAL);
        candRow.setGravity(Gravity.CENTER_VERTICAL);
        candidateBar.addView(candRow, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        candidateBar.setVisibility(View.GONE);
        root.addView(candidateBar);

        keyboardView = new LinearLayout(this);
        keyboardView.setOrientation(LinearLayout.VERTICAL);
        keyboardView.setPadding(dp(4), dp(8), dp(4), dp(8));
        FrameLayout.LayoutParams kp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        kp.topMargin = dp(48);
        keyboardView.setLayoutParams(kp);
        root.addView(keyboardView);

        tapPop = new TextView(this);
        tapPop.setTextSize(32);
        tapPop.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tapPop.setTextColor(color(R.color.ime_key_text));
        tapPop.setBackground(rounded(R.color.ime_pop_bg, 10));
        tapPop.setPadding(dp(14), dp(6), dp(14), dp(6));
        tapPop.setVisibility(View.GONE);
        root.addView(tapPop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        rebuild();
        return root;
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        flushWord();
        clearComposition();
        super.onFinishInputView(finishingInput);
    }

    // --------------------------------------------------------------- build UI

    private void rebuild() {
        if (tapPop != null) {
            tapPop.setVisibility(View.GONE);
        }
        hideAltPopup();
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
        row.addView(makeKey("\uD83C\uDF10", 1.2f, true));                 // globe / switch
        row.addView(makeKey(page == LETTERS ? "123" : "ABC", 1.5f, true));
        row.addView(makeKey(",", 1f, false));
        row.addView(makeKey(" ", 4f, false));                            // space (mode label)
        row.addView(makeKey(".", 1f, false));
        row.addView(makeKey("\u21B5", 1.5f, true, true));                // return (accent)
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
        boolean letter = label.length() == 1 && Character.isLetter(label.charAt(0));
        String display = label;
        if (label.equals(" ")) {
            display = (inputMode == PINYIN) ? "拼音" : "ABC";
        } else if (shiftState != 0 && letter) {
            display = label.toUpperCase(Locale.ROOT);
        }

        final TextView key = new TextView(this);
        key.setText(display);
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
        final String shown = display;

        String alts = null;
        if (label.length() == 1) {
            char c = label.charAt(0);
            if (Character.isLetter(c)) {
                alts = LETTER_ALTS.get(Character.toLowerCase(c));
            } else {
                alts = PUNCT_ALTS.get(c);
            }
        }
        final boolean hasAlt = alts != null && !alts.isEmpty();
        final String altsFinal = alts == null ? "" : alts;

        if (hasAlt) {
            key.setOnTouchListener((v, e) -> {
                int act = e.getAction();
                if (act == MotionEvent.ACTION_DOWN) {
                    v.setAlpha(0.5f);
                    if (poppable) {
                        showTapPop(shown, v);
                    }
                    lpKey = v;
                    startLongPress(label.charAt(0), altsFinal);
                    return true;
                } else if (act == MotionEvent.ACTION_MOVE) {
                    if (longPressing) {
                        updateAltHover(e.getRawX());
                    }
                    return true;
                } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                    v.setAlpha(1f);
                    if (longPressing) {
                        String sel = altSelection();
                        hideAltPopup();
                        longPressing = false;
                        commitAlt(sel);
                    } else {
                        cancelLongPress();
                        if (poppable) {
                            hideTapPop();
                        }
                        onKey(label);
                    }
                    return true;
                }
                return false;
            });
        } else {
            key.setOnTouchListener((v, e) -> {
                int act = e.getAction();
                if (act == MotionEvent.ACTION_DOWN) {
                    v.setAlpha(0.5f);
                    if (poppable) {
                        showTapPop(shown, v);
                    }
                    return true;
                } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                    v.setAlpha(1f);
                    if (poppable) {
                        hideTapPop();
                    }
                    onKey(label);
                    return true;
                }
                return false;
            });
        }
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
            if (composing()) {
                composeBuffer.deleteCharAt(composeBuffer.length() - 1);
                record("[\u232B]");
                if (composeBuffer.length() == 0) {
                    clearComposition();
                } else {
                    updateComposition();
                }
            } else {
                handleBackspace();
            }
            return;
        }
        if ("\u21B5".equals(label)) { // return
            if (composing()) {
                selectFirstCandidate();
            }
            commitText("\n");
            record("[\u21B5]");
            return;
        }
        if ("\uD83C\uDF10".equals(label)) { // globe -> switch 中/EN
            if (composing()) {
                selectFirstCandidate();
            }
            inputMode = (inputMode == PINYIN) ? LATIN : PINYIN;
            if (inputMode == LATIN) {
                clearComposition();
            }
            rebuild();
            return;
        }
        if (label.equals("123") || label.equals("ABC")) { // page toggle
            page = (page == LETTERS) ? NUMBERS : LETTERS;
            if (page != LETTERS) {
                clearComposition();
            }
            rebuild();
            return;
        }
        if (label.equals("#+=")) { // numbers <-> symbols
            page = (page == NUMBERS) ? SYMBOLS : NUMBERS;
            rebuild();
            return;
        }
        if (label.equals(" ")) { // space
            if (composing()) {
                selectFirstCandidate();
            } else if (inputMode == LATIN) {
                flushWord();
            }
            commitText(" ");
            record(" ");
            return;
        }

        String out = label;
        if (shiftState != 0 && label.length() == 1 && Character.isLetter(label.charAt(0))) {
            out = label.toUpperCase(Locale.ROOT);
            if (shiftState == 1) {
                shiftState = 0;
            }
        }
        pressKey(out);
    }

    private void pressKey(String out) {
        if (composing()) {
            if (out.equals(" ") || out.equals(",") || out.equals(".")
                    || out.equals("\n")) {
                selectFirstCandidate();
            }
        }
        if (page == LETTERS && inputMode == PINYIN && out.length() == 1
                && Character.isLetter(out.charAt(0))) {
            composeBuffer.append(Character.toLowerCase(out.charAt(0)));
            updateComposition();
            return;
        }
        if (page == LETTERS && inputMode == LATIN && out.length() == 1
                && Character.isLetter(out.charAt(0))) {
            wordBuffer.append(out);
            scheduleFlush();
            return;
        }
        commitText(out);
        record(out);
    }

    private void commitAlt(String sel) {
        if (sel == null || sel.isEmpty()) {
            return;
        }
        if (page == LETTERS && inputMode == PINYIN && sel.length() == 1
                && Character.isLetter(sel.charAt(0))) {
            composeBuffer.append(Character.toLowerCase(sel.charAt(0)));
            updateComposition();
        } else {
            commitText(sel);
            record(sel);
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

    // --------------------------------------------------- pinyin composition

    private boolean composing() {
        return composeBuffer.length() > 0;
    }

    private void updateComposition() {
        List<PinyinEngine.Token> tokens = engine.segment(composeBuffer.toString());
        currentCandidates = engine.candidates(tokens);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.setComposingText(composeBuffer.toString(), 1);
        }
        renderCandidates(tokens, currentCandidates);
    }

    private void clearComposition() {
        composeBuffer.setLength(0);
        currentCandidates = null;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.finishComposingText();
        }
        if (candidateBar != null) {
            candidateBar.setVisibility(View.GONE);
        }
        if (candRow != null) {
            candRow.removeAllViews();
        }
    }

    private void selectFirstCandidate() {
        if (composeBuffer.length() == 0) {
            return;
        }
        List<PinyinEngine.Candidate> cands = currentCandidates;
        if (cands == null || cands.isEmpty()) {
            commitText(composeBuffer.toString());
            record(composeBuffer.toString());
            clearComposition();
            return;
        }
        pickCandidate(cands.get(0));
    }

    private void pickCandidate(PinyinEngine.Candidate c) {
        commitText(c.text);
        record(c.text);
        if (c.phrase) {
            clearComposition();
        } else {
            removeFirstSyllable();
            if (composeBuffer.length() > 0) {
                updateComposition();
            } else {
                clearComposition();
            }
        }
    }

    private void removeFirstSyllable() {
        List<PinyinEngine.Token> tokens = engine.segment(composeBuffer.toString());
        int end = engine.firstSyllableEnd(tokens);
        if (end > 0) {
            composeBuffer.delete(0, end);
        }
    }

    private void renderCandidates(List<PinyinEngine.Token> tokens,
                                  List<PinyinEngine.Candidate> cands) {
        candRow.removeAllViews();
        if (tokens.isEmpty()) {
            candidateBar.setVisibility(View.GONE);
            return;
        }
        String py = engine.displayPinyin(tokens);
        if (!py.isEmpty()) {
            TextView pyv = new TextView(this);
            pyv.setText(py);
            pyv.setTextSize(16);
            pyv.setTextColor(color(R.color.ime_cand_pinyin));
            pyv.setPadding(dp(14), 0, dp(10), 0);
            pyv.setGravity(Gravity.CENTER_VERTICAL);
            candRow.addView(pyv);
        }
        int limit = Math.min(cands.size(), 24);
        for (int i = 0; i < limit; i++) {
            final PinyinEngine.Candidate c = cands.get(i);
            TextView chip = new TextView(this);
            chip.setText(c.text);
            chip.setTextSize(18);
            chip.setTypeface(Typeface.DEFAULT, i == 0 ? Typeface.BOLD : Typeface.NORMAL);
            chip.setTextColor(color(R.color.ime_key_text));
            chip.setPadding(dp(14), 0, dp(14), 0);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setBackground(rounded(R.color.ime_bar_bg, 8));
            chip.setOnClickListener(v -> pickCandidate(c));
            if (i > 0 || !py.isEmpty()) {
                View div = new View(this);
                div.setBackgroundColor(color(R.color.ime_bar_border));
                candRow.addView(div, new LinearLayout.LayoutParams(dp(1), dp(24)));
            }
            candRow.addView(chip);
        }
        candidateBar.setVisibility(View.VISIBLE);
    }

    // ----------------------------------------------------- word aggregation

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
        String pkg = (info != null && info.packageName != null) ? info.packageName : "unknown";
        logManager.appendEntry(pkg, text);
    }

    // ------------------------------------------------------- long-press alts

    private void startLongPress(char base, String alts) {
        lpBase = String.valueOf(base);
        lpAlts = alts;
        longPressing = false;
        cancelLongPress();
        final char b = base;
        longPressRunnable = () -> {
            longPressing = true;
            hideTapPop();
            showAltPopup(b, lpAlts);
        };
        handler.postDelayed(longPressRunnable, LONG_PRESS_MS);
    }

    private void cancelLongPress() {
        if (longPressRunnable != null) {
            handler.removeCallbacks(longPressRunnable);
        }
    }

    private void showAltPopup(char base, String alts) {
        hideAltPopup();
        altCharList.clear();
        altCells.clear();
        altCharList.add(String.valueOf(base));
        for (char c : alts.toCharArray()) {
            altCharList.add(String.valueOf(c));
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(rounded(R.color.ime_alt_bg, 14));
        row.setPadding(dp(6), dp(4), dp(6), dp(4));
        cellW = dp(40);
        for (int i = 0; i < altCharList.size(); i++) {
            TextView cell = new TextView(this);
            cell.setText(altCharList.get(i));
            cell.setGravity(Gravity.CENTER);
            cell.setTextSize(22);
            cell.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            cell.setTextColor(color(R.color.ime_key_text));
            cell.setLayoutParams(new LinearLayout.LayoutParams(cellW, dp(46)));
            row.addView(cell);
            altCells.add(cell);
        }
        root.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] r = new int[2];
        int[] k = new int[2];
        root.getLocationOnScreen(r);
        if (lpKey != null) {
            lpKey.getLocationOnScreen(k);
        }
        int keyCx = k[0] - r[0] + (lpKey != null ? lpKey.getWidth() / 2 : 0);
        int keyTop = k[1] - r[1];
        int popW = row.getMeasuredWidth();
        int popH = row.getMeasuredHeight();
        altLeft = keyCx - popW / 2;
        altLeft = Math.max(dp(4), Math.min(altLeft, root.getWidth() - popW - dp(4)));
        int top = keyTop - popH - dp(10);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) row.getLayoutParams();
        lp.leftMargin = altLeft;
        lp.topMargin = Math.max(dp(2), top);
        row.setLayoutParams(lp);
        altRow = row;
        setAltHover(0);
    }

    private void updateAltHover(float rawX) {
        if (altRow == null) {
            return;
        }
        int[] r = new int[2];
        root.getLocationOnScreen(r);
        int x = (int) (rawX - r[0]);
        if (x < altLeft || x > altLeft + altRow.getMeasuredWidth()) {
            setAltHover(-1);
            return;
        }
        int idx = (x - altLeft) / cellW;
        if (idx < 0) {
            idx = 0;
        }
        if (idx >= altCells.size()) {
            idx = altCells.size() - 1;
        }
        setAltHover(idx);
    }

    private void setAltHover(int idx) {
        altHover = idx;
        for (int i = 0; i < altCells.size(); i++) {
            altCells.get(i).setBackgroundColor(
                    i == idx ? color(R.color.ime_alt_hi) : 0x00000000);
        }
    }

    private String altSelection() {
        if (altHover >= 0 && altHover < altCharList.size()) {
            return altCharList.get(altHover);
        }
        return lpBase;
    }

    private void hideAltPopup() {
        if (altRow != null) {
            root.removeView(altRow);
            altRow = null;
        }
        altCells.clear();
        altCharList.clear();
        altHover = -1;
    }

    // ------------------------------------------------------- iOS key-pop

    private void showTapPop(String text, View key) {
        if (tapPop == null || root == null) {
            return;
        }
        tapPop.setText(text);
        tapPop.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] r = new int[2];
        int[] k = new int[2];
        root.getLocationOnScreen(r);
        key.getLocationOnScreen(k);
        int cx = k[0] - r[0] + key.getWidth() / 2;
        int top = k[1] - r[1];
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) tapPop.getLayoutParams();
        lp.leftMargin = cx - tapPop.getMeasuredWidth() / 2;
        lp.topMargin = top - tapPop.getMeasuredHeight() - dp(6);
        tapPop.setLayoutParams(lp);
        tapPop.setVisibility(View.VISIBLE);
        tapPop.setAlpha(1f);
        tapPop.setScaleX(1f);
        tapPop.setScaleY(1f);
        tapPop.animate()
                .alpha(0f)
                .setStartDelay(140)
                .setDuration(120)
                .withEndAction(() -> tapPop.setVisibility(View.GONE))
                .start();
    }

    private void hideTapPop() {
        if (tapPop != null) {
            tapPop.setVisibility(View.GONE);
        }
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
