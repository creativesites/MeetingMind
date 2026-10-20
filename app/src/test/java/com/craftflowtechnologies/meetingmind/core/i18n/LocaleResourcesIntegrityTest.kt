package com.craftflowtechnologies.meetingmind.core.i18n

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Validates that all localized string resources are syntactically valid XML,
 * contain non-empty values, have no orphaned keys, and cover core app keys.
 */
class LocaleResourcesIntegrityTest {

    private fun findResDir(): File {
        val candidates = listOf(
            File("app/src/main/res"),
            File("src/main/res")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Cannot find res directory in $candidates")
    }

    private fun parseStringsXml(file: File): Map<String, String> {
        assertTrue("File ${file.path} does not exist", file.exists())
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(file)
        doc.documentElement.normalize()

        val stringNodes = doc.getElementsByTagName("string")
        val map = mutableMapOf<String, String>()
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            if (node is Element) {
                val name = node.getAttribute("name")
                val text = node.textContent
                map[name] = text
            }
        }
        return map
    }

    private val coreKeys = listOf(
        "nav_today",
        "nav_notes",
        "nav_circles",
        "nav_faith",
        "nav_work",
        "nav_settings",
        "action_save",
        "action_cancel",
        "action_done",
        "action_back",
        "settings_language_title",
        "settings_language_description",
        "settings_language_follow_system",
        "settings_language_asr_notice",
        "recording_button_record",
        "onboarding_headline",
        "onboarding_button_continue"
    )

    @Test
    fun `default English strings file exists and contains all core keys`() {
        val resDir = findResDir()
        val enFile = File(resDir, "values/strings.xml")
        val enStrings = parseStringsXml(enFile)

        coreKeys.forEach { key ->
            assertTrue("Base strings.xml missing core key '$key'", enStrings.containsKey(key))
            assertFalse("Base strings.xml has blank value for key '$key'", enStrings[key].isNullOrBlank())
        }
    }

    @Test
    fun `all eight target localized resource files exist`() {
        val resDir = findResDir()
        val targetDirs = listOf(
            "values-fr",
            "values-es",
            "values-pt",
            "values-sw",
            "values-ny",
            "values-sn",
            "values-zu",
            "values-b+bem"
        )

        targetDirs.forEach { dirName ->
            val dir = File(resDir, dirName)
            assertTrue("Expected localized directory $dirName to exist", dir.exists() && dir.isDirectory)
            val file = File(dir, "strings.xml")
            assertTrue("Expected strings.xml in $dirName to exist", file.exists() && file.isFile)
        }
    }

    @Test
    fun `every localized language pack contains non-empty core keys and valid parent keys`() {
        val resDir = findResDir()
        val enStrings = parseStringsXml(File(resDir, "values/strings.xml"))

        val targetDirs = listOf(
            "values-fr",
            "values-es",
            "values-pt",
            "values-sw",
            "values-ny",
            "values-sn",
            "values-zu",
            "values-b+bem"
        )

        targetDirs.forEach { dirName ->
            val file = File(resDir, "$dirName/strings.xml")
            val langStrings = parseStringsXml(file)

            // 1. Verify core keys exist in this language
            coreKeys.forEach { key ->
                assertTrue(
                    "Localized file $dirName/strings.xml missing core key '$key'",
                    langStrings.containsKey(key)
                )
                assertFalse(
                    "Localized file $dirName/strings.xml has blank value for key '$key'",
                    langStrings[key].isNullOrBlank()
                )
            }

            // 2. Verify all keys defined in this language are valid in canonical English strings.xml
            langStrings.keys.forEach { key ->
                assertTrue(
                    "Localized file $dirName/strings.xml has orphan key '$key' not found in base strings.xml",
                    enStrings.containsKey(key)
                )
            }
        }
    }
}
