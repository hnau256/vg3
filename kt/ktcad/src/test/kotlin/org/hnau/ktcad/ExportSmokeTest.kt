package org.hnau.ktcad

import org.hnau.ktcad.ir.Vec2
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
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

    @Test
    fun step_preview_lists_every_part_and_stl_only_the_printable_ones() {
        val step = File(System.getProperty("java.io.tmpdir"), "vg3-smoke-preview.step")
        val printDir = File(System.getProperty("java.io.tmpdir"), "vg3-smoke-print")
        step.delete()
        printDir.deleteRecursively()

        listOf(
            PrintPart(
                part = Part(name = "hook", solid = box(1.0, 1.0, 1.0)),
                stlTransformation = { rotateX(Math.PI) },
            ),
            PrintPart(part = Part(name = "cabinet", solid = box(3.0, 3.0, 3.0))),
        ).stepPreviewAndStlExport(
            step = Format.Step(filename = step.absolutePath),
            stl = Format.Stl(output = Output.Multi(printDir.absolutePath)),
        )

        assertTrue(step.exists() && step.length() > 0, "STEP preview must be written: ${step.absolutePath}")
        assertTrue(File(printDir, "hook.stl").exists(), "printable part must have an STL")
        assertFalse(
            File(printDir, "cabinet.stl").exists(),
            "a preview-only part must not be exported to STL",
        )
    }
}
