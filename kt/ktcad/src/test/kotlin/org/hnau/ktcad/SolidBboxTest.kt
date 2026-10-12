package org.hnau.ktcad

import arrow.core.nonEmptyListOf
import org.hnau.ktcad.axis.A2Values
import org.hnau.ktcad.axis.A3
import org.hnau.ktcad.axis.A3Values
import org.hnau.ktcad.bbox.bbox
import org.hnau.ktcad.axis.direction
import org.hnau.ktcad.axis.times
import org.hnau.ktcad.ir.RadiusSpec
import org.hnau.ktcad.ir.TransformOp
import org.hnau.ktcad.ir.Vec3
import org.hnau.ktcad.region.polygon
import org.hnau.ktcad.solid.cut
import org.hnau.ktcad.solid.cylinder
import org.hnau.ktcad.solid.fuse
import org.hnau.ktcad.solid.mirror
import org.hnau.ktcad.solid.offset
import org.hnau.ktcad.solid.rotate
import org.hnau.ktcad.solid.scale
import org.hnau.ktcad.solid.translate
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class SolidBboxTest {

    private val square = polygon(
        A2Values(
            x = 0.0,
            y = 0.0,
        ),
        A2Values(
            x = 2.0,
            y = 0.0,
        ),
        A2Values(
            x = 2.0,
            y = 2.0,
        ),
        A2Values(
            x = 0.0,
            y = 2.0,
        ),
    )

    private fun box(
        x0: Double,
        x1: Double,
        y0: Double,
        y1: Double,
        z0: Double,
        z1: Double,
    ) = A3Values(
        x = x0..x1,
        y = y0..y1,
        z = z0..z1,
    )

    private fun assertBox(
        expected: A3Values<ClosedRange<Double>>,
        actual: A3Values<ClosedRange<Double>>,
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
        assertEquals(
            expected = expected.z.start,
            actual = actual.z.start,
            absoluteTolerance = eps,
        )
        assertEquals(
            expected = expected.z.endInclusive,
            actual = actual.z.endInclusive,
            absoluteTolerance = eps,
        )
    }

    @Test
    fun primitives_are_exact() {
        assertBox(
            expected = box(
                x0 = 0.0,
                x1 = 1.0,
                y0 = 0.0,
                y1 = 2.0,
                z0 = 0.0,
                z1 = 3.0,
            ),
            actual = org.hnau.ktcad.solid.box(
                A3Values(
                    x = 1.0,
                    y = 2.0,
                    z = 3.0,
                ),
            ).bbox,
        )
        assertBox(
            expected = box(
                x0 = -3.0,
                x1 = 3.0,
                y0 = -3.0,
                y1 = 3.0,
                z0 = -3.0,
                z1 = 3.0,
            ),
            actual = sphere(3.0).bbox,
        )
        assertBox(
            expected = box(
                x0 = -2.0,
                x1 = 2.0,
                y0 = -2.0,
                y1 = 2.0,
                z0 = 0.0,
                z1 = 5.0,
            ),
            actual = cylinder(
                radius = 2.0,
                height = 5.0,
            ).bbox,
        )
        assertBox(
            expected = box(
                x0 = -3.0,
                x1 = 3.0,
                y0 = -3.0,
                y1 = 3.0,
                z0 = 0.0,
                z1 = 4.0,
            ),
            actual = cone(
                height = 4.0,
                radius_bottom = 3.0,
                radius_top = 1.0,
            ).bbox,
        )
        assertBox(
            expected = box(
                x0 = -6.0,
                x1 = 6.0,
                y0 = -6.0,
                y1 = 6.0,
                z0 = -1.0,
                z1 = 1.0,
            ),
            actual = torus(
                major_radius = 5.0,
                minor_radius = 1.0,
            ).bbox,
        )
        assertBox(
            expected = box(
                x0 = 0.0,
                x1 = 4.0,
                y0 = 0.0,
                y1 = 5.0,
                z0 = 0.0,
                z1 = 6.0,
            ),
            actual = wedge(
                height = 6.0,
                length = 5.0,
                top_width = 2.0,
                width = 4.0,
            ).bbox,
        )
    }

    @Test
    fun halfspace_is_unbounded() {
        val halfspace = halfspace().bbox
        assertEquals(
            expected = Double.NEGATIVE_INFINITY,
            actual = halfspace.x.start,
        )
        assertEquals(
            expected = Double.POSITIVE_INFINITY,
            actual = halfspace.y.endInclusive,
        )
        assertEquals(
            expected = 0.0,
            actual = halfspace.z.endInclusive,
        )
    }

    @Test
    fun polyhedron_is_exact() {
        val solid = polyhedron(
            faces = nonEmptyListOf(
                listOf(
                    0,
                    1,
                    2,
                ),
            ),
            points = nonEmptyListOf(
                Vec3(
                    x = 0.0,
                    y = 0.0,
                    z = 0.0,
                ),
                Vec3(
                    x = 1.0,
                    y = 0.0,
                    z = 0.0,
                ),
                Vec3(
                    x = 0.0,
                    y = 1.0,
                    z = 2.0,
                ),
            ),
        )
        assertBox(
            expected = box(
                x0 = 0.0,
                x1 = 1.0,
                y0 = 0.0,
                y1 = 1.0,
                z0 = 0.0,
                z1 = 2.0,
            ),
            actual = solid.bbox,
        )
    }

    @Test
    fun extrude_and_revolve_build_from_the_profile() {
        assertBox(
            expected = box(
                x0 = 0.0,
                x1 = 2.0,
                y0 = 0.0,
                y1 = 2.0,
                z0 = 0.0,
                z1 = 5.0,
            ),
            actual = extrude(
                height = 5.0,
                profile = square,
            ).bbox,
        )

        val ring = polygon(
            A2Values(
                x = 2.0,
                y = 0.0,
            ),
            A2Values(
                x = 4.0,
                y = 0.0,
            ),
            A2Values(
                x = 4.0,
                y = 3.0,
            ),
            A2Values(
                x = 2.0,
                y = 3.0,
            ),
        )
        assertBox(
            expected = box(
                x0 = -4.0,
                x1 = 4.0,
                y0 = 0.0,
                y1 = 3.0,
                z0 = -4.0,
                z1 = 4.0,
            ),
            actual = revolve(
                angle = 2.0 * PI,
                profile = ring,
            ).bbox,
        )
    }

    @Test
    fun loft_unions_its_sections_and_sweep_grows_the_path() {
        val lower = Path(
            A3Values(
                x = 0.0,
                y = 0.0,
                z = 0.0,
            ),
            lineTo(
                A3Values(
                    x = 4.0,
                    y = 0.0,
                    z = 0.0,
                ),
            ),
            lineTo(
                A3Values(
                    x = 4.0,
                    y = 4.0,
                    z = 0.0,
                ),
            ),
        )
        val upper = Path(
            A3Values(
                x = 0.0,
                y = 0.0,
                z = 10.0,
            ),
            lineTo(
                A3Values(
                    x = 2.0,
                    y = 0.0,
                    z = 10.0,
                ),
            ),
            lineTo(
                A3Values(
                    x = 2.0,
                    y = 2.0,
                    z = 10.0,
                ),
            ),
        )
        assertBox(
            expected = box(
                x0 = 0.0,
                x1 = 4.0,
                y0 = 0.0,
                y1 = 4.0,
                z0 = 0.0,
                z1 = 10.0,
            ),
            actual = loft(
                sections = nonEmptyListOf(
                    lower,
                    upper,
                ),
            ).bbox,
        )

        val path = Path(
            A3Values(
                x = 0.0,
                y = 0.0,
                z = 0.0,
            ),
            lineTo(
                A3Values(
                    x = 10.0,
                    y = 0.0,
                    z = 0.0,
                ),
            ),
        )
        assertBox(
            expected = box(
                x0 = -2.0,
                x1 = 12.0,
                y0 = -2.0,
                y1 = 2.0,
                z0 = -2.0,
                z1 = 2.0,
            ),
            actual = sweep(
                path = path,
                profile = square,
            ).bbox,
        )
    }

    @Test
    fun booleans_union_operands_but_cut_ignores_tools() {
        assertBox(
            expected = box(
                x0 = 0.0,
                x1 = 6.0,
                y0 = 0.0,
                y1 = 1.0,
                z0 = 0.0,
                z1 = 1.0,
            ),
            actual = nonEmptyListOf(
                org.hnau.ktcad.solid.box(
                    A3Values(
                        x = 1.0,
                        y = 1.0,
                        z = 1.0,
                    ),
                ),
                org.hnau.ktcad.solid.box(
                    A3Values(
                        x = 1.0,
                        y = 1.0,
                        z = 1.0,
                    ),
                ).translate(A3.X.direction * 5.0),
            ).fuse().bbox,
        )
        assertBox(
            expected = box(
                x0 = 0.0,
                x1 = 4.0,
                y0 = 0.0,
                y1 = 4.0,
                z0 = 0.0,
                z1 = 4.0,
            ),
            actual = nonEmptyListOf(
                org.hnau.ktcad.solid.box(
                    A3Values(
                        x = 4.0,
                        y = 4.0,
                        z = 4.0,
                    ),
                ),
                halfspace().translate(A3.Z.direction * 2.0),
            ).cut().bbox,
        )
    }

    @Test
    fun transforms_apply_to_the_box_corners() {
        assertBox(
            expected = box(
                x0 = 2.0,
                x1 = 3.0,
                y0 = 3.0,
                y1 = 4.0,
                z0 = 4.0,
                z1 = 5.0,
            ),
            actual = org.hnau.ktcad.solid.box(
                A3Values(
                    x = 1.0,
                    y = 1.0,
                    z = 1.0,
                ),
            ).translate(
                A3Values(
                    x = 2.0,
                    y = 3.0,
                    z = 4.0,
                ),
            ).bbox,
        )
        assertBox(
            expected = box(
                x0 = -1.0,
                x1 = 0.0,
                y0 = 0.0,
                y1 = 1.0,
                z0 = 0.0,
                z1 = 1.0,
            ),
            actual = org.hnau.ktcad.solid.box(
                A3Values(
                    x = 1.0,
                    y = 1.0,
                    z = 1.0,
                ),
            ).rotate(
                axis = A3.Z.direction,
                angle = PI / 2,
            ).bbox,
        )
        assertBox(
            expected = box(
                x0 = 0.0,
                x1 = 2.0,
                y0 = 0.0,
                y1 = 2.0,
                z0 = 0.0,
                z1 = 2.0,
            ),
            actual = org.hnau.ktcad.solid.box(
                A3Values(
                    x = 1.0,
                    y = 1.0,
                    z = 1.0,
                ),
            ).scale(2.0).bbox,
        )
        assertBox(
            expected = box(
                x0 = 0.0,
                x1 = 1.0,
                y0 = 0.0,
                y1 = 1.0,
                z0 = -1.0,
                z1 = 0.0,
            ),
            actual = org.hnau.ktcad.solid.box(
                A3Values(
                    x = 1.0,
                    y = 1.0,
                    z = 1.0,
                ),
            ).mirror(A3.Z.direction).bbox,
        )

        val matrix = Solid.Transform(
            op = TransformOp.Matrix(
                m = listOf(
                    1.0,
                    0.0,
                    0.0,
                    2.0,
                    0.0,
                    1.0,
                    0.0,
                    3.0,
                    0.0,
                    0.0,
                    1.0,
                    4.0,
                    0.0,
                    0.0,
                    0.0,
                    1.0,
                ),
            ),
            target = org.hnau.ktcad.solid.box(
                A3Values(
                    x = 1.0,
                    y = 1.0,
                    z = 1.0,
                ),
            ),
        )
        assertBox(
            expected = box(
                x0 = 2.0,
                x1 = 3.0,
                y0 = 3.0,
                y1 = 4.0,
                z0 = 4.0,
                z1 = 5.0,
            ),
            actual = matrix.bbox,
        )
    }

    @Test
    fun offset_grows_and_fillet_keeps_the_box() {
        assertBox(
            expected = box(
                x0 = -1.0,
                x1 = 2.0,
                y0 = -1.0,
                y1 = 2.0,
                z0 = -1.0,
                z1 = 2.0,
            ),
            actual = org.hnau.ktcad.solid.box(
                A3Values(
                    x = 1.0,
                    y = 1.0,
                    z = 1.0,
                ),
            ).offset(1.0).bbox,
        )

        val filleted = fillet(
            radius = RadiusSpec.All(0.1),
            target = org.hnau.ktcad.solid.box(
                A3Values(
                    x = 1.0,
                    y = 1.0,
                    z = 1.0,
                ),
            ),
        )
        assertBox(
            expected = box(
                x0 = 0.0,
                x1 = 1.0,
                y0 = 0.0,
                y1 = 1.0,
                z0 = 0.0,
                z1 = 1.0,
            ),
            actual = filleted.bbox,
        )
    }
}
