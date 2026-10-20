package com.crimson.command;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;

public class MainActivity extends Activity {
    private WebView browser;
    private SharedPreferences preferences;
    private String server = "";
    private boolean setupMode = true;
    private static final String SETUP_PAGE = "file:///android_asset/setup.html";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(10, 8, 13));
        getWindow().setNavigationBarColor(Color.rgb(10, 8, 13));
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            getWindow().getDecorView().setSystemUiVisibility(0);
            getWindow().getAttributes().layoutInDisplayCutoutMode = 0;
        }
        preferences = getSharedPreferences("crimson_settings", MODE_PRIVATE);
        server = preferences.getString("server_origin", "");
        browser = new WebView(this);
        browser.setBackgroundColor(Color.rgb(9, 8, 12));
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setAllowFileAccess(true);
        browser.getSettings().setAllowContentAccess(false);
        browser.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        browser.getSettings().setSupportMultipleWindows(false);
        browser.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        browser.getSettings().setAllowFileAccessFromFileURLs(false);
        browser.getSettings().setAllowUniversalAccessFromFileURLs(false);
        if (android.os.Build.VERSION.SDK_INT >= 26) browser.getSettings().setSafeBrowsingEnabled(true);
        WebView.setWebContentsDebuggingEnabled(false);
        browser.setWebChromeClient(new WebChromeClient());
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri target = request.getUrl();
                if (request.isForMainFrame() && !setupMode && target.toString().startsWith(server + "/")) return false;
                if (setupMode && target.toString().equals(SETUP_PAGE)) return false;
                if (target.getScheme() != null && (target.getScheme().equals("https") || target.getScheme().equals("http"))) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, target)); } catch (Exception e) { toast("No browser available"); }
                }
                return true;
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (!request.isForMainFrame() || setupMode) return;
                showSetup("Connection lost. Verify your command server is online.");
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                if (!setupMode) showSetup("The command server's TLS certificate is invalid.");
            }
        });
        setContentView(browser);
        if (server.isEmpty()) showSetup("");
        else showRemote();
    }

    private void showSetup(String issue) {
        runOnUiThread(() -> {
            setupMode = true;
            browser.addJavascriptInterface(new LocalBridge(), "CrimsonNative");
            browser.loadUrl(SETUP_PAGE);
            if (!issue.isEmpty()) toast(issue);
        });
    }

    private void showRemote() {
        runOnUiThread(() -> {
            setupMode = false;
            browser.removeJavascriptInterface("CrimsonNative");
            browser.loadUrl(server + "/");
        });
    }

    private void toast(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
    }

    private void js(String method, String arg) {
        runOnUiThread(() -> {
            if (setupMode) browser.evaluateJavascript("window." + method + "(" + org.json.JSONObject.quote(arg) + ");", null);
        });
    }

    private boolean validOrigin(String raw) {
        try {
            URI u = new URI(raw);
            String scheme = u.getScheme();
            String host = u.getHost();
            if (host == null || u.getRawUserInfo() != null || u.getRawQuery() != null || u.getRawFragment() != null) return false;
            if (!u.getPath().isEmpty() && !u.getPath().equals("/")) return false;
            boolean safeHttps = "https".equalsIgnoreCase(scheme);
            boolean safeLoopback = "http".equalsIgnoreCase(scheme) &&
                ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host));
            return safeHttps || safeLoopback;
        } catch (Exception e) { return false; }
    }

    private String verify(String origin) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(origin + "/api/bootstrap").openConnection();
        conn.setConnectTimeout(7000);
        conn.setReadTimeout(7000);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestMethod("GET");
        try {
            if (conn.getResponseCode() != 200) throw new Exception("Server answered HTTP " + conn.getResponseCode() + ", not Crimson Command.");
            try (InputStream in = conn.getInputStream()) {
                byte[] bytes = in.readNBytes(4096);
                String reply = new String(bytes, StandardCharsets.UTF_8);
                org.json.JSONObject json = new org.json.JSONObject(reply);
                if (!json.has("needsLogin")) throw new Exception("This endpoint isn't a Crimson Command server.");
            }
            return "";
        } finally { conn.disconnect(); }
    }

    private final class LocalBridge {
        @JavascriptInterface public void connect(String raw) {
            if (!setupMode) return;
            String origin = raw == null ? "" : raw.trim().replaceAll("/+$", "");
            if (!validOrigin(origin)) {
                js("connectionFailed", "Use a HTTPS server address. Only localhost may use HTTP.");
                return;
            }
            new Thread(() -> {
                try {
                    verify(origin);
                    preferences.edit().putString("server_origin", origin).apply();
                    server = origin;
                    showRemote();
                } catch (Exception e) {
                    js("connectionFailed", "Can't connect: " + e.getMessage());
                }
            }, "connection-check").start();
        }
        @JavascriptInterface public String savedServer() {
            return setupMode ? server : "";
        }
        @JavascriptInterface public void retry() {
            if (!setupMode) return;
            if (server.isEmpty()) js("connectionFailed", "Set a server address first.");
            else connect(server);
        }
    }

    @Override public void onBackPressed() {
        if (!setupMode && browser.canGoBack()) { browser.goBack(); return; }
        if (!setupMode) {
            new AlertDialog.Builder(this)
                .setTitle("Crimson Command")
                .setMessage("Choose what you'd like to do.")
                .setPositiveButton("Change server", (d, w) -> showSetup(""))
                .setNeutralButton("Refresh", (d, w) -> browser.reload())
                .setNegativeButton("Close", (d, w) -> finish())
                .show();
        } else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (browser != null) browser.destroy();
        super.onDestroy();
    }
}
