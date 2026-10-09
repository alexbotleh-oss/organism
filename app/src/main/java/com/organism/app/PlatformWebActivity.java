package com.organism.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.net.Uri;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
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
    private static final int FILE_CHOOSER_REQUEST = 4107;
    private ValueCallback<Uri[]> pendingFileChooser;

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

        Button external = new Button(this);
        external.setText("Открыть в браузере");
        external.setAllCaps(false);
        external.setOnClickListener(v -> openExternalBrowser());
        root.addView(external, new LinearLayout.LayoutParams(-1, -2));

        TextView status = new TextView(this);
        status.setText("Если поле ввода или отправка не работают здесь, откройте официальный сайт в браузере. Запрос и ответ передаются вручную.");
        status.setTextColor(Color.rgb(75, 83, 96));
        status.setTextSize(12);
        status.setPadding(dp(12), dp(2), dp(12), dp(4));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                if (pendingFileChooser != null) {
                    pendingFileChooser.onReceiveValue(null);
                    pendingFileChooser = null;
                }
                pendingFileChooser = filePathCallback;
                try {
                    Intent chooserIntent = fileChooserParams.createIntent();
                    startActivityForResult(chooserIntent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    pendingFileChooser.onReceiveValue(null);
                    pendingFileChooser = null;
                    Toast.makeText(PlatformWebActivity.this,
                            "Не удалось открыть выбор файла: " + e.getClass().getSimpleName(),
                            Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        webView.loadUrl("https://chatgpt.com/");
    }

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void openExternalBrowser() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/"));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть браузер: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
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

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (pendingFileChooser != null) {
                Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                pendingFileChooser.onReceiveValue(results);
                pendingFileChooser = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (pendingFileChooser != null) {
            pendingFileChooser.onReceiveValue(null);
            pendingFileChooser = null;
        }
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
