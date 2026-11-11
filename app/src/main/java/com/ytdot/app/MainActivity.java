package com.ytdot.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends Activity {

    private WebView web;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;
    private WebChromeClient chrome;

    private static String dot(Uri u) {
        String url = u.toString();
        String host = u.getHost();
        String path = u.getPath();
        if (host == null || path == null) return url;
        if (host.endsWith(".")) return url;
        boolean yt = host.equals("www.youtube.com") || host.equals("youtube.com") || host.equals("m.youtube.com");
        boolean watch = path.startsWith("/watch") || path.startsWith("/shorts");
        if (yt && watch) return url.replace("://" + host + "/", "://" + host + "./");
        return url;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { return true; }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                v.loadUrl(dot(request.getUrl()));
                return true;
            }
            @Override
            public void doUpdateVisitedHistory(WebView v, String url, boolean isReload) {
                String d = dot(Uri.parse(url));
                if (!d.equals(url)) v.loadUrl(d);
            }
        });

        chrome = new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view;
                customCb = callback;
                web.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
            }
            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                web.setVisibility(View.VISIBLE);
                if (customCb != null) { customCb.onCustomViewHidden(); customCb = null; }
            }
        };
        web.setWebChromeClient(chrome);

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }

        try { FirebaseMessaging.getInstance().subscribeToTopic("all"); } catch (Exception ignored) { }

        String start = getIntent() != null ? getIntent().getStringExtra("url") : null;
        if (start == null || start.length() == 0) start = "https://www.youtube.com/";
        web.loadUrl(dot(Uri.parse(start)));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String u = intent.getStringExtra("url");
        if (u != null && u.length() > 0) web.loadUrl(dot(Uri.parse(u)));
    }

    @Override
    public void onBackPressed() {
        if (customView != null) { chrome.onHideCustomView(); return; }
        if (web.canGoBack()) { web.goBack(); return; }
        super.onBackPressed();
    }
}
