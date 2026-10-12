package org.hnau.ktcad

import org.hnau.ktcad.axis.Axis2
import org.hnau.ktcad.axis.Axis3
import org.hnau.ktcad.bbox.combineWith
import org.hnau.ktcad.bbox.copy
import org.hnau.ktcad.bbox.get
import org.hnau.ktcad.bbox.map
import org.hnau.ktcad.bbox.minus
import org.hnau.ktcad.bbox.plus
import org.hnau.ktcad.bbox.times
import org.hnau.ktcad.bbox.vec2
import org.hnau.ktcad.bbox.vec3
import org.hnau.ktcad.ir.Vec2
import org.hnau.ktcad.ir.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals

class VecExtTest {

    @Test
    fun get_indexes_by_axis() {
        val v3 = Vec3(x = 1.0, y = 2.0, z = 3.0)
        assertEquals(1.0, v3[Axis3.X])
        assertEquals(2.0, v3[Axis3.Y])
        assertEquals(3.0, v3[Axis3.Z])

        val v2 = Vec2(x = 4.0, y = 5.0)
        assertEquals(4.0, v2[Axis2.X])
        assertEquals(5.0, v2[Axis2.Y])
    }

    @Test
    fun map_transforms_every_component() {
        assertEquals(
            Vec3(x = 2.0, y = 4.0, z = 6.0),
            Vec3(x = 1.0, y = 2.0, z = 3.0).map { it * 2 },
        )
        assertEquals(
            Vec3(x = 1.0, y = 2.0, z = -3.0),
            Vec3(x = 1.0, y = 2.0, z = 3.0).map { axis, value ->
                if (axis == Axis3.Z) -value else value
            },
        )
        assertEquals(
            Vec2(x = 2.0, y = -4.0),
            Vec2(x = 1.0, y = 4.0).map { axis, value ->
                if (axis == Axis2.Y) -value else value * 2
            },
        )
    }

    @Test
    fun combineWith_pairs_components_by_axis() {
        assertEquals(
            Vec3(x = 5.0, y = 7.0, z = 9.0),
            Vec3(x = 1.0, y = 2.0, z = 3.0)
                .combineWith(Vec3(x = 4.0, y = 5.0, z = 6.0)) { a, b -> a + b },
        )
        assertEquals(
            Vec2(x = 3.0, y = 7.0),
            Vec2(x = 1.0, y = 3.0)
                .combineWith(Vec2(x = 2.0, y = 4.0)) { axis, a, b ->
                    if (axis == Axis2.X) a + b else a + b
                },
        )
    }

    @Test
    fun create_builds_from_an_axis_function() {
        assertEquals(
            Vec3(x = 1.0, y = 0.0, z = 0.0),
            vec3 { axis -> if (axis == Axis3.X) 1.0 else 0.0 },
        )
        assertEquals(
            Vec2(x = 0.0, y = 1.0),
            vec2 { axis -> if (axis == Axis2.Y) 1.0 else 0.0 },
        )
    }

    @Test
    fun times_scales_every_component() {
        assertEquals(Vec3(x = 2.0, y = 4.0, z = 6.0), Vec3(x = 1.0, y = 2.0, z = 3.0) * 2.0)
        assertEquals(Vec3(x = 2.0, y = 4.0, z = 6.0), 2.0 * Vec3(x = 1.0, y = 2.0, z = 3.0))
        assertEquals(Vec2(x = 2.0, y = 4.0), Vec2(x = 1.0, y = 2.0) * 2.0)
        assertEquals(Vec3(x = -1.0, y = 0.0, z = 0.0), Vec3(x = 1.0, y = 0.0, z = 0.0) * -1.0)
    }

    @Test
    fun plus_minus_and_times_combine_components() {
        val a = Vec3(x = 1.0, y = 2.0, z = 3.0)
        val b = Vec3(x = 4.0, y = 5.0, z = 6.0)
        assertEquals(Vec3(x = 5.0, y = 7.0, z = 9.0), a + b)
        assertEquals(Vec3(x = -3.0, y = -3.0, z = -3.0), a - b)
        assertEquals(Vec3(x = 4.0, y = 10.0, z = 18.0), a * b)
    }

    @Test
    fun copy_transforms_a_single_component() {
        assertEquals(
            Vec3(x = 1.0, y = -2.0, z = 3.0),
            Vec3(x = 1.0, y = 2.0, z = 3.0).copy(Axis3.Y) { -it },
        )
        assertEquals(
            Vec2(x = -1.0, y = 2.0),
            Vec2(x = 1.0, y = 2.0).copy(Axis2.X) { -it },
        )
    }
}
