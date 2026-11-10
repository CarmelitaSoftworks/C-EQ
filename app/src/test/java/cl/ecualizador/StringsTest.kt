package cl.ecualizador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Los textos del menú existen en inglés (por defecto), español y portugués, con los mismos datos variables. */
class StringsTest {
    private fun read(dir: String): Map<String, String> {
        val f = listOf(File("src/main/res/$dir/strings.xml"), File("app/src/main/res/$dir/strings.xml")).first { it.exists() }
        return Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(f.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun placeholders(s: String) = Regex("""%\d\$[sd]""").findAll(s).map { it.value }.sorted().toList()

    private fun matches(dir: String) {
        val en = read("values")
        val other = read(dir)
        assertEquals("claves solo en inglés ($dir): ${en.keys - other.keys}", emptySet<String>(), en.keys - other.keys)
        assertEquals("claves que sobran en $dir: ${other.keys - en.keys}", emptySet<String>(), other.keys - en.keys)
        for (k in en.keys) {
            assertEquals("datos variables de $k ($dir)", placeholders(en.getValue(k)), placeholders(other.getValue(k)))
            assertTrue("$k vacío ($dir)", en.getValue(k).isNotBlank() && other.getValue(k).isNotBlank())
        }
    }

    @Test
    fun englishAndSpanishMatch() = matches("values-es")

    @Test
    fun englishAndPortugueseMatch() = matches("values-pt")
}
