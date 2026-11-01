package cl.ecualizador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewFeaturesTest {
    @Test
    fun headphoneModelsByBluetoothName() {
        assertEquals("Sony WH-1000XM4", HeadphoneProfiles.match("WH-1000XM4")?.model)
        assertEquals("Sony WF-1000XM5", HeadphoneProfiles.match("WF-1000XM5")?.model)
        assertEquals("Samsung Galaxy Buds2 Pro", HeadphoneProfiles.match("Galaxy Buds2 Pro (A1B2)")?.model)
        assertEquals("Samsung Galaxy Buds2", HeadphoneProfiles.match("Galaxy Buds2 (A1B2)")?.model)
        assertEquals("Apple Airpods Pro 2", HeadphoneProfiles.match("AirPods Pro 2")?.model)
        assertEquals("Apple AirPods Pro", HeadphoneProfiles.match("AirPods Pro de Ana")?.model)
        assertEquals("JBL Tune 510BT", HeadphoneProfiles.match("JBL TUNE510BT")?.model)
        assertNull(HeadphoneProfiles.match("JBL Flip 6"))
        assertNull(HeadphoneProfiles.match(null))
        for (p in HeadphoneProfiles.ALL) {
            assertEquals(EqCore.BANDS, p.levels.size)
            assertTrue(p.model, p.levels.all { it in EqCore.MIN_DB..EqCore.MAX_DB })
        }
    }

    @Test
    fun recognizedHeadphonesUseTheirMeasuredCurve() {
        val p = HeadphoneProfiles.match("WH-1000XM4")!!
        assertEquals(p.levels.toList(), AutoProfile.curveFor(OutputKind.BT_UNKNOWN, "WH-1000XM4").levels)
        assertEquals(AutoProfile.curveFor(OutputKind.BT_HEADPHONES).levels, AutoProfile.curveFor(OutputKind.BT_HEADPHONES, "Audífonos X").levels)
        // A volumen alto se cuidan como audífonos (agudos más suaves), aunque el tipo no se supiera
        val loud = AutoEq.curve(OutputKind.BT_UNKNOWN, ListenContent.MUSIC, 1f, "WH-1000XM4").levels
        val mid = AutoEq.curve(OutputKind.BT_UNKNOWN, ListenContent.MUSIC, 0.6f, "WH-1000XM4").levels
        assertTrue(loud[8] < mid[8])
    }

    private fun eqf(vararg presets: Pair<String, IntArray>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write("Winamp EQ library file v1.1".toByteArray())
        out.write(byteArrayOf(0x1A, '!'.code.toByte(), '-'.code.toByte(), '-'.code.toByte()))
        for ((name, values) in presets) {
            out.write(name.toByteArray().copyOf(257))
            out.write(ByteArray(11) { values.getOrElse(it) { 31 }.toByte() })
        }
        return out.toByteArray()
    }

    @Test
    fun readsWinampPresetFiles() {
        val list = EqCore.parseWinampEq(eqf("Rock" to IntArray(10) { if (it < 2) 0 else 63 }, "Flat" to IntArray(10) { 31 }))
        assertEquals(listOf("Rock", "Flat"), list.map { it.first })
        assertEquals(12f, list[0].second[0], 0f)
        assertEquals(-12f, list[0].second[9], 0f)
        assertTrue(list[1].second.all { kotlin.math.abs(it) <= 0.5f })
        assertTrue(EqCore.parseWinampEq("no es un archivo de Winamp".toByteArray()).isEmpty())
    }

    @Test
    fun presetsAreValid() {
        assertTrue(EqCore.PRESETS.size >= 23)
        for ((name, levels) in EqCore.PRESETS) {
            assertEquals(name, EqCore.BANDS, levels.size)
            assertTrue(name, levels.all { it in EqCore.MIN_DB..EqCore.MAX_DB })
        }
    }

    @Test
    fun randomSkinIsAlwaysADifferentOne() {
        val names = listOf("A", "B", "C")
        val r = java.util.Random(1)
        repeat(50) { assertNotEquals("B", SkinShuffle.pick(names, "B", r)) }
        assertNull(SkinShuffle.pick(listOf("A"), "A", r))
    }

    @Test
    fun remindsToCheckForUpdatesOnlyWhenTheVersionIsOld() {
        assertTrue(!Updates.shouldRemind(daysSinceBuild = 10, today = 100, snoozedUntil = 0))
        assertTrue(Updates.shouldRemind(daysSinceBuild = Updates.REMIND_AFTER_DAYS, today = 100, snoozedUntil = 0))
        assertTrue(!Updates.shouldRemind(daysSinceBuild = 60, today = 100, snoozedUntil = 120))
        assertTrue(Updates.shouldRemind(daysSinceBuild = 60, today = 120, snoozedUntil = 120))
    }
}
