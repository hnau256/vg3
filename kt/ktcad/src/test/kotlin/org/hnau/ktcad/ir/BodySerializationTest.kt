package org.hnau.ktcad.ir

import arrow.core.nonEmptyListOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BodySerializationTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun node_serializes_as_tagged_sealed_interface() {
        val body = Body.Box(width = 1.0, length = 2.0, height = 3.0)
        val actual = Json.parseToJsonElement(json.encodeToString(Body.serializer(), body))
        val expected = Json.parseToJsonElement(
            """{ "type": "box", "width": 1.0, "length": 2.0, "height": 3.0 }""",
        )
        assertEquals(expected, actual)
    }

    @Test
    fun default_valued_properties_are_omitted() {
        val body = Body.Sweep(
            profile = Profile(
                start = Vec2(0.0, 0.0),
                edges = nonEmptyListOf(Curve2.Line(Vec2(1.0, 0.0))),
            ),
            path = Path(
                start = Vec3(0.0, 0.0, 0.0),
                edges = nonEmptyListOf(Curve3.Line(Vec3(0.0, 0.0, 1.0))),
            ),
        )
        val actual = Json.parseToJsonElement(json.encodeToString(Body.serializer(), body))
        assertTrue("mode" !in actual.jsonObject, "default `mode` must be omitted: $actual")
    }

    @Test
    fun operands_are_plain_integers() {
        val actual = Json.parseToJsonElement(
            json.encodeToString(
                Body.serializer(),
                Body.Bool(
                    kind = BooleanKind.CUT,
                    arguments = nonEmptyListOf(BodyIndex(0)),
                    tools = nonEmptyListOf(BodyIndex(1)),
                ),
            ),
        )
        val expected = Json.parseToJsonElement(
            """{ "type": "bool", "kind": "cut", "arguments": [0], "tools": [1] }""",
        )
        assertEquals(expected, actual)
    }

    @Test
    fun nested_transform_op_is_tagged() {
        val op = TransformOp.Rotate(center = Vec3(0.0, 0.0, 0.0), axis = Vec3(0.0, 0.0, 1.0), angle = 0.5)
        val actual = Json.parseToJsonElement(json.encodeToString(TransformOp.serializer(), op))
        val expected = Json.parseToJsonElement(
            """{ "type": "rotate", "center": {"x":0.0,"y":0.0,"z":0.0},
                "axis": {"x":0.0,"y":0.0,"z":1.0}, "angle": 0.5 }""",
        )
        assertEquals(expected, actual)
    }
}
