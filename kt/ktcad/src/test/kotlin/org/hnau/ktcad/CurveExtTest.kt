package org.hnau.ktcad

import arrow.core.nonEmptyListOf
import org.hnau.ktcad.axis.A2Values
import org.hnau.ktcad.axis.A3Values
import org.hnau.ktcad.axis.toVec3
import org.hnau.ktcad.ir.Curve2
import org.hnau.ktcad.ir.Curve3
import org.hnau.ktcad.ir.Vec2
import org.hnau.ktcad.ir.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CurveExtTest {

    private fun a2(
        x: Double,
        y: Double,
    ) = A2Values(
        x = x,
        y = y,
    )
    private fun a3(
        x: Double,
        y: Double,
        z: Double,
    ) = A3Values(
        x = x,
        y = y,
        z = z,
    )

    @Test
    fun contour_builds_from_segments() {
        val region = contour(
            a2(
                x = 0.0,
                y = 0.0,
            ),
            lineTo(
                a2(
                    x = 10.0,
                    y = 0.0,
                ),
            ),
            lineRel(
                a2(
                    x = 0.0,
                    y = 10.0,
                ),
            ),
        ) as Region.Contour
        assertEquals(
            expected = listOf(
                Curve2.Line(
                    Vec2(
                        x = 10.0,
                        y = 0.0,
                    ),
                ),
                Curve2.Line(
                    Vec2(
                        x = 10.0,
                        y = 10.0,
                    ),
                ),
            ),
            actual = region.edges.toList(),
        )
        assertEquals(
            expected = Vec2(
                x = 0.0,
                y = 0.0,
            ),
            actual = region.start,
        )
    }

    @Test
    fun path_builds_from_segments() {
        val path = Path(
            a3(
                x = 0.0,
                y = 0.0,
                z = 0.0,
            ),
            lineTo(
                a3(
                    x = 1.0,
                    y = 2.0,
                    z = 3.0,
                ),
            ),
        )
        assertEquals(
            expected = listOf(
                Curve3.Line(
                    Vec3(
                        x = 1.0,
                        y = 2.0,
                        z = 3.0,
                    ),
                ),
            ),
            actual = path.edges.toList(),
        )
    }

    @Test
    fun arcRel_is_relative_to_the_current_point() {
        val region = contour(
            a2(
                x = 0.0,
                y = 0.0,
            ),
            lineTo(
                a2(
                    x = 2.0,
                    y = 0.0,
                ),
            ),
            arcRel(
                via = a2(
                    x = 0.0,
                    y = 1.0,
                ),
                to = a2(
                    x = 0.0,
                    y = 2.0,
                ),
            ),
        ) as Region.Contour
        assertEquals(
            expected = Curve2.Arc(
                via = Vec2(
                    x = 2.0,
                    y = 1.0,
                ),
                to = Vec2(
                    x = 2.0,
                    y = 2.0,
                ),
            ),
            actual = region.edges.last(),
        )
    }

    @Test
    fun circle_is_a_region_circle() {
        assertEquals(
            expected = Region.Circle(radius = 5.0),
            actual = circle(5.0),
        )
    }

    @Test
    fun polygon_region_holds_its_points() {
        val poly = polygon(
            a2(
                x = 0.0,
                y = 0.0,
            ),
            a2(
                x = 4.0,
                y = 0.0,
            ),
            a2(
                x = 4.0,
                y = 3.0,
            ),
        )
        assertEquals(
            expected = Region.Polygon(
                nonEmptyListOf(
                    Vec2(
                        x = 0.0,
                        y = 0.0,
                    ),
                    Vec2(
                        x = 4.0,
                        y = 0.0,
                    ),
                    Vec2(
                        x = 4.0,
                        y = 3.0,
                    ),
                ),
            ),
            actual = poly,
        )
    }

    @Test
    fun polyline_is_open_and_polygon_closes() {
        val a = a3(
            x = 0.0,
            y = 0.0,
            z = 0.0,
        )
        val b = a3(
            x = 1.0,
            y = 0.0,
            z = 0.0,
        )
        val c = a3(
            x = 1.0,
            y = 1.0,
            z = 0.0,
        )
        assertEquals(
            expected = 2,
            actual = polyline(
                a,
                b,
                c,
            ).edges.size,
        )
        assertEquals(
            expected = 3,
            actual = polygon(
                a,
                b,
                c,
            ).edges.size,
        )   // + closing line back to start
        assertEquals(
            expected = Curve3.Line(a.toVec3()),
            actual = polygon(
                a,
                b,
                c,
            ).edges.last(),
        )
    }

    @Test
    fun built_region_feeds_extrude() {
        val solid = extrude(
            height = 5.0,
            profile = polygon(
                a2(
                    x = 0.0,
                    y = 0.0,
                ),
                a2(
                    x = 10.0,
                    y = 0.0,
                ),
                a2(
                    x = 10.0,
                    y = 10.0,
                ),
                a2(
                    x = 0.0,
                    y = 10.0,
                ),
            ),
        )
        check(solid is Solid.Extrude)
    }

    @Test
    fun helix_builds_a_helix_curve() {
        val path = Path(
            a3(
                x = 0.0,
                y = 0.0,
                z = 0.0,
            ),
            helix(
                pitch = 1.0,
                height = 4.0,
            ),
        )
        assertEquals(
            expected = listOf(
                Curve3.Helix(
                    height = 4.0,
                    pitch = 1.0,
                    right_handed = true,
                ),
            ),
            actual = path.edges.toList(),
        )
    }

    @Test
    fun helix_can_be_followed_by_an_absolute_segment() {
        val path = Path(
            a3(
                x = 0.0,
                y = 0.0,
                z = 0.0,
            ),
            helix(
                pitch = 1.0,
                height = 4.0,
            ),
            lineTo(
                a3(
                    x = 0.0,
                    y = 0.0,
                    z = 10.0,
                ),
            ),
        )
        assertEquals(
            expected = 2,
            actual = path.edges.size,
        )
    }

    @Test
    fun relative_segment_after_helix_fails() {
        assertFailsWith<IllegalStateException> {
            Path(
                a3(
                    x = 0.0,
                    y = 0.0,
                    z = 0.0,
                ),
                helix(
                    pitch = 1.0,
                    height = 4.0,
                ),
                lineRel(
                    a3(
                        x = 0.0,
                        y = 0.0,
                        z = 1.0,
                    ),
                ),
            )
        }
    }

    @Test
    fun bezier_builds_a_bezier_curve() {
        val path = Path(
            a3(
                x = 0.0,
                y = 0.0,
                z = 0.0,
            ),
            bezierTo(
                a3(
                    x = 0.0,
                    y = 0.0,
                    z = 5.0,
                ),
                a3(
                    x = 10.0,
                    y = 0.0,
                    z = 5.0,
                ),
                a3(
                    x = 10.0,
                    y = 0.0,
                    z = 10.0,
                ),
            ),
        )
        assertEquals(
            expected = listOf(
                Curve3.Bezier(
                    nonEmptyListOf(
                        Vec3(
                            x = 0.0,
                            y = 0.0,
                            z = 5.0,
                        ),
                        Vec3(
                            x = 10.0,
                            y = 0.0,
                            z = 5.0,
                        ),
                        Vec3(
                            x = 10.0,
                            y = 0.0,
                            z = 10.0,
                        ),
                    ),
                ),
            ),
            actual = path.edges.toList(),
        )
    }

    @Test
    fun spline_carries_end_tangents() {
        val path = Path(
            a3(
                x = 0.0,
                y = 0.0,
                z = 0.0,
            ),
            splineTo(
                a3(
                    x = 10.0,
                    y = 0.0,
                    z = 0.0,
                ),
                tangentStart = a3(
                    x = 0.0,
                    y = 0.0,
                    z = 1.0,
                ),
                tangentEnd = a3(
                    x = 1.0,
                    y = 0.0,
                    z = 0.0,
                ),
            ),
        )
        assertEquals(
            expected = Curve3.Spline(
                points = nonEmptyListOf(
                    Vec3(
                        x = 10.0,
                        y = 0.0,
                        z = 0.0,
                    ),
                ),
                tangent_start = Vec3(
                    x = 0.0,
                    y = 0.0,
                    z = 1.0,
                ),
                tangent_end = Vec3(
                    x = 1.0,
                    y = 0.0,
                    z = 0.0,
                ),
            ),
            actual = path.edges.head,
        )
    }
}
