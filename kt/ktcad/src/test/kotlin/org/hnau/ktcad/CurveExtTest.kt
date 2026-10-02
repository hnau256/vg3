package org.hnau.ktcad

import org.hnau.ktcad.ir.Curve2
import org.hnau.ktcad.ir.Curve3
import org.hnau.ktcad.ir.Point2
import org.hnau.ktcad.ir.Point3
import kotlin.test.Test
import kotlin.test.assertEquals

class CurveExtTest {

    @Test
    fun lineTo_appends_absolute_segment() {
        val profile = p(0.0, 0.0).lineTo(10.0, 0.0).lineTo(10.0, 10.0)
        assertEquals(
            listOf(
                Curve2.Line(Point2(10.0, 0.0)),
                Curve2.Line(Point2(10.0, 10.0)),
            ),
            profile.edges.toList(),
        )
        assertEquals(Point2(10.0, 10.0), profile.current)
    }

    @Test
    fun lineRel_is_relative_to_current_point() {
        val profile = p(5.0, 5.0).lineRel(2.0, 0.0).lineRel(0.0, 3.0)
        assertEquals(
            listOf(
                Curve2.Line(Point2(7.0, 5.0)),
                Curve2.Line(Point2(7.0, 8.0)),
            ),
            profile.edges.toList(),
        )
    }

    @Test
    fun arcRel_offsets_both_via_and_to_from_current() {
        val profile = p(0.0, 0.0).arcRel(viaDx = 1.0, viaDy = 1.0, toDx = 2.0, toDy = 0.0)
        assertEquals(
            Curve2.Arc(via = Point2(1.0, 1.0), to = Point2(2.0, 0.0)),
            profile.edges.single(),
        )
    }

    @Test
    fun path_rel_offsets_are_3d() {
        val path = p(0.0, 0.0, 0.0).lineRel(1.0, 2.0, 3.0)
        assertEquals(Curve3.Line(Point3(1.0, 2.0, 3.0)), path.edges.single())
    }

    @Test
    fun built_profile_feeds_extrude() {
        val solid = extrude(5.0, p(0.0, 0.0).lineTo(10.0, 0.0).lineTo(10.0, 10.0).lineTo(0.0, 10.0))
        // The engine auto-closes the profile, so this is a valid prism.
        check(solid is Solid.Extrude)
    }

    @Test
    fun circle_is_two_arcs_returning_to_start() {
        val c = circle(radius = 5.0)
        assertEquals(2, c.edges.size)
        assertEquals(Point2(5.0, 0.0), c.start)
        assertEquals(c.start, c.current)
    }

    @Test
    fun polygon_profile_chains_lines() {
        val poly = polygon(Point2(0.0, 0.0), Point2(4.0, 0.0), Point2(4.0, 3.0))
        assertEquals(
            listOf(Curve2.Line(Point2(4.0, 0.0)), Curve2.Line(Point2(4.0, 3.0))),
            poly.edges.toList(),
        )
    }

    @Test
    fun polyline_is_open_and_polygon_closes() {
        val a = Point3(0.0, 0.0, 0.0)
        val b = Point3(1.0, 0.0, 0.0)
        val c = Point3(1.0, 1.0, 0.0)
        assertEquals(2, polyline(a, b, c).edges.size)
        assertEquals(3, polygon(a, b, c).edges.size)   // + closing line back to start
        assertEquals(Curve3.Line(a), polygon(a, b, c).edges.last())
    }
}
