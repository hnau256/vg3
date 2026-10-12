package org.hnau.ktcad

import org.hnau.ktcad.axis.A2Values
import org.hnau.ktcad.region.plus
import org.hnau.ktcad.ir.RadiusSpec
import org.hnau.ktcad.ir.Sketch
import org.hnau.ktcad.region.circle
import org.hnau.ktcad.region.extrude
import org.hnau.ktcad.region.fillet2dAll
import org.hnau.ktcad.region.fillet2dExpression
import org.hnau.ktcad.region.fillet2dSelected
import org.hnau.ktcad.region.offset2d
import org.hnau.ktcad.region.polygon2
import org.hnau.ktcad.region.translate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegionExtTest {

    private val square = polygon2(
        A2Values(
            x = 0.0,
            y = 0.0,
        ),
        A2Values(
            x = 10.0,
            y = 0.0,
        ),
        A2Values(
            x = 10.0,
            y = 10.0,
        ),
        A2Values(
            x = 0.0,
            y = 10.0,
        ),
    )

    @Test
    fun booleans_lower_to_a_single_sketch_bool() {
        val arena = Arena()
        val index = arena.region(square + circle(2.0))

        // square, circle, bool — bottom-up, operands before the parent.
        assertEquals(
            expected = 3,
            actual = arena.sketches.size,
        )
        assertEquals(
            expected = 2,
            actual = index.value,
        )
        val bool = arena.sketches[index.value] as Sketch.Bool
        assertEquals(
            expected = listOf(0),
            actual = bool.arguments.map { it.value },
        )
        assertEquals(
            expected = listOf(1),
            actual = bool.tools.map { it.value },
        )
    }

    @Test
    fun transform_lowers_to_a_sketch_transform() {
        val arena = Arena()
        val index = arena.region(
            square.translate(
                A2Values(
                    x = 3.0,
                    y = 4.0,
                ),
            ),
        )

        assertEquals(
            expected = 2,
            actual = arena.sketches.size,
        )
        val transform = arena.sketches[index.value] as Sketch.Transform
        assertEquals(
            expected = 0,
            actual = transform.target.value,
        )
    }

    @Test
    fun repeated_regions_are_deduplicated() {
        val arena = Arena()
        val first = arena.region(square)
        val second = arena.region(square)
        assertEquals(
            expected = first,
            actual = second,
        )
        assertEquals(
            expected = 1,
            actual = arena.sketches.size,
        )
    }

    @Test
    fun extrude_lowers_a_region_reference_into_the_body_arena() {
        val arena = Arena()
        val body = arena.body(square.extrude(5.0))

        assertTrue(body.value >= 0)
        assertEquals(
            expected = 1,
            actual = arena.sketches.size,
        )
        assertEquals(
            expected = 1,
            actual = arena.bodies.size,
        )
    }

    @Test
    fun fillet2d_lowers_to_a_sketch_fillet() {
        val arena = Arena()
        val index = arena.region(square.fillet2dAll(1.0))

        assertEquals(
            expected = 2,
            actual = arena.sketches.size,
        )
        val fillet = arena.sketches[index.value] as Sketch.Fillet2d
        assertEquals(
            expected = 0,
            actual = fillet.target.value,
        )
        assertEquals(
            expected = RadiusSpec.All(radius = 1.0),
            actual = fillet.radius,
        )
    }

    @Test
    fun fillet2d_expression_and_selection_lower_to_radius_specs() {
        assertTrue(square.fillet2dExpression("vertex.angle > 1.5") is Region.Fillet2d)
        val selected = square.fillet2dSelected(
            selectEdgeExpression = "vertex.angle > 1.5",
            radius = 2.0,
        ) as Region.Fillet2d
        assertEquals(
            expected = RadiusSpec.Selected(
                expression = "vertex.angle > 1.5",
                radius = 2.0,
            ),
            actual = selected.radius,
        )
    }

    @Test
    fun offset2d_lowers_to_a_sketch_offset() {
        val arena = Arena()
        val index = arena.region(circle(5.0).offset2d(2.0))

        assertEquals(
            expected = 2,
            actual = arena.sketches.size,
        )
        val offset = arena.sketches[index.value] as Sketch.Offset2d
        assertEquals(
            expected = 0,
            actual = offset.target.value,
        )
        assertEquals(
            expected = 2.0,
            actual = offset.distance,
        )
    }
}
