package org.hnau.ktcad

import arrow.core.nonEmptyListOf
import org.hnau.ktcad.axis.A2Values
import org.hnau.ktcad.axis.A3Values
import org.hnau.ktcad.ir.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrimitiveExtTest {

    @Test
    fun box_without_centering_is_a_plain_box() {
        assertEquals(
            expected = Solid.Box(
                width = 1.0,
                length = 2.0,
                height = 3.0,
            ),
            actual = box(
                A3Values(
                    x = 1.0,
                    y = 2.0,
                    z = 3.0,
                ),
            ),
        )
    }

    @Test
    fun centering_an_axis_adds_a_transform() {
        assertTrue(
            box(
                size = A3Values(
                    x = 1.0,
                    y = 2.0,
                    z = 3.0,
                ),
                centered = A3Values(
                    x = true,
                    y = false,
                    z = false,
                ),
            )
                is Solid.Transform,
        )
        assertTrue(
            cylinder(
                radius = 1.0,
                height = 3.0,
                centerZ = true,
            ) is Solid.Transform,
        )
    }

    @Test
    fun cylinder_without_centering_is_a_plain_cylinder() {
        assertEquals(
            expected = Solid.Cylinder(
                radius = 1.0,
                height = 3.0,
            ),
            actual = cylinder(
                radius = 1.0,
                height = 3.0,
            ),
        )
    }

    @Test
    fun rect_without_centering_is_a_plain_polygon() {
        val expected = Region.Polygon(
            points = nonEmptyListOf(
                Vec2(
                    x = 0.0,
                    y = 0.0,
                ),
                Vec2(
                    x = 2.0,
                    y = 0.0,
                ),
                Vec2(
                    x = 2.0,
                    y = 3.0,
                ),
                Vec2(
                    x = 0.0,
                    y = 3.0,
                ),
            ),
        )
        assertEquals(
            expected = expected,
            actual = rect(
                A2Values(
                    x = 2.0,
                    y = 3.0,
                ),
            ),
        )
    }

    @Test
    fun centering_rect_adds_a_transform() {
        assertTrue(
            rect(
                size = A2Values(
                    x = 2.0,
                    y = 3.0,
                ),
                centered = A2Values(
                    x = true,
                    y = false,
                ),
            )
                is Region.Transform,
        )
    }

    @Test
    fun circle_maps_to_region_circle() {
        assertEquals(
            expected = Region.Circle(radius = 5.0),
            actual = circle(5.0),
        )
    }
}
