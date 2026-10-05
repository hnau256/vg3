package org.hnau.ktcad

import arrow.core.nonEmptyListOf
import org.hnau.ktcad.ir.Curve2
import org.hnau.ktcad.ir.Curve3
import org.hnau.ktcad.ir.Vec2
import org.hnau.ktcad.ir.Vec3
import org.hnau.ktcad.ir.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CurveExtTest {

    @Test
    fun profile_builds_from_segments() {
        val profile = Profile(
            start = p(0.0, 0.0),
            lineTo(10.0, 0.0),
            lineRel(0.0, 10.0),
        )
        assertEquals(
            listOf(Curve2.Line(Vec2(10.0, 0.0)), Curve2.Line(Vec2(10.0, 10.0))),
            profile.edges.toList(),
        )
        assertEquals(Vec2(0.0, 0.0), profile.start)
    }

    @Test
    fun profile_accepts_a_non_empty_list_constructor() {
        val profile = Profile(start = p(0.0, 0.0), edges = nonEmptyListOf(Curve2.Line(p(1.0, 0.0))))
        assertEquals(listOf(Curve2.Line(Vec2(1.0, 0.0))), profile.edges.toList())
    }

    @Test
    fun path_builds_from_segments() {
        val path = Path(p(0.0, 0.0, 0.0), lineTo(1.0, 2.0, 3.0))
        assertEquals(listOf(Curve3.Line(Vec3(1.0, 2.0, 3.0))), path.edges.toList())
    }

    @Test
    fun arcRel_is_relative_to_the_current_point() {
        val profile = Profile(p(0.0, 0.0), lineTo(2.0, 0.0), arcRel(viaDx = 0.0, viaDy = 1.0, toDx = 0.0, toDy = 2.0))
        assertEquals(
            Curve2.Arc(via = Vec2(2.0, 1.0), to = Vec2(2.0, 2.0)),
            profile.edges.last(),
        )
    }

    @Test
    fun circle_is_two_arcs_returning_to_start() {
        val c = circle(radius = 5.0)
        assertEquals(2, c.edges.size)
        assertEquals(Vec2(5.0, 0.0), c.start)
        assertEquals(Vec2(5.0, 0.0), (c.edges.last() as Curve2.Arc).to)
    }

    @Test
    fun polygon_profile_chains_lines() {
        val poly = polygon(Vec2(0.0, 0.0), Vec2(4.0, 0.0), Vec2(4.0, 3.0))
        assertEquals(
            listOf(Curve2.Line(Vec2(4.0, 0.0)), Curve2.Line(Vec2(4.0, 3.0))),
            poly.edges.toList(),
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
    fun built_profile_feeds_extrude() {
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
