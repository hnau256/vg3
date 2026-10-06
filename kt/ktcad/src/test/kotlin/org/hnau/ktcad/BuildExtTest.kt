package org.hnau.ktcad

import arrow.core.toNonEmptyListOrThrow
import org.hnau.ktcad.ir.FilletKind
import org.hnau.ktcad.ir.Vec2
import org.hnau.ktcad.ir.RadiusSpec
import org.hnau.ktcad.ir.SweepMode
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildExtTest {

    private val square = polygon(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(10.0, 10.0), Vec2(0.0, 10.0))

    @Test
    fun extrude_maps_to_factory() {
        assertEquals(extrude(height = 5.0, profile = square), square.extrude(5.0))
    }

    @Test
    fun revolve_maps_to_factory() {
        assertEquals(revolve(angle = 1.5, profile = square), square.revolve(1.5))
    }

    @Test
    fun sweep_maps_to_factory() {
        val spine = Path(p(0.0, 0.0, 0.0), lineTo(0.0, 0.0, 20.0))
        assertEquals(sweep(mode = null, path = spine, profile = square), square.sweep(spine))
        assertEquals(
            sweep(mode = SweepMode.RIGID, path = spine, profile = square),
            square.sweep(spine, SweepMode.RIGID),
        )
    }

    @Test
    fun loft_maps_to_factory() {
        val sections = listOf(
            Path(p(0.0, 0.0, 0.0), lineTo(10.0, 0.0, 0.0), lineTo(10.0, 10.0, 0.0)),
            Path(p(0.0, 0.0, 10.0), lineTo(10.0, 0.0, 10.0), lineTo(10.0, 10.0, 10.0)),
        )
        assertEquals(loft(ruled = true, sections = sections.toNonEmptyListOrThrow()), sections.toNonEmptyListOrThrow().loft(ruled = true))
    }

    @Test
    fun fillet_all_and_expression() {
        val solid = box(10.0, 10.0, 10.0)
        assertEquals(
            fillet(kind = FilletKind.FILLET, radius = RadiusSpec.All(2.0), target = solid),
            solid.filletAll(2.0),
        )
        assertEquals(
            fillet(
                kind = FilletKind.CHAMFER,
                radius = RadiusSpec.Expression("1.0"),
                target = solid,
            ),
            solid.filletExpression("1.0", FilletKind.CHAMFER),
        )
    }

    @Test
    fun fillet_selected_with_radius() {
        val solid = box(10.0, 10.0, 10.0)
        assertEquals(
            fillet(
                kind = FilletKind.FILLET,
                radius = RadiusSpec.Selected(expression = "true", radius = 1.0),
                target = solid,
            ),
            solid.filletSelected("true", 1.0),
        )
    }
}
