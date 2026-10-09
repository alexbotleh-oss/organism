package com.organism.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Persistent bounded trace for OAuth stages. Never pass credentials or raw server bodies here. */
final class AuthTrace {
    private static final String PREFS = "organism";
    private static final String KEY = "auth_diagnostic_trace";
    private static final int MAX_LINES = 120;

    private AuthTrace() {}

    static synchronized void record(Context context, String stage, String outcome, String detail) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String previous = prefs.getString(KEY, "");
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            String safeDetail = clean(detail);
            String line = time + " | " + clean(stage) + " | " + clean(outcome)
                    + (safeDetail.isEmpty() ? "" : " | " + safeDetail);
            String[] lines = previous.isEmpty() ? new String[0] : previous.split("\\n");
            StringBuilder next = new StringBuilder();
            int first = Math.max(0, lines.length - MAX_LINES + 1);
            for (int i = first; i < lines.length; i++) {
                if (!lines[i].isEmpty()) next.append(lines[i]).append('\n');
            }
            next.append(line).append('\n');
            prefs.edit().putString(KEY, next.toString()).apply();
        } catch (Exception ignored) {
            // Diagnostics must not interrupt the authentication flow.
        }
    }

    static String read(Context context) {
        String trace = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "");
        return trace.isEmpty()
                ? "Журнал пуст. Выполните одну попытку подключения ChatGPT и откройте журнал снова."
                : trace;
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }

    private static String clean(String value) {
        if (value == null) return "";
        String result = value.replace('|', '/').replace('\n', ' ').replace('\r', ' ');
        return result.length() > 180 ? result.substring(0, 180) : result;
    }
}
