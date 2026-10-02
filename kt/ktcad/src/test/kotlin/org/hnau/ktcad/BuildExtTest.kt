package org.hnau.ktcad

import org.hnau.ktcad.ir.FilletKind
import org.hnau.ktcad.ir.RadiusSpec
import org.hnau.ktcad.ir.SweepMode
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildExtTest {

    private val square = profile(0.0, 0.0).lineTo(10.0, 0.0).lineTo(10.0, 10.0).lineTo(0.0, 10.0)

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
        val spine = path(0.0, 0.0, 0.0).lineTo(0.0, 0.0, 20.0)
        assertEquals(sweep(mode = null, path = spine, profile = square), square.sweep(spine))
        assertEquals(
            sweep(mode = SweepMode.RIGID, path = spine, profile = square),
            square.sweep(spine, SweepMode.RIGID),
        )
    }

    @Test
    fun loft_maps_to_factory() {
        val sections = listOf(
            path(0.0, 0.0, 0.0).lineTo(10.0, 0.0, 0.0).lineTo(10.0, 10.0, 0.0),
            path(0.0, 0.0, 10.0).lineTo(10.0, 0.0, 10.0).lineTo(10.0, 10.0, 10.0),
        )
        assertEquals(loft(ruled = true, sections = sections), sections.loft(ruled = true))
    }

    @Test
    fun fillet_with_constant_and_expression() {
        val solid = box(10.0, 10.0, 10.0)
        assertEquals(
            fillet(kind = FilletKind.FILLET, radius = RadiusSpec.All(2.0), target = solid),
            solid.fillet(2.0),
        )
        assertEquals(
            fillet(
                kind = FilletKind.CHAMFER,
                radius = RadiusSpec.Expression("if edge.is_vertical { 1.0 } else { 0.0 }"),
                target = solid,
            ),
            solid.fillet("if edge.is_vertical { 1.0 } else { 0.0 }", FilletKind.CHAMFER),
        )
    }
}
