package org.hnau.ktcad

/**
 * DSL sugar for the print/preview workflow: one call renders a STEP preview of the whole assembly
 * (design models together with preview-only helpers) and exports the printable parts as STL.
 */

/**
 * A part in the [stepPreviewAndStlExport] workflow: [part] is always rendered in the STEP preview;
 * it is exported to STL only when [stlTransformation] is set.
 *
 * [stlTransformation] adjusts the shape for printing only (e.g. reorienting it to avoid overhangs);
 * the STEP preview keeps the design orientation.
 */
data class PrintPart(
    val part: Part,
    val stlTransformation: (Solid.() -> Solid)?,
)

/**
 * Renders every part as a STEP preview and exports the printable parts as STL, running the engine
 * twice.
 *
 * The STEP model holds all parts in design orientation; the STL model only the parts with an
 * [PrintPart.stlTransformation], each transformed by it.
 */
fun List<PrintPart>.stepPreviewAndStlExport(
    step: Format.Step = Format.Step(filename = "out/preview.step"),
    stl: Format.Stl = Format.Stl(
        output = Output.Multi(
            path = "out/stl",
        )
    ),
) {
    map(PrintPart::part)
        .model()
        .export(step)

    mapNotNull { printPart ->
        val stlTransform = printPart.stlTransformation ?: return@mapNotNull null
        val part = printPart.part
        Part(
            name = part.name,
            solid = part.solid.stlTransform(),
            color = part.color,
        )
    }
        .model()
        .export(stl)
}
