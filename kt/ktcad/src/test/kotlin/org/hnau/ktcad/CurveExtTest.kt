package org.hnau.ktcad

import arrow.core.nonEmptyListOf
import org.hnau.ktcad.ir.Curve2
import org.hnau.ktcad.ir.Curve3
import org.hnau.ktcad.ir.Vec2
import org.hnau.ktcad.ir.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CurveExtTest {

    @Test
    fun contour_builds_from_segments() {
        val region = contour(
            start = p(0.0, 0.0),
            lineTo(10.0, 0.0),
            lineRel(0.0, 10.0),
        ) as Region.Contour
        assertEquals(
            listOf(Curve2.Line(Vec2(10.0, 0.0)), Curve2.Line(Vec2(10.0, 10.0))),
            region.edges.toList(),
        )
        assertEquals(Vec2(0.0, 0.0), region.start)
    }

    @Test
    fun path_builds_from_segments() {
        val path = Path(p(0.0, 0.0, 0.0), lineTo(1.0, 2.0, 3.0))
        assertEquals(listOf(Curve3.Line(Vec3(1.0, 2.0, 3.0))), path.edges.toList())
    }

    @Test
    fun arcRel_is_relative_to_the_current_point() {
        val region = contour(p(0.0, 0.0), lineTo(2.0, 0.0), arcRel(viaDx = 0.0, viaDy = 1.0, toDx = 0.0, toDy = 2.0)) as Region.Contour
        assertEquals(
            Curve2.Arc(via = Vec2(2.0, 1.0), to = Vec2(2.0, 2.0)),
            region.edges.last(),
        )
    }

    @Test
    fun circle_is_a_region_circle() {
        assertEquals(Region.Circle(radius = 5.0), circle(5.0))
    }

    @Test
    fun polygon_region_holds_its_points() {
        val poly = polygon(Vec2(0.0, 0.0), Vec2(4.0, 0.0), Vec2(4.0, 3.0))
        assertEquals(
            Region.Polygon(nonEmptyListOf(Vec2(0.0, 0.0), Vec2(4.0, 0.0), Vec2(4.0, 3.0))),
            poly,
        )
    }

    @Test
    fun polyline_is_open_and_polygon_closes() {
        val a = Vec3(0.0, 0.0, 0.0)
        val b = Vec3(1.0, 0.0, 0.0)
        val c = Vec3(1.0, 1.0, 0.0)
        assertEquals(2, polyline(a, b, c).edges.size)
        assertEquals(3, polygon(a, b, c).edges.size)   // + closing line back to start
        assertEquals(Curve3.Line(a), polygon(a, b, c).edges.last())
    }

    @Test
    fun built_region_feeds_extrude() {
        val solid = extrude(5.0, polygon(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(10.0, 10.0), Vec2(0.0, 10.0)))
        check(solid is Solid.Extrude)
    }

    private val helix: PathSegment = { Curve3.Helix(pitch = 1.0, height = 4.0, right_handed = true) }

    @Test
    fun helix_can_be_followed_by_an_absolute_segment() {
        val path = Path(p(0.0, 0.0, 0.0), helix, lineTo(0.0, 0.0, 10.0))
        assertEquals(2, path.edges.size)
    }

    @Test
    fun relative_segment_after_helix_fails() {
        assertFailsWith<IllegalStateException> {
            Path(p(0.0, 0.0, 0.0), helix, lineRel(0.0, 0.0, 1.0))
        }
    }
}
