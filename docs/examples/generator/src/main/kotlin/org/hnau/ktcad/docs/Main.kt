package org.hnau.ktcad.docs

import org.hnau.ktcad.Format
import org.hnau.ktcad.Output
import org.hnau.ktcad.Part
import org.hnau.ktcad.Solid
import org.hnau.ktcad.Vg3
import org.hnau.ktcad.docs.generated.Example
import java.io.File

/**
 * The docs example generator: compiles the snippet selected by the `generate` Gradle task, lowers it
 * to the IR, and writes `<name>.json` (canonical IR) plus `<name>.png` (preview) into the output
 * directory.
 *
 * The snippet lives in a `.kt` file whose first `// vg3:` comment line carries the camera; the file
 * name (without `.kt`) is the part name. The snippet must evaluate to a [Solid] or a `List<Part>`.
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: <example.kt> <out-dir>" }
    val input = File(args[0])
    val outputDir = File(args[1])
    require(input.isFile) { "example file not found: ${input.absolutePath}" }

    val camera = parseCamera(input.readText())
    val name = input.nameWithoutExtension
    val parts = toParts(Example.value(), name)

    outputDir.mkdirs()
    File(outputDir, "$name.json").writeText(Vg3.json(parts))
    Vg3.export(
        parts = parts,
        format = Format.Png(
            output = Output.Single(File(outputDir, "$name.png").absolutePath),
            azimuth = camera.azimuth,
            elevation = camera.elevation,
            size = camera.size,
        ),
    )
}

private const val CameraPrefix = "// vg3:"

private data class Camera(
    val azimuth: Double?,
    val elevation: Double?,
    val size: Int?,
)

/** Reads the `// vg3: key=value …` line; absent options fall back to the engine defaults. */
private fun parseCamera(source: String): Camera {
    val header = source.lineSequence()
        .map(String::trim)
        .firstOrNull { it.startsWith(CameraPrefix) }
        ?: return Camera(azimuth = null, elevation = null, size = null)

    var azimuth: Double? = null
    var elevation: Double? = null
    var size: Int? = null
    header.removePrefix(CameraPrefix)
        .trim()
        .split(Regex("\\s+"))
        .filter(String::isNotEmpty)
        .forEach { token ->
            val key = token.substringBefore('=')
            val value = token.substringAfter('=', missingDelimiterValue = "")
            require(value.isNotEmpty()) { "camera option '$token' must be key=value" }
            when (key) {
                "azimuth" -> azimuth = value.toDoubleOrNull()
                    ?: error("invalid camera azimuth: '$value'")
                "elevation" -> elevation = value.toDoubleOrNull()
                    ?: error("invalid camera elevation: '$value'")
                "size" -> size = value.toIntOrNull()
                    ?: error("invalid camera size: '$value'")
                else -> error("unknown camera option '$key'")
            }
        }
    return Camera(azimuth = azimuth, elevation = elevation, size = size)
}

/** A bare [Solid] becomes a single named part; a `List<Part>` is used as is. */
private fun toParts(value: Any, name: String): List<Part> = when (value) {
    is Solid -> listOf(Part(name = name, solid = value))
    is List<*> -> value.map { element ->
        require(element is Part) {
            "an example list must contain only Part, got ${element?.let { it::class.qualifiedName }}"
        }
        element
    }
    else -> error("an example must evaluate to Solid or List<Part>, got ${value::class.qualifiedName}")
}
