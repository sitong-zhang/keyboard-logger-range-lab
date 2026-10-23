package com.example.keylogger;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 本地日志管理：所有键盘记录只写入应用私有存储，不联网、不申请外部存储权限。
 *  - 内部存储：getFilesDir()/keylog.txt（仅本应用可读）
 *  - 导出：复制到 getExternalFilesDir("export")（应用私有外部目录，无需权限，
 *         用户可用 adb pull 取出）
 */
public class LogManager {

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

    /** 追加一行日志；对“完全相同且同一来源”的连续重复做去重，避免刷屏。 */
    public synchronized void append(String line) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String full = ts + "  " + line;
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

    public synchronized String read() {
        if (!internalFile.exists()) {
            return "（暂无记录）";
        }
        try (BufferedReader br = new BufferedReader(new FileReader(internalFile))) {
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = br.readLine()) != null) {
                sb.append(l).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            return "读取失败: " + e.getMessage();
        }
    }

    public synchronized void clear() {
        if (internalFile.exists()) {
            internalFile.delete();
        }
        lastLine = "";
    }

    /** 将内部日志复制到应用私有外部目录，便于用 adb 取出。 */
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
