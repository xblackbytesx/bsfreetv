package com.bsfreetv.launcher.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Guards the translations Weblate (or anyone) sends in. A placeholder that is
 * dropped or renumbered crashes String.format at runtime, and this app may be
 * the only Home on the device, so it is checked here instead.
 * Runs from the app module directory, like Gradle's unit tests.
 */
class TranslationsTest {
    private val res = File("src/main/res")
    private val placeholder = Regex("""%(\d+\$)?[sd]|%%""")

    /** key -> placeholders, for strings and every plural quantity. */
    private fun load(file: File): Map<String, List<Set<String>>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = LinkedHashMap<String, List<Set<String>>>()
        val root = doc.documentElement
        val children = root.childNodes
        for (i in 0 until children.length) {
            val el = children.item(i) as? Element ?: continue
            val name = el.getAttribute("name")
            if (el.getAttribute("translatable") == "false") continue
            val texts = when (el.tagName) {
                "string" -> listOf(el.textContent)
                "plurals" -> (0 until el.getElementsByTagName("item").length).map { el.getElementsByTagName("item").item(it).textContent }
                else -> continue
            }
            check(name !in out) { "${file.path}: duplicate key $name" }
            out[name] = texts.map { t -> placeholder.findAll(t).map { it.value.replace("%%", "%%") }.toSet() }
        }
        return out
    }

    private val base by lazy { load(File(res, "values/strings.xml")) }
    private val translations by lazy {
        res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists() }!!
            .associate { it.name to load(File(it, "strings.xml")) }
    }

    @Test fun `there are translations to check`() {
        assertTrue(translations.size >= 12)
    }

    @Test fun `translations only use keys that exist in English`() {
        translations.forEach { (dir, keys) ->
            val unknown = keys.keys - base.keys
            assertTrue("$dir has unknown keys $unknown", unknown.isEmpty())
        }
    }

    @Test fun `placeholders match the English source`() {
        translations.forEach { (dir, keys) ->
            keys.forEach { (key, variants) ->
                // Every quantity must carry the same placeholders as English "other";
                // languages without plurals (ja, zh) only have "other".
                val expected = base.getValue(key).last()
                variants.forEach { got -> assertEquals("$dir/$key", expected, got) }
            }
        }
    }

    @Test fun `shipped translations are complete`() {
        // Weblate languages may be partial (English fills in); the five we ship are not.
        listOf("values-nl", "values-de", "values-es", "values-fr", "values-it", "values-pt", "values-pl", "values-uk", "values-ar", "values-ja", "values-ko", "values-zh-rCN").forEach { dir ->
            val missing = base.keys - translations.getValue(dir).keys
            assertTrue("$dir misses $missing", missing.isEmpty())
        }
    }

    @Test fun `every values folder is offered in the language picker`() {
        fun tag(dir: String) = dir.removePrefix("values-").replace("-r", "-")
        val folders = translations.keys.map(::tag).toSet()
        val offered = SupportedLanguages.all.map { it.tag }.toSet() - "en"
        assertEquals(folders, offered)
    }

    @Test fun `language picker cycles system then each language`() {
        var tag: String? = null
        val seen = mutableListOf<String?>()
        repeat(SupportedLanguages.all.size + 1) {
            tag = SupportedLanguages.next(tag)
            seen += tag
        }
        assertEquals(SupportedLanguages.all.map { it.tag } + listOf(null), seen)
        assertEquals("en", SupportedLanguages.next("gone"))
    }
}
