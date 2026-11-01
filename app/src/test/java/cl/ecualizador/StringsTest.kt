package cl.ecualizador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Los textos del menú existen en inglés (por defecto) y en español, con los mismos datos variables. */
class StringsTest {
    private fun read(dir: String): Map<String, String> {
        val f = listOf(File("src/main/res/$dir/strings.xml"), File("app/src/main/res/$dir/strings.xml")).first { it.exists() }
        return Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(f.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun placeholders(s: String) = Regex("""%\d\$[sd]""").findAll(s).map { it.value }.sorted().toList()

    @Test
    fun englishAndSpanishMatch() {
        val en = read("values")
        val es = read("values-es")
        assertEquals("claves solo en inglés: ${en.keys - es.keys}", emptySet<String>(), en.keys - es.keys)
        assertEquals("claves solo en español: ${es.keys - en.keys}", emptySet<String>(), es.keys - en.keys)
        for (k in en.keys) {
            assertEquals("datos variables de $k", placeholders(en.getValue(k)), placeholders(es.getValue(k)))
            assertTrue("$k vacío", en.getValue(k).isNotBlank() && es.getValue(k).isNotBlank())
        }
    }
}
