package org.hnau.ktcad

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
}
