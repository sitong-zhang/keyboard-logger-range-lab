package com.keyrecord.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Built-in keyboard that replicates the Apple iOS system keyboard:
 *  - pixel-accurate iOS geometry (key height/width/gap/radius from reverse-engineered ratios)
 *  - iconic iOS palette: light-gray keys, darker special keys, blue accent, wide space bar
 *  - bottom row: globe | 123 | space (wide) | mic | return  (matches iOS exactly)
 *  - iOS candidate bar (联想条) above the keys with pinyin + live candidates
 *  - magnified key-pop on press, long-press symbol/letter popups
 *  - globe toggles 中文拼音 (PINYIN) and English (LATIN)
 *  - microphone triggers on-device/system speech dictation (no INTERNET permission used)
 *
 * Every committed character / word is written locally via LogManager at commit time
 * (real-time, works in floating windows / split screen). No network is used.
 */
public class TraceInputMethod extends InputMethodService {

    private static final long LONG_PRESS_MS = 350;
    private static final int LETTERS = 0, NUMBERS = 1, SYMBOLS = 2;
    private static final int PINYIN = 0, LATIN = 1;

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
    private TextView keyPop;

    // geometry (px), computed from screen width using iOS ratios
    private int screenW;
    private float keyW, gap, keyH, radius, specialW;

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
    private int altLeft = 0, cellW = 0, altHover = -1;
    private String lpBase = "";
    private View lpKey = null;

    // voice
    private SpeechRecognizer speechRecognizer;
    private boolean listening = false;

    @Override
    public void onCreate() {
        super.onCreate();
        logManager = LogManager.getInstance(this);
        engine = new PinyinEngine();
        engine.load(this);
    }

