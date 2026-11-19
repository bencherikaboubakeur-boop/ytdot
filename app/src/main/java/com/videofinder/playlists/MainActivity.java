package com.videofinder.playlists;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
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
        "document.addEventListener('play',function(e){try{VFBridge.onVideoPlayed();}catch(x){}},true);" +
        "document.addEventListener('pause',function(e){try{VFBridge.onVideoPaused();}catch(x){}" +
        "var t=e.target;if(window.__bgActive&&t&&t.tagName==='VIDEO'){try{t.play();}catch(x){}}},true);})();";
    private static final String JS_IS_PLAYING =
        "(function(){var v=document.querySelector('video');return !!(v&&!v.paused&&!v.ended&&v.currentTime>0);})()";
    private static final String JS_RESUME =
        "(function(){var v=document.querySelector('video');if(v&&v.paused&&!v.ended){try{v.play();}catch(x){}}})()";
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

    // ===== Background playback state =====
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private volatile boolean lastPlaying = false;
    private volatile boolean inBackground = false;
    private int bgMissCount = 0;

    /** Instant play/pause tracking from the page (no polling race). */
    private final class Bridge {
        @JavascriptInterface public void onVideoPlayed() { lastPlaying = true; }
        @JavascriptInterface public void onVideoPaused() { if (!inBackground) lastPlaying = false; }
    }

    private final Runnable fgPoll = new Runnable() {
        @Override public void run() {
            if (inBackground || web == null) return;
            web.evaluateJavascript(JS_IS_PLAYING, v -> { if (!inBackground) lastPlaying = "true".equals(v); });
            pollHandler.postDelayed(this, 2000);
        }
    };

    /** Watchdog while screen is off: re-play if anything paused, stop when video ended. */
    private final Runnable bgPoll = new Runnable() {
        @Override public void run() {
            if (!inBackground || web == null) return;
            web.evaluateJavascript(JS_IS_PLAYING, v -> {
                if (!inBackground) return;
                if ("true".equals(v)) {
                    bgMissCount = 0;
                } else {
                    bgMissCount++;
                    web.evaluateJavascript(JS_RESUME, null);
                    if (bgMissCount >= 3) {
                        inBackground = false;
                        web.evaluateJavascript(JS_BG_OFF, null);
                        stopPlaybackService();
                        Log.i(TAG, "background playback ended");
                        return;
                    }
                }
                pollHandler.postDelayed(this, 3000);
            });
        }
    };
    // =======================================

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
        web.setLayerType(View.LAYER_TYPE_NONE, null);
        web.addJavascriptInterface(new Bridge(), "VFBridge");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                view.evaluateJavascript(JS_PATCH, null);
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

    // ===== Lifecycle: the fix lives here =====
    @Override protected void onPause() {
        Ads.pause();
        pollHandler.removeCallbacks(fgPoll);
        // SYNCHRONOUS start — onPause is the last moment Android allows FGS start
        if (lastPlaying && !inBackground) {
            inBackground = true;
            bgMissCount = 0;
            if (web != null) web.evaluateJavascript(JS_BG_ON, null);
            startPlaybackService();
            pollHandler.postDelayed(bgPoll, 4000);
            Log.i(TAG, "background playback ON");
        }
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        Ads.resume();
        inBackground = false;
        pollHandler.removeCallbacks(bgPoll);
        if (web != null) web.evaluateJavascript(JS_BG_OFF, null);
        stopPlaybackService();
        pollHandler.post(fgPoll);
        if (adsInit.get() && consent != null && consent.canRequestAds()) {
            Ads.maybeShow(this, adSlot);
        }
    }

    @Override protected void onDestroy() {
        pollHandler.removeCallbacksAndMessages(null);
        stopPlaybackService();
        super.onDestroy();
    }

    private void startPlaybackService() {
        try {
            Intent i = new Intent(this, PlaybackService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Exception e) {
            Log.e(TAG, "foreground service start FAILED", e);
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

    @Override public void onBackPressed() {
        if (customView != null) { chrome.onHideCustomView(); return; }
        if (web != null && web.canGoBack()) { web.goBack(); return; }
        super.onBackPressed();
    }
}
