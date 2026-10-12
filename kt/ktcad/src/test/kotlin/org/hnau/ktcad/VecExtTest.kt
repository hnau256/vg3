package org.hnau.ktcad

import org.hnau.commons.kotlin.foldBoolean
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
        val v3 = Vec3(
            x = 1.0,
            y = 2.0,
            z = 3.0,
        )
        assertEquals(
            expected = 1.0,
            actual = v3[Axis3.X],
        )
        assertEquals(
            expected = 2.0,
            actual = v3[Axis3.Y],
        )
        assertEquals(
            expected = 3.0,
            actual = v3[Axis3.Z],
        )

        val v2 = Vec2(
            x = 4.0,
            y = 5.0,
        )
        assertEquals(
            expected = 4.0,
            actual = v2[Axis2.X],
        )
        assertEquals(
            expected = 5.0,
            actual = v2[Axis2.Y],
        )
    }

    @Test
    fun map_transforms_every_component() {
        assertEquals(
            expected = Vec3(
                x = 2.0,
                y = 4.0,
                z = 6.0,
            ),
            actual = Vec3(
                x = 1.0,
                y = 2.0,
                z = 3.0,
            ).map { it * 2 },
        )
        assertEquals(
            expected = Vec3(
                x = 1.0,
                y = 2.0,
                z = -3.0,
            ),
            actual = Vec3(
                x = 1.0,
                y = 2.0,
                z = 3.0,
            ).map { axis, value ->
                (axis == Axis3.Z).foldBoolean(
                    ifTrue = { -value },
                    ifFalse = { value },
                )
            },
        )
        assertEquals(
            expected = Vec2(
                x = 2.0,
                y = -4.0,
            ),
            actual = Vec2(
                x = 1.0,
                y = 4.0,
            ).map { axis, value ->
                (axis == Axis2.Y).foldBoolean(
                    ifTrue = { -value },
                    ifFalse = { value * 2 },
                )
            },
        )
    }

    @Test
    fun combineWith_pairs_components_by_axis() {
        assertEquals(
            expected = Vec3(
                x = 5.0,
                y = 7.0,
                z = 9.0,
            ),
            actual = Vec3(
                x = 1.0,
                y = 2.0,
                z = 3.0,
            ).combineWith(
                other = Vec3(
                    x = 4.0,
                    y = 5.0,
                    z = 6.0,
                ),
            ) { a, b -> a + b },
        )
        assertEquals(
            expected = Vec2(
                x = 3.0,
                y = 7.0,
            ),
            actual = Vec2(
                x = 1.0,
                y = 3.0,
            ).combineWith(
                other = Vec2(
                    x = 2.0,
                    y = 4.0,
                ),
            ) { axis, a, b ->
                (axis == Axis2.X).foldBoolean(
                    ifTrue = { a + b },
                    ifFalse = { a + b },
                )
            },
        )
    }

    @Test
    fun create_builds_from_an_axis_function() {
        assertEquals(
            expected = Vec3(
                x = 1.0,
                y = 0.0,
                z = 0.0,
            ),
            actual = vec3 { axis ->
                (axis == Axis3.X).foldBoolean(
                    ifTrue = { 1.0 },
                    ifFalse = { 0.0 },
                )
            },
        )
        assertEquals(
            expected = Vec2(
                x = 0.0,
                y = 1.0,
            ),
            actual = vec2 { axis ->
                (axis == Axis2.Y).foldBoolean(
                    ifTrue = { 1.0 },
                    ifFalse = { 0.0 },
                )
            },
        )
    }

    @Test
    fun times_scales_every_component() {
        assertEquals(
            expected = Vec3(
                x = 2.0,
                y = 4.0,
                z = 6.0,
            ),
            actual = Vec3(
                x = 1.0,
                y = 2.0,
                z = 3.0,
            ) * 2.0,
        )
        assertEquals(
            expected = Vec3(
                x = 2.0,
                y = 4.0,
                z = 6.0,
            ),
            actual = 2.0 * Vec3(
                x = 1.0,
                y = 2.0,
                z = 3.0,
            ),
        )
        assertEquals(
            expected = Vec2(
                x = 2.0,
                y = 4.0,
            ),
            actual = Vec2(
                x = 1.0,
                y = 2.0,
            ) * 2.0,
        )
        assertEquals(
            expected = Vec3(
                x = -1.0,
                y = 0.0,
                z = 0.0,
            ),
            actual = Vec3(
                x = 1.0,
                y = 0.0,
                z = 0.0,
            ) * -1.0,
        )
    }

    @Test
    fun plus_minus_and_times_combine_components() {
        val a = Vec3(
            x = 1.0,
            y = 2.0,
            z = 3.0,
        )
        val b = Vec3(
            x = 4.0,
            y = 5.0,
            z = 6.0,
        )
        assertEquals(
            expected = Vec3(
                x = 5.0,
                y = 7.0,
                z = 9.0,
            ),
            actual = a + b,
        )
        assertEquals(
            expected = Vec3(
                x = -3.0,
                y = -3.0,
                z = -3.0,
            ),
            actual = a - b,
        )
        assertEquals(
            expected = Vec3(
                x = 4.0,
                y = 10.0,
                z = 18.0,
            ),
            actual = a * b,
        )
    }

    @Test
    fun copy_transforms_a_single_component() {
        assertEquals(
            expected = Vec3(
                x = 1.0,
                y = -2.0,
                z = 3.0,
            ),
            actual = Vec3(
                x = 1.0,
                y = 2.0,
                z = 3.0,
            ).copy(axis = Axis3.Y) { -it },
        )
        assertEquals(
            expected = Vec2(
                x = -1.0,
                y = 2.0,
            ),
            actual = Vec2(
                x = 1.0,
                y = 2.0,
            ).copy(axis = Axis2.X) { -it },
        )
    }
}
