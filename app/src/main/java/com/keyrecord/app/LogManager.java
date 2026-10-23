package com.keyrecord.app;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Local-only log store. Every keystroke is written to this app's private
 * storage and never leaves the device (no network permission is requested).
 *
 * Line format (tab separated):
 *   yyyy-MM-dd HH:mm:ss \t <package> \t <text>
 * Service lifecycle events use the reserved package token "__EVENT__".
 */
public class LogManager {

    public static final class LogEntry {
        public final long time;
        public final String pkg;
        public final String text;
        public final boolean event;

        public LogEntry(long time, String pkg, String text, boolean event) {
            this.time = time;
            this.pkg = pkg;
            this.text = text;
            this.event = event;
        }
    }

    public static final class AppStat {
        public final String pkg;
        public final int count;
        public AppStat(String pkg, int count) {
            this.pkg = pkg;
            this.count = count;
        }
    }

    public static final class Stats {
        public int keystrokes;
        public int apps;
        public long since;
        public java.util.List<AppStat> topApps = new java.util.ArrayList<>();
    }

    private static final String EVENT_TOKEN = "__EVENT__";
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    private static LogManager instance;
    private final Context ctx;
    private final File internalFile;
    private String lastLine = "";

    private LogManager(Context c) {
        ctx = c.getApplicationContext();
        internalFile = new File(ctx.getFilesDir(), "keylog.txt");
    }

    public static synchronized LogManager getInstance(Context c) {
        if (instance == null) {
            instance = new LogManager(c);
        }
        return instance;
    }

    public synchronized void appendEntry(String pkg, String text) {
        write(FMT.format(new Date()), pkg, text);
    }

    public synchronized void appendEvent(String message) {
        write(FMT.format(new Date()), EVENT_TOKEN, message);
    }

    private void write(String ts, String pkg, String text) {
        String full = ts + "\t" + pkg + "\t" + text;
        if (full.equals(lastLine)) {
            return;
        }
        lastLine = full;
        try (FileWriter fw = new FileWriter(internalFile, true)) {
            fw.append(full).append("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized List<LogEntry> readEntries() {
        List<LogEntry> out = new ArrayList<>();
        if (!internalFile.exists()) {
            return out;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(internalFile))) {
            String l;
            while ((l = br.readLine()) != null) {
                if (l.isEmpty()) {
                    continue;
                }
                String[] p = l.split("\t", 3);
                if (p.length < 3) {
                    continue;
                }
                long t = parseTime(p[0]);
                boolean event = EVENT_TOKEN.equals(p[1]);
                out.add(new LogEntry(t, p[1], p[2], event));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return out;
    }

    private long parseTime(String s) {
        try {
            return FMT.parse(s).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    public synchronized Stats getStats() {
        Stats s = new Stats();
        HashSet<String> apps = new HashSet<>();
        java.util.HashMap<String, Integer> counts = new java.util.HashMap<>();
        for (LogEntry e : readEntries()) {
            if (e.event) {
                continue;
            }
            s.keystrokes++;
            apps.add(e.pkg);
            counts.put(e.pkg, counts.getOrDefault(e.pkg, 0) + 1);
            if (s.since == 0 || e.time < s.since) {
                s.since = e.time;
            }
        }
        s.apps = apps.size();
        java.util.List<AppStat> list = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, Integer> en : counts.entrySet()) {
            list.add(new AppStat(en.getKey(), en.getValue()));
        }
        list.sort((a, b) -> b.count - a.count);
        s.topApps = list;
        return s;
    }

    /** Returns up to n most recent (non-event) entries, newest first. */
    public synchronized List<LogEntry> getRecent(int n) {
        List<LogEntry> all = readEntries();
        List<LogEntry> recent = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && recent.size() < n; i--) {
            if (!all.get(i).event) {
                recent.add(all.get(i));
            }
        }
        return recent;
    }

    public synchronized void clear() {
        if (internalFile.exists()) {
            internalFile.delete();
        }
        lastLine = "";
    }

    public synchronized File export() {
        File dir = ctx.getExternalFilesDir("export");
        if (dir != null) {
            dir.mkdirs();
        }
        File out = new File(dir, "keylog_export.txt");
        try (FileReader fr = new FileReader(internalFile);
             FileWriter fw = new FileWriter(out)) {
            char[] buf = new char[1024];
            int n;
            while ((n = fr.read(buf)) != -1) {
                fw.write(buf, 0, n);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return out;
    }
}
