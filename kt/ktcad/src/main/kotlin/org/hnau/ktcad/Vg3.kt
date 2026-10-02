package org.hnau.ktcad

import kotlinx.serialization.json.Json
import org.hnau.ktcad.ir.Color
import org.hnau.ktcad.ir.Export
import org.hnau.ktcad.ir.Model
import org.hnau.ktcad.ir.Node
import org.hnau.ktcad.ir.Operand
import java.util.concurrent.TimeUnit

/**
 * The vg3 frontend: builds the IR (a flat arena of [Node]s) and hands it to the `vg3` binary.
 *
 * Mirrors the CLI: a model (`version`/`parts`/`export`) plus an `export` configuration.
 */
object Vg3 {

    private const val VERSION = 1

    /**
     * Lowers [parts] to the flat arena (FORMAT.md), serializes the model and export config, and
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
                index = Operand(arena.add(part.node)),
                name = part.name,
                color = part.color,
            )
        }
        val modelJson = modelJson.encodeToString(
            Model.serializer(),
            Model(version = VERSION, parts = arena.nodes, export = exports),
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

/** A named, optionally colored node to place in the model's `export` list. */
data class Part(
    val name: String,
    val node: Node,
    val color: Color? = null,
)

/** A `box` primitive: from the origin along `+x`, `+y`, `+z`. */
fun box(width: Double, length: Double, height: Double): Node =
    Node.Box(width = width, length = length, height = height)

/**
 * Lowers the reference DAG to the flat arena (FORMAT.md):
 * - every distinct node occupies exactly one position (identity dedup → reuse shares an entry);
 * - operands are indices strictly less than the node's own index (back-references only).
 */
private class Arena {
    private val visited = HashMap<Node, Int>()
    val nodes = mutableListOf<Node>()

    fun add(node: Node): Int = visited.getOrPut(node) {
        val index = nodes.size
        nodes.add(node)
        index
    }
}

internal val modelJson = Json {
    encodeDefaults = false
    explicitNulls = false
    prettyPrint = true
}
