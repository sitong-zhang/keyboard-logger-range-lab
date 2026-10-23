package com.keyrecord.app;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Offline pinyin engine built from high-quality open-source dictionaries:
 *   - rime-ice (iDvel/rime-ice, GPL-3.0) phrase dictionaries (base/ext/tencent/others)
 *   - pypinyin phrase + char dictionaries
 *   - a small curated seed list of the most common words
 *
 * Assets (tab-separated):
 *   - pinyin_chars.txt    : "syllable<TAB>char char char ..."
 *   - pinyin_phrases.txt  : "pinyin<TAB>phrase phrase ..."   (keys sorted alphabetically)
 *
 * Candidate generation mimics the iOS pinyin candidate bar:
 *   - phrase candidates whose pinyin equals the typed buffer (exact), then
 *   - phrase candidates whose pinyin STARTS WITH the buffer (prefix auto-complete), then
 *   - single-character candidates for the first syllable.
 */
public class PinyinEngine {

    private static final String TAG = "PinyinEngine";
    private final Map<String, String[]> charMap = new HashMap<>();
    private final Map<String, String[]> phraseMap = new HashMap<>();
    private String[] sortedPhraseKeys = new String[0];

    public void load(Context ctx) {
        loadLines(ctx, "pinyin_chars.txt", charMap);
        loadLines(ctx, "pinyin_phrases.txt", phraseMap);
        loadSyllables(ctx);
        syllableSet.addAll(charMap.keySet()); // char syllables are valid too
        sortedPhraseKeys = phraseMap.keySet().toArray(new String[0]);
        Arrays.sort(sortedPhraseKeys);
    }

    private void loadLines(Context ctx, String file, Map<String, String[]> out) {
        try {
            InputStream is = ctx.getAssets().open(file);
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                int t = line.indexOf('\t');
                if (t < 0) continue;
                String key = line.substring(0, t);
                String[] vals = line.substring(t + 1).split(" ");
                out.put(key, vals);
            }
            br.close();
        } catch (IOException e) {
            Log.w(TAG, "load " + file + " failed", e);
        }
    }

    public static class Token {
        public final String text;
        public final boolean syllable;
        Token(String text, boolean syllable) { this.text = text; this.syllable = syllable; }
    }

    /** A candidate on the bar. consumeAll=true means committing clears the buffer. */
    public static class Candidate {
        public final String text;
        public final boolean consumeAll;
        Candidate(String text, boolean consumeAll) { this.text = text; this.consumeAll = consumeAll; }
    }

    /** Forward max-match segmentation of a typed pinyin string. */
    public List<Token> segment(String input) {
        List<Token> tokens = new ArrayList<>();
        int i = 0, n = input.length();
        while (i < n) {
            int match = 0;
            int maxL = Math.min(7, n - i);
            for (int L = maxL; L >= 1; L--) {
                if (isSyllable(input.substring(i, i + L))) { match = L; break; }
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

    private boolean isSyllable(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c < 'a' || c > 'z') && c != 'v') return false;
        }
        // cheap membership check against the phrase/char key space
        return charMap.containsKey(s) || phraseMap.containsKey(s)
                || syllableSet.contains(s);
    }

    private final Set<String> syllableSet = new java.util.HashSet<>();

    public void loadSyllables(Context ctx) {
        try {
            InputStream is = ctx.getAssets().open("pinyin_syllables.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) syllableSet.add(line);
            }
            br.close();
        } catch (IOException e) {
            Log.w(TAG, "load syllables failed", e);
        }
    }

    private String firstSyllable(List<Token> tokens) {
        for (Token t : tokens) {
            if (t.syllable) return t.text;
        }
        return null;
    }

    /** Full pinyin of the typed buffer (syllables joined, space separated). */
    public String displayPinyin(List<Token> tokens) {
        StringBuilder sb = new StringBuilder();
        for (Token t : tokens) {
            if (t.syllable) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(t.text);
            }
        }
        return sb.toString();
    }

    /**
     * Build the candidate list for the typed buffer.
     * Returns ordered: exact phrases, prefix auto-complete phrases, then single chars.
     */
    public List<Candidate> getCandidates(String buffer) {
        List<Candidate> out = new ArrayList<>();
        if (buffer == null || buffer.isEmpty()) return out;
        List<Token> tokens = segment(buffer);
        String whole = displayPinyin(tokens).replace(" ", "");
        if (whole.isEmpty()) return out;

        // 1) exact phrase match (full buffer is a complete pinyin)
        String[] exact = phraseMap.get(whole);
        if (exact != null) {
            for (String p : exact) {
                if (!p.isEmpty()) out.add(new Candidate(p, true));
            }
        }
        // 2) prefix auto-complete (keys starting with the buffer)
        int[] range = prefixRange(whole);
        for (int i = range[0]; i < range[1]; i++) {
            String key = sortedPhraseKeys[i];
            if (key.equals(whole)) continue; // already as exact
            String[] vals = phraseMap.get(key);
            if (vals != null && vals.length > 0 && !vals[0].isEmpty()) {
                out.add(new Candidate(vals[0], true));
            }
            if (out.size() >= 18) break;
        }
        // 3) single-character candidates for the first syllable
        String first = firstSyllable(tokens);
        if (first != null) {
            String[] ch = charMap.get(first);
            if (ch != null) {
                for (String c : ch) {
                    if (!c.isEmpty()) out.add(new Candidate(c, false));
                    if (out.size() >= 30) break;
                }
            }
        }
        return out;
    }

    /** [start, end) indices in sortedPhraseKeys for keys starting with prefix. */
    private int[] prefixRange(String prefix) {
        int lo = lowerBound(prefix);
        int hi = lo;
        while (hi < sortedPhraseKeys.length && sortedPhraseKeys[hi].startsWith(prefix)) hi++;
        return new int[]{lo, hi};
    }

    /** First index where key >= prefix (binary search). */
    private int lowerBound(String prefix) {
        int lo = 0, hi = sortedPhraseKeys.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sortedPhraseKeys[mid].compareTo(prefix) < 0) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /** Remove the first syllable from a buffer, leaving the rest to keep composing. */
    public String consumeFirstSyllable(String buffer) {
        List<Token> tokens = segment(buffer);
        int off = 0;
        for (Token t : tokens) {
            if (t.syllable) return buffer.substring(off + t.text.length());
            off += t.text.length();
        }
        return buffer;
    }
}
