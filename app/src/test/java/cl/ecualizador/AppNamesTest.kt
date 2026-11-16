package cl.ecualizador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Ajuste por app: las apps conocidas se pueden ver (queries del manifiesto) y qué ajuste vale. */
class AppNamesTest {
    @Test
    fun manifestDeclaresEveryKnownApp() {
        val f = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.exists() }
        val declared = Regex("""<package android:name="([^"]+)"""").findAll(f.readText()).map { it.groupValues[1] }.toSet()
        assertTrue(AppNames.KNOWN_APPS.size > 30)
        assertEquals("apps conocidas sin declarar en queries", emptySet<String>(), AppNames.KNOWN_APPS - declared)
    }

    @Test
    fun ruleNeedsAnExistingPreset() {
        val rules = mapOf("com.spotify.music" to "Rock", "org.videolan.vlc" to "Borrado")
        val exists = { name: String -> name == "Rock" }
        assertEquals("com.spotify.music" to "Rock", EqCore.appRule("com.spotify.music", rules, exists))
        assertNull(EqCore.appRule("org.videolan.vlc", rules, exists))
        assertNull(EqCore.appRule("com.otra.app", rules, exists))
        assertNull(EqCore.appRule("", rules, exists))
    }
}
