package com.keyrecord.app;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tiny offline pinyin engine.
 *
 * Loads three asset files built from the pypinyin dictionaries
 * (pinyin_dict.json / phrases_dict.json) plus a corpus frequency table:
 *   - pinyin_chars.txt   : "syllable<TAB>char char char ..."
 *   - pinyin_phrases.txt : "pinyin<TAB>phrase phrase ..."
 *   - pinyin_syllables.txt : one valid syllable per line
 *
 * It segments a typed pinyin string into syllables (forward max-match) and
 * produces a candidate list that mimics the iOS candidate bar: full-phrase
 * matches first, then single-character candidates for the first syllable.
 */
public class PinyinEngine {

    private static final String TAG = "PinyinEngine";
    private final Map<String, String[]> charMap = new HashMap<>();
    private final Map<String, String[]> phraseMap = new HashMap<>();
    private final Set<String> syllables = new java.util.HashSet<>();

    public void load(Context ctx) {
        loadLines(ctx, "pinyin_chars.txt", charMap);
        loadLines(ctx, "pinyin_phrases.txt", phraseMap);
        try {
            InputStream is = ctx.getAssets().open("pinyin_syllables.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    syllables.add(line);
                }
            }
            br.close();
        } catch (IOException e) {
            Log.w(TAG, "load syllables failed", e);
        }
    }

    private void loadLines(Context ctx, String file, Map<String, String[]> out) {
        try {
            InputStream is = ctx.getAssets().open(file);
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                int t = line.indexOf('\t');
                if (t < 0) {
                    continue;
                }
                String key = line.substring(0, t);
                String[] vals = line.substring(t + 1).split(" ");
                out.put(key, vals);
            }
            br.close();
        } catch (IOException e) {
            Log.w(TAG, "load " + file + " failed", e);
        }
    }

    /** A segmented unit: either a pinyin syllable or a literal character. */
    public static class Token {
        public final String text;
        public final boolean syllable;

        Token(String text, boolean syllable) {
            this.text = text;
            this.syllable = syllable;
        }
    }

    /** A candidate shown on the bar. phrase=true consumes all syllables. */
    public static class Candidate {
        public final String text;
        public final boolean phrase;

        Candidate(String text, boolean phrase) {
            this.text = text;
            this.phrase = phrase;
        }
    }

    /** Forward max-match segmentation of a typed pinyin string. */
    public List<Token> segment(String input) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = input.length();
        while (i < n) {
            int match = 0;
            int maxL = Math.min(7, n - i);
            for (int L = maxL; L >= 1; L--) {
                String sub = input.substring(i, i + L);
                if (syllables.contains(sub)) {
                    match = L;
                    break;
                }
            }
            if (match > 0) {
                tokens.add(new Token(input.substring(i, i + match), true));
                i += match;
            } else {
                tokens.add(new Token(input.substring(i, i + 1), false));
                i += 1;
            }
        }
        return tokens;
    }

    /** Build the candidate list for the current composition tokens. */
    public List<Candidate> candidates(List<Token> tokens) {
        List<Candidate> out = new ArrayList<>();
        StringBuilder whole = new StringBuilder();
        int firstSyllable = -1;
        for (int k = 0; k < tokens.size(); k++) {
            if (tokens.get(k).syllable) {
                whole.append(tokens.get(k).text);
                if (firstSyllable < 0) {
                    firstSyllable = k;
                }
            }
        }
        if (firstSyllable < 0) {
            return out;
        }
        String[] ph = phraseMap.get(whole.toString());
        if (ph != null) {
            for (String p : ph) {
                if (!p.isEmpty()) {
                    out.add(new Candidate(p, true));
                }
            }
        }
        String first = tokens.get(firstSyllable).text;
        String[] ch = charMap.get(first);
        if (ch != null) {
            for (String c : ch) {
                if (!c.isEmpty()) {
                    out.add(new Candidate(c, false));
                }
            }
        }
        return out;
    }

    /** Human-readable pinyin with syllable separators (e.g. "ni hao"). */
    public String displayPinyin(List<Token> tokens) {
        StringBuilder sb = new StringBuilder();
        for (Token t : tokens) {
            if (t.syllable) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(t.text);
            }
        }
        return sb.toString();
    }

    /** Return the char offset (in the raw string) just past the first syllable. */
    public int firstSyllableEnd(List<Token> tokens) {
        int off = 0;
        for (Token t : tokens) {
            if (t.syllable) {
                return off + t.text.length();
            }
            off += t.text.length();
        }
        return -1;
    }
}
