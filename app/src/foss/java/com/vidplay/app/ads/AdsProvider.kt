package com.vidplay.app.ads

import android.content.Context

object AdsProvider {
    fun create(context: Context): AdsController = NoAdsController()
}
