package com.jdmontalvo.vidcofre.ads

import android.content.Context

object AdsProvider {
    fun create(context: Context): AdsController = NoAdsController()
}
