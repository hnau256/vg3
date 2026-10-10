package org.hnau.ktcad

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.hnau.ktcad.ir.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Vg3ModelTest {

    @Test
    fun palette_color_is_deterministic_and_in_range() {
        val color = paletteColor("box")
        assertEquals(color, paletteColor("box"))
        assertTrue(color in PALETTE)
    }

    @Test
    fun json_assigns_a_palette_color_when_none_is_given() {
        val model = Json.parseToJsonElement(
            listOf(Part(name = "box", solid = box(1.0, 1.0, 1.0))).model().json(),
        )
        val color = model.jsonObject["export"]!!.jsonArray[0].jsonObject["color"]
        assertNotNull(color, "an absent color must be filled from the palette")
    }

    @Test
    fun json_keeps_an_explicit_color() {
        val explicit = Color(r = 0.1, g = 0.2, b = 0.3)
        val model = Json.parseToJsonElement(
            listOf(Part(name = "box", solid = box(1.0, 1.0, 1.0), color = explicit))
                .model()
                .json(),
        )
        val color = model.jsonObject["export"]!!.jsonArray[0].jsonObject["color"]!!.jsonObject
        assertEquals(0.1, color["r"]!!.jsonPrimitive.double)
        assertEquals(0.2, color["g"]!!.jsonPrimitive.double)
        assertEquals(0.3, color["b"]!!.jsonPrimitive.double)
    }
}
