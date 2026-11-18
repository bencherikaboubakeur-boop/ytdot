package com.videofinder.playlists;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import android.widget.Toast;

import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {

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

        analytics = FirebaseAnalytics.getInstance(this);
        adSlot = findViewById(R.id.adSlot);
        web = findViewById(R.id.web);

        findViewById(R.id.btnRefresh).setOnClickListener(v -> web.reload());
        findViewById(R.id.btnSave).setOnClickListener(v -> {
            Library.add(this, web.getTitle(), web.getUrl());
            analytics.logEvent("video_saved", null);
            Toast.makeText(this, "Saved to library", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btnLibrary).setOnClickListener(v ->
                startActivityForResult(new Intent(this, LibraryActivity.class), REQ_LIBRARY));

        setupWebView();
        setupConsentThenAds();
        setupNotifications();

        String start = getIntent() != null ? getIntent().getStringExtra("url") : null;
        web.loadUrl(start == null || start.isEmpty() ? HOME : start);
    }

    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        web.setBackgroundColor(Color.parseColor("#0F0F14"));

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String host = r.getUrl().getHost();
                if (host != null && (host.endsWith("youtube.com") || host.endsWith("youtu.be"))) {
                    return false;   // stay in app, normal URL, untouched
                }
                try { startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl())); }
                catch (Exception ignored) {}
                return true;
            }
        });

        chrome = new WebChromeClient() {
            @Override public void onShowCustomView(View view, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = view; customCb = cb;
                web.setVisibility(View.GONE);
                adSlot.setVisibility(View.GONE);      // never cover the player
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
        consent = UserMessagingPlatform.getConsentInformation(this);
        ConsentRequestParameters params = ConsentRequestParameters.builder().build();

        consent.requestConsentInfoUpdate(this, params,
            () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(this, err -> {
                if (consent.canRequestAds()) initAds();
            }),
            err -> { if (consent.canRequestAds()) initAds(); });

        if (consent.canRequestAds()) initAds();
    }

    private void initAds() {
        if (adsInit.getAndSet(true)) return;
        new Thread(() -> {
            MobileAds.initialize(this, status -> {});
            runOnUiThread(() -> Ads.maybeShow(this, adSlot));
        }).start();
    }

    private void setupNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        try { FirebaseMessaging.getInstance().subscribeToTopic("all"); }
        catch (Exception ignored) {}
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_LIBRARY && res == RESULT_OK && data != null) {
            String u = data.getStringExtra("url");
            if (u != null) web.loadUrl(u);
        }
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        String u = i.getStringExtra("url");
        if (u != null && !u.isEmpty()) web.loadUrl(u);
    }

    @Override protected void onResume() {
        super.onResume();
        Ads.resume();
        if (adsInit.get() && consent != null && consent.canRequestAds()) {
            Ads.maybeShow(this, adSlot);   // respects the 2-hour cooldown
        }
    }

    @Override protected void onPause() { Ads.pause(); super.onPause(); }

    @Override public void onBackPressed() {
        if (customView != null) { chrome.onHideCustomView(); return; }
        if (web.canGoBack()) { web.goBack(); return; }
        super.onBackPressed();
    }
}
