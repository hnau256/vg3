package org.hnau.ktcad

import arrow.core.nonEmptyListOf
import org.hnau.ktcad.axis.Axis3
import org.hnau.ktcad.axis.Axis3Values
import org.hnau.ktcad.axis.direction
import org.hnau.ktcad.axis.times
import org.hnau.ktcad.ir.FaceSelection
import org.hnau.ktcad.ir.JoinKind
import org.hnau.ktcad.ir.TransformOp
import org.hnau.ktcad.ir.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals

class SolidExtTest {

    private val a = box(Axis3Values(x = 1.0, y = 1.0, z = 1.0))
    private val b = sphere(1.0)

    @Test
    fun operators_map_to_booleans() {
        assertEquals(fuse(listOf(a, b)), a + b)
        assertEquals(cut(base = a, tools = nonEmptyListOf(b)), a - b)
        assertEquals(common(listOf(a, b)), a * b)
    }

    @Test
    fun axis_direction_builds_a_translation() {
        assertEquals(a.translate(Axis3Values(x = 3.0, y = 0.0, z = 0.0)), a.translate(Axis3.X.direction * 3.0))
        assertEquals(a.translate(Axis3Values(x = -3.0, y = 0.0, z = 0.0)), a.translate(Axis3.X.direction * -3.0))
        assertEquals(a.translate(Axis3Values(x = 0.0, y = 3.0, z = 0.0)), a.translate(Axis3.Y.direction * 3.0))
        assertEquals(a.translate(Axis3Values(x = 0.0, y = 0.0, z = 5.0)), a.translate(Axis3.Z.direction * 5.0))
        assertEquals(a.translate(Axis3Values(x = 0.0, y = 0.0, z = -5.0)), a.translate(Axis3.Z.direction * -5.0))
    }

    @Test
    fun rotate_uses_the_given_axis() {
        val op = TransformOp.Rotate(center = Vec3(0.0, 0.0, 0.0), axis = Vec3(1.0, 0.0, 0.0), angle = 0.5)
        assertEquals(transform(op, a), a.rotate(Axis3.X.direction, 0.5))
    }

    @Test
    fun uniform_scale_expands_all_axes() {
        assertEquals(a.scale(Axis3Values(x = 2.0, y = 2.0, z = 2.0)), a.scale(2.0))
    }

    @Test
    fun non_uniform_scale_uses_an_axis_value() {
        val op = TransformOp.Scale(value = Vec3(2.0, 1.0, 1.0))
        assertEquals(transform(op, a), a.scale(Axis3Values(x = 2.0, y = 1.0, z = 1.0)))
    }

    @Test
    fun mirror_uses_the_given_normal() {
        val op = TransformOp.Mirror(center = Vec3(0.0, 0.0, 0.0), normal = Vec3(0.0, 0.0, 1.0))
        assertEquals(transform(op, a), a.mirror(Axis3.Z.direction))
    }

    @Test
    fun offset_maps_to_the_offset_node() {
        assertEquals(offset(distance = 2.0, target = a), a.offset(2.0))
    }

    @Test
    fun wedge_angle_and_join_map_to_the_ir() {
        val wedge = cylinder(5.0, 10.0, angle = 1.5)
        wedge as Solid.Cylinder
        assertEquals(1.5, wedge.angle)

        val grown = box(Axis3Values(x = 1.0, y = 1.0, z = 1.0)).offset(2.0, JoinKind.INTERSECTION)
        grown as Solid.Offset
        assertEquals(JoinKind.INTERSECTION, grown.join)
    }

    @Test
    fun thickSolid_maps_to_the_thick_solid_node() {
        val hollow = box(Axis3Values(x = 10.0, y = 10.0, z = 10.0)).thickSolid(-1.0, "is_parallel(face.normal, Z)")
        hollow as Solid.ThickSolid
        assertEquals(-1.0, hollow.offset)
        assertEquals(
            FaceSelection.Selected(expression = "is_parallel(face.normal, Z)"),
            hollow.faces,
        )
    }
}
