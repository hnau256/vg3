package org.hnau.ktcad

import org.hnau.ktcad.ir.Vec3
import org.hnau.ktcad.ir.TransformOp
import kotlin.test.Test
import kotlin.test.assertEquals

class SolidExtTest {

    private val a = box(1.0, 1.0, 1.0)
    private val b = sphere(1.0)

    @Test
    fun operators_map_to_booleans() {
        assertEquals(fuse(listOf(a, b)), a + b)
        assertEquals(cut(base = a, tools = listOf(b)), a - b)
        assertEquals(common(listOf(a, b)), a * b)
    }

    @Test
    fun up_is_translate_along_z() {
        assertEquals(a.translate(0.0, 0.0, 5.0), a.up(5.0))
        assertEquals(a.translate(0.0, 0.0, -5.0), a.down(5.0))
    }

    @Test
    fun axes_follow_the_documented_convention() {
        assertEquals(a.translate(3.0, 0.0, 0.0), a.right(3.0))
        assertEquals(a.translate(-3.0, 0.0, 0.0), a.left(3.0))
        assertEquals(a.translate(0.0, 3.0, 0.0), a.forward(3.0))
        assertEquals(a.translate(0.0, -3.0, 0.0), a.back(3.0))
    }

    @Test
    fun rotateX_uses_the_x_axis() {
        val expected = a.rotate(Vec3(1.0, 0.0, 0.0), 0.5)
        assertEquals(expected, a.rotateX(0.5))
    }

    @Test
    fun uniform_scale_expands_all_axes() {
        assertEquals(a.scale(2.0, 2.0, 2.0), a.scale(2.0))
    }

    @Test
    fun mirrorXY_is_mirror_about_z_normal() {
        val op = TransformOp.Mirror(center = org.hnau.ktcad.ir.Vec3(0.0, 0.0, 0.0), normal = Vec3(0.0, 0.0, 1.0))
        assertEquals(transform(op, a), a.mirrorXY())
    }

    @Test
    fun offset_maps_to_the_offset_node() {
        assertEquals(offset(distance = 2.0, target = a), a.offset(2.0))
    }
}
