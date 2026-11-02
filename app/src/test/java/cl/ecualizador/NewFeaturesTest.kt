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
        // Apple nombra igual varias generaciones: sin decir cuál, no se adivina la curva
        assertNull(HeadphoneProfiles.match("AirPods Pro de Ana"))
        assertNull(HeadphoneProfiles.match("AirPods"))
        // Otras marcas con nombres parecidos no reciben la curva de Samsung
        assertNull(HeadphoneProfiles.match("OnePlus Nord Buds 2"))
        assertNull(HeadphoneProfiles.match("realme Buds 2"))
        assertNull(HeadphoneProfiles.match("HUAWEI FreeBuds Pro 3"))
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
        // El modelo reconocido es audífono, aunque la clase Bluetooth diga otra cosa
        assertEquals(OutputKind.BT_HEADPHONES, AutoProfile.classifyBluetooth("WH-1000XM4", 0x0414))
        assertEquals(p.levels.toList(), AutoProfile.curveFor(OutputKind.BT_HEADPHONES, p).levels)
        assertEquals(AutoProfile.curveFor(OutputKind.BT_HEADPHONES).levels, AutoProfile.curveFor(OutputKind.BT_HEADPHONES, null).levels)
        // A volumen alto, agudos más suaves sobre la curva medida
        val loud = AutoEq.curve(OutputKind.BT_HEADPHONES, ListenContent.MUSIC, 1f, p).levels
        val mid = AutoEq.curve(OutputKind.BT_HEADPHONES, ListenContent.MUSIC, 0.6f, p).levels
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
        // El preamplificador (byte 11) se suma a todas las bandas: −6 dB baja la curva entera
        val minus6 = Math.round((12f + 6f) * 63f / 24f)
        val withPreamp = EqCore.parseWinampEq(eqf("Fuerte" to IntArray(11) { if (it == 10) minus6 else 0 })).single().second
        assertTrue(withPreamp.all { kotlin.math.abs(it - 6f) <= 0.5f })
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

    @Test
    fun phoneSpeakerProtectionCapsDeepBassOnly() {
        val boosted = EqCore.protectSpeaker(EqCore.PRESETS.getValue("Graves extra"))
        assertEquals(0f, boosted[0], 0f)
        assertEquals(0f, boosted[1], 0f)
        assertTrue(boosted[2] <= 2f)
        // Los cortes y las bandas medias y agudas no cambian
        val cut = listOf(-6f, -5f, -2f, 0f, 1f, 1.5f, 2f, 1.5f, 1f, 0f)
        assertEquals(cut, EqCore.protectSpeaker(cut))
        assertTrue(EqCore.LIMITER_THRESHOLD_DB < 0f && EqCore.LIMITER_RATIO >= 20f)
    }

    @Test
    fun equalizerCurveIsAloneByDefaultLikeWinamp() {
        assertTrue(!SpectrumSettings().winampEqSpectrum)
    }
}
