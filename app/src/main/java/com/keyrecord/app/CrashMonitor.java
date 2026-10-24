package com.keyrecord.app;

import android.content.Context;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Lightweight local crash monitor. Catches any uncaught exception in this
 * process, appends the full stack trace to a local file, then chains to the
 * previous handler so the system still sees the crash.
 *
 * The log never leaves the device; it can be viewed in CrashActivity.
 */
public final class CrashMonitor {

    private static final long MAX_BYTES = 512 * 1024; // keep the file small
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    private CrashMonitor() {
    }

    public static void install(Context context) {
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler prev =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                try {
                    save(app, thread, throwable);
                } catch (Throwable ignored) {
                    // Never let the monitor itself break crash handling.
                }
                if (prev != null) {
                    prev.uncaughtException(thread, throwable);
                }
            }
        });
    }

    /** Public hook for non-fatal but important startup errors. */
    public static void saveApplicationError(Context context, Throwable throwable) {
        save(context, Thread.currentThread(), throwable);
    }

    /** Continuously capture device logs (incl. native "Fatal signal") to a
     *  local file so the real cause can be read in 崩溃日志 on a real device. */
    public static void startLogcatCapture(Context context) {
        try {
            final File f = logcatFile(context.getApplicationContext());
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Clear first, then stream to a rotating file.
                        new ProcessBuilder("logcat", "-c").redirectErrorStream(true)
                                .start().waitFor();
                        new ProcessBuilder("logcat", "-f", f.getAbsolutePath(),
                                "-v", "time", "-n", "3", "-r", "500", "*:W")
                                .redirectErrorStream(true)
                                .start().waitFor();
                    } catch (Throwable ignored) {
                    }
                }
            }, "logcat-capture").start();
        } catch (Throwable ignored) {
        }
    }

    public static String readLogcat(Context context) {
        File f = logcatFile(context.getApplicationContext());
        if (!f.exists()) {
            return null;
        }
        try (FileReader r = new FileReader(f)) {
            char[] buf = new char[(int) Math.min(f.length(), MAX_BYTES)];
            int n = r.read(buf);
            return new String(buf, 0, Math.max(n, 0));
        } catch (IOException e) {
            return null;
        }
    }

    private static void save(Context app, Thread thread, Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));

        StringBuilder sb = new StringBuilder();
        sb.append("==== 崩溃时间：").append(FMT.format(new Date())).append("\n");
        sb.append("线程：").append(thread.getName()).append("\n");
        try {
            sb.append("版本：").append(
                    app.getPackageManager()
                            .getPackageInfo(app.getPackageName(), 0)
                            .versionName).append("\n");
        } catch (Exception ignored) {
        }
        sb.append("---- 堆栈 ----\n").append(sw.toString()).append("\n\n");

        File f = crashFile(app);
        // Truncate from the head if the file grows too large.
        if (f.length() > MAX_BYTES) {
            String tail = readHead(f, (int) (MAX_BYTES / 2));
            try (FileWriter w = new FileWriter(f, false)) {
                w.write("（旧日志已截断）\n");
                w.write(tail);
            } catch (IOException ignored) {
            }
        }
        try (FileWriter w = new FileWriter(f, true)) {
            w.write(sb.toString());
        } catch (IOException ignored) {
        }
    }

    private static File crashFile(Context app) {
        File dir = new File(app.getFilesDir(), "crash");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "crash.log");
    }

    private static File logcatFile(Context app) {
        File dir = new File(app.getFilesDir(), "crash");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "logcat.txt");
    }

    private static String readHead(File f, int maxChars) {
        try (FileReader r = new FileReader(f)) {
            char[] buf = new char[maxChars];
            int n = r.read(buf);
            return new String(buf, 0, Math.max(n, 0));
        } catch (IOException e) {
            return "";
        }
    }

    /** Returns the whole crash log, or null when there is none. */
    public static String read(Context context) {
        File f = crashFile(context.getApplicationContext());
        if (!f.exists()) {
            return null;
        }
        try (FileReader r = new FileReader(f)) {
            char[] buf = new char[(int) Math.min(f.length(), MAX_BYTES)];
            int n = r.read(buf);
            return new String(buf, 0, Math.max(n, 0));
        } catch (IOException e) {
            return null;
        }
    }

    public static void clear(Context context) {
        crashFile(context.getApplicationContext()).delete();
        File dir = new File(context.getApplicationContext().getFilesDir(), "crash");
        File[] rotated = dir.listFiles((d, name) -> name.startsWith("logcat"));
        if (rotated != null) {
            for (File f : rotated) {
                f.delete();
            }
        }
    }
}
