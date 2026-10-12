package org.hnau.ktcad

import arrow.core.nonEmptyListOf
import org.hnau.ktcad.bbox.Axis3
import org.hnau.ktcad.ir.Vec3
import org.hnau.ktcad.ir.FaceSelection
import org.hnau.ktcad.ir.JoinKind
import org.hnau.ktcad.ir.TransformOp
import kotlin.test.Test
import kotlin.test.assertEquals

class SolidExtTest {

    private val a = box(1.0, 1.0, 1.0)
    private val b = sphere(1.0)

    @Test
    fun operators_map_to_booleans() {
        assertEquals(fuse(listOf(a, b)), a + b)
        assertEquals(cut(base = a, tools = nonEmptyListOf(b)), a - b)
        assertEquals(common(listOf(a, b)), a * b)
    }

    @Test
    fun translate_along_an_axis_uses_the_documented_convention() {
        assertEquals(a.translate(3.0, 0.0, 0.0), a.translate(Axis3.X, 3.0))
        assertEquals(a.translate(-3.0, 0.0, 0.0), a.translate(Axis3.X, -3.0))
        assertEquals(a.translate(0.0, 3.0, 0.0), a.translate(Axis3.Y, 3.0))
        assertEquals(a.translate(0.0, 0.0, 5.0), a.translate(Axis3.Z, 5.0))
        assertEquals(a.translate(0.0, 0.0, -5.0), a.translate(Axis3.Z, -5.0))
    }

    @Test
    fun rotate_uses_the_given_axis() {
        val op = TransformOp.Rotate(center = Vec3(0.0, 0.0, 0.0), axis = Vec3(1.0, 0.0, 0.0), angle = 0.5)
        assertEquals(transform(op, a), a.rotate(Axis3.X, 0.5))
    }

    @Test
    fun uniform_scale_expands_all_axes() {
        assertEquals(a.scale(2.0, 2.0, 2.0), a.scale(2.0))
    }

    @Test
    fun mirror_uses_the_given_axis_as_the_plane_normal() {
        val op = TransformOp.Mirror(center = Vec3(0.0, 0.0, 0.0), normal = Vec3(0.0, 0.0, 1.0))
        assertEquals(transform(op, a), a.mirror(Axis3.Z))
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

        val grown = box(1.0, 1.0, 1.0).offset(2.0, JoinKind.INTERSECTION)
        grown as Solid.Offset
        assertEquals(JoinKind.INTERSECTION, grown.join)
    }

    @Test
    fun thickSolid_maps_to_the_thick_solid_node() {
        val hollow = box(10.0, 10.0, 10.0).thickSolid(-1.0, "is_parallel(face.normal, Z)")
        hollow as Solid.ThickSolid
        assertEquals(-1.0, hollow.offset)
        assertEquals(
            FaceSelection.Selected(expression = "is_parallel(face.normal, Z)"),
            hollow.faces,
        )
    }
}
