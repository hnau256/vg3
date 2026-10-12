package org.hnau.ktcad

import arrow.core.toNonEmptyListOrThrow
import org.hnau.ktcad.axis.A2Values
import org.hnau.ktcad.axis.A3Values
import org.hnau.ktcad.ir.Continuity
import org.hnau.ktcad.ir.FilletKind
import org.hnau.ktcad.ir.Vec2
import org.hnau.ktcad.ir.Vec3
import org.hnau.ktcad.ir.RadiusSpec
import org.hnau.ktcad.ir.SweepMode
import org.hnau.ktcad.ir.TransitionKind
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildExtTest {

    private val square = polygon(
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

    @Test
    fun extrude_maps_to_factory() {
        assertEquals(
            expected = extrude(
                height = 5.0,
                profile = square,
            ),
            actual = square.extrude(5.0),
        )
    }

    @Test
    fun revolve_maps_to_factory() {
        assertEquals(
            expected = revolve(
                angle = 1.5,
                profile = square,
            ),
            actual = square.revolve(1.5),
        )
    }

    @Test
    fun sweep_maps_to_factory() {
        val spine = Path(
            A3Values(
                x = 0.0,
                y = 0.0,
                z = 0.0,
            ),
            lineTo(
                A3Values(
                    x = 0.0,
                    y = 0.0,
                    z = 20.0,
                ),
            ),
        )
        assertEquals(
            expected = sweep(
                path = spine,
                profile = square,
            ),
            actual = square.sweep(spine),
        )
        assertEquals(
            expected = sweep(
                mode = SweepMode.RIGID,
                path = spine,
                profile = square,
            ),
            actual = square.sweep(
                path = spine,
                mode = SweepMode.RIGID,
            ),
        )
    }

    @Test
    fun loft_maps_to_factory() {
        val sections = listOf(
            Path(
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
                lineTo(
                    A3Values(
                        x = 10.0,
                        y = 10.0,
                        z = 0.0,
                    ),
                ),
            ),
            Path(
                A3Values(
                    x = 0.0,
                    y = 0.0,
                    z = 10.0,
                ),
                lineTo(
                    A3Values(
                        x = 10.0,
                        y = 0.0,
                        z = 10.0,
                    ),
                ),
                lineTo(
                    A3Values(
                        x = 10.0,
                        y = 10.0,
                        z = 10.0,
                    ),
                ),
            ),
        )
        assertEquals(
            expected = loft(
                ruled = true,
                sections = sections.toNonEmptyListOrThrow(),
            ),
            actual = sections.toNonEmptyListOrThrow().loft(ruled = true),
        )
    }

    @Test
    fun fillet_all_and_expression() {
        val solid = box(
            A3Values(
                x = 10.0,
                y = 10.0,
                z = 10.0,
            ),
        )
        assertEquals(
            expected = fillet(
                kind = FilletKind.FILLET,
                radius = RadiusSpec.All(2.0),
                target = solid,
            ),
            actual = solid.filletAll(2.0),
        )
        assertEquals(
            expected = fillet(
                kind = FilletKind.CHAMFER,
                radius = RadiusSpec.Expression("1.0"),
                target = solid,
            ),
            actual = solid.filletExpression(
                expression = "1.0",
                kind = FilletKind.CHAMFER,
            ),
        )
    }

    @Test
    fun sweep_and_loft_settings_map_to_the_ir() {
        val spine = Path(
            A3Values(
                x = 0.0,
                y = 0.0,
                z = 0.0,
            ),
            lineTo(
                A3Values(
                    x = 0.0,
                    y = 0.0,
                    z = 10.0,
                ),
            ),
        )
        val square = polygon(
            A2Values(
                x = -1.0,
                y = -1.0,
            ),
            A2Values(
                x = 1.0,
                y = -1.0,
            ),
            A2Values(
                x = 1.0,
                y = 1.0,
            ),
        )
        val sweep = square.sweep(
            path = spine,
            transition = TransitionKind.ROUND_CORNER,
        )
        sweep as Solid.Sweep
        assertEquals(
            expected = TransitionKind.ROUND_CORNER,
            actual = sweep.transition,
        )

        val sections = listOf(
            Path(
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
                lineTo(
                    A3Values(
                        x = 10.0,
                        y = 10.0,
                        z = 0.0,
                    ),
                ),
            ),
            Path(
                A3Values(
                    x = 0.0,
                    y = 0.0,
                    z = 10.0,
                ),
                lineTo(
                    A3Values(
                        x = 10.0,
                        y = 0.0,
                        z = 10.0,
                    ),
                ),
                lineTo(
                    A3Values(
                        x = 10.0,
                        y = 10.0,
                        z = 10.0,
                    ),
                ),
            ),
        )
        val loft = sections.toNonEmptyListOrThrow().loft(
            continuity = Continuity.C2,
            maxDegree = 8,
        )
        loft as Solid.Loft
        assertEquals(
            expected = Continuity.C2,
            actual = loft.continuity,
        )
        assertEquals(
            expected = 8,
            actual = loft.max_degree,
        )
    }

    @Test
    fun fillet_selected_with_radius() {
        val solid = box(
            A3Values(
                x = 10.0,
                y = 10.0,
                z = 10.0,
            ),
        )
        assertEquals(
            expected = fillet(
                kind = FilletKind.FILLET,
                radius = RadiusSpec.Selected(
                    expression = "true",
                    radius = 1.0,
                ),
                target = solid,
            ),
            actual = solid.filletSelected(
                expression = "true",
                radius = 1.0,
            ),
        )
    }
}
