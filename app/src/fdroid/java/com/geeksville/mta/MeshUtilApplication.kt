package com.geeksville.mta

import com.geeksville.mta.android.GeeksvilleApplication
import com.geeksville.mta.android.Logging
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MeshUtilApplication : GeeksvilleApplication() {

    override fun onCreate() {
        super.onCreate()

        Logging.showLogs = BuildConfig.DEBUG

    }
}