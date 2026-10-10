package com.organism.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.net.Uri;
import android.webkit.WebChromeClient;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.DownloadListener;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class PlatformWebActivity extends Activity {
    public static final String EXTRA_PROMPT = "com.organism.app.PLATFORM_WEB_PROMPT";
    public static final String EXTRA_TURN_ID = "com.organism.app.PLATFORM_WEB_TURN_ID";
    private WebView webView;
    private String prompt = "";
    private static final int FILE_CHOOSER_REQUEST = 4107;
    private ValueCallback<Uri[]> pendingFileChooser;
    private final ArrayList<String> diagnosticEvents = new ArrayList<>();
    private static final int MAX_DIAGNOSTIC_EVENTS = 80;
    private int chooserAttempt = 0;
    private static final int MICROPHONE_PERMISSION_REQUEST = 4201;
    private PermissionRequest pendingWebPermissionRequest;

    private void recordDiagnostic(String event) {
        String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date()) + " " + event;
        diagnosticEvents.add(line);
        if (diagnosticEvents.size() > MAX_DIAGNOSTIC_EVENTS) diagnosticEvents.remove(0);
        Log.i("ORGANISM-WebDiag", line);
        AppDiagnostics.record(this, "WEBVIEW", "EVENT", event);
    }

    private void showDiagnostics() {
        String webViewVersion = "не определён";
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            android.content.pm.PackageInfo webViewPackage = android.webkit.WebView.getCurrentWebViewPackage();
            if (webViewPackage != null) webViewVersion = webViewPackage.versionName;
        }
        StringBuilder report = new StringBuilder("ORGANISM · диагностика вложений/WebView\n");
        report.append("Версия Android: ").append(android.os.Build.VERSION.RELEASE)
                .append(" (SDK ").append(android.os.Build.VERSION.SDK_INT).append(")\n")
                .append("WebView: ").append(webViewVersion).append("\n")
                .append("Попыток выбора файла: ").append(chooserAttempt).append("\n")
                .append("Разрешение микрофона Android: ").append(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED ? "разрешено" : "не выдано").append("\n\n");
        if (diagnosticEvents.isEmpty()) report.append("Событий пока нет.");
        else for (String event : diagnosticEvents) report.append(event).append('\n');
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        TextView body = new TextView(this);
        body.setText(report.toString());
        body.setTextIsSelectable(true);
        body.setTextSize(12);
        body.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(body);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Диагностика ORGANISM")
                .setView(scroll)
                .setNegativeButton("Закрыть", null)
                .setPositiveButton("Копировать отчёт", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("ORGANISM diagnostics", report.toString()));
                        Toast.makeText(this, "Отчёт скопирован. В нём нет адресов выбранных файлов.", Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        AppDiagnostics.record(this, "WEBVIEW", "ACTIVITY_OPENED", "ChatGPT Web activity created");
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
        Button diagnostics = new Button(this);
        diagnostics.setText("Log");
        diagnostics.setAllCaps(false);
        diagnostics.setMinHeight(dp(48));
        diagnostics.setOnClickListener(v -> showDiagnostics());
        header.addView(diagnostics, new LinearLayout.LayoutParams(-2, -2));
        Button back = new Button(this);
        back.setText("Назад");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        Button toggleComposer = new Button(this);
        toggleComposer.setText("Показать ввод");
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
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            handleWebDownload(url, userAgent, contentDisposition, mimeType, contentLength);
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri uri = request.getUrl();
                String scheme = uri == null ? "" : String.valueOf(uri.getScheme()).toLowerCase(Locale.ROOT);
                String host = uri == null ? "" : uri.getHost();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    if (isEmbeddedWebHost(host)) return false;
                    recordDiagnostic("WEB_LINK opening external browser scheme=" + scheme + " host=" + (host == null ? "unknown" : host));
                    openExternalUrl(uri);
                    return true;
                }
                if ("mailto".equals(scheme) || "tel".equals(scheme)) {
                    recordDiagnostic("WEB_LINK opening external handler scheme=" + scheme);
                    openExternalUrl(uri);
                    return true;
                }
                if ("blob".equals(scheme) || "data".equals(scheme)) {
                    recordDiagnostic("WEB_LINK unsupported navigation scheme=" + scheme);
                    return true;
                }
                return false;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                recordDiagnostic("WEBVIEW page started: " + safeHost(url));
            }
            @Override public void onPageFinished(WebView view, String url) {
                recordDiagnostic("WEBVIEW page finished: " + safeHost(url));
            }
            @Override public void onReceivedError(WebView view, android.webkit.WebResourceRequest request,
                    android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) recordDiagnostic("WEBVIEW main-frame error code="
                        + error.getErrorCode() + " description=" + error.getDescription());
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> handleWebPermissionRequest(request));
            }

            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                recordDiagnostic("WEB_PERMISSION request cancelled by WebView");
                if (pendingWebPermissionRequest == request) pendingWebPermissionRequest = null;
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                chooserAttempt++;
                recordDiagnostic("FILE_CHOOSER requested attempt=" + chooserAttempt
                        + " mode=" + fileChooserParams.getMode()
                        + " acceptTypes=" + java.util.Arrays.toString(fileChooserParams.getAcceptTypes()));
                if (pendingFileChooser != null) {
                    pendingFileChooser.onReceiveValue(null);
                    pendingFileChooser = null;
                }
                pendingFileChooser = filePathCallback;
                try {
                    Intent chooserIntent = fileChooserParams.createIntent();
                    recordDiagnostic("FILE_CHOOSER launching intent=" + chooserIntent.getAction());
                    startActivityForResult(chooserIntent, FILE_CHOOSER_REQUEST);
                    recordDiagnostic("FILE_CHOOSER picker activity launched");
                    return true;
                } catch (Exception e) {
                    recordDiagnostic("FILE_CHOOSER launch exception=" + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
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
            // Collapse the helper field after copying so it cannot compete with ChatGPT's native composer.
            composer.setVisibility(View.GONE);
            helper.setVisibility(View.GONE);
            toggleComposer.setText("Показать ввод");
            input.clearFocus();
            android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
            webView.requestFocus();
            Toast.makeText(this, "Запрос скопирован. Вставьте его в поле ChatGPT и отправьте на сайте. Автоматическая отправка пока не реализована.", Toast.LENGTH_LONG).show();
        });
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(112), dp(52));
        sendParams.leftMargin = dp(8);
        composer.addView(send, sendParams);
        root.addView(composer, new LinearLayout.LayoutParams(-1, -2));
        // Keep the native ChatGPT input unobstructed until the ORGANISM field is requested.
        composer.setVisibility(View.GONE);
        helper.setVisibility(View.GONE);

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
        recordDiagnostic("ACTIVITY created; contentAccess=" + settings.getAllowContentAccess() + ", fileAccess=" + settings.getAllowFileAccess());
        webView.loadUrl("https://chatgpt.com/");
    }

    private boolean isEmbeddedWebHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("chatgpt.com") || h.endsWith(".chatgpt.com")
                || h.equals("chat.openai.com") || h.equals("auth.openai.com")
                || h.endsWith(".auth.openai.com") || h.equals("accounts.google.com");
    }

    private void openExternalUrl(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(intent);
            recordDiagnostic("WEB_LINK external handler launched");
        } catch (Exception e) {
            recordDiagnostic("WEB_LINK external handler failed=" + e.getClass().getSimpleName());
            Toast.makeText(this, "Не удалось открыть ссылку: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleWebDownload(String rawUrl, String userAgent, String contentDisposition,
            String mimeType, long contentLength) {
        Uri uri = Uri.parse(rawUrl);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        recordDiagnostic("WEB_DOWNLOAD requested scheme=" + scheme + " host="
                + (host == null ? "unknown" : host) + " mime=" + String.valueOf(mimeType)
                + " bytes=" + contentLength);
        // ChatGPT file downloads may rely on the website download/filename flow.
        // DownloadManager saved this device test as content.bin, while the same file
        // downloaded correctly in the ordinary browser. Delegate ChatGPT downloads
        // to the browser instead of saving an unverified generic payload.
        if ("chatgpt.com".equalsIgnoreCase(host)
                || (host != null && host.toLowerCase(Locale.ROOT).endsWith(".chatgpt.com"))) {
            recordDiagnostic("WEB_DOWNLOAD delegating ChatGPT download to external browser");
            openExternalUrl(uri);
            Toast.makeText(this, "Открываю загрузку ChatGPT в обычном браузере.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            recordDiagnostic("WEB_DOWNLOAD rejected unsupported scheme=" + scheme);
            Toast.makeText(this, "Эту ссылку нельзя скачать напрямую из WebView. Откройте её в обычном браузере.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("DownloadManager unavailable");
            String filename = URLUtil.guessFileName(rawUrl, contentDisposition, mimeType);
            filename = filename.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
            if (filename.isEmpty() || ".".equals(filename) || "..".equals(filename)) filename = "organism-download";
            DownloadManager.Request request = new DownloadManager.Request(uri)
                    .setTitle(filename)
                    .setDescription("Загрузка файла из ORGANISM Web")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false);
            if (mimeType != null && !mimeType.trim().isEmpty()) request.setMimeType(mimeType);
            if (userAgent != null && !userAgent.trim().isEmpty()) request.addRequestHeader("User-Agent", userAgent);
            String cookie = CookieManager.getInstance().getCookie(rawUrl);
            if (cookie != null && !cookie.isEmpty()) request.addRequestHeader("Cookie", cookie);
            long id = manager.enqueue(request);
            recordDiagnostic("WEB_DOWNLOAD enqueued id=" + id);
            Toast.makeText(this, "Загрузка началась. Результат будет в уведомлениях и папке «Загрузки».", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            recordDiagnostic("WEB_DOWNLOAD enqueue failed=" + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
            Toast.makeText(this, "Не удалось начать загрузку: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleWebPermissionRequest(PermissionRequest request) {
        String host = request.getOrigin() == null ? "" : request.getOrigin().getHost();
        String scheme = request.getOrigin() == null ? "" : request.getOrigin().getScheme();
        boolean trusted = "https".equalsIgnoreCase(scheme) && host != null
                && ("chatgpt.com".equalsIgnoreCase(host) || host.endsWith(".chatgpt.com")
                || "chat.openai.com".equalsIgnoreCase(host));
        boolean asksForAudio = false;
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) asksForAudio = true;
        }
        recordDiagnostic("WEB_PERMISSION requested host=" + (host == null ? "unknown" : host)
                + " resources=" + java.util.Arrays.toString(request.getResources())
                + " trustedOrigin=" + trusted);
        if (!trusted || !asksForAudio || request.getResources().length != 1) {
            recordDiagnostic("WEB_PERMISSION denied: unsupported origin or resource set");
            request.deny();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            recordDiagnostic("WEB_PERMISSION granting audio capture; Android runtime permission already granted");
            request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            return;
        }
        if (pendingWebPermissionRequest != null) {
            recordDiagnostic("WEB_PERMISSION denied: another microphone request is pending");
            request.deny();
            return;
        }
        pendingWebPermissionRequest = request;
        recordDiagnostic("WEB_PERMISSION waiting for Android RECORD_AUDIO runtime permission");
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MICROPHONE_PERMISSION_REQUEST);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != MICROPHONE_PERMISSION_REQUEST) return;
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        recordDiagnostic("ANDROID_PERMISSION RECORD_AUDIO result=" + (granted ? "granted" : "denied"));
        PermissionRequest request = pendingWebPermissionRequest;
        pendingWebPermissionRequest = null;
        if (request == null) {
            recordDiagnostic("WEB_PERMISSION no pending WebView request after Android permission dialog");
            return;
        }
        if (granted && webView != null && request.getOrigin() != null
                && "https".equalsIgnoreCase(request.getOrigin().getScheme())
                && ("chatgpt.com".equalsIgnoreCase(request.getOrigin().getHost())
                    || request.getOrigin().getHost().endsWith(".chatgpt.com")
                    || "chat.openai.com".equalsIgnoreCase(request.getOrigin().getHost()))) {
            recordDiagnostic("WEB_PERMISSION granting audio capture after Android permission approval");
            request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        } else {
            recordDiagnostic("WEB_PERMISSION denied after Android permission result");
            request.deny();
            Toast.makeText(this, "Для голосового ввода нужно разрешить микрофон в Android.", Toast.LENGTH_LONG).show();
        }
    }

    private String safeHost(String rawUrl) {
        try {
            Uri uri = Uri.parse(rawUrl);
            return uri.getScheme() + "://" + uri.getHost();
        } catch (Exception ignored) { return "unknown"; }
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
            int clipCount = data != null && data.getClipData() != null ? data.getClipData().getItemCount() : 0;
            String resultAction = data == null ? "none" : String.valueOf(data.getAction());
            String resultType = data == null ? "none" : String.valueOf(data.getType());
            recordDiagnostic("FILE_CHOOSER activity result received; resultCode=" + resultCode
                    + ", dataPresent=" + (data != null) + ", action=" + resultAction
                    + ", type=" + resultType + ", dataUriPresent=" + (data != null && data.getData() != null)
                    + ", clipItems=" + clipCount + ", flags=" + (data == null ? 0 : data.getFlags()));
            if (pendingFileChooser != null) {
                Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                int parsedCount = results == null ? 0 : results.length;
                if (resultCode == RESULT_OK && parsedCount == 0 && data != null) {
                    // Some document/photo pickers return the selected content URI through data/ClipData
                    // even when FileChooserParams.parseResult() does not expose it.
                    ArrayList<Uri> recovered = new ArrayList<>();
                    if (data.getData() != null) recovered.add(data.getData());
                    if (data.getClipData() != null) {
                        for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                            Uri itemUri = data.getClipData().getItemAt(i).getUri();
                            if (itemUri != null && !recovered.contains(itemUri)) recovered.add(itemUri);
                        }
                    }
                    if (!recovered.isEmpty()) {
                        results = recovered.toArray(new Uri[0]);
                        recordDiagnostic("FILE_CHOOSER fallback recovered URI count=" + results.length);
                    } else {
                        recordDiagnostic("FILE_CHOOSER fallback found no URI in data/ClipData");
                    }
                }
                int count = results == null ? 0 : results.length;
                recordDiagnostic("FILE_CHOOSER resultCode=" + resultCode + " parsedUriCount=" + parsedCount
                        + " deliveredUriCount=" + count
                        + " outcome=" + (resultCode != RESULT_OK ? "cancelled_or_failed" : (count == 0 ? "empty_result" : "uri_returned")));
                if (results != null) {
                    for (int i = 0; i < results.length; i++) {
                        String mime = "unknown";
                        try { mime = getContentResolver().getType(results[i]); } catch (Exception ignored) { }
                        recordDiagnostic("FILE_CHOOSER selected item#" + (i + 1) + " mime=" + mime);
                    }
                }
                pendingFileChooser.onReceiveValue(results);
                pendingFileChooser = null;
            } else {
                recordDiagnostic("FILE_CHOOSER result arrived without pending callback");
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override protected void onPause() {
        // Suspend this WebView while its Activity is backgrounded without affecting other WebViews.
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (pendingWebPermissionRequest != null) {
            try { pendingWebPermissionRequest.deny(); } catch (Exception ignored) { }
            pendingWebPermissionRequest = null;
            recordDiagnostic("WEB_PERMISSION denied because Activity is being destroyed");
        }
        if (pendingFileChooser != null) {
            recordDiagnostic("FILE_CHOOSER callback cancelled during Activity destroy");
            pendingFileChooser.onReceiveValue(null);
            pendingFileChooser = null;
        }
        if (webView != null) {
            WebView closingWebView = webView;
            // Detach the heavy view from the Activity before releasing its native renderer bindings.
            if (closingWebView.getParent() instanceof ViewGroup) {
                ((ViewGroup) closingWebView.getParent()).removeView(closingWebView);
            }
            closingWebView.stopLoading();
            closingWebView.setWebChromeClient(null);
            closingWebView.setWebViewClient(null);
            closingWebView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
