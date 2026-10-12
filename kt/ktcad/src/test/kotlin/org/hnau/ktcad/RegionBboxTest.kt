package org.hnau.ktcad

import org.hnau.ktcad.axis.A2
import org.hnau.ktcad.axis.A2Values
import org.hnau.ktcad.region.plus
import org.hnau.ktcad.bbox.bbox
import org.hnau.ktcad.axis.direction
import org.hnau.ktcad.region.circle
import org.hnau.ktcad.region.mirror
import org.hnau.ktcad.region.offset2d
import org.hnau.ktcad.region.arc2To
import org.hnau.ktcad.region.bezier2To
import org.hnau.ktcad.region.contour
import org.hnau.ktcad.region.line2Rel
import org.hnau.ktcad.region.line2To
import org.hnau.ktcad.region.polygon2
import org.hnau.ktcad.region.rect
import org.hnau.ktcad.region.rotate
import org.hnau.ktcad.region.scale
import org.hnau.ktcad.region.translate
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class RegionBboxTest {

    private val square = polygon2(
        A2Values(
            x = 0.0,
            y = 0.0,
        ),
        A2Values(
            x = 10.0,
            y = 0.0,
        ),
        A2Values(
            x = 10.0,
            y = 10.0,
        ),
        A2Values(
            x = 0.0,
            y = 10.0,
        ),
    )

    private fun box(
        x0: Double,
        x1: Double,
        y0: Double,
        y1: Double,
    ) = A2Values(
        x = x0..x1,
        y = y0..y1,
    )

    private fun assertBox(
        expected: A2Values<ClosedRange<Double>>,
        actual: A2Values<ClosedRange<Double>>,
    ) {
        val eps = 1e-9
        assertEquals(
            expected = expected.x.start,
            actual = actual.x.start,
            absoluteTolerance = eps,
        )
        assertEquals(
            expected = expected.x.endInclusive,
            actual = actual.x.endInclusive,
            absoluteTolerance = eps,
        )
        assertEquals(
            expected = expected.y.start,
            actual = actual.y.start,
            absoluteTolerance = eps,
        )
        assertEquals(
            expected = expected.y.endInclusive,
            actual = actual.y.endInclusive,
            absoluteTolerance = eps,
        )
    }

    @Test
    fun polygon_and_circle_are_exact() {
        assertBox(
            expected = box(
                x0 = 0.0,
                x1 = 10.0,
                y0 = 0.0,
                y1 = 10.0,
            ),
            actual = square.bbox,
        )
        assertBox(
            expected = box(
                x0 = -5.0,
                x1 = 5.0,
                y0 = -5.0,
                y1 = 5.0,
            ),
            actual = circle(5.0).bbox,
        )
    }

    @Test
    fun contour_bounds_lines_and_arcs_by_control_points() {
        val lines = contour(
            A2Values(
                x = 0.0,
                y = 0.0,
            ),
            line2To(
                A2Values(
                    x = 10.0,
                    y = 0.0,
                ),
            ),
            line2Rel(
                A2Values(
                    x = 0.0,
                    y = 10.0,
                ),
            ),
        )
        assertBox(
            expected = box(
                x0 = 0.0,
                x1 = 10.0,
                y0 = 0.0,
                y1 = 10.0,
            ),
            actual = lines.bbox,
        )

        val arc = contour(
            A2Values(
                x = 0.0,
                y = 0.0,
            ),
            arc2To(
                via = A2Values(
                    x = 5.0,
                    y = 5.0,
                ),
                to = A2Values(
                    x = 10.0,
                    y = 0.0,
                ),
            ),
        )
        assertBox(
            expected = box(
                x0 = 0.0,
                x1 = 10.0,
                y0 = 0.0,
                y1 = 5.0,
            ),
            actual = arc.bbox,
        )
    }

    @Test
    fun contour_outlines_bezier_poles_from_the_start_point() {
        val region = contour(
            A2Values(
                x = 0.0,
                y = 0.0,
            ),
            bezier2To(
                A2Values(
                    x = 10.0,
                    y = 0.0,
                ),
                A2Values(
                    x = 10.0,
                    y = 10.0,
                ),
            ),
        )
        assertBox(
            expected = box(
                x0 = 0.0,
                x1 = 10.0,
                y0 = 0.0,
                y1 = 10.0,
            ),
            actual = region.bbox,
        )
    }

    @Test
    fun transforms_apply_to_the_box_corners() {
        assertBox(
            expected = box(
                x0 = 3.0,
                x1 = 4.0,
                y0 = 4.0,
                y1 = 5.0,
            ),
            actual = rect(
                A2Values(
                    x = 1.0,
                    y = 1.0,
                ),
            ).translate(
                A2Values(
                    x = 3.0,
                    y = 4.0,
                ),
            ).bbox,
        )
        assertBox(
            expected = box(
                x0 = 0.0,
                x1 = 2.0,
                y0 = -1.0,
                y1 = 0.0,
            ),
            actual = rect(
                A2Values(
                    x = 1.0,
                    y = 1.0,
                ),
            ).scale(
                A2Values(
                    x = 2.0,
                    y = -1.0,
                ),
            ).bbox,
        )
        assertBox(
            expected = box(
                x0 = -1.0,
                x1 = 0.0,
                y0 = 0.0,
                y1 = 1.0,
            ),
            actual = rect(
                A2Values(
                    x = 1.0,
                    y = 1.0,
                ),
            ).rotate(PI / 2).bbox,
        )
        assertBox(
            expected = box(
                x0 = -1.0,
                x1 = 0.0,
                y0 = 0.0,
                y1 = 1.0,
            ),
            actual = rect(
                A2Values(
                    x = 1.0,
                    y = 1.0,
                ),
            ).mirror(A2.X.direction).bbox,
        )
    }

    @Test
    fun booleans_union_their_operands() {
        assertBox(
            expected = box(
                x0 = -2.0,
                x1 = 10.0,
                y0 = -2.0,
                y1 = 10.0,
            ),
            actual = (square + circle(2.0)).bbox,
        )
    }

    @Test
    fun offset_grows_and_shrink_stays_inside() {
        assertBox(
            expected = box(
                x0 = -7.0,
                x1 = 7.0,
                y0 = -7.0,
                y1 = 7.0,
            ),
            actual = circle(5.0).offset2d(2.0).bbox,
        )
        assertBox(
            expected = circle(5.0).bbox,
            actual = circle(5.0).offset2d(-1.0).bbox,
        )
    }
}
