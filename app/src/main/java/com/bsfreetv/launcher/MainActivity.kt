package com.bsfreetv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.bsfreetv.launcher.feature.home.HomeViewModel
import com.bsfreetv.launcher.feature.home.LauncherRoot
import com.bsfreetv.launcher.i18n.LanguageStore
import dagger.hilt.android.AndroidEntryPoint

/** The Home activity. singleTask: a Home press while showing arrives as onNewIntent. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: HomeViewModel by viewModels()

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = vm.onScreenOff()
    }

    /** Applies the in-app language override (Android 11 has no per-app language). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        setContent { LauncherRoot(vm) }
    }

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.onHomePressed()
    }
}
