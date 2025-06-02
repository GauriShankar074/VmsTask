package com.gauri.vmstask.application

import androidx.multidex.MultiDexApplication
import com.gauri.vmstask.BuildConfig
import com.google.android.libraries.places.api.Places
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VmsApp:MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        }
    }
}