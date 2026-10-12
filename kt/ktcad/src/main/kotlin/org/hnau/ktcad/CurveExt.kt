package org.hnau.ktcad

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import org.hnau.ktcad.bbox.combineWith
import org.hnau.ktcad.ir.Curve2
import org.hnau.ktcad.ir.Curve3
import org.hnau.ktcad.ir.Path
import org.hnau.ktcad.ir.Vec2
import org.hnau.ktcad.ir.Vec3

/**
 * DSL sugar for contours.
 *
 * A 2D contour is a [Region] with at least one edge (`edges` is a `NonEmptyList`), built by the
 * `contour(...)` factory from a start point and **segments**. A 3D path is built by `Path(...)`.
 * `…To` segments are absolute, `…Rel` are relative to the current point; a segment receives the
 * current point **lazily** (`() -> Point`), so an absolute segment does not force it — which lets an
 * absolute segment follow a `helix` (whose end the DSL cannot compute).
 */

typealias ContourSegment = (() -> Vec2) -> Curve2
typealias PathSegment = (() -> Vec3) -> Curve3

// --- 2D segments ------------------------------------------------------------

fun lineTo(to: Vec2): ContourSegment = { Curve2.Line(to = to) }

/** A `line` offset from the current point by `delta`. */
fun lineRel(delta: Vec2): ContourSegment = { current ->
    Curve2.Line(to = current().combineWith(delta) { a, b -> a + b })
}

fun arcTo(via: Vec2, to: Vec2): ContourSegment = { Curve2.Arc(via = via, to = to) }

/** An `arc` whose `via` and `to` are offsets from the current point. */
fun arcRel(via: Vec2, to: Vec2): ContourSegment = { current ->
    val point = current()
    Curve2.Arc(
        via = point.combineWith(via) { a, b -> a + b },
        to = point.combineWith(to) { a, b -> a + b },
    )
}

fun splineTo(
    initial: Vec2,
    vararg additional: Vec2,
    tangentStart: Vec2? = null,
    tangentEnd: Vec2? = null,
): ContourSegment = {
    Curve2.Spline(
        points = nonEmptyListOf(initial, *additional),
        tangent_start = tangentStart,
        tangent_end = tangentEnd,
    )
}

/** A 2D Bezier: [first], [second] and [additional] are the poles after the current point. */
fun bezierTo(first: Vec2, second: Vec2, vararg additional: Vec2): ContourSegment =
    { Curve2.Bezier(points = nonEmptyListOf(first, second, *additional)) }

// --- 3D segments ------------------------------------------------------------

fun lineTo(to: Vec3): PathSegment = { Curve3.Line(to = to) }

/** A `line` offset from the current point by `delta`. */
fun lineRel(delta: Vec3): PathSegment = { current ->
    Curve3.Line(to = current().combineWith(delta) { a, b -> a + b })
}

fun arcTo(via: Vec3, to: Vec3): PathSegment = { Curve3.Arc(via = via, to = to) }

/** An `arc` whose `via` and `to` are offsets from the current point. */
fun arcRel(via: Vec3, to: Vec3): PathSegment = { current ->
    val point = current()
    Curve3.Arc(
        via = point.combineWith(via) { a, b -> a + b },
        to = point.combineWith(to) { a, b -> a + b },
    )
}

fun splineTo(
    initial: Vec3,
    vararg additional: Vec3,
    tangentStart: Vec3? = null,
    tangentEnd: Vec3? = null,
): PathSegment = {
    Curve3.Spline(
        points = nonEmptyListOf(initial, *additional),
        tangent_start = tangentStart,
        tangent_end = tangentEnd,
    )
}

/** A 3D Bezier: [first], [second] and [additional] are the poles after the current point. */
fun bezierTo(first: Vec3, second: Vec3, vararg additional: Vec3): PathSegment =
    { Curve3.Bezier(points = nonEmptyListOf(first, second, *additional)) }

/**
 * A helix around the Z axis through the origin, starting at the current point (its radius and phase
 * are taken from there). [rightHanded] selects the winding; the number of turns is `height / pitch`.
 */
fun helix(pitch: Double, height: Double, rightHanded: Boolean = true): PathSegment =
    { Curve3.Helix(pitch = pitch, height = height, right_handed = rightHanded) }

// --- Contour factory --------------------------------------------------------

/** A planar region from a start point and segments (relative segments see the current point). */
fun contour(start: Vec2, segments: NonEmptyList<ContourSegment>): Region =
    Region.Contour(start = start, edges = buildChain(start, segments, Curve2::end))

fun contour(start: Vec2, initial: ContourSegment, vararg additional: ContourSegment): Region =
    contour(start, nonEmptyListOf(initial, *additional))

fun Path(start: Vec3, segments: NonEmptyList<PathSegment>): Path =
    Path(start = start, edges = buildChain(start, segments, Curve3::end))

fun Path(start: Vec3, initial: PathSegment, vararg additional: PathSegment): Path =
    Path(start, nonEmptyListOf(initial, *additional))

/**
 * Chains [segments] into edges: each segment is given the current point **lazily** (`() -> Point`),
 * so an absolute segment does not force it, and [end] tracks the running endpoint.
 */
private fun <P, C> buildChain(
    start: P,
    segments: NonEmptyList<(() -> P) -> C>,
    end: (C) -> P,
): NonEmptyList<C> {
    val first = segments.head({ start })
    val (edges, _) = segments.tail.fold(nonEmptyListOf(first) to { end(first) }) { (acc, current), segment ->
        val curve = segment(current)
        (acc + curve) to { end(curve) }
    }
    return edges
}

// --- Ready-made 3D contours -------------------------------------------------

/** An open 3D polyline through the given absolute points. */
fun polyline(first: Vec3, second: Vec3, vararg tail: Vec3): Path =
    Path(first, lineTo(second), *tail.map { lineTo(it) }.toTypedArray())

/** A closed 3D polygon through the given absolute points. */
fun polygon(first: Vec3, second: Vec3, vararg tail: Vec3): Path =
    polyline(first, second, *tail).close()

/** Closes a 3D contour with a `line` back to its start. */
fun Path.close(): Path = copy(edges = edges + Curve3.Line(to = start))

// --- Segment endpoints ------------------------------------------------------

internal fun Curve2.end(): Vec2 = when (this) {
    is Curve2.Line -> to
    is Curve2.Arc -> to
    is Curve2.Spline -> points.last()
    is Curve2.Bezier -> points.last()
}

private fun Curve3.end(): Vec3 = when (this) {
    is Curve3.Line -> to
    is Curve3.Arc -> to
    is Curve3.Spline -> points.last()
    is Curve3.Bezier -> points.last()
    is Curve3.Helix -> error("a helix cannot be followed by a relative segment")
}
