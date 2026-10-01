package org.hnau.ktcad

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * The export configuration handed to `vg3 --export-config-json`.
 *
 * A discriminated union on `format`; `output` (the shared layout) lives on each variant. Optional
 * parameters are omitted when null, so the engine applies its own defaults.
 */
@Serializable
@JsonClassDiscriminator("format")
sealed interface Format {

    @SerialName("stl")
    @Serializable
    data class Stl(
        val output: Output,
        val tolerance: Double? = null,
    ) : Format

    @SerialName("png")
    @Serializable
    data class Png(
        val output: Output,
        val size: Int? = null,
        val azimuth: Double? = null,
        val elevation: Double? = null,
    ) : Format
}

/** How an exporter lays its results out on disk (`single` or `multi`). */
@Serializable
sealed interface Output {

    /** Everything into one file. */
    @SerialName("single")
    @Serializable
    data class Single(val filename: String) : Output

    /** One file per exported part: `<path>/<name>.<extension>`, named by the model's `export` list. */
    @SerialName("multi")
    @Serializable
    data class Multi(val path: String) : Output
}

/** Serializes the export configuration exactly as the CLI expects. */
internal fun Format.toJson(): String = exportConfigJson.encodeToString(Format.serializer(), this)

internal val exportConfigJson = Json {
    encodeDefaults = false
    explicitNulls = false
    prettyPrint = true
}
