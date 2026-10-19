package com.bsfreetv.launcher.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI text comes from string resources so it can be translated. This fails on a
 * word-bearing string literal passed straight to one of our text composables.
 * Symbols and interpolations ("‹ $label ›", "$mark …") are fine.
 */
class NoHardcodedTextTest {
    private val roots = listOf("feature", "ui").map { File("src/main/java/com/bsfreetv/launcher/$it") }
    private val call = Regex("""\b(Text|Pill|SettingRow|PanelButton|SectionHeader|MenuItem|HeroText)\(\s*"[^"$]*\p{L}{2}""")
    private val statusLine = Regex("""\bStatusLine\([^,]+,\s*"[^"$]*\p{L}{2}""")

    @Test fun `no hardcoded UI text`() {
        val hits = roots.flatMap { root -> root.walk().filter { it.extension == "kt" }.toList() }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                if (call.containsMatchIn(line) || statusLine.containsMatchIn(line)) "${f.name}:${i + 1}: ${line.trim()}" else null
            }
        }
        assertTrue("Move these to strings.xml:\n" + hits.joinToString("\n"), hits.isEmpty())
    }
}
