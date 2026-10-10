package com.organism.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.webkit.WebView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small persistent, bounded, privacy-conscious diagnostic trail shared by app screens. */
public final class AppDiagnostics {
    private static final String PREFS = "organism_diagnostics";
    private static final String KEY_EVENTS = "events";
    private static final int MAX_EVENTS = 300;
    private AppDiagnostics() {}

    public static synchronized void record(Context context, String category, String outcome, String details) {
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(new Date())
                + " [" + clean(category) + "] " + clean(outcome)
                + (details == null || details.trim().isEmpty() ? "" : " — " + clean(details));
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String old = prefs.getString(KEY_EVENTS, "");
        String[] rows = old.isEmpty() ? new String[0] : old.split("\\n");
        StringBuilder next = new StringBuilder();
        int start = Math.max(0, rows.length - MAX_EVENTS + 1);
        for (int i = start; i < rows.length; i++) {
            if (rows[i].isEmpty()) continue;
            if (next.length() > 0) next.append('\n');
            next.append(rows[i]);
        }
        if (next.length() > 0) next.append('\n');
        next.append(line);
        prefs.edit().putString(KEY_EVENTS, next.toString()).apply();
        android.util.Log.i("ORGANISM-DIAG", line);
    }

    public static synchronized String report(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String webViewVersion = "unknown";
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                android.content.pm.PackageInfo info = WebView.getCurrentWebViewPackage();
                if (info != null) webViewVersion = info.versionName;
            } catch (Exception ignored) { }
        }
        StringBuilder out = new StringBuilder("ORGANISM diagnostic report\n");
        out.append("Android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
                .append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append("\n")
                .append("WebView: ").append(webViewVersion).append("\n")
                .append("Recent events (max ").append(MAX_EVENTS).append("):\n");
        String events = prefs.getString(KEY_EVENTS, "");
        out.append(events.isEmpty() ? "(no events yet)" : events);
        return out.toString();
    }

    public static synchronized void clear(Context context) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_EVENTS).apply();
        record(context, "DIAGNOSTICS", "CLEARED", "User cleared diagnostic event history");
    }

    private static String clean(String value) {
        if (value == null) return "null";
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
