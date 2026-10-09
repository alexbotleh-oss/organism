package com.organism.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class PlatformWebActivity extends Activity {
    public static final String EXTRA_PROMPT = "com.organism.app.PLATFORM_WEB_PROMPT";
    public static final String EXTRA_TURN_ID = "com.organism.app.PLATFORM_WEB_TURN_ID";
    private WebView webView;
    private String prompt = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prompt = getIntent().getStringExtra(EXTRA_PROMPT);
        if (prompt == null) prompt = "";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        TextView notice = new TextView(this);
        notice.setText("ChatGPT Web · ручной режим\nЗапрос не отправляется автоматически. Скопируйте подготовленный текст, вставьте его в ChatGPT и отправьте там. Ответ скопируйте вручную, затем вернитесь в ORGANISM и нажмите «Вставить ответ из ChatGPT Web». Страница и аккаунт не читаются кодом ORGANISM.");
        notice.setTextColor(Color.rgb(35, 43, 58));
        notice.setTextSize(13);
        notice.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.addView(notice, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button preview = new Button(this);
        preview.setText("Просмотр");
        preview.setAllCaps(false);
        preview.setOnClickListener(v -> previewPrompt());
        actions.addView(preview, new LinearLayout.LayoutParams(0, -2, 1));
        Button copy = new Button(this);
        copy.setText("Копировать");
        copy.setAllCaps(false);
        copy.setOnClickListener(v -> copyPrompt());
        actions.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        Button reload = new Button(this);
        reload.setText("Обновить");
        reload.setAllCaps(false);
        reload.setOnClickListener(v -> { if (webView != null) webView.reload(); });
        actions.addView(reload, new LinearLayout.LayoutParams(-2, -2));
        Button close = new Button(this);
        close.setText("Назад");
        close.setAllCaps(false);
        close.setOnClickListener(v -> finish());
        actions.addView(close, new LinearLayout.LayoutParams(-2, -2));
        root.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        webView.loadUrl("https://chatgpt.com/");
    }

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void previewPrompt() {
        if (prompt.trim().isEmpty()) {
            Toast.makeText(this, "Подготовьте запрос в чате ORGANISM, затем откройте веб-режим.", Toast.LENGTH_LONG).show();
            return;
        }
        TextView body = new TextView(this);
        body.setText(prompt);
        body.setTextIsSelectable(true);
        body.setTextSize(13);
        body.setPadding(dp(16), dp(12), dp(16), dp(12));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(body);
        new android.app.AlertDialog.Builder(this)
            .setTitle("Точный текст, подготовленный ORGANISM")
            .setView(scroll)
            .setPositiveButton("Закрыть", null)
            .setNeutralButton("Копировать", (dialog, which) -> copyPrompt())
            .show();
    }

    private void copyPrompt() {
        if (prompt.trim().isEmpty()) {
            Toast.makeText(this, "Подготовьте запрос в чате ORGANISM, затем откройте веб-режим.", Toast.LENGTH_LONG).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "Буфер обмена недоступен.", Toast.LENGTH_LONG).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("ORGANISM prompt", prompt));
        Toast.makeText(this, "Подготовленный запрос скопирован. Вставьте его в ChatGPT Web.", Toast.LENGTH_LONG).show();
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
