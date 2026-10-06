package org.hnau.ktcad

import org.hnau.ktcad.ir.RadiusSpec
import org.hnau.ktcad.ir.Sketch
import org.hnau.ktcad.ir.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegionExtTest {

    private val square = polygon(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(10.0, 10.0), Vec2(0.0, 10.0))

    @Test
    fun booleans_lower_to_a_single_sketch_bool() {
        val arena = Arena()
        val index = arena.region(square.union(circle(2.0)))

        // square, circle, bool — bottom-up, operands before the parent.
        assertEquals(3, arena.sketches.size)
        assertEquals(2, index.value)
        val bool = arena.sketches[index.value] as Sketch.Bool
        assertEquals(listOf(0), bool.arguments.map { it.value })
        assertEquals(listOf(1), bool.tools.map { it.value })
    }

    @Test
    fun transform_lowers_to_a_sketch_transform() {
        val arena = Arena()
        val index = arena.region(square.translate(3.0, 4.0))

        assertEquals(2, arena.sketches.size)
        val transform = arena.sketches[index.value] as Sketch.Transform
        assertEquals(0, transform.target.value)
    }

    @Test
    fun repeated_regions_are_deduplicated() {
        val arena = Arena()
        val first = arena.region(square)
        val second = arena.region(square)
        assertEquals(first, second)
        assertEquals(1, arena.sketches.size)
    }

    @Test
    fun extrude_lowers_a_region_reference_into_the_body_arena() {
        val arena = Arena()
        val body = arena.body(square.extrude(5.0))

        assertTrue(body.value >= 0)
        assertEquals(1, arena.sketches.size)
        assertEquals(1, arena.bodies.size)
    }

    @Test
    fun fillet2d_lowers_to_a_sketch_fillet() {
        val arena = Arena()
        val index = arena.region(square.fillet2dAll(1.0))

        assertEquals(2, arena.sketches.size)
        val fillet = arena.sketches[index.value] as Sketch.Fillet2d
        assertEquals(0, fillet.target.value)
        assertEquals(RadiusSpec.All(radius = 1.0), fillet.radius)
    }

    @Test
    fun fillet2d_expression_and_selection_lower_to_radius_specs() {
        assertTrue(square.fillet2dExpression("vertex.angle > 1.5") is Region.Fillet2d)
        val selected = square.fillet2dSelected("vertex.angle > 1.5", 2.0) as Region.Fillet2d
        assertEquals(
            RadiusSpec.Selected(expression = "vertex.angle > 1.5", radius = 2.0),
            selected.radius,
        )
    }

    @Test
    fun offset2d_lowers_to_a_sketch_offset() {
        val arena = Arena()
        val index = arena.region(circle(5.0).offset2d(2.0))

        assertEquals(2, arena.sketches.size)
        val offset = arena.sketches[index.value] as Sketch.Offset2d
        assertEquals(0, offset.target.value)
        assertEquals(2.0, offset.distance)
    }
}
