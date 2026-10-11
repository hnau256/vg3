package org.hnau.ktcad

import arrow.core.nonEmptyListOf
import org.hnau.ktcad.bbox.Axis3Values
import org.hnau.ktcad.bbox.bbox
import org.hnau.ktcad.ir.RadiusSpec
import org.hnau.ktcad.ir.TransformOp
import org.hnau.ktcad.ir.Vec2
import org.hnau.ktcad.ir.Vec3
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class SolidBboxTest {

    private val square = polygon(Vec2(0.0, 0.0), Vec2(2.0, 0.0), Vec2(2.0, 2.0), Vec2(0.0, 2.0))

    private fun box(x0: Double, x1: Double, y0: Double, y1: Double, z0: Double, z1: Double) =
        Axis3Values(x = x0..x1, y = y0..y1, z = z0..z1)

    private fun assertBox(expected: Axis3Values<ClosedRange<Double>>, actual: Axis3Values<ClosedRange<Double>>) {
        val eps = 1e-9
        assertEquals(expected.x.start, actual.x.start, eps)
        assertEquals(expected.x.endInclusive, actual.x.endInclusive, eps)
        assertEquals(expected.y.start, actual.y.start, eps)
        assertEquals(expected.y.endInclusive, actual.y.endInclusive, eps)
        assertEquals(expected.z.start, actual.z.start, eps)
        assertEquals(expected.z.endInclusive, actual.z.endInclusive, eps)
    }

    @Test
    fun primitives_are_exact() {
        assertBox(box(0.0, 1.0, 0.0, 2.0, 0.0, 3.0), box(1.0, 2.0, 3.0).bbox)
        assertBox(box(-3.0, 3.0, -3.0, 3.0, -3.0, 3.0), sphere(3.0).bbox)
        assertBox(box(-2.0, 2.0, -2.0, 2.0, 0.0, 5.0), cylinder(2.0, 5.0).bbox)
        assertBox(box(-3.0, 3.0, -3.0, 3.0, 0.0, 4.0), cone(4.0, 3.0, 1.0).bbox)
        assertBox(box(-6.0, 6.0, -6.0, 6.0, -1.0, 1.0), torus(5.0, 1.0).bbox)
        assertBox(box(0.0, 4.0, 0.0, 5.0, 0.0, 6.0), wedge(6.0, 5.0, 2.0, 4.0).bbox)
    }

    @Test
    fun halfspace_is_unbounded() {
        val halfspace = halfspace().bbox
        assertEquals(Double.NEGATIVE_INFINITY, halfspace.x.start)
        assertEquals(Double.POSITIVE_INFINITY, halfspace.y.endInclusive)
        assertEquals(0.0, halfspace.z.endInclusive)
    }

    @Test
    fun polyhedron_is_exact() {
        val solid = polyhedron(
            faces = nonEmptyListOf(listOf(0, 1, 2)),
            points = nonEmptyListOf(Vec3(0.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 2.0)),
        )
        assertBox(box(0.0, 1.0, 0.0, 1.0, 0.0, 2.0), solid.bbox)
    }

    @Test
    fun extrude_and_revolve_build_from_the_profile() {
        assertBox(box(0.0, 2.0, 0.0, 2.0, 0.0, 5.0), extrude(5.0, square).bbox)

        val ring = polygon(Vec2(2.0, 0.0), Vec2(4.0, 0.0), Vec2(4.0, 3.0), Vec2(2.0, 3.0))
        assertBox(box(-4.0, 4.0, 0.0, 3.0, -4.0, 4.0), revolve(2.0 * PI, ring).bbox)
    }

    @Test
    fun loft_unions_its_sections_and_sweep_grows_the_path() {
        val lower = Path(p(0.0, 0.0, 0.0), lineTo(4.0, 0.0, 0.0), lineTo(4.0, 4.0, 0.0))
        val upper = Path(p(0.0, 0.0, 10.0), lineTo(2.0, 0.0, 10.0), lineTo(2.0, 2.0, 10.0))
        assertBox(box(0.0, 4.0, 0.0, 4.0, 0.0, 10.0), loft(sections = nonEmptyListOf(lower, upper)).bbox)

        val path = Path(p(0.0, 0.0, 0.0), lineTo(10.0, 0.0, 0.0))
        assertBox(box(-2.0, 12.0, -2.0, 2.0, -2.0, 2.0), sweep(path = path, profile = square).bbox)
    }

    @Test
    fun booleans_union_operands_but_cut_ignores_tools() {
        assertBox(
            box(0.0, 6.0, 0.0, 1.0, 0.0, 1.0),
            fuse(listOf(box(1.0, 1.0, 1.0), box(1.0, 1.0, 1.0).translate(5.0, 0.0, 0.0))).bbox,
        )
        assertBox(
            box(0.0, 4.0, 0.0, 4.0, 0.0, 4.0),
            cut(box(4.0, 4.0, 4.0), nonEmptyListOf(halfspace().up(2.0))).bbox,
        )
    }

    @Test
    fun transforms_apply_to_the_box_corners() {
        assertBox(box(2.0, 3.0, 3.0, 4.0, 4.0, 5.0), box(1.0, 1.0, 1.0).translate(2.0, 3.0, 4.0).bbox)
        assertBox(box(-1.0, 0.0, 0.0, 1.0, 0.0, 1.0), box(1.0, 1.0, 1.0).rotateZ(PI / 2).bbox)
        assertBox(box(0.0, 2.0, 0.0, 2.0, 0.0, 2.0), box(1.0, 1.0, 1.0).scale(2.0).bbox)
        assertBox(box(0.0, 1.0, 0.0, 1.0, -1.0, 0.0), box(1.0, 1.0, 1.0).mirrorXY().bbox)

        val matrix = Solid.Transform(
            op = TransformOp.Matrix(m = listOf(1.0, 0.0, 0.0, 2.0, 0.0, 1.0, 0.0, 3.0, 0.0, 0.0, 1.0, 4.0, 0.0, 0.0, 0.0, 1.0)),
            target = box(1.0, 1.0, 1.0),
        )
        assertBox(box(2.0, 3.0, 3.0, 4.0, 4.0, 5.0), matrix.bbox)
    }

    @Test
    fun offset_grows_and_fillet_keeps_the_box() {
        assertBox(box(-1.0, 2.0, -1.0, 2.0, -1.0, 2.0), box(1.0, 1.0, 1.0).offset(1.0).bbox)

        val filleted = fillet(radius = RadiusSpec.All(0.1), target = box(1.0, 1.0, 1.0))
        assertBox(box(0.0, 1.0, 0.0, 1.0, 0.0, 1.0), filleted.bbox)
    }
}
