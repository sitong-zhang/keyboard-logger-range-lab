package com.keyrecord.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.HashMap;
import java.util.Map;

/** Resolves an app package name to its human-readable label, with caching. */
public class AppLabels {

    private static final Map<String, String> CACHE = new HashMap<>();

    public static String getLabel(Context context, String pkg) {
        if (pkg == null) {
            return "?";
        }
        synchronized (CACHE) {
            String cached = CACHE.get(pkg);
            if (cached != null) {
                return cached;
            }
        }
        PackageManager pm = context.getPackageManager();
        String label = pkg;
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            CharSequence name = pm.getApplicationLabel(info);
            if (name != null && !name.toString().isEmpty()) {
                label = name.toString();
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // App not installed (uninstalled after logging) — keep the package name.
        }
        synchronized (CACHE) {
            CACHE.put(pkg, label);
        }
        return label;
    }
}
