package cl.ecualizador

import org.junit.Assert.assertEquals
import org.junit.Test

/** Ajuste por app: la forma del preajuste se suma a la curva final, sin salirse del rango. */
class AppPresetsTest {
    @Test
    fun shapeIsAddedOnTopOfTheCurve() {
        val curve = List(EqCore.BANDS) { 1f }
        val shape = EqCore.PRESETS.getValue("Graves extra")
        val out = EqCore.addShape(curve, shape)
        for (i in 0 until EqCore.BANDS) assertEquals(1f + shape[i], out[i], 0.001f)
    }

    @Test
    fun staysInRange() {
        val out = EqCore.addShape(List(EqCore.BANDS) { 20f }, List(EqCore.BANDS) { 12f })
        out.forEach { assertEquals(21f, it, 0f) }
        val low = EqCore.addShape(List(EqCore.BANDS) { -14f }, List(EqCore.BANDS) { -12f })
        low.forEach { assertEquals(-15f, it, 0f) }
    }

    @Test
    fun flatShapeChangesNothing() {
        val curve = List(EqCore.BANDS) { it - 4f }
        assertEquals(curve, EqCore.addShape(curve, EqCore.PRESETS.getValue("Plano")))
    }
}
