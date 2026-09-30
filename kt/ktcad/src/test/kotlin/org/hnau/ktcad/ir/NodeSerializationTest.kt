package org.hnau.ktcad.ir

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NodeSerializationTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun node_serializes_as_tagged_sealed_interface() {
        val node = Node.Box(width = 1.0, length = 2.0, height = 3.0)
        val actual = Json.parseToJsonElement(json.encodeToString(Node.serializer(), node))
        val expected = Json.parseToJsonElement(
            """{ "type": "box", "width": 1.0, "length": 2.0, "height": 3.0 }""",
        )
        assertEquals(expected, actual)
    }

    @Test
    fun default_valued_properties_are_omitted() {
        val node = Node.Sweep(
            profile = Profile(start = Point2(0.0, 0.0), edges = emptyList()),
            path = Path(start = Point3(0.0, 0.0, 0.0), edges = emptyList()),
        )
        val actual = Json.parseToJsonElement(json.encodeToString(Node.serializer(), node))
        assertTrue("mode" !in actual.jsonObject, "default `mode` must be omitted: $actual")
    }

    @Test
    fun operands_are_plain_integers() {
        val actual = Json.parseToJsonElement(json.encodeToString(Node.serializer(), Node.Fuse(parts = listOf(0, 1))))
        val expected = Json.parseToJsonElement("""{ "type": "fuse", "parts": [0, 1] }""")
        assertEquals(expected, actual)
    }

    @Test
    fun nested_transform_op_is_tagged() {
        val op = TransformOp.Rotate(center = Point3(0.0, 0.0, 0.0), axis = Normal3(0.0, 0.0, 1.0), angle = 0.5)
        val actual = Json.parseToJsonElement(json.encodeToString(TransformOp.serializer(), op))
        val expected = Json.parseToJsonElement(
            """{ "type": "rotate", "center": {"x":0.0,"y":0.0,"z":0.0},
                "axis": {"dx":0.0,"dy":0.0,"dz":1.0}, "angle": 0.5 }""",
        )
        assertEquals(expected, actual)
    }
}