    @Override
    public View onCreateInputView() {
        computeGeometry();
        root = new FrameLayout(this);
        root.setBackgroundColor(color(R.color.ime_bg));

        // candidate bar (联想条) — top strip above the keys
        candidateBar = new HorizontalScrollView(this);
        candidateBar.setHorizontalScrollBarEnabled(false);
        candidateBar.setBackgroundColor(color(R.color.ime_bar_bg));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) keyH);
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
        keyboardView.setGravity(Gravity.CENTER_HORIZONTAL);
        keyboardView.setPadding(dp(4), dp(6), dp(4), dp(8));
        FrameLayout.LayoutParams kp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        kp.topMargin = (int) keyH + dp(4);
        keyboardView.setLayoutParams(kp);
        root.addView(keyboardView);

        keyPop = new TextView(this);
        keyPop.setTextSize(28);
        keyPop.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        keyPop.setTextColor(color(R.color.ime_key_text));
        keyPop.setBackground(rounded(R.color.ime_pop_bg, 10));
        keyPop.setGravity(Gravity.CENTER);
        keyPop.setPadding(dp(16), dp(8), dp(16), dp(8));
        keyPop.setVisibility(View.GONE);
        root.addView(keyPop, new FrameLayout.LayoutParams(
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

    @Override
    public void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        super.onDestroy();
    }

    // ----------------------------------------------------------- geometry

    private void computeGeometry() {
        screenW = getResources().getDisplayMetrics().widthPixels;
        float sidePad = dp(6);
        float units = 10f + 9f * (10f / 64f); // 10 keys + 9 gaps(10/64)
        keyW = (screenW - 2 * sidePad) / units;
        gap = keyW * (10f / 64f);
        keyH = keyW * (78f / 64f);
        radius = keyW * (8f / 64f);
        specialW = keyW * (81f / 64f);
    }

    // ------------------------------------------------------------- build UI

    private void rebuild() {
        hideKeyPop();
        hideAltPopup();
        keyboardView.removeAllViews();

        if (page == LETTERS) {
            addRow("qwertyuiop");
            addRow("asdfghjkl");
            addSideRow("zxcvbnm");
        } else if (page == NUMBERS) {
            addRow("1234567890");
            addRow("-/:;()$&@\"'.");
            addSymbolRow("#+=", ",.;?!'", "ABC");
        } else {
            addRow("[]{}#%^*+=");
            addRow("_\\|~<>€£¥•");
            addSymbolRow("123", ",.;?!'", "ABC");
        }
        addBottomRow();
    }

    private void addRow(String keys) {
        LinearLayout row = newRow();
        for (int i = 0; i < keys.length(); i++) {
            row.addView(makeKey(String.valueOf(keys.charAt(i)), keyW, false));
        }
        keyboardView.addView(row);
    }

    private void addSideRow(String keys) {
        LinearLayout row = newRow();
        row.addView(makeKey("\u21E7", specialW, true));          // shift
        for (int i = 0; i < keys.length(); i++) {
            row.addView(makeKey(String.valueOf(keys.charAt(i)), keyW, false));
        }
        row.addView(makeKey("\u232B", specialW, true));          // delete
        keyboardView.addView(row);
    }

    /** numbers/symbols third row: [special toggle] [keys] [ABC] [delete] */
    private void addSymbolRow(String leftLabel, String midKeys, String rightLabel) {
        LinearLayout row = newRow();
        row.addView(makeKey(leftLabel, specialW, true));
        for (int i = 0; i < midKeys.length(); i++) {
            row.addView(makeKey(String.valueOf(midKeys.charAt(i)), keyW, false));
        }
        row.addView(makeKey(rightLabel, specialW, true));
        row.addView(makeKey("\u232B", specialW, true));
        keyboardView.addView(row);
    }

    /** Bottom row (iOS): globe | 123/ABC | space (wide) | mic | return. */
    private void addBottomRow() {
        LinearLayout row = newRow();
        float avail = screenW - 2 * dp(6);
        float spaceW = avail - 4 * specialW - 4 * gap;
        if (spaceW < keyW) spaceW = keyW;

        row.addView(makeKey("\uD83C\uDF10", specialW, true));                    // globe
        row.addView(makeKey(page == LETTERS ? "123" : "ABC", specialW, true));  // 123 / ABC
        row.addView(makeKey(" ", spaceW, false));                                // space (mode label)
        row.addView(makeKey("\uD83C\uDFA4", specialW, true));                    // mic
        row.addView(makeKey("\u21B5", specialW, true, true));                   // return (accent)
        keyboardView.addView(row);
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) keyH);
        lp.topMargin = (int) (gap * 0.5f);
        row.setLayoutParams(lp);
        return row;
    }

    private TextView makeKey(String label, float widthPx, boolean special) {
        return makeKey(label, widthPx, special, false);
    }

    private TextView makeKey(String label, float widthPx, boolean special, boolean accent) {
        boolean letter = label.length() == 1 && Character.isLetter(label.charAt(0));
        String display = label;
        if (label.equals(" ")) {
            display = ""; // iOS space bar is blank
        } else if (shiftState != 0 && letter) {
            display = label.toUpperCase(Locale.ROOT);
        }

        final TextView key = new TextView(this);
        key.setText(display);
        key.setGravity(Gravity.CENTER);
        key.setMaxLines(1);
        key.setSingleLine(true);
        key.setTextSize(special || label.length() > 2 ? 15 : 22);
        key.setTypeface(Typeface.DEFAULT, (special && label.length() <= 2)
                ? Typeface.NORMAL : Typeface.BOLD);
        key.setTextColor(accent ? 0xFFFFFFFF : color(R.color.ime_key_text));
        key.setBackground(accent
                ? rounded(R.color.ime_accent, (int) radius)
                : rounded(special ? R.color.ime_key_special : R.color.ime_key_bg, (int) radius));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) widthPx, (int) keyH);
        lp.leftMargin = (int) (gap * 0.5f);
        lp.rightMargin = (int) (gap * 0.5f);
        key.setLayoutParams(lp);

        final String shown = display;
        String alts = null;
        if (label.length() == 1) {
            char c = label.charAt(0);
            alts = Character.isLetter(c) ? LETTER_ALTS.get(Character.toLowerCase(c)) : PUNCT_ALTS.get(c);
        }
        final boolean hasAlt = alts != null && !alts.isEmpty();
        final String altsFinal = alts == null ? "" : alts;
        final boolean poppable = !special && !accent && label.length() == 1 && !label.equals(" ");

        if (hasAlt) {
            key.setOnTouchListener((v, e) -> {
                int act = e.getAction();
                if (act == MotionEvent.ACTION_DOWN) {
                    v.setAlpha(0.6f);
                    if (poppable) showKeyPop(shown, v);
                    lpKey = v;
                    startLongPress(label.charAt(0), altsFinal);
                    return true;
                } else if (act == MotionEvent.ACTION_MOVE) {
                    if (longPressing) updateAltHover(e.getRawX());
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
                        hideKeyPop();
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
                    v.setAlpha(0.6f);
                    if (poppable) showKeyPop(shown, v);
                    return true;
                } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                    v.setAlpha(1f);
                    hideKeyPop();
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
                if (composeBuffer.length() == 0) clearComposition();
                else updateComposition();
            } else {
                handleBackspace();
            }
            return;
        }
        if ("\u21B5".equals(label)) { // return
            if (composing()) selectFirstCandidate();
            commitText("\n");
            record("[\u21B5]");
            return;
        }
        if ("\uD83C\uDF10".equals(label)) { // globe -> 中/EN
            if (composing()) selectFirstCandidate();
            inputMode = (inputMode == PINYIN) ? LATIN : PINYIN;
            if (inputMode == LATIN) clearComposition();
            rebuild();
            return;
        }
        if ("\uD83C\uDFA4".equals(label)) { // mic -> voice
            onMic();
            return;
        }
        if (label.equals("123") || label.equals("ABC")) { // 123 <-> letters
            if (composing()) selectFirstCandidate();
            if (label.equals("123") && page == SYMBOLS) {
                page = NUMBERS;
            } else {
                page = LETTERS;
            }
            clearComposition();
            rebuild();
            return;
        }
        if (label.equals("#+=")) {
            page = SYMBOLS; rebuild(); return;
        }
        if (label.equals(" ")) { // space
            if (composing()) {
                selectFirstCandidate();
            } else if (inputMode == LATIN) {
                flushWord();
                commitText(" ");
                record(" ");
            } else {
                commitText(" ");
                record(" ");
            }
            return;
        }

        String out = label;
        if (shiftState != 0 && label.length() == 1 && Character.isLetter(label.charAt(0))) {
            out = label.toUpperCase(Locale.ROOT);
            if (shiftState == 1) shiftState = 0;
        }
        pressKey(out);
    }

    private void pressKey(String out) {
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
        if (sel == null || sel.isEmpty()) return;
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
        if (ic != null) ic.deleteSurroundingText(1, 0);
        record("[\u232B]");
    }

    private void commitText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(text, 1);
    }

    // ------------------------------------------------ pinyin composition

    private boolean composing() { return composeBuffer.length() > 0; }

    private void updateComposition() {
        String buffer = composeBuffer.toString();
        currentCandidates = engine.getCandidates(buffer);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.setComposingText(buffer, 1);
        renderCandidates(buffer, currentCandidates);
    }

    private void clearComposition() {
        composeBuffer.setLength(0);
        currentCandidates = null;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.finishComposingText();
        if (candidateBar != null) candidateBar.setVisibility(View.GONE);
        if (candRow != null) candRow.removeAllViews();
    }

    private void selectFirstCandidate() {
        if (composeBuffer.length() == 0) return;
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
        if (c.consumeAll) {
            clearComposition();
        } else {
            composeBuffer.replace(0, composeBuffer.length(),
                    engine.consumeFirstSyllable(composeBuffer.toString()));
            if (composeBuffer.length() > 0) updateComposition();
            else clearComposition();
        }
    }

    private void renderCandidates(String buffer, List<PinyinEngine.Candidate> cands) {
        candRow.removeAllViews();
        if (cands == null || cands.isEmpty()) {
            candidateBar.setVisibility(View.GONE);
            return;
        }
        TextView pyv = new TextView(this);
        pyv.setText(buffer);
        pyv.setTextSize(17);
        pyv.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        pyv.setTextColor(color(R.color.ime_cand_pinyin));
        pyv.setPadding(dp(16), 0, dp(10), 0);
        pyv.setGravity(Gravity.CENTER_VERTICAL);
        candRow.addView(pyv);

        int limit = Math.min(cands.size(), 20);
        for (int i = 0; i < limit; i++) {
            final PinyinEngine.Candidate c = cands.get(i);
            TextView chip = new TextView(this);
            chip.setText(c.text);
            chip.setTextSize(19);
            chip.setTypeface(Typeface.DEFAULT, i == 0 ? Typeface.BOLD : Typeface.NORMAL);
            chip.setTextColor(color(R.color.ime_key_text));
            chip.setPadding(dp(14), 0, dp(14), 0);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setOnClickListener(v -> pickCandidate(c));
            View div = new View(this);
            div.setBackgroundColor(color(R.color.ime_bar_border));
            candRow.addView(div, new LinearLayout.LayoutParams(dp(1), dp(26)));
            candRow.addView(chip);
        }
        candidateBar.setVisibility(View.VISIBLE);
        candidateBar.post(() -> candidateBar.fullScroll(View.FOCUS_LEFT));
    }

    // ----------------------------------------------------- word aggregation

    private void scheduleFlush() {
        handler.removeCallbacks(flushRunnable);
        handler.postDelayed(flushRunnable, 900);
    }

    private void flushWord() {
        handler.removeCallbacks(flushRunnable);
        if (wordBuffer.length() == 0) return;
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
        longPressing = false;
        cancelLongPress();
        final char b = base;
        longPressRunnable = () -> {
            longPressing = true;
            hideKeyPop();
            showAltPopup(b, alts);
        };
        handler.postDelayed(longPressRunnable, LONG_PRESS_MS);
    }

    private void cancelLongPress() {
        if (longPressRunnable != null) handler.removeCallbacks(longPressRunnable);
    }

    private void showAltPopup(char base, String alts) {
        hideAltPopup();
        altCharList.clear();
        altCells.clear();
        altCharList.add(String.valueOf(base));
        for (char c : alts.toCharArray()) altCharList.add(String.valueOf(c));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(rounded(R.color.ime_alt_bg, (int) radius + 4));
        row.setPadding(dp(6), dp(4), dp(6), dp(4));
        cellW = (int) (keyW * 0.9f);
        for (int i = 0; i < altCharList.size(); i++) {
            TextView cell = new TextView(this);
            cell.setText(altCharList.get(i));
            cell.setGravity(Gravity.CENTER);
            cell.setTextSize(22);
            cell.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            cell.setTextColor(color(R.color.ime_key_text));
            cell.setLayoutParams(new LinearLayout.LayoutParams(cellW, (int) (keyH * 0.85f)));
            row.addView(cell);
            altCells.add(cell);
        }
        root.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] r = new int[2], k = new int[2];
        root.getLocationOnScreen(r);
        if (lpKey != null) lpKey.getLocationOnScreen(k);
        int keyCx = k[0] - r[0] + (lpKey != null ? lpKey.getWidth() / 2 : 0);
        int keyTop = k[1] - r[1];
        int popW = row.getMeasuredWidth();
        int popH = row.getMeasuredHeight();
        altLeft = keyCx - popW / 2;
        altLeft = Math.max(dp(4), Math.min(altLeft, root.getWidth() - popW - dp(4)));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) row.getLayoutParams();
        lp.leftMargin = altLeft;
        lp.topMargin = Math.max(dp(2), keyTop - popH - dp(8));
        row.setLayoutParams(lp);
        altRow = row;
        setAltHover(0);
    }

    private void updateAltHover(float rawX) {
        if (altRow == null) return;
        int[] r = new int[2];
        root.getLocationOnScreen(r);
        int x = (int) (rawX - r[0]);
        if (x < altLeft || x > altLeft + altRow.getMeasuredWidth()) { setAltHover(-1); return; }
        int idx = (x - altLeft) / cellW;
        idx = Math.max(0, Math.min(altCells.size() - 1, idx));
        setAltHover(idx);
    }

    private void setAltHover(int idx) {
        altHover = idx;
        for (int i = 0; i < altCells.size(); i++) {
            altCells.get(i).setBackgroundColor(i == idx ? color(R.color.ime_alt_hi) : 0x00000000);
        }
    }

    private String altSelection() {
        if (altHover >= 0 && altHover < altCharList.size()) return altCharList.get(altHover);
        return lpBase;
    }

    private void hideAltPopup() {
        if (altRow != null) { root.removeView(altRow); altRow = null; }
        altCells.clear();
        altCharList.clear();
        altHover = -1;
    }

    // ------------------------------------------------------- iOS key-pop

    private void showKeyPop(String text, View key) {
        if (keyPop == null || root == null) return;
        keyPop.setText(text);
        keyPop.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] r = new int[2], k = new int[2];
        root.getLocationOnScreen(r);
        key.getLocationOnScreen(k);
        int cx = k[0] - r[0] + key.getWidth() / 2;
        int top = k[1] - r[1];
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) keyPop.getLayoutParams();
        lp.leftMargin = cx - keyPop.getMeasuredWidth() / 2;
        lp.topMargin = top - keyPop.getMeasuredHeight() - (int) (keyH * 0.35f);
        keyPop.setLayoutParams(lp);
        keyPop.setVisibility(View.VISIBLE);
        keyPop.setAlpha(1f);
        keyPop.setScaleX(1f);
        keyPop.setScaleY(1f);
    }

    private void hideKeyPop() {
        if (keyPop != null) keyPop.setVisibility(View.GONE);
    }

    // ----------------------------------------------------------- voice input

    private void onMic() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Intent i = new Intent(this, PermissionActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            showToast("请授权麦克风以使用语音输入");
            return;
        }
        startVoice();
    }

    private void startVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showToast("设备不支持语音识别");
            return;
        }
        if (speechRecognizer != null) speechRecognizer.destroy();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { setListening(true); }
            @Override public void onBeginningOfSpeech() { setListening(true); }
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { setListening(false); }
            @Override public void onError(int error) { setListening(false); }
            @Override public void onResults(Bundle results) {
                setListening(false);
                List<String> matches = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0);
                    commitText(text);
                    record(text);
                    if (inputMode == PINYIN && composing()) clearComposition();
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toString());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speechRecognizer.startListening(intent);
    }

    private void setListening(boolean on) {
        listening = on;
        if (on) showToast("聆听中…说出内容");
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ utils

    private GradientDrawable rounded(int colorRes, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color(colorRes));
        d.setCornerRadius(radiusPx);
        return d;
    }

    private int color(int res) { return getResources().getColor(res, getTheme()); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
