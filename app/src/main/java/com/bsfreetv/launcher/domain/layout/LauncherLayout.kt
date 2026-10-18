package com.bsfreetv.launcher.domain.layout

import kotlinx.serialization.Serializable

/** Where an app lives. Apps in no list are Unsorted (only in All apps). */
enum class Section { MEDIA, TOOLS, HIDDEN }

/** A launchable app as the catalog sees it. Keyed by package name. */
data class AppInfo(val packageName: String, val label: String, val hasBanner: Boolean)

/**
 * The user's arrangement, persisted as JSON. Pure value: every edit returns a
 * new layout, so it is trivially testable and DataStore writes are atomic.
 *
 * [known] remembers every package the launcher has ever seen. Seeds only apply
 * the first time a package appears, so moving Jellyfin out of Media sticks.
 * [fresh] are packages installed since the user last opened All apps.
 */
@Serializable
data class LauncherLayout(
    val media: List<String> = emptyList(),
    val tools: List<String> = emptyList(),
    val hidden: List<String> = emptyList(),
    val known: Set<String> = emptySet(),
    val fresh: Set<String> = emptySet(),
) {
    fun sectionOf(pkg: String): Section? = when (pkg) {
        in media -> Section.MEDIA
        in tools -> Section.TOOLS
        in hidden -> Section.HIDDEN
        else -> null
    }

    private fun without(pkg: String) = copy(media = media - pkg, tools = tools - pkg, hidden = hidden - pkg)

    /** Move [pkg] to the end of [section] (null = Unsorted). */
    fun assign(pkg: String, section: Section?): LauncherLayout {
        val base = without(pkg)
        return when (section) {
            Section.MEDIA -> base.copy(media = base.media + pkg)
            Section.TOOLS -> base.copy(tools = base.tools + pkg)
            Section.HIDDEN -> base.copy(hidden = base.hidden + pkg)
            null -> base
        }.copy(fresh = fresh - pkg)
    }

    /** Shift [pkg] by [delta] within its ordered section; no-op at the ends or when unsorted. */
    fun move(pkg: String, delta: Int): LauncherLayout {
        fun List<String>.shifted(): List<String> {
            val i = indexOf(pkg)
            val j = (i + delta).coerceIn(0, size - 1)
            if (i < 0 || i == j) return this
            return toMutableList().apply { removeAt(i); add(j, pkg) }
        }
        return when (sectionOf(pkg)) {
            Section.MEDIA -> copy(media = media.shifted())
            Section.TOOLS -> copy(tools = tools.shifted())
            Section.HIDDEN -> copy(hidden = hidden.shifted())
            null -> this
        }
    }

    fun clearFresh() = if (fresh.isEmpty()) this else copy(fresh = emptySet())

    /**
     * Fold in the installed apps: first-seen packages get their seed section
     * (or become fresh when unseeded). Uninstalled packages keep their slot, so
     * reinstalling an app (or an app update that briefly removes it) restores it.
     * The very first reconcile marks nothing fresh: everything is new then.
     */
    fun reconcile(installed: List<AppInfo>, seeds: SeedRules = SeedRules.Default): LauncherLayout {
        val firstRun = known.isEmpty()
        var out = this
        val newFresh = fresh.toMutableSet()
        for (app in installed.sortedBy { it.label.lowercase() }) {
            if (app.packageName in known) continue
            val seed = seeds.sectionFor(app)
            out = if (seed != null) out.assign(app.packageName, seed) else out
            if (seed == null && !firstRun) newFresh += app.packageName
        }
        return out.copy(known = known + installed.map { it.packageName }, fresh = newFresh)
    }
}

/** The arrangement resolved against what is actually installed, in display order. */
data class ResolvedLayout(
    val media: List<AppInfo>,
    val tools: List<AppInfo>,
    val unsorted: List<AppInfo>,
    val hidden: List<AppInfo>,
    val freshCount: Int,
)

fun LauncherLayout.resolve(installed: List<AppInfo>): ResolvedLayout {
    val byPkg = installed.associateBy { it.packageName }
    fun pick(list: List<String>) = list.mapNotNull { byPkg[it] }
    val placed = (media + tools + hidden).toSet()
    return ResolvedLayout(
        media = pick(media),
        tools = pick(tools),
        unsorted = installed.filter { it.packageName !in placed }.sortedBy { it.label.lowercase() },
        hidden = pick(hidden),
        freshCount = fresh.count { it in byPkg && it !in placed },
    )
}
