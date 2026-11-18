package com.videofinder.playlists;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.FrameLayout;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

public final class Ads {
    private static final long VISIBLE_MS  = 3_000L;
    private static final long COOLDOWN_MS = 2 * 60 * 60_000L;

    private static long lastShownAt = 0L;
    private static AdView current;
    private static final Handler H = new Handler(Looper.getMainLooper());

    public static void maybeShow(Activity a, FrameLayout slot) {
        long now = System.currentTimeMillis();
        if (lastShownAt != 0 && now - lastShownAt < COOLDOWN_MS) return;
        if (current != null) return;

        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        int widthDp = (int) (dm.widthPixels / dm.density);
        if (widthDp <= 0) widthDp = 320;

        AdView av = new AdView(a);
        av.setAdUnitId(BuildConfig.BANNER_ID);
        av.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(a, widthDp));

        av.setAdListener(new com.google.android.gms.ads.AdListener() {
            @Override public void onAdLoaded() {
                slot.setVisibility(View.VISIBLE);
                lastShownAt = System.currentTimeMillis();
                H.postDelayed(() -> hide(slot), VISIBLE_MS);
            }
            @Override public void onAdFailedToLoad(com.google.android.gms.ads.LoadAdError e) {
                android.util.Log.w("ADS", "banner failed: " + e.getCode() + " " + e.getMessage());
                destroy(slot);
            }
        });

        current = av;
        slot.removeAllViews();
        slot.addView(av);
        av.loadAd(new AdRequest.Builder().build());
    }

    private static void hide(FrameLayout slot) {
        slot.setVisibility(View.GONE);
        destroy(slot);
    }

    private static void destroy(FrameLayout slot) {
        if (current != null) { current.destroy(); current = null; }
        slot.removeAllViews();
    }

    public static void pause()  { if (current != null) current.pause(); }
    public static void resume() { if (current != null) current.resume(); }
}