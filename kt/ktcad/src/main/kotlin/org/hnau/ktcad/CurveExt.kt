package org.hnau.ktcad

import arrow.core.nonEmptyListOf
import arrow.core.toNonEmptyListOrThrow
import org.hnau.ktcad.ir.Curve2
import org.hnau.ktcad.ir.Curve3
import org.hnau.ktcad.ir.Path
import org.hnau.ktcad.ir.Point2
import org.hnau.ktcad.ir.Point3
import org.hnau.ktcad.ir.Profile
import kotlin.jvm.JvmName

/**
 * DSL sugar for contours: chained, immutable builders. A contour always has at least one edge
 * (`Profile.edges`/`Path.edges` are `NonEmptyList`), so a chain **starts from a point** — the first
 * segment creates the contour, and every call returns a ready `Profile`/`Path`.
 *
 * `…To` methods take **absolute** coordinates (matching the IR); `…Rel` methods take offsets from
 * the current point (the end of the last edge).
 *
 * Note the engine auto-closes a `Profile` always, and a `Path` only when used as a `loft` section.
 */

// --- Points -----------------------------------------------------------------

fun p(x: Double, y: Double): Point2 = Point2(x = x, y = y)

fun p(x: Double, y: Double, z: Double): Point3 = Point3(x = x, y = y, z = z)

// --- Profile (2D contour) ---------------------------------------------------

/** Start a 2D contour with a `line` to the absolute point `(x, y)`. */
fun Point2.lineTo(x: Double, y: Double): Profile = lineTo(p(x, y))

fun Point2.lineTo(to: Point2): Profile =
    Profile(start = this, edges = nonEmptyListOf(Curve2.Line(to = to)))

/** Start a 2D contour with a `line` by `(dx, dy)`. */
fun Point2.lineRel(dx: Double, dy: Double): Profile = lineTo(x + dx, y + dy)

/** Start a 2D contour with an `arc` through `via` ending at `to`, absolute. */
fun Point2.arcTo(viaX: Double, viaY: Double, toX: Double, toY: Double): Profile =
    arcTo(via = p(viaX, viaY), to = p(toX, toY))

fun Point2.arcTo(via: Point2, to: Point2): Profile =
    Profile(start = this, edges = nonEmptyListOf(Curve2.Arc(via = via, to = to)))

/** Start a 2D contour with an `arc` whose `via`/`to` are relative to this point. */
fun Point2.arcRel(viaDx: Double, viaDy: Double, toDx: Double, toDy: Double): Profile =
    arcTo(via = p(x + viaDx, y + viaDy), to = p(x + toDx, y + toDy))

/** Start a 2D contour with a `spline` through the given absolute points. */
fun Point2.splineTo(vararg points: Point2): Profile =
    Profile(start = this, edges = nonEmptyListOf(Curve2.Spline(points = points.toList().toNonEmptyListOrThrow())))

/** Append a `line` to the absolute point `(x, y)`. */
fun Profile.lineTo(x: Double, y: Double): Profile = lineTo(p(x, y))

fun Profile.lineTo(to: Point2): Profile = copy(edges = edges + Curve2.Line(to = to))

/** Append a `line` by `(dx, dy)` from the current point. */
fun Profile.lineRel(dx: Double, dy: Double): Profile {
    val current = current
    return lineTo(current.x + dx, current.y + dy)
}

/** Append an `arc` through `via` ending at `to`, absolute. */
fun Profile.arcTo(viaX: Double, viaY: Double, toX: Double, toY: Double): Profile =
    arcTo(via = p(viaX, viaY), to = p(toX, toY))

fun Profile.arcTo(via: Point2, to: Point2): Profile = copy(edges = edges + Curve2.Arc(via = via, to = to))

/** Append an `arc` whose `via`/`to` are relative to the current point. */
fun Profile.arcRel(viaDx: Double, viaDy: Double, toDx: Double, toDy: Double): Profile {
    val current = current
    return arcTo(
        via = p(current.x + viaDx, current.y + viaDy),
        to = p(current.x + toDx, current.y + toDy),
    )
}

/** Append a `spline` through the given absolute points. */
fun Profile.splineTo(vararg points: Point2): Profile =
    copy(edges = edges + Curve2.Spline(points = points.toList().toNonEmptyListOrThrow()))

// --- Path (3D contour) ------------------------------------------------------

/** Start a 3D contour with a `line` to the absolute point `(x, y, z)`. */
fun Point3.lineTo(x: Double, y: Double, z: Double): Path = lineTo(p(x, y, z))

fun Point3.lineTo(to: Point3): Path =
    Path(start = this, edges = nonEmptyListOf(Curve3.Line(to = to)))

/** Start a 3D contour with a `line` by `(dx, dy, dz)`. */
fun Point3.lineRel(dx: Double, dy: Double, dz: Double): Path = lineTo(x + dx, y + dy, z + dz)

/** Start a 3D contour with an `arc` through `via` ending at `to`, absolute. */
fun Point3.arcTo(
    viaX: Double, viaY: Double, viaZ: Double,
    toX: Double, toY: Double, toZ: Double,
): Path = arcTo(via = p(viaX, viaY, viaZ), to = p(toX, toY, toZ))

