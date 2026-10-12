package org.hnau.ktcad

import arrow.core.nonEmptyListOf
import org.hnau.ktcad.axis.A3
import org.hnau.ktcad.axis.A3Values
import org.hnau.ktcad.axis.direction
import org.hnau.ktcad.axis.times
import org.hnau.ktcad.ir.FaceSelection
import org.hnau.ktcad.ir.JoinKind
import org.hnau.ktcad.ir.TransformOp
import org.hnau.ktcad.ir.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals

class SolidExtTest {

    private val a = box(
        A3Values(
            x = 1.0,
            y = 1.0,
            z = 1.0,
        ),
    )
    private val b = sphere(1.0)

    @Test
    fun operators_map_to_booleans() {
        assertEquals(
            expected = fuse(
                listOf(
                    a,
                    b,
                ),
            ),
            actual = a + b,
        )
        assertEquals(
            expected = cut(
                base = a,
                tools = nonEmptyListOf(b),
            ),
            actual = a - b,
        )
        assertEquals(
            expected = common(
                listOf(
                    a,
                    b,
                ),
            ),
            actual = a * b,
        )
    }

    @Test
    fun axis_direction_builds_a_translation() {
        assertEquals(
            expected = a.translate(
                A3Values(
                    x = 3.0,
                    y = 0.0,
                    z = 0.0,
                ),
            ),
            actual = a.translate(A3.X.direction * 3.0),
        )
        assertEquals(
            expected = a.translate(
                A3Values(
                    x = -3.0,
                    y = 0.0,
                    z = 0.0,
                ),
            ),
            actual = a.translate(A3.X.direction * -3.0),
        )
        assertEquals(
            expected = a.translate(
                A3Values(
                    x = 0.0,
                    y = 3.0,
                    z = 0.0,
                ),
            ),
            actual = a.translate(A3.Y.direction * 3.0),
        )
        assertEquals(
            expected = a.translate(
                A3Values(
                    x = 0.0,
                    y = 0.0,
                    z = 5.0,
                ),
            ),
            actual = a.translate(A3.Z.direction * 5.0),
        )
        assertEquals(
            expected = a.translate(
                A3Values(
                    x = 0.0,
                    y = 0.0,
                    z = -5.0,
                ),
            ),
            actual = a.translate(A3.Z.direction * -5.0),
        )
    }

    @Test
    fun rotate_uses_the_given_axis() {
        val op = TransformOp.Rotate(
            center = Vec3(
                x = 0.0,
                y = 0.0,
                z = 0.0,
            ),
            axis = Vec3(
                x = 1.0,
                y = 0.0,
                z = 0.0,
            ),
            angle = 0.5,
        )
        assertEquals(
            expected = transform(
                op = op,
                target = a,
            ),
            actual = a.rotate(
                axis = A3.X.direction,
                angle = 0.5,
            ),
        )
    }

    @Test
    fun uniform_scale_expands_all_axes() {
        assertEquals(
            expected = a.scale(
                A3Values(
                    x = 2.0,
                    y = 2.0,
                    z = 2.0,
                ),
            ),
            actual = a.scale(2.0),
        )
    }

    @Test
    fun non_uniform_scale_uses_an_axis_value() {
        val op = TransformOp.Scale(
            value = Vec3(
                x = 2.0,
                y = 1.0,
                z = 1.0,
            ),
        )
        assertEquals(
            expected = transform(
                op = op,
                target = a,
            ),
            actual = a.scale(
                A3Values(
                    x = 2.0,
                    y = 1.0,
                    z = 1.0,
                ),
            ),
        )
    }

    @Test
    fun mirror_uses_the_given_normal() {
        val op = TransformOp.Mirror(
            center = Vec3(
                x = 0.0,
                y = 0.0,
                z = 0.0,
            ),
            normal = Vec3(
                x = 0.0,
                y = 0.0,
                z = 1.0,
            ),
        )
        assertEquals(
            expected = transform(
                op = op,
                target = a,
            ),
            actual = a.mirror(A3.Z.direction),
        )
    }

    @Test
    fun offset_maps_to_the_offset_node() {
        assertEquals(
            expected = offset(
                distance = 2.0,
                target = a,
            ),
            actual = a.offset(2.0),
        )
    }

    @Test
    fun wedge_angle_and_join_map_to_the_ir() {
        val wedge = cylinder(
            radius = 5.0,
            height = 10.0,
            angle = 1.5,
        )
        wedge as Solid.Cylinder
        assertEquals(
            expected = 1.5,
            actual = wedge.angle,
        )

        val grown = box(
            A3Values(
                x = 1.0,
                y = 1.0,
                z = 1.0,
            ),
        ).offset(
            distance = 2.0,
            join = JoinKind.INTERSECTION,
        )
        grown as Solid.Offset
        assertEquals(
            expected = JoinKind.INTERSECTION,
            actual = grown.join,
        )
    }

    @Test
    fun thickSolid_maps_to_the_thick_solid_node() {
        val hollow = box(
            A3Values(
                x = 10.0,
                y = 10.0,
                z = 10.0,
            ),
        ).thickSolid(
            offset = -1.0,
            expression = "is_parallel(face.normal, Z)",
        )
        hollow as Solid.ThickSolid
        assertEquals(
            expected = -1.0,
            actual = hollow.offset,
        )
        assertEquals(
            expected = FaceSelection.Selected(expression = "is_parallel(face.normal, Z)"),
            actual = hollow.faces,
        )
    }
}
