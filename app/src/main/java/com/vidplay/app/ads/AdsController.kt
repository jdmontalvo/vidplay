package com.vidplay.app.ads

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

/**
 * Advertising surface used by the shared UI.
 *
 * The `play` flavor implements it with Google AdMob (native ad inside the video
 * library). The `foss` flavor returns a no-op controller and never links the
 * advertising SDK, so the personal build keeps no INTERNET permission.
 */
interface AdsController {
    /** False in the personal build. */
    val enabled: Boolean

    /** True when a native ad is loaded and ready to be rendered. */
    val adReady: StateFlow<Boolean>

    /** True when the user must be able to reopen the ad consent form. */
    val privacyOptionsRequired: StateFlow<Boolean>

    /** Registers the current activity so the SDK can show its consent form. */
    fun attach(activity: Activity)

    /** The video library became visible: load an ad if the frequency cap allows. */
    fun onLibraryVisible()

    /** Hides the current ad until the process restarts. */
    fun dismissAd()

    /** Reopens the Google consent form (required for EEA/UK users). */
    fun showPrivacyOptions()

    /** Native ad card. Renders nothing when there is no ad. */
    @Composable
    fun NativeAdCard(modifier: Modifier)
}