fun Point3.arcTo(via: Point3, to: Point3): Path =
    Path(start = this, edges = nonEmptyListOf(Curve3.Arc(via = via, to = to)))

/** Start a 3D contour with an `arc` whose `via`/`to` are relative to this point. */
fun Point3.arcRel(
    viaDx: Double, viaDy: Double, viaDz: Double,
    toDx: Double, toDy: Double, toDz: Double,
): Path = arcTo(
    via = p(x + viaDx, y + viaDy, z + viaDz),
    to = p(x + toDx, y + toDy, z + toDz),
)

/** Start a 3D contour with a `spline` through the given absolute points. */
fun Point3.splineTo(vararg points: Point3): Path =
    Path(start = this, edges = nonEmptyListOf(Curve3.Spline(points = points.toList().toNonEmptyListOrThrow())))

/** Append a `line` to the absolute point `(x, y, z)`. */
fun Path.lineTo(x: Double, y: Double, z: Double): Path = lineTo(p(x, y, z))

fun Path.lineTo(to: Point3): Path = copy(edges = edges + Curve3.Line(to = to))

/** Append a `line` by `(dx, dy, dz)` from the current point. */
fun Path.lineRel(dx: Double, dy: Double, dz: Double): Path {
    val current = current
    return lineTo(current.x + dx, current.y + dy, current.z + dz)
}

/** Append an `arc` through `via` ending at `to`, absolute. */
fun Path.arcTo(
    viaX: Double, viaY: Double, viaZ: Double,
    toX: Double, toY: Double, toZ: Double,
): Path = arcTo(via = p(viaX, viaY, viaZ), to = p(toX, toY, toZ))

fun Path.arcTo(via: Point3, to: Point3): Path = copy(edges = edges + Curve3.Arc(via = via, to = to))

/** Append an `arc` whose `via`/`to` are relative to the current point. */
fun Path.arcRel(
    viaDx: Double, viaDy: Double, viaDz: Double,
    toDx: Double, toDy: Double, toDz: Double,
): Path {
    val current = current
    return arcTo(
        via = p(current.x + viaDx, current.y + viaDy, current.z + viaDz),
        to = p(current.x + toDx, current.y + toDy, current.z + toDz),
    )
}

/** Append a `spline` through the given absolute points. */
fun Path.splineTo(vararg points: Point3): Path =
    copy(edges = edges + Curve3.Spline(points = points.toList().toNonEmptyListOrThrow()))

// --- Ready-made contours ----------------------------------------------------

/** A full circle of [radius] centred at the origin, as two `arc`s. */
fun circle(radius: Double): Profile = circle(center = p(0.0, 0.0), radius = radius)

/** A full circle of [radius] centred at [center], as two `arc`s. */
fun circle(center: Point2, radius: Double): Profile =
    p(center.x + radius, center.y)
        .arcTo(via = p(center.x, center.y + radius), to = p(center.x - radius, center.y))
        .arcTo(via = p(center.x, center.y - radius), to = p(center.x + radius, center.y))

/** A closed 2D polygon through the given absolute points (the engine auto-closes profiles). */
fun polygon(vararg points: Point2): Profile = polygon(points.toList())

@JvmName("polygon2d")
fun polygon(points: List<Point2>): Profile = polyline(points.first(), points.drop(1))

/** An open 3D polyline through the given absolute points. */
fun polyline(vararg points: Point3): Path = polyline(points.first(), points.drop(1))

/** A closed 3D polygon through the given absolute points. */
fun polygon(vararg points: Point3): Path = polygon(points.toList())

@JvmName("polygon3d")
fun polygon(points: List<Point3>): Path = polyline(points.first(), points.drop(1)).close()

private fun polyline(first: Point2, rest: List<Point2>): Profile {
    require(rest.isNotEmpty()) { "polygon needs at least two points" }
    return rest.drop(1).fold(first.lineTo(rest.first())) { profile, point -> profile.lineTo(point) }
}

private fun polyline(first: Point3, rest: List<Point3>): Path {
    require(rest.isNotEmpty()) { "polyline needs at least two points" }
    return rest.drop(1).fold(first.lineTo(rest.first())) { path, point -> path.lineTo(point) }
}

/** Closes a 3D contour with a `line` back to its start. */
fun Path.close(): Path = lineTo(start)

// --- Current point ----------------------------------------------------------

/** The end of the last edge (a contour always has at least one edge). */
val Profile.current: Point2
    get() = when (val last = edges.last()) {
        is Curve2.Line -> last.to
        is Curve2.Arc -> last.to
        is Curve2.Spline -> last.points.last()
    }

/** The end of the last edge (a contour always has at least one edge). */
val Path.current: Point3
    get() = when (val last = edges.last()) {
        is Curve3.Line -> last.to
        is Curve3.Arc -> last.to
        is Curve3.Spline -> last.points.last()
        is Curve3.Helix -> error("helix has no explicit end point; use lineTo/arcTo after it")
    }
