package com.bsfreetv.launcher.domain

import com.bsfreetv.launcher.domain.layout.AppInfo
import com.bsfreetv.launcher.domain.layout.LauncherLayout
import com.bsfreetv.launcher.domain.layout.resolve
import com.bsfreetv.launcher.domain.layout.Section
import com.bsfreetv.launcher.domain.layout.SeedRules
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTest {
    private fun app(pkg: String, label: String) = AppInfo(pkg, label, hasBanner = true)
    private val jellyfin = app("org.jellyfin.androidtv", "Jellyfin")
    private val newpipe = app("org.schabi.newpipe", "NewPipe")
    private val npo = app("nl.uitzendinggemist", "NPO Start")
    private val steam = app("com.valvesoftware.steamlink", "Steam Link")
    private val netflix = app("com.netflix.ninja", "Netflix")
    private val droidify = app("com.looker.droidify", "Droid-ify")
    private val obtainium = app("dev.imranr.obtainium.fdroid", "Obtainium")
    private val kodi = app("org.xbmc.kodi", "Kodi")
    private val installed = listOf(netflix, kodi, jellyfin, obtainium, newpipe, droidify, steam, npo)

    @Test fun `first run seeds media and tools, rest unsorted, nothing fresh`() {
        val l = LauncherLayout().reconcile(installed)
        assertEquals(setOf(jellyfin, newpipe, npo, steam, netflix).map { it.packageName }.toSet(), l.media.toSet())
        assertEquals(setOf(droidify.packageName, obtainium.packageName), l.tools.toSet())
        assertNull(l.sectionOf(kodi.packageName))
        assertTrue(l.fresh.isEmpty())
        assertEquals(installed.size, l.known.size)
    }

    @Test fun `seeding is by label when the package id differs`() {
        val npoVariant = app("nl.npo.start.tv", "NPO Start")
        assertEquals(Section.MEDIA, SeedRules.Default.sectionFor(npoVariant))
        assertNull(SeedRules.Default.sectionFor(app("com.example.npoorly", "Not NPO")))
    }

    @Test fun `user choices survive later reconciles`() {
        var l = LauncherLayout().reconcile(installed)
        l = l.assign(netflix.packageName, null)
        l = l.reconcile(installed)
        assertNull(l.sectionOf(netflix.packageName))
    }

    @Test fun `newly installed unseeded apps become fresh until All apps opens`() {
        var l = LauncherLayout().reconcile(installed)
        val spotify = app("com.spotify.tv.android", "Spotify")
        l = l.reconcile(installed + spotify)
        assertEquals(setOf(spotify.packageName), l.fresh)
        assertEquals(1, l.resolve(installed + spotify).freshCount)
        assertTrue(l.clearFresh().fresh.isEmpty())
        // Sorting it also clears it.
        assertTrue(l.assign(spotify.packageName, Section.MEDIA).fresh.isEmpty())
    }

    @Test fun `move reorders within a section and clamps at the ends`() {
        val l = LauncherLayout(media = listOf("a", "b", "c"))
        assertEquals(listOf("b", "a", "c"), l.move("a", 1).media)
        assertEquals(listOf("a", "b", "c"), l.move("a", -1).media)
        assertEquals(listOf("c", "a", "b"), l.move("c", -5).media)
        assertEquals(l, l.move("zzz", 1))
    }

    @Test fun `assign moves between sections without duplicates`() {
        val l = LauncherLayout(media = listOf("a", "b"), tools = listOf("t"))
            .assign("a", Section.TOOLS)
            .assign("t", Section.HIDDEN)
        assertEquals(listOf("b"), l.media)
        assertEquals(listOf("a"), l.tools)
        assertEquals(listOf("t"), l.hidden)
    }

    @Test fun `uninstalled apps keep their slot but do not show`() {
        val l = LauncherLayout().reconcile(installed)
        val r = l.resolve(installed - netflix)
        assertTrue(r.media.none { it.packageName == netflix.packageName })
        assertTrue(netflix.packageName in l.media)
        // Reinstall puts it back where it was.
        assertTrue(l.resolve(installed).media.any { it.packageName == netflix.packageName })
    }

    @Test fun `layout round trips through json and ignores unknown keys`() {
        val json = Json { ignoreUnknownKeys = true }
        val l = LauncherLayout().reconcile(installed).assign(kodi.packageName, Section.HIDDEN)
        val text = json.encodeToString(LauncherLayout.serializer(), l)
        assertEquals(l, json.decodeFromString(LauncherLayout.serializer(), text))
        assertEquals(LauncherLayout(media = listOf("x")), json.decodeFromString(LauncherLayout.serializer(), """{"media":["x"],"future":1}"""))
    }

    @Test fun `settings decode tolerates old and unknown values`() {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val s = json.decodeFromString(LauncherSettings.serializer(), """{"cardSize":"HUGE","showClock":false,"later":true}""")
        assertEquals(CardSize.MEDIUM, s.cardSize)
        assertEquals(false, s.showClock)
        // Settings saved before the tool size existed get the default.
        assertEquals(ToolSize.MEDIUM, s.toolSize)
    }
}
