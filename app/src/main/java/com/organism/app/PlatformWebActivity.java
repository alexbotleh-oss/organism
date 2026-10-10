package com.organism.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
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
        root.setFocusableInTouchMode(true);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset = insets.getSystemWindowInsetTop();
            int bottomInset = insets.getSystemWindowInsetBottom();
            view.setPadding(0, topInset, 0, bottomInset);
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(4), dp(12), dp(4));
        TextView title = new TextView(this);
        title.setText("ORGANISM · ChatGPT");
        title.setTextSize(16);
        title.setTextColor(Color.rgb(35, 43, 58));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button back = new Button(this);
        back.setText("Назад");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        Button toggleComposer = new Button(this);
        toggleComposer.setText("Скрыть ввод");
        toggleComposer.setAllCaps(false);
        toggleComposer.setMinHeight(dp(48));
        header.addView(toggleComposer, new LinearLayout.LayoutParams(-2, -2));
        header.addView(back, new LinearLayout.LayoutParams(-2, -2));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        // Android grants access to user-selected content:// URIs. Keep file:// disabled.
        settings.setAllowContentAccess(true);
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
                    startActivityForResult(fileChooserParams.createIntent(), FILE_CHOOSER_REQUEST);
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

        TextView helper = new TextView(this);
        helper.setText("Вводи запрос здесь. Пока сайт требует ручную отправку, ORGANISM не будет сообщать, что запрос отправлен автоматически.");
        helper.setTextSize(11);
        helper.setTextColor(Color.rgb(90, 98, 110));
        helper.setPadding(dp(12), dp(3), dp(12), dp(3));
        root.addView(helper, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.BOTTOM);
        composer.setPadding(dp(12), dp(8), dp(12), dp(12));
        composer.setBackgroundColor(Color.rgb(248, 249, 251));
        EditText input = new EditText(this);
        input.setHint("Сообщение для ChatGPT…");
        input.setTextSize(16);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setSingleLine(false);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        if (!prompt.trim().isEmpty()) input.setText(prompt);
        composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        Button send = new Button(this);
        send.setText("Отправить");
        send.setAllCaps(false);
        send.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, "Сначала введи запрос.", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null) {
                Toast.makeText(this, "Буфер обмена недоступен.", Toast.LENGTH_LONG).show();
                return;
            }
            clipboard.setPrimaryClip(ClipData.newPlainText("ORGANISM prompt", text));
            Toast.makeText(this, "Текст скопирован. Вставь его в поле ChatGPT и отправь на сайте. Автоматическая отправка пока не реализована.", Toast.LENGTH_LONG).show();
        });
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(112), dp(52));
        sendParams.leftMargin = dp(8);
        composer.addView(send, sendParams);
        root.addView(composer, new LinearLayout.LayoutParams(-1, -2));

        toggleComposer.setOnClickListener(v -> {
            boolean currentlyVisible = composer.getVisibility() == View.VISIBLE;
            int nextVisibility = currentlyVisible ? View.GONE : View.VISIBLE;
            composer.setVisibility(nextVisibility);
            helper.setVisibility(nextVisibility);
            toggleComposer.setText(currentlyVisible ? "Показать ввод" : "Скрыть ввод");
            if (currentlyVisible) {
                input.clearFocus();
                android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
            }
        });

        setContentView(root);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        root.requestApplyInsets();
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
