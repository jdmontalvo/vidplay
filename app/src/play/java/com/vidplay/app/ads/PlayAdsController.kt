package com.vidplay.app.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.vidplay.app.BuildConfig
import com.vidplay.app.databinding.AdNativeCardBinding
import java.lang.ref.WeakReference
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * AdMob implementation: one native card inside the video library, with a hard
 * cap of one ad per hour and four per day. Nothing is ever requested from the
 * player or the vault.
 */
class PlayAdsController(private val context: Context) : AdsController {

    override val enabled: Boolean = BuildConfig.ADS_ENABLED && BuildConfig.ADS_CONFIGURED

    private val _adReady = MutableStateFlow(false)
    override val adReady: StateFlow<Boolean> = _adReady.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    override val privacyOptionsRequired: StateFlow<Boolean> =
        _privacyOptionsRequired.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var activityRef: WeakReference<Activity>? = null
    private var consentInformation: ConsentInformation? = null
    private var consentResolved = false
    private var consentInFlight = false

    private var sdkReady = false
    private var sdkInitializing = false
    private val pendingInit = mutableListOf<() -> Unit>()

    private var loading = false
    private var dismissedThisSession = false
    private val nativeAdState = mutableStateOf<NativeAd?>(null)

    override fun attach(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    override fun onLibraryVisible() {
        if (!enabled || dismissedThisSession) return
        val activity = activityRef?.get() ?: return
        if (nativeAdState.value != null || loading) return

        if (consentResolved) {
            if (consentInformation?.canRequestAds() == true) loadIfAllowed()
            return
        }
        if (consentInFlight) return
        consentInFlight = true

        val information = UserMessagingPlatform.getConsentInformation(context)
        consentInformation = information
        information.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    consentInFlight = false
                    consentResolved = true
                    updatePrivacyOptions()
                    if (information.canRequestAds()) loadIfAllowed()
                }
            },
            { error ->
                Log.w(TAG, "Consent update failed: ${error.message}")
                consentInFlight = false
                consentResolved = true
                updatePrivacyOptions()
                if (information.canRequestAds()) loadIfAllowed()
            },
        )
    }

    override fun dismissAd() {
        val ad = nativeAdState.value ?: return
        nativeAdState.value = null
        _adReady.value = false
        dismissedThisSession = true
        // Let Compose detach the view before destroying the ad.
        mainHandler.post { runCatching { ad.destroy() } }
    }

    override fun showPrivacyOptions() {
        val activity = activityRef?.get() ?: return
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "Privacy options failed: ${error.message}")
            updatePrivacyOptions()
            // The user may have granted consent here; retry the ad once.
            if (error == null && consentInformation?.canRequestAds() == true) loadIfAllowed()
        }
    }

    @Composable
    override fun NativeAdCard(modifier: Modifier) {
        val ad = nativeAdState.value ?: return

        Column(modifier = modifier.fillMaxWidth()) {
            key(ad) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx ->
                        val binding = AdNativeCardBinding.inflate(LayoutInflater.from(ctx))
                        bind(binding, ad)
                        binding.root
                    },
                )
            }
            TextButton(
                onClick = { dismissAd() },
                modifier = Modifier.align(Alignment.End).padding(top = 2.dp, end = 4.dp),
            ) {
                Text("Ocultar anuncio", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    private fun updatePrivacyOptions() {
        _privacyOptionsRequired.value =
            consentInformation?.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun loadIfAllowed() {
        if (!enabled || dismissedThisSession) return
        if (nativeAdState.value != null || loading) return
        if (!capAllows()) return

        loading = true
        ensureSdk {
            val request = NativeAdRequest.Builder(
                BuildConfig.ADMOB_NATIVE_UNIT_ID,
                listOf(NativeAd.NativeAdType.NATIVE),
            ).build()
            NativeAdLoader.load(
                request,
                object : NativeAdLoaderCallback {
                    override fun onNativeAdLoaded(nativeAd: NativeAd) {
                        mainHandler.post {
                            loading = false
                            nativeAd.adEventCallback = object : NativeAdEventCallback {
                                override fun onAdClicked() = Unit
                            }
                            nativeAdState.value = nativeAd
                            _adReady.value = true
                            recordLoad()
                        }
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        mainHandler.post {
                            loading = false
                            Log.w(TAG, "Native ad failed: ${adError.message}")
                        }
                    }
                },
            )
        }
    }

    private fun ensureSdk(onReady: () -> Unit) {
        if (sdkReady) {
            onReady()
            return
        }
        pendingInit += onReady
        if (sdkInitializing) return
        sdkInitializing = true
        scope.launch {
            MobileAds.initialize(
                context,
                InitializationConfig.Builder(BuildConfig.ADMOB_APP_ID).build(),
            ) {
                mainHandler.post {
                    sdkReady = true
                    sdkInitializing = false
                    val callbacks = pendingInit.toList()
                    pendingInit.clear()
                    callbacks.forEach { it() }
                }
            }
        }
    }

    private fun capAllows(): Boolean {
        val now = System.currentTimeMillis()
        val lastLoad = prefs.getLong(KEY_LAST_LOAD, 0L)
        if (now - lastLoad < MIN_INTERVAL_MS) return false

        val today = LocalDate.now().toEpochDay()
        val storedDay = prefs.getLong(KEY_DAY, Long.MIN_VALUE)
        val loadsToday = if (storedDay == today) prefs.getInt(KEY_LOADS, 0) else 0
        return loadsToday < MAX_PER_DAY
    }

    private fun recordLoad() {
        val today = LocalDate.now().toEpochDay()
        val storedDay = prefs.getLong(KEY_DAY, Long.MIN_VALUE)
        val loadsToday = if (storedDay == today) prefs.getInt(KEY_LOADS, 0) else 0
        prefs.edit()
            .putLong(KEY_LAST_LOAD, System.currentTimeMillis())
            .putLong(KEY_DAY, today)
            .putInt(KEY_LOADS, loadsToday + 1)
            .apply()
    }

    private fun bind(binding: AdNativeCardBinding, ad: NativeAd) {
        val view = binding.root
        view.headlineView = binding.adHeadline
        view.bodyView = binding.adBody
        view.callToActionView = binding.adCta
        view.iconView = binding.adIcon
        view.advertiserView = binding.adAdvertiser
        view.priceView = binding.adPrice
        view.starRatingView = binding.adStars
        view.storeView = binding.adStore

        binding.adHeadline.text = ad.headline
        binding.adHeadline.visibility = visibilityOf(ad.headline)

        binding.adBody.text = ad.body
        binding.adBody.visibility = visibilityOf(ad.body)

        binding.adAdvertiser.text = ad.advertiser
        binding.adAdvertiser.visibility = visibilityOf(ad.advertiser)

        binding.adCta.text = ad.callToAction
        binding.adCta.visibility = visibilityOf(ad.callToAction)

        binding.adPrice.text = ad.price
        binding.adPrice.visibility = visibilityOf(ad.price)

        binding.adStore.text = ad.store
        binding.adStore.visibility = visibilityOf(ad.store)

        val icon = ad.icon?.drawable
        binding.adIcon.setImageDrawable(icon)
        binding.adIcon.visibility = visibilityOf(icon)

        val stars = ad.starRating
        binding.adStars.rating = stars?.toFloat() ?: 0f
        binding.adStars.visibility = if (stars == null || stars <= 0.0) {
            View.GONE
        } else {
            View.VISIBLE
        }

        binding.adMedia.imageScaleType = ImageView.ScaleType.CENTER_CROP
        view.registerNativeAd(ad, binding.adMedia)
    }

    private fun visibilityOf(value: Any?): Int =
        if (value == null) View.INVISIBLE else View.VISIBLE

    private companion object {
        const val TAG = "VidCofreAds"
        const val PREFS_NAME = "vidplay_ads"
        const val KEY_LAST_LOAD = "last_load_ms"
        const val KEY_DAY = "load_day"
        const val KEY_LOADS = "loads_today"
        const val MIN_INTERVAL_MS = 60L * 60L * 1000L
        const val MAX_PER_DAY = 4
    }
}
