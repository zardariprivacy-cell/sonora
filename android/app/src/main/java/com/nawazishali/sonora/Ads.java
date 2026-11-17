package com.nawazishali.sonora;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

/**
 * AdMob for Sonora (added in v3.1) — the same proven manual integration as
 * Ilmora: Google Mobile Ads SDK 23.6.0 bundled and dexed without Gradle
 * (see build-v31.sh). App ID is in AndroidManifest.xml; the two unit IDs
 * below are the owner's real Sonora units (publisher pub-5229747293404900).
 *
 * IMPORTANT STATE: the AdMob account is still "being verified", so real ads
 * do NOT serve yet. Everything here therefore fails SILENTLY by design:
 * no error dialogs, no placeholder boxes, no crashes — the banner container
 * stays hidden until an ad has actually loaded, and navigation is never
 * blocked waiting for an interstitial. Never click your own ads.
 */
public class Ads {

    private static final String BANNER_ID = "ca-app-pub-5229747293404900/4996795821";       // Sonora banner unit
    private static final String INTERSTITIAL_ID = "ca-app-pub-5229747293404900/4613652442"; // Sonora interstitial unit
    private static final long INTERSTITIAL_MIN_INTERVAL_MS = 3 * 60 * 1000L;

    private static boolean initialized = false;
    private static boolean initReady = false;
    private static AdView pendingBanner = null;

    private static ViewGroup bannerBox = null;
    private static boolean bannerLoaded = false;
    private static boolean bannerSuppressed = false; // e.g. Settings tab

    private static InterstitialAd interstitial = null;
    private static boolean interstitialLoading = false;
    private static long lastInterstitialShownMs = 0;

    /** Starts the ads SDK once and preloads an interstitial. Never throws. */
    public static void init(Context ctx) {
        try {
            if (initialized) return;
            initialized = true;
            MobileAds.initialize(ctx.getApplicationContext(), status -> {
                initReady = true;
                if (pendingBanner != null) {
                    AdView v = pendingBanner;
                    pendingBanner = null;
                    requestBanner(v);
                }
            });
            loadInterstitial(ctx);
        } catch (Throwable ignored) {
            // Ads are optional; the app must work without them.
        }
    }

    private static void requestBanner(AdView adView) {
        try { adView.loadAd(new AdRequest.Builder().build()); }
        catch (Throwable ignored) { }
    }

    private static void applyBannerVisibility() {
        if (bannerBox == null) return;
        bannerBox.setVisibility(bannerLoaded && !bannerSuppressed ? View.VISIBLE : View.GONE);
    }

    /** MainActivity hides the banner on the Settings tab through this. */
    public static void setBannerSuppressed(boolean suppressed) {
        bannerSuppressed = suppressed;
        applyBannerVisibility();
    }

    /**
     * Creates the banner inside the given container. The container stays
     * GONE until an ad actually loads, so an unfilled banner (account still
     * being verified) leaves no empty box on screen. Never throws.
     */
    public static void attachBanner(Activity activity, ViewGroup container) {
        try {
            if (container == null) return;
            init(activity);
            bannerBox = container;
            bannerLoaded = false;
            applyBannerVisibility();

            AdView adView = new AdView(activity);
            AdSize size;
            try {
                int widthDp = (int) (activity.getResources().getDisplayMetrics().widthPixels
                        / activity.getResources().getDisplayMetrics().density);
                size = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp);
                if (size == null) size = AdSize.BANNER;
            } catch (Throwable t) {
                size = AdSize.BANNER;
            }
            adView.setAdSize(size);
            adView.setAdUnitId(BANNER_ID);
            adView.setAdListener(new AdListener() {
                @Override public void onAdLoaded() {
                    bannerLoaded = true;
                    applyBannerVisibility();
                }

                @Override public void onAdFailedToLoad(LoadAdError error) {
                    bannerLoaded = false;
                    applyBannerVisibility(); // collapse silently
                }
            });
            container.addView(adView);
            if (initReady) requestBanner(adView);
            else pendingBanner = adView;
        } catch (Throwable ignored) { }
    }

    private static void loadInterstitial(Context ctx) {
        try {
            if (interstitial != null || interstitialLoading) return;
            interstitialLoading = true;
            InterstitialAd.load(ctx.getApplicationContext(), INTERSTITIAL_ID,
                    new AdRequest.Builder().build(),
                    new InterstitialAdLoadCallback() {
                        @Override public void onAdLoaded(InterstitialAd ad) {
                            interstitialLoading = false;
                            interstitial = ad;
                        }

                        @Override public void onAdFailedToLoad(LoadAdError error) {
                            interstitialLoading = false;
                            interstitial = null; // silent: try again on the next init/show attempt
                        }
                    });
        } catch (Throwable ignored) {
            interstitialLoading = false;
        }
    }

    /**
     * Interstitial gate for opening a video. Returns true when an ad was
     * shown — the caller must then run {@code proceed} from the ad's
     * dismiss callback (this method arranges that). Returns false when the
     * caller should proceed immediately: no ad loaded, the 3-minute cap has
     * not elapsed, or anything went wrong. Never blocks, never throws.
     * Only ever called on a user action (opening a video) — never on app
     * start and never during playback.
     */
    public static boolean showInterstitialBefore(final Activity act, final Runnable proceed) {
        try {
            init(act);
            if (interstitial == null) return false;
            long now = System.currentTimeMillis();
            if (lastInterstitialShownMs > 0
                    && now - lastInterstitialShownMs < INTERSTITIAL_MIN_INTERVAL_MS) return false;
            final InterstitialAd ad = interstitial;
            interstitial = null;
            lastInterstitialShownMs = now;
            ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override public void onAdDismissedFullScreenContent() {
                    proceed.run();
                    loadInterstitial(act);
                }

                @Override public void onAdFailedToShowFullScreenContent(
                        com.google.android.gms.ads.AdError error) {
                    proceed.run();
                    loadInterstitial(act);
                }
            });
            ad.show(act);
            return true;
        } catch (Throwable t) {
            try { loadInterstitial(act); } catch (Throwable ignored) { }
            return false;
        }
    }
}
