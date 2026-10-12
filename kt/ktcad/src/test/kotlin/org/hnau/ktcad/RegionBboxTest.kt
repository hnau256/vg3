package org.hnau.ktcad

import org.hnau.ktcad.bbox.Axis2
import org.hnau.ktcad.bbox.Axis2Values
import org.hnau.ktcad.bbox.bbox
import org.hnau.ktcad.bbox.direction
import org.hnau.ktcad.ir.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals

class RegionBboxTest {

    private val square = polygon(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(10.0, 10.0), Vec2(0.0, 10.0))

    private fun box(x0: Double, x1: Double, y0: Double, y1: Double) =
        Axis2Values(x = x0..x1, y = y0..y1)

    private fun assertBox(expected: Axis2Values<ClosedRange<Double>>, actual: Axis2Values<ClosedRange<Double>>) {
        val eps = 1e-9
        assertEquals(expected.x.start, actual.x.start, eps)
        assertEquals(expected.x.endInclusive, actual.x.endInclusive, eps)
        assertEquals(expected.y.start, actual.y.start, eps)
        assertEquals(expected.y.endInclusive, actual.y.endInclusive, eps)
    }

    @Test
    fun polygon_and_circle_are_exact() {
        assertBox(box(0.0, 10.0, 0.0, 10.0), square.bbox)
        assertBox(box(-5.0, 5.0, -5.0, 5.0), circle(5.0).bbox)
    }

    @Test
    fun contour_bounds_lines_and_arcs_by_control_points() {
        val lines = contour(Vec2(0.0, 0.0), lineTo(Vec2(10.0, 0.0)), lineRel(Vec2(0.0, 10.0)))
        assertBox(box(0.0, 10.0, 0.0, 10.0), lines.bbox)

        val arc = contour(Vec2(0.0, 0.0), arcTo(via = Vec2(5.0, 5.0), to = Vec2(10.0, 0.0)))
        assertBox(box(0.0, 10.0, 0.0, 5.0), arc.bbox)
    }

    @Test
    fun contour_outlines_bezier_poles_from_the_start_point() {
        val region = contour(Vec2(0.0, 0.0), bezierTo(Vec2(10.0, 0.0), Vec2(10.0, 10.0)))
        assertBox(box(0.0, 10.0, 0.0, 10.0), region.bbox)
    }

    @Test
    fun transforms_apply_to_the_box_corners() {
        assertBox(box(3.0, 4.0, 4.0, 5.0), rect(1.0, 1.0).translate(Vec2(3.0, 4.0)).bbox)
        assertBox(box(0.0, 2.0, -1.0, 0.0), rect(1.0, 1.0).scale(Vec2(2.0, -1.0)).bbox)
        assertBox(box(-1.0, 0.0, 0.0, 1.0), rect(1.0, 1.0).rotate(kotlin.math.PI / 2).bbox)
        assertBox(box(-1.0, 0.0, 0.0, 1.0), rect(1.0, 1.0).mirror(Axis2.X.direction).bbox)
    }

    @Test
    fun booleans_union_their_operands() {
        assertBox(box(-2.0, 10.0, -2.0, 10.0), square.fuse(circle(2.0)).bbox)
    }

    @Test
    fun offset_grows_and_shrink_stays_inside() {
        assertBox(box(-7.0, 7.0, -7.0, 7.0), circle(5.0).offset2d(2.0).bbox)
        assertBox(circle(5.0).bbox, circle(5.0).offset2d(-1.0).bbox)
    }
}
