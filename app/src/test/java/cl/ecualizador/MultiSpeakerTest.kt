package cl.ecualizador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MultiSpeakerTest {
    @Test
    fun recognizesBrandsByName() {
        assertEquals("Sony", MultiSpeaker.brandOf("SRS-XB43")?.name)
        assertEquals("Sony", MultiSpeaker.brandOf("ULT FIELD 1")?.name)
        assertEquals("JBL", MultiSpeaker.brandOf("JBL Flip 6")?.name)
        assertEquals("JBL", MultiSpeaker.brandOf("Charge 5")?.name)
        assertEquals("Ultimate Ears", MultiSpeaker.brandOf("WONDERBOOM 3")?.name)
        assertEquals("Soundcore", MultiSpeaker.brandOf("Soundcore Motion 300")?.name)
        assertEquals("Marshall", MultiSpeaker.brandOf("Emberton II")?.name)
        assertNull(MultiSpeaker.brandOf("Parlante genérico"))
    }

    @Test
    fun sameBrandFirstThenSystemOptionsThenCable() {
        val plan = MultiSpeaker.plan(listOf("JBL Flip 6", "JBL Charge 5", "SRS-XB13"), broadcast = true, samsung = true)
        assertEquals(
            listOf(
                MultiSpeaker.Option.SameBrand(MultiSpeaker.BRANDS.first { it.name == "JBL" }, 2),
                MultiSpeaker.Option.Auracast, MultiSpeaker.Option.DualAudio, MultiSpeaker.Option.Cable,
            ),
            plan,
        )
    }

    @Test
    fun differentBrandsOnAPlainPhoneOnlyHaveTheCable() {
        assertEquals(listOf(MultiSpeaker.Option.Cable), MultiSpeaker.plan(listOf("JBL Flip 6", "SRS-XB13"), broadcast = false, samsung = false))
        assertEquals(listOf(MultiSpeaker.Option.Cable), MultiSpeaker.plan(emptyList(), broadcast = false, samsung = false))
    }
}
