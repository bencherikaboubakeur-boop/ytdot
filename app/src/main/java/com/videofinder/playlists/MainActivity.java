package com.videofinder.playlists;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final String TAG = "VideoFinder";
    private static final String HOME = "https://m.youtube.com/";
    private static final int REQ_LIBRARY = 10;

    // ===== Background playback JavaScript =====
    private static final String JS_PATCH =
        "(function(){if(window.__bgPatched)return;window.__bgPatched=true;window.__bgActive=false;" +
        "Object.defineProperty(document,'hidden',{get:function(){return false;},configurable:true});" +
        "Object.defineProperty(document,'visibilityState',{get:function(){return 'visible';},configurable:true});" +
        "Object.defineProperty(document,'webkitHidden',{get:function(){return false;},configurable:true});" +
        "document.hasFocus=function(){return true;};" +
        "window.addEventListener('visibilitychange',function(e){e.stopImmediatePropagation();},true);" +
        "window.addEventListener('pagehide',function(e){e.stopImmediatePropagation();},true);" +
        "window.addEventListener('blur',function(e){e.stopImmediatePropagation();},true);" +
        "document.addEventListener('pause',function(e){var t=e.target;" +
        "if(window.__bgActive&&t&&t.tagName==='VIDEO'){try{t.play();}catch(x){}}},true);})();";
    private static final String JS_IS_PLAYING =
        "(function(){var v=document.querySelector('video');return !!(v&&!v.paused&&!v.ended&&v.currentTime>0);})()";
    private static final String JS_BG_ON  = "window.__bgActive=true;";
    private static final String JS_BG_OFF = "window.__bgActive=false;";
    private static final String JS_PAUSE  = "(function(){var v=document.querySelector('video');if(v)v.pause();})()";
    // ===========================================

    private WebView web;
    private FrameLayout adSlot;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;
    private WebChromeClient chrome;
    private ConsentInformation consent;
    private FirebaseAnalytics analytics;
    private final AtomicBoolean adsInit = new AtomicBoolean(false);

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        try {
            analytics = FirebaseAnalytics.getInstance(this);
        } catch (Exception e) {
            Log.e(TAG, "Firebase init failed", e);
        }

        adSlot = findViewById(R.id.adSlot);
        web = findViewById(R.id.web);

        // Notification "Stop" button -> pause video + kill service
        PlaybackService.onStopRequested = () -> {
            web.evaluateJavascript(JS_PAUSE, null);
            stopPlaybackService();
        };

        findViewById(R.id.btnRefresh).setOnClickListener(v -> {
            if (web != null) web.reload();
        });

        findViewById(R.id.btnSave).setOnClickListener(v -> {
            try {
                Library.add(this, web.getTitle(), web.getUrl());
                if (analytics != null) analytics.logEvent("video_saved", null);
                Toast.makeText(this, "Saved to library", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Save failed", e);
            }
        });

        findViewById(R.id.btnLibrary).setOnClickListener(v -> {
            try {
                startActivityForResult(new Intent(this, LibraryActivity.class), REQ_LIBRARY);
            } catch (Exception e) {
                Log.e(TAG, "Library open failed", e);
            }
        });

        setupWebView();
        setupConsentThenAds();
        setupNotifications();

        String start = getIntent() != null ? getIntent().getStringExtra("url") : null;
        if (web != null) {
            web.loadUrl(start == null || start.isEmpty() ? HOME : start);
        }
    }

    private void setupWebView() {
        if (web == null) return;

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setBackgroundColor(Color.parseColor("#0F0F14"));
        web.setLayerType(View.LAYER_TYPE_NONE, null); // hardware acceleration ON for video

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                view.evaluateJavascript(JS_PATCH, null); // patch before YouTube's player loads
            }

            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String host = r.getUrl().getHost();
                if (host != null && (host.endsWith("youtube.com") || host.endsWith("youtu.be"))) {
                    return false;
                }
                try { startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl())); }
                catch (Exception ignored) {}
                return true;
            }

            @Override public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e(TAG, "WebView error: " + description);
            }
        });

        chrome = new WebChromeClient() {
            @Override public void onShowCustomView(View view, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = view; customCb = cb;
                web.setVisibility(View.GONE);
                adSlot.setVisibility(View.GONE);
                addContentView(view, new FrameLayout.LayoutParams(-1, -1));
            }
            @Override public void onHideCustomView() {
                if (customView == null) return;
                ((FrameLayout) getWindow().getDecorView()).removeView(customView);
                customView = null;
                web.setVisibility(View.VISIBLE);
                if (customCb != null) { customCb.onCustomViewHidden(); customCb = null; }
            }
        };
        web.setWebChromeClient(chrome);
    }

    // ===== Background playback lifecycle =====
    @Override protected void onStop() {
        super.onStop();
        if (web == null) return;
        web.evaluateJavascript(JS_IS_PLAYING, value -> {
            if ("true".equals(value)) {
                web.evaluateJavascript(JS_BG_ON, null);
                startPlaybackService();
            }
        });
    }

    private void startPlaybackService() {
        try {
            Intent i = new Intent(this, PlaybackService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Exception e) {
            Log.w(TAG, "foreground service start failed", e);
        }
    }

    private void stopPlaybackService() {
        try { stopService(new Intent(this, PlaybackService.class)); }
        catch (Exception ignored) {}
    }
    // ==========================================

    private void setupConsentThenAds() {
        try {
            consent = UserMessagingPlatform.getConsentInformation(this);
            ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();

            consent.requestConsentInfoUpdate(this, params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(this, err -> {
                    if (consent.canRequestAds()) initAds();
                }),
                err -> {
                    Log.w(TAG, "Consent update failed: " + err.getMessage());
                    if (consent.canRequestAds()) initAds();
                });

            if (consent.canRequestAds()) initAds();
        } catch (Exception e) {
            Log.e(TAG, "Consent/Ads setup failed", e);
            initAds();
        }
    }

    private void initAds() {
        if (adsInit.getAndSet(true)) return;
        new Thread(() -> {
            try {
                MobileAds.initialize(this, status -> {});
                runOnUiThread(() -> Ads.maybeShow(this, adSlot));
            } catch (Exception e) {
                Log.e(TAG, "AdMob init failed", e);
            }
        }).start();
    }

    private void setupNotifications() {
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
            FirebaseMessaging.getInstance().subscribeToTopic("all");
        } catch (Exception e) {
            Log.w(TAG, "Notification setup failed", e);
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_LIBRARY && res == RESULT_OK && data != null) {
            String u = data.getStringExtra("url");
            if (u != null && web != null) web.loadUrl(u);
        }
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        String u = i.getStringExtra("url");
        if (u != null && !u.isEmpty() && web != null) web.loadUrl(u);
    }

    @Override protected void onResume() {
        super.onResume();
        Ads.resume();
        if (web != null) web.evaluateJavascript(JS_BG_OFF, null);
        stopPlaybackService(); // back in foreground -> no wake lock needed
        if (adsInit.get() && consent != null && consent.canRequestAds()) {
            Ads.maybeShow(this, adSlot);
        }
    }

    @Override protected void onPause() { Ads.pause(); super.onPause(); }

    @Override public void onBackPressed() {
        if (customView != null) { chrome.onHideCustomView(); return; }
        if (web != null && web.canGoBack()) { web.goBack(); return; }
        super.onBackPressed();
    }
}
