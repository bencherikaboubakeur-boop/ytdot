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

        // Safe Firebase init
        try {
            analytics = FirebaseAnalytics.getInstance(this);
        } catch (Exception e) {
            Log.e(TAG, "Firebase init failed", e);
        }

        adSlot = findViewById(R.id.adSlot);
        web = findViewById(R.id.web);

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
        
        // CRITICAL FIX: Disable hardware acceleration on WebView to prevent black screen crashes on MIUI/some Android versions [[34]][[35]]
        web.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String host = r.getUrl().getHost();
                if (host != null && (host.endsWith("youtube.com") || host.endsWith("youtu.be"))) {
                    return false;
                }
                try { startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl())); }
                catch (Exception ignored) {}
                return true;
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e(TAG, "WebView error: " + description);
                // Don't crash on network errors
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

    private void setupConsentThenAds() {
        try {
            consent = UserMessagingPlatform.getConsentInformation(this);
            // FIXED: Use correct Builder API for UMP v4.0.0 [[29]][[33]]
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
            // Try to init ads anyway without consent flow
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
