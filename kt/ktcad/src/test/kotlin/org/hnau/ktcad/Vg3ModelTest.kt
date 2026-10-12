package org.hnau.ktcad

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.hnau.ktcad.axis.A3Values
import org.hnau.ktcad.ir.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Vg3ModelTest {

    @Test
    fun palette_color_is_deterministic_and_in_range() {
        val color = paletteColor("box")
        assertEquals(
            expected = color,
            actual = paletteColor("box"),
        )
        assertTrue(color in PALETTE)
    }

    @Test
    fun json_assigns_a_palette_color_when_none_is_given() {
        val model = Json.parseToJsonElement(
            listOf(
                Part(
                    name = "box",
                    solid = box(
                        A3Values(
                            x = 1.0,
                            y = 1.0,
                            z = 1.0,
                        ),
                    ),
                ),
            ).model().json(),
        )
        val color = model.jsonObject["export"]!!.jsonArray[0].jsonObject["color"]
        assertNotNull(
            actual = color,
            message = "an absent color must be filled from the palette",
        )
    }

    @Test
    fun json_keeps_an_explicit_color() {
        val explicit = Color(
            r = 0.1,
            g = 0.2,
            b = 0.3,
        )
        val model = Json.parseToJsonElement(
            listOf(
                Part(
                    name = "box",
                    solid = box(
                        A3Values(
                            x = 1.0,
                            y = 1.0,
                            z = 1.0,
                        ),
                    ),
                    color = explicit,
                ),
            )
                .model()
                .json(),
        )
        val color = model.jsonObject["export"]!!.jsonArray[0].jsonObject["color"]!!.jsonObject
        assertEquals(
            expected = 0.1,
            actual = color["r"]!!.jsonPrimitive.double,
        )
        assertEquals(
            expected = 0.2,
            actual = color["g"]!!.jsonPrimitive.double,
        )
        assertEquals(
            expected = 0.3,
            actual = color["b"]!!.jsonPrimitive.double,
        )
    }
}
