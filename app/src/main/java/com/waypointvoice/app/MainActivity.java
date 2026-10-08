package com.waypointvoice.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Hosts the Waypoint Voice web app (app/src/main/assets/index.html) and gives it
 * native powers through NativeBridge: background GPS, music controls, ducked voice audio.
 */
public class MainActivity extends Activity {
    static final String START_URL = "https://appassets.androidplatform.net/assets/index.html";
    static final int REQ_PERMS = 1;
    static final int REQ_MIC = 2;
    static final int REQ_FILE = 3;
    static final int REQ_WEB_MIC = 4;
    PermissionRequest pendingMic;
    ValueCallback<Uri[]> fileCallback;

    WebView web;
    NativeBridge bridge;
    boolean usingBundled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setUserAgentString(s.getUserAgentString() + " WaypointVoice/1.0");

        // Serve the bundled web app from a secure https address so storage, caching and CORS all work.
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        final String remote = BuildConfig.WEB_URL;
        final String remoteHost = remote.isEmpty() ? "" : Uri.parse(remote).getHost();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                // The app page itself: try your website first, and if there's no signal, serve the copy
                // built into the app *at the same address*, so saved places and offline maps still show up.
                if (!remoteHost.isEmpty() && remoteHost.equals(u.getHost()) && request.isForMainFrame()) {
                    return pageWithFallback(u.toString());
                }
                return loader.shouldInterceptRequest(u);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String host = u.getHost();
                if ("appassets.androidplatform.net".equals(host) || (!remoteHost.isEmpty() && remoteHost.equals(host))) return false;
                openExternal(u); // websites, phone numbers etc. open in their own apps
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                // No signal or site unreachable: fall back to the copy built into the app
                if (request.isForMainFrame() && !usingBundled) {
                    usingBundled = true;
                    view.loadUrl(START_URL);
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && !usingBundled && response.getStatusCode() >= 400) {
                    usingBundled = true;
                    view.loadUrl(START_URL);
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            // Lets the assistant record your voice (for tone detection)
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean audio = false;
                    for (String r : request.getResources()) if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) audio = true;
                    if (!audio) { request.deny(); return; }
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(new String[]{ PermissionRequest.RESOURCE_AUDIO_CAPTURE });
                    } else {
                        if (pendingMic != null) pendingMic.deny();
                        pendingMic = request;
                        requestPermissions(new String[]{ Manifest.permission.RECORD_AUDIO }, REQ_WEB_MIC);
                    }
                });
            }

            // Lets "Change photo" open your gallery
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
                return true;
            }
        });

        bridge = new NativeBridge(this, web);
        web.addJavascriptInterface(bridge, "Native");
        if (remote.isEmpty()) { usingBundled = true; web.loadUrl(START_URL); }
        else web.loadUrl(remote);

        askPermissions();
    }

    WebResourceResponse pageWithFallback(String url) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(6000);
            c.setUseCaches(false);
            c.setRequestProperty("Cache-Control", "no-cache");
            if (c.getResponseCode() == 200) {
                return new WebResourceResponse("text/html", "utf-8", c.getInputStream());
            }
        } catch (Exception ignored) { }
        try {
            return new WebResourceResponse("text/html", "utf-8", getAssets().open("index.html"));
        } catch (Exception e) {
            return null;
        }
    }

    void openExternal(Uri u) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, u);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) { }
    }

    void askPermissions() {
        List<String> need = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERMS);
    }

    void askMic() {
        requestPermissions(new String[]{ Manifest.permission.RECORD_AUDIO }, REQ_MIC);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS) bridge.onLocationPermissionResult();
        if (requestCode == REQ_MIC) bridge.onMicPermissionResult(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED);
        if (requestCode == REQ_WEB_MIC && pendingMic != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) pendingMic.grant(new String[]{ PermissionRequest.RESOURCE_AUDIO_CAPTURE });
            else pendingMic.deny();
            pendingMic = null;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        bridge.js("window.__nativeResume && window.__nativeResume()");
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else moveTaskToBack(true); // keep running (and navigating) instead of closing
    }

    @Override
    protected void onDestroy() {
        bridge.destroy();
        web.destroy();
        super.onDestroy();
    }
}
