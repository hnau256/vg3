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

        listOf(Part(name = "box", solid = box(1.0, 2.0, 3.0)))
            .model()
            .export(Format.Stl(output = Output.Single(output.absolutePath)))

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
        listOf(Part(name = "prism", solid = square.extrude(5.0)))
            .model()
            .export(Format.Stl(output = Output.Single(output.absolutePath)))

        assertTrue(output.exists() && output.length() > 0, "STL must be written: ${output.absolutePath}")
    }

    @Test
    fun extruded_filleted_region_exports_to_stl() {
        val output = File(System.getProperty("java.io.tmpdir"), "vg3-smoke-fillet2d.stl")
        output.delete()

        listOf(Part(name = "rounded", solid = rect(10.0, 10.0).fillet2dAll(2.0).extrude(5.0)))
            .model()
            .export(Format.Stl(output = Output.Single(output.absolutePath)))

        assertTrue(output.exists() && output.length() > 0, "STL must be written: ${output.absolutePath}")
    }

    @Test
    fun donut_with_a_hole_exports_to_stl() {
        val output = File(System.getProperty("java.io.tmpdir"), "vg3-smoke-donut.stl")
        output.delete()

        val donut = circle(10.0).cut(circle(5.0)).offset2d(2.0).extrude(3.0)
        listOf(Part(name = "donut", solid = donut))
            .model()
            .export(Format.Stl(output = Output.Single(output.absolutePath)))

        assertTrue(output.exists() && output.length() > 0, "STL must be written: ${output.absolutePath}")
    }

    @Test
    fun thick_solid_exports_to_stl() {
        val output = File(System.getProperty("java.io.tmpdir"), "vg3-smoke-thick.stl")
        output.delete()

        val hollow = box(10.0, 10.0, 10.0).thickSolid(
            offset = -1.0,
            expression = "is_parallel(face.normal, Z) && is_close(face.center.z, box.max.z)",
        )
        listOf(Part(name = "hollow", solid = hollow))
            .model()
            .export(Format.Stl(output = Output.Single(output.absolutePath)))

        assertTrue(output.exists() && output.length() > 0, "STL must be written: ${output.absolutePath}")
    }

    @Test
    fun json_report_exports() {
        val output = File(System.getProperty("java.io.tmpdir"), "vg3-smoke-report.json")
        output.delete()

        listOf(Part(name = "box", solid = box(1.0, 2.0, 3.0)))
            .model()
            .export(Format.Json(filename = output.absolutePath))

        assertTrue(output.exists() && output.length() > 0, "report must be written: ${output.absolutePath}")
        assertTrue(output.readText().contains("\"name\": \"box\""), "report names the body")
    }
}
