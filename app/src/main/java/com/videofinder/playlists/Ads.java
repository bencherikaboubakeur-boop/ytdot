package com.videofinder.playlists;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;

/** One instance per Activity. No static Views, no leaks. */
public final class Ads {
    private static final String TAG = "Ads";
    private static volatile boolean initialized = false;

    private final Activity activity;
    private final FrameLayout slot;
    private AdView adView;

    public Ads(Activity a, FrameLayout slot) { this.activity = a; this.slot = slot; }

    public static void init(Context c, Runnable onDone) {
        if (initialized) { if (onDone != null) onDone.run(); return; }
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try { MobileAds.initialize(app, status -> {}); } catch (Exception e) { Log.w(TAG, "init", e); }
            initialized = true;
            if (onDone != null) new Handler(Looper.getMainLooper()).post(onDone);
        }).start();
    }

    public void load() {
        if (activity == null || slot == null || adView != null) return;
        try {
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            int widthDp = (int) (dm.widthPixels / dm.density);
            if (widthDp <= 0) widthDp = 320;

            AdView av = new AdView(activity);
            av.setAdUnitId(BuildConfig.BANNER_ID);
            av.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp));
            av.setAdListener(new AdListener() {
                @Override public void onAdLoaded() { slot.setVisibility(View.VISIBLE); }
                @Override public void onAdFailedToLoad(LoadAdError e) {
                    Log.w(TAG, "banner failed: " + e.getCode());
                    destroy();
                }
            });
            adView = av;
            slot.removeAllViews();
            slot.addView(av);
            av.loadAd(new AdRequest.Builder().build());
        } catch (Exception e) {
            Log.e(TAG, "load", e);
            destroy();
        }
    }

    public void resume()  { if (adView != null) try { adView.resume(); } catch (Exception ignored) {} }
    public void pause()   { if (adView != null) try { adView.pause();  } catch (Exception ignored) {} }

    public void destroy() {
        if (adView != null) { try { adView.destroy(); } catch (Exception ignored) {} adView = null; }
        if (slot != null) { slot.removeAllViews(); slot.setVisibility(View.GONE); }
    }
}
