package org.hnau.ktcad

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
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

// --- Points -----------------------------------------------------------------

fun p(x: Double, y: Double): Vec2 = Vec2(x = x, y = y)

fun p(x: Double, y: Double, z: Double): Vec3 = Vec3(x = x, y = y, z = z)

// --- 2D segments ------------------------------------------------------------

fun lineTo(x: Double, y: Double): ContourSegment = lineTo(p(x, y))

fun lineTo(to: Vec2): ContourSegment = { Curve2.Line(to = to) }

fun lineRel(dx: Double, dy: Double): ContourSegment = { current ->
    val point = current()
    Curve2.Line(to = p(point.x + dx, point.y + dy))
}

fun arcTo(via: Vec2, to: Vec2): ContourSegment = { Curve2.Arc(via = via, to = to) }

fun arcTo(viaX: Double, viaY: Double, toX: Double, toY: Double): ContourSegment =
    arcTo(via = p(viaX, viaY), to = p(toX, toY))

fun arcRel(viaDx: Double, viaDy: Double, toDx: Double, toDy: Double): ContourSegment = { current ->
    val point = current()
    Curve2.Arc(
        via = p(point.x + viaDx, point.y + viaDy),
        to = p(point.x + toDx, point.y + toDy),
    )
}

fun splineTo(initial: Vec2, vararg additional: Vec2): ContourSegment =
    { Curve2.Spline(points = nonEmptyListOf(initial, *additional)) }

// --- 3D segments ------------------------------------------------------------

fun lineTo(x: Double, y: Double, z: Double): PathSegment = lineTo(p(x, y, z))

fun lineTo(to: Vec3): PathSegment = { Curve3.Line(to = to) }

fun lineRel(dx: Double, dy: Double, dz: Double): PathSegment = { current ->
    val point = current()
    Curve3.Line(to = p(point.x + dx, point.y + dy, point.z + dz))
}

fun arcTo(via: Vec3, to: Vec3): PathSegment = { Curve3.Arc(via = via, to = to) }

fun arcTo(
    viaX: Double, viaY: Double, viaZ: Double,
    toX: Double, toY: Double, toZ: Double,
): PathSegment = arcTo(via = p(viaX, viaY, viaZ), to = p(toX, toY, toZ))

fun arcRel(
    viaDx: Double, viaDy: Double, viaDz: Double,
    toDx: Double, toDy: Double, toDz: Double,
): PathSegment = { current ->
    val point = current()
    Curve3.Arc(
        via = p(point.x + viaDx, point.y + viaDy, point.z + viaDz),
        to = p(point.x + toDx, point.y + toDy, point.z + toDz),
    )
}

fun splineTo(initial: Vec3, vararg additional: Vec3): PathSegment =
    { Curve3.Spline(points = nonEmptyListOf(initial, *additional)) }

// --- Contour factory --------------------------------------------------------

/** A planar region from a start point and segments (relative segments see the current point). */
fun contour(start: Vec2, segments: NonEmptyList<ContourSegment>): Region {
    val first = segments.head({ start })
    val (edges, _) = segments.tail.fold(nonEmptyListOf(first) to { first.end() }) { (acc, current), segment ->
        val curve = segment(current)
        (acc + curve) to { curve.end() }
    }
    return Region.Contour(start = start, edges = edges)
}

fun contour(start: Vec2, initial: ContourSegment, vararg additional: ContourSegment): Region =
    contour(start, nonEmptyListOf(initial, *additional))

fun Path(start: Vec3, segments: NonEmptyList<PathSegment>): Path {
    val first = segments.head({ start })
    val (edges, _) = segments.tail.fold(nonEmptyListOf(first) to { first.end() }) { (acc, current), segment ->
        val curve = segment(current)
        (acc + curve) to { curve.end() }
    }
    return Path(start = start, edges = edges)
}

fun Path(start: Vec3, initial: PathSegment, vararg additional: PathSegment): Path =
    Path(start, nonEmptyListOf(initial, *additional))

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

private fun Curve2.end(): Vec2 = when (this) {
    is Curve2.Line -> to
    is Curve2.Arc -> to
    is Curve2.Spline -> points.last()
}

private fun Curve3.end(): Vec3 = when (this) {
    is Curve3.Line -> to
    is Curve3.Arc -> to
    is Curve3.Spline -> points.last()
    is Curve3.Helix -> error("a helix cannot be followed by a relative segment")
}
