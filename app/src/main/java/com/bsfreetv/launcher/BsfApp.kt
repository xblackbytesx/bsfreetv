package com.bsfreetv.launcher

import android.app.Application
import com.bsfreetv.launcher.system.HomeControl
import com.bsfreetv.launcher.system.HomeGuard
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BsfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Lets the Google TV Home button service step aside if we keep crashing.
        HomeGuard.installCrashRecorder(this)
        // Shows the Home button service in Accessibility on Google TV only. Off the
        // main thread (PackageManager), and never allowed to break startup.
        Thread { runCatching { HomeControl(this).syncHomeButtonService() } }.start()
    }
}
