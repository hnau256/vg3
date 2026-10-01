package org.hnau.ktcad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class FormatSerializationTest {

    @Test
    fun stl_config_matches_cli_shape() {
        val format = Format.Stl(output = Output.Single("out.stl"))
        val actual = Json.parseToJsonElement(format.toJson())
        val expected = Json.parseToJsonElement(
            """{ "format": "stl", "output": { "type": "single", "filename": "out.stl" } }""",
        )
        assertEquals(expected, actual)
    }

    @Test
    fun nullable_parameters_are_omitted() {
        val format = Format.Png(output = Output.Multi("renders"), size = 256)
        val actual = Json.parseToJsonElement(format.toJson())
        val expected = Json.parseToJsonElement(
            """{ "format": "png", "output": { "type": "multi", "path": "renders" }, "size": 256 }""",
        )
        assertEquals(expected, actual)
    }
}
