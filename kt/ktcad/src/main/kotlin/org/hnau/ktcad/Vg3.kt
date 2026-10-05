package org.hnau.ktcad

import kotlinx.serialization.json.Json
import org.hnau.ktcad.ir.Color
import org.hnau.ktcad.ir.Export
import org.hnau.ktcad.ir.Model
import org.hnau.ktcad.ir.Body
import org.hnau.ktcad.ir.BodyIndex
import org.hnau.ktcad.ir.Sketch
import org.hnau.ktcad.ir.SketchIndex
import java.util.concurrent.TimeUnit

/**
 * The vg3 frontend: builds the IR (two flat arenas — planar `Sketch`es and `ir.Body`s) from the
 * [Solid] domain graph and hands it to the `vg3` binary.
 *
 * Mirrors the CLI: a model (`version`/`sketches`/`bodies`/`export`) plus an `export` configuration.
 */
object Vg3 {

    private const val VERSION = 1

    /**
     * Lowers [parts] to the flat arenas, serializes the model and export config, and
     * runs `vg3`.
     *
     * `vg3` must be on `PATH` (override with the `VG3_BIN` environment variable).
     */
    fun export(
        parts: List<Part>,
        format: Format,
    ) {
        val arena = Arena()
        val exports = parts.map { part ->
            Export(
                index = arena.body(part.solid),
                name = part.name,
                color = part.color,
            )
        }
        val modelJson = modelJson.encodeToString(
            Model.serializer(),
            Model(
                version = VERSION,
                sketches = arena.sketches,
                bodies = arena.bodies,
                export = exports,
            ),
        )
        run(modelJson, format.toJson())
    }

    private fun run(modelJson: String, exportConfigJson: String) {
        val command = listOf(
            System.getenv("VG3_BIN") ?: "vg3",
            "--model-json", modelJson,
            "--export-config-json", exportConfigJson,
        )
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(10, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            error("vg3 did not finish within 10 minutes")
        }
        check(process.exitValue() == 0) {
            "vg3 failed (exit ${process.exitValue()}):\n$output"
        }
    }
}

/** A named, optionally colored solid to place in the model's `export` list. */
data class Part(
    val name: String,
    val solid: Solid,
    val color: Color? = null,
)

/**
 * Lowers the reference DAG to the flat arenas:
 * - every distinct `Solid` occupies exactly one position in `bodies` (structural `equals` dedup →
 *   reuse shares an entry), added bottom-up so operands always precede their parents;
 * - every distinct `Region` a body references occupies exactly one position in `sketches`, lowered
 *   on demand;
 * - operands are indices strictly less than the node's own index (back-references only).
 */
class Arena {
    private val solids = HashMap<Solid, BodyIndex>()
    private val regions = HashMap<Region, SketchIndex>()

    val sketches = mutableListOf<Sketch>()
    val bodies = mutableListOf<Body>()

    /** The `SketchIndex` of [region], lowering it (and its children first) into the sketch arena. */
    fun region(region: Region): SketchIndex = regions.getOrPut(region) {
        val sketch = region.lower(::region)
        sketches += sketch
        SketchIndex(sketches.lastIndex)
    }

    /** The `BodyIndex` of [solid], lowering it (and its children first) into the body arena. */
    fun body(solid: Solid): BodyIndex = solids.getOrPut(solid) {
        val body = solid.lower(::body, ::region)
        bodies += body
        BodyIndex(bodies.lastIndex)
    }
}

internal val modelJson = Json {
    encodeDefaults = false
    explicitNulls = false
    prettyPrint = true
}
