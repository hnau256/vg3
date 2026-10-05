package org.hnau.ktcad

import org.hnau.ktcad.ir.Vec2
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * End-to-end smoke test: builds a box, lowers it to IR, and runs the `vg3` binary to produce an STL.
 *
 * Requires `vg3` on `PATH` (or `VG3_BIN`); skipped when the binary is unavailable.
 */
class ExportSmokeTest {

    @Test
    fun box_exports_to_stl() {
        val output = File(System.getProperty("java.io.tmpdir"), "vg3-smoke-box.stl")
        output.delete()

        Vg3.export(
            parts = listOf(Part(name = "box", solid = box(1.0, 2.0, 3.0))),
            format = Format.Stl(output = Output.Single(output.absolutePath)),
        )

        assertTrue(output.exists() && output.length() > 0, "STL must be written: ${output.absolutePath}")
    }

    @Test
    fun extruded_region_exports_to_stl() {
        val output = File(System.getProperty("java.io.tmpdir"), "vg3-smoke-prism.stl")
        output.delete()

        val square = polygon(
            Vec2(0.0, 0.0),
            Vec2(10.0, 0.0),
            Vec2(10.0, 10.0),
            Vec2(0.0, 10.0),
        )
        Vg3.export(
            parts = listOf(Part(name = "prism", solid = square.extrude(5.0))),
            format = Format.Stl(output = Output.Single(output.absolutePath)),
        )

        assertTrue(output.exists() && output.length() > 0, "STL must be written: ${output.absolutePath}")
    }

    @Test
    fun extruded_filleted_region_exports_to_stl() {
        val output = File(System.getProperty("java.io.tmpdir"), "vg3-smoke-fillet2d.stl")
        output.delete()

        Vg3.export(
            parts = listOf(Part(name = "rounded", solid = rect(10.0, 10.0).fillet2d(2.0).extrude(5.0))),
            format = Format.Stl(output = Output.Single(output.absolutePath)),
        )

        assertTrue(output.exists() && output.length() > 0, "STL must be written: ${output.absolutePath}")
    }
}
