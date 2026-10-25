package com.bsfreetv.launcher.feature.home

import com.bsfreetv.launcher.data.NextUpItem
import com.bsfreetv.launcher.domain.LauncherSettings
import com.bsfreetv.launcher.domain.layout.Section

/** Everything the screens can ask for; the ViewModel implements it, previews and tests stub it. */
interface HomeActions {
    fun launch(pkg: String)
    fun launch(item: NextUpItem)
    fun openMenu(pkg: String)
    fun open(overlay: Overlay)
    fun back(): Boolean
    fun assign(pkg: String, section: Section?)
    fun startMove(pkg: String)
    fun move(delta: Int)
    fun endMove()
    fun focusHandled()
    fun appInfo(pkg: String)
    fun uninstall(pkg: String)
    fun openSystemSettings(): Boolean
    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings)
    fun previewWallpaper(id: String?)
    fun chooseWallpaper(id: String)
    /** VPN pill pressed: look up the public IP (the only network request it makes). */
    fun lookUpIp()

    object None : HomeActions {
        override fun launch(pkg: String) = Unit
        override fun launch(item: NextUpItem) = Unit
        override fun openMenu(pkg: String) = Unit
        override fun open(overlay: Overlay) = Unit
        override fun back() = false
        override fun assign(pkg: String, section: Section?) = Unit
        override fun startMove(pkg: String) = Unit
        override fun move(delta: Int) = Unit
        override fun endMove() = Unit
        override fun focusHandled() = Unit
        override fun appInfo(pkg: String) = Unit
        override fun uninstall(pkg: String) = Unit
        override fun openSystemSettings() = false
        override fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) = Unit
        override fun previewWallpaper(id: String?) = Unit
        override fun chooseWallpaper(id: String) = Unit
        override fun lookUpIp() = Unit
    }
}
