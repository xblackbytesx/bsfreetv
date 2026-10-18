package com.bsfreetv.launcher.domain.layout

/**
 * Where well-known apps land the first time they are seen. Matching by package
 * first; the label pattern catches store variants with other package ids.
 */
class SeedRules(private val rules: List<Rule>) {
    data class Rule(val section: Section, val packages: Set<String>, val label: Regex? = null)

    fun sectionFor(app: AppInfo): Section? =
        rules.firstOrNull { app.packageName in it.packages }?.section
            ?: rules.firstOrNull { it.label?.containsMatchIn(app.label) == true }?.section

    companion object {
        val Default = SeedRules(
            listOf(
                Rule(Section.MEDIA, setOf("org.jellyfin.androidtv"), Regex("^Jellyfin\\b", RegexOption.IGNORE_CASE)),
                Rule(Section.MEDIA, setOf("org.schabi.newpipe"), Regex("^NewPipe\\b", RegexOption.IGNORE_CASE)),
                Rule(Section.MEDIA, setOf("nl.uitzendinggemist"), Regex("^NPO( Start)?\\b", RegexOption.IGNORE_CASE)),
                Rule(Section.MEDIA, setOf("com.valvesoftware.steamlink"), Regex("^Steam Link\\b", RegexOption.IGNORE_CASE)),
                Rule(Section.MEDIA, setOf("com.netflix.ninja", "com.netflix.mediaclient"), Regex("^Netflix\\b", RegexOption.IGNORE_CASE)),
                Rule(Section.TOOLS, setOf("com.looker.droidify"), Regex("^Droid-?ify\\b", RegexOption.IGNORE_CASE)),
                Rule(Section.TOOLS, setOf("dev.imranr.obtainium", "dev.imranr.obtainium.fdroid"), Regex("^Obtainium\\b", RegexOption.IGNORE_CASE)),
            ),
        )
    }
}
