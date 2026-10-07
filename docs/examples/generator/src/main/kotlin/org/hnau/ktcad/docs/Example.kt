package org.hnau.ktcad.docs

/**
 * One documentation example, compiled into the generated `examples` list.
 *
 * [id] is the source file name (without `.kt`) and names the exported JSON/PNG; [camera] is the raw
 * `// vg3:` header (parsed at export time); [build] evaluates the snippet to a `Solid` or
 * `List<Part>`.
 */
class Example(
    val id: String,
    val camera: String,
    val build: () -> Any,
)
