package com.vidplay.app.ads

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Personal build: no advertising SDK is linked. */
class NoAdsController : AdsController {
    override val enabled: Boolean = false
    override val adReady: StateFlow<Boolean> = MutableStateFlow(false)
    override val privacyOptionsRequired: StateFlow<Boolean> = MutableStateFlow(false)

    override fun attach(activity: Activity) = Unit
    override fun onLibraryVisible() = Unit
    override fun dismissAd() = Unit
    override fun showPrivacyOptions() = Unit

    @Composable
    override fun NativeAdCard(modifier: Modifier) = Unit
}
