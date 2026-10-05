package org.hnau.ktcad

import arrow.core.nonEmptyListOf
import org.hnau.ktcad.ir.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrimitiveExtTest {

    @Test
    fun box_without_centering_is_a_plain_box() {
        assertEquals(Solid.Box(width = 1.0, length = 2.0, height = 3.0), box(1.0, 2.0, 3.0))
    }

    @Test
    fun centering_an_axis_adds_a_transform() {
        assertTrue(box(1.0, 2.0, 3.0, centerX = true) is Solid.Transform)
        assertTrue(cylinder(1.0, 3.0, centerZ = true) is Solid.Transform)
    }

    @Test
    fun cylinder_without_centering_is_a_plain_cylinder() {
        assertEquals(Solid.Cylinder(radius = 1.0, height = 3.0), cylinder(1.0, 3.0))
    }

    @Test
    fun rect_without_centering_is_a_plain_polygon() {
        val expected = Region.Polygon(
            points = nonEmptyListOf(
                Vec2(0.0, 0.0),
                Vec2(2.0, 0.0),
                Vec2(2.0, 3.0),
                Vec2(0.0, 3.0),
            ),
        )
        assertEquals(expected, rect(2.0, 3.0))
    }

    @Test
    fun centering_rect_adds_a_transform() {
        assertTrue(rect(2.0, 3.0, centerX = true) is Region.Transform)
    }

    @Test
    fun circle_maps_to_region_circle() {
        assertEquals(Region.Circle(radius = 5.0), circle(5.0))
    }
}
