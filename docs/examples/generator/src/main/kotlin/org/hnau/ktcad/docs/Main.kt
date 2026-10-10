package org.hnau.ktcad.docs

import org.hnau.ktcad.*
import org.hnau.ktcad.docs.generated.examples
import java.io.File

/**
 * Batch docs example generator. Exports every example compiled into the generated `examples` list
 * as `<id>.json` (canonical IR) and `<id>.png` (preview) into the output directory.
 *
 * Usage: `<out-dir> <size> <compression>`. The image size and compression are global (identical
 * dimensions for every preview); the camera stays per example in its `// vg3:` header.
 */
fun main(args: Array<String>) {
    require(args.size == 3) { "usage: <out-dir> <size> <compression>" }
    val outputDir = File(args[0])
    val size = args[1].toIntOrNull() ?: error("invalid size: '${args[1]}'")
    val compression = args[2].toIntOrNull() ?: error("invalid compression: '${args[2]}'")
    outputDir.mkdirs()

    for (example in examples) {
        val camera = parseCamera(example.id, example.camera)
        val model = toParts(example.build(), example.id).model()
        File(outputDir, "${example.id}.json").writeText(model.json())
        model.export(
            Format.Png(
                output = Output.Single(File(outputDir, "${example.id}.png").absolutePath),
                azimuth = camera.azimuth,
                elevation = camera.elevation,
                size = size,
                compression = compression,
            ),
        )
    }
}

private data class Camera(
    val azimuth: Double?,
    val elevation: Double?,
)

/** Parses the `// vg3:` header (`azimuth=… elevation=…`); absent options fall back to defaults. */
private fun parseCamera(id: String, camera: String): Camera {
    var azimuth: Double? = null
    var elevation: Double? = null
    camera.split(Regex("\\s+"))
        .filter(String::isNotEmpty)
        .forEach { token ->
            val key = token.substringBefore('=')
            val value = token.substringAfter('=', missingDelimiterValue = "")
            require(value.isNotEmpty()) { "example '$id': camera option '$token' must be key=value" }
            when (key) {
                "azimuth" -> azimuth = value.toDoubleOrNull()
                    ?: error("example '$id': invalid camera azimuth '$value'")
                "elevation" -> elevation = value.toDoubleOrNull()
                    ?: error("example '$id': invalid camera elevation '$value'")
                else -> error(
                    "example '$id': unknown camera option '$key' " +
                        "(size and compression are script parameters)",
                )
            }
        }
    return Camera(azimuth = azimuth, elevation = elevation)
}

/** A bare [Solid] becomes a single part named after the example; a `List<Part>` is used as is. */
private fun toParts(value: Any, id: String): List<Part> = when (value) {
    is Solid -> listOf(Part(name = id, solid = value))
    is List<*> -> value.map { element ->
        require(element is Part) {
            "example '$id' must list only Part, got ${element?.let { it::class.qualifiedName }}"
        }
        element
    }
    else -> error(
        "example '$id' must evaluate to Solid or List<Part>, got ${value::class.qualifiedName}",
    )
}
