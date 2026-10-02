package org.hnau.ktcad

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import org.hnau.ktcad.ir.Curve2
import org.hnau.ktcad.ir.Curve3
import org.hnau.ktcad.ir.Path
import org.hnau.ktcad.ir.Point2
import org.hnau.ktcad.ir.Point3
import org.hnau.ktcad.ir.Profile

/**
 * DSL sugar for contours.
 *
 * A contour always has at least one edge (`edges` is a `NonEmptyList`), so it is built by a factory
 * (`Profile(...)`/`Path(...)`, capitalised like the generated class) from a start point and
 * **segments**. `…To` segments are absolute, `…Rel` are relative to the current point; a segment
 * receives the current point **lazily** (`() -> Point`), so an absolute segment does not force it —
 * which lets an absolute segment follow a `helix` (whose end the DSL cannot compute).
 */

typealias ProfileSegment = (() -> Point2) -> Curve2
typealias PathSegment = (() -> Point3) -> Curve3

// --- Points -----------------------------------------------------------------

fun p(x: Double, y: Double): Point2 = Point2(x = x, y = y)

fun p(x: Double, y: Double, z: Double): Point3 = Point3(x = x, y = y, z = z)

// --- 2D segments ------------------------------------------------------------

fun lineTo(x: Double, y: Double): ProfileSegment = lineTo(p(x, y))

fun lineTo(to: Point2): ProfileSegment = { Curve2.Line(to = to) }

fun lineRel(dx: Double, dy: Double): ProfileSegment = { current ->
    val point = current()
    Curve2.Line(to = p(point.x + dx, point.y + dy))
}

fun arcTo(via: Point2, to: Point2): ProfileSegment = { Curve2.Arc(via = via, to = to) }

fun arcTo(viaX: Double, viaY: Double, toX: Double, toY: Double): ProfileSegment =
    arcTo(via = p(viaX, viaY), to = p(toX, toY))

fun arcRel(viaDx: Double, viaDy: Double, toDx: Double, toDy: Double): ProfileSegment = { current ->
    val point = current()
    Curve2.Arc(
        via = p(point.x + viaDx, point.y + viaDy),
        to = p(point.x + toDx, point.y + toDy),
    )
}

fun splineTo(initial: Point2, vararg additional: Point2): ProfileSegment =
    { Curve2.Spline(points = nonEmptyListOf(initial, *additional)) }

// --- 3D segments ------------------------------------------------------------

fun lineTo(x: Double, y: Double, z: Double): PathSegment = lineTo(p(x, y, z))

fun lineTo(to: Point3): PathSegment = { Curve3.Line(to = to) }

fun lineRel(dx: Double, dy: Double, dz: Double): PathSegment = { current ->
    val point = current()
    Curve3.Line(to = p(point.x + dx, point.y + dy, point.z + dz))
}

fun arcTo(via: Point3, to: Point3): PathSegment = { Curve3.Arc(via = via, to = to) }

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

fun splineTo(initial: Point3, vararg additional: Point3): PathSegment =
    { Curve3.Spline(points = nonEmptyListOf(initial, *additional)) }

// --- Contour factories ------------------------------------------------------

fun Profile(start: Point2, segments: NonEmptyList<ProfileSegment>): Profile {
    val first = segments.head({ start })
    val (edges, _) = segments.tail.fold(nonEmptyListOf(first) to { first.end() }) { (acc, current), segment ->
        val curve = segment(current)
        (acc + curve) to { curve.end() }
    }
    return Profile(start = start, edges = edges)
}

fun Profile(start: Point2, initial: ProfileSegment, vararg additional: ProfileSegment): Profile =
    Profile(start, nonEmptyListOf(initial, *additional))

fun Path(start: Point3, segments: NonEmptyList<PathSegment>): Path {
    val first = segments.head({ start })
    val (edges, _) = segments.tail.fold(nonEmptyListOf(first) to { first.end() }) { (acc, current), segment ->
        val curve = segment(current)
        (acc + curve) to { curve.end() }
    }
    return Path(start = start, edges = edges)
}

fun Path(start: Point3, initial: PathSegment, vararg additional: PathSegment): Path =
    Path(start, nonEmptyListOf(initial, *additional))

// --- Ready-made contours ----------------------------------------------------

/** A full circle of [radius] centred at the origin, as two `arc`s. */
fun circle(radius: Double): Profile = circle(center = p(0.0, 0.0), radius = radius)

/** A full circle of [radius] centred at [center], as two `arc`s. */
fun circle(center: Point2, radius: Double): Profile = Profile(
    start = p(center.x + radius, center.y),
    arcTo(via = p(center.x, center.y + radius), to = p(center.x - radius, center.y)),
    arcTo(via = p(center.x, center.y - radius), to = p(center.x + radius, center.y)),
)

/** A 2D polygon through the given absolute points (the engine auto-closes profiles). */
fun polygon(first: Point2, second: Point2, vararg tail: Point2): Profile =
    Profile(first, lineTo(second), *tail.map { lineTo(it) }.toTypedArray())

/** An open 3D polyline through the given absolute points. */
fun polyline(first: Point3, second: Point3, vararg tail: Point3): Path =
    Path(first, lineTo(second), *tail.map { lineTo(it) }.toTypedArray())

/** A closed 3D polygon through the given absolute points. */
fun polygon(first: Point3, second: Point3, vararg tail: Point3): Path =
    polyline(first, second, *tail).close()

/** Closes a 3D contour with a `line` back to its start. */
fun Path.close(): Path = copy(edges = edges + Curve3.Line(to = start))

// --- Segment endpoints ------------------------------------------------------

private fun Curve2.end(): Point2 = when (this) {
    is Curve2.Line -> to
    is Curve2.Arc -> to
    is Curve2.Spline -> points.last()
}

private fun Curve3.end(): Point3 = when (this) {
    is Curve3.Line -> to
    is Curve3.Arc -> to
    is Curve3.Spline -> points.last()
    is Curve3.Helix -> error("a helix cannot be followed by a relative segment")
}
