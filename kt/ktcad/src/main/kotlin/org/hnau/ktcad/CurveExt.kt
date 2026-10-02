package org.hnau.ktcad

import org.hnau.ktcad.ir.Curve2
import org.hnau.ktcad.ir.Curve3
import org.hnau.ktcad.ir.Path
import org.hnau.ktcad.ir.Point2
import org.hnau.ktcad.ir.Point3
import org.hnau.ktcad.ir.Profile

/**
 * DSL sugar for contours: chained, immutable builders over the generated [Profile] / [Path].
 *
 * Every method returns a new contour, so a chain can stop at any point and the value is already a
 * valid `Profile`/`Path`. `…To` methods take **absolute** coordinates (matching the IR); `…Rel`
 * methods take offsets from the current point (the end of the last edge, or `start`).
 *
 * Note the engine auto-closes a `Profile` always, and a `Path` only when used as a `loft` section.
 */

// --- Points -----------------------------------------------------------------

fun p(x: Double, y: Double): Point2 = Point2(x = x, y = y)

fun p(x: Double, y: Double, z: Double): Point3 = Point3(x = x, y = y, z = z)

// --- Profile (2D contour) ---------------------------------------------------

/** Start a 2D contour at `(x, y)`. */
fun profile(x: Double, y: Double): Profile = Profile(start = p(x, y), edges = emptyList())

/** Start a 2D contour at [start]. */
fun profile(start: Point2): Profile = Profile(start = start, edges = emptyList())

/** A `line` to the absolute point `(x, y)`. */
fun Profile.lineTo(x: Double, y: Double): Profile = lineTo(p(x, y))

fun Profile.lineTo(to: Point2): Profile = copy(edges = edges + Curve2.Line(to = to))

/** A `line` by `(dx, dy)` from the current point. */
fun Profile.lineRel(dx: Double, dy: Double): Profile {
    val current = current
    return lineTo(current.x + dx, current.y + dy)
}

/** An `arc` (through `via`, ending at `to`), absolute. */
fun Profile.arcTo(viaX: Double, viaY: Double, toX: Double, toY: Double): Profile =
    arcTo(via = p(viaX, viaY), to = p(toX, toY))

fun Profile.arcTo(via: Point2, to: Point2): Profile =
    copy(edges = edges + Curve2.Arc(via = via, to = to))

/** An `arc` with `via` and `to` relative to the current point. */
fun Profile.arcRel(viaDx: Double, viaDy: Double, toDx: Double, toDy: Double): Profile {
    val current = current
    return arcTo(
        via = p(current.x + viaDx, current.y + viaDy),
        to = p(current.x + toDx, current.y + toDy),
    )
}

/** A `spline` through the given absolute points. */
fun Profile.splineTo(vararg points: Point2): Profile = splineTo(points.toList())

fun Profile.splineTo(points: List<Point2>): Profile =
    copy(edges = edges + Curve2.Spline(points = points))

// --- Path (3D contour) ------------------------------------------------------

/** Start a 3D contour at `(x, y, z)`. */
fun path(x: Double, y: Double, z: Double): Path = Path(start = p(x, y, z), edges = emptyList())

/** Start a 3D contour at [start]. */
fun path(start: Point3): Path = Path(start = start, edges = emptyList())

/** A `line` to the absolute point `(x, y, z)`. */
fun Path.lineTo(x: Double, y: Double, z: Double): Path = lineTo(p(x, y, z))

fun Path.lineTo(to: Point3): Path = copy(edges = edges + Curve3.Line(to = to))

/** A `line` by `(dx, dy, dz)` from the current point. */
fun Path.lineRel(dx: Double, dy: Double, dz: Double): Path {
    val current = current
    return lineTo(current.x + dx, current.y + dy, current.z + dz)
}

/** An `arc` (through `via`, ending at `to`), absolute. */
fun Path.arcTo(viaX: Double, viaY: Double, viaZ: Double, toX: Double, toY: Double, toZ: Double): Path =
    arcTo(via = p(viaX, viaY, viaZ), to = p(toX, toY, toZ))

fun Path.arcTo(via: Point3, to: Point3): Path =
    copy(edges = edges + Curve3.Arc(via = via, to = to))

/** An `arc` with `via` and `to` relative to the current point. */
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

/** A `spline` through the given absolute points. */
fun Path.splineTo(vararg points: Point3): Path = splineTo(points.toList())

fun Path.splineTo(points: List<Point3>): Path =
    copy(edges = edges + Curve3.Spline(points = points))

// --- Ready-made contours ----------------------------------------------------

/** A full circle of [radius] centred at the origin, as two `arc`s. */
fun circle(radius: Double): Profile = circle(center = p(0.0, 0.0), radius = radius)

/** A full circle of [radius] centred at [center], as two `arc`s. */
fun circle(center: Point2, radius: Double): Profile =
    profile(center.x + radius, center.y)
        .arcTo(via = p(center.x, center.y + radius), to = p(center.x - radius, center.y))
        .arcTo(via = p(center.x, center.y - radius), to = p(center.x + radius, center.y))

/** A closed 2D polygon through the given absolute points (the engine auto-closes profiles). */
fun polygon(vararg points: Point2): Profile = polygon(points.toList())

@JvmName("polygon2d")
fun polygon(points: List<Point2>): Profile {
    require(points.isNotEmpty()) { "polygon needs at least one point" }
    return points.drop(1).fold(profile(points.first())) { profile, point -> profile.lineTo(point) }
}

/** An open 3D polyline through the given absolute points. */
fun polyline(vararg points: Point3): Path = polyline(points.toList())

fun polyline(points: List<Point3>): Path {
    require(points.isNotEmpty()) { "polyline needs at least one point" }
    return points.drop(1).fold(path(points.first())) { path, point -> path.lineTo(point) }
}

/** A closed 3D polygon through the given absolute points. */
fun polygon(vararg points: Point3): Path = polygon(points.toList())

@JvmName("polygon3d")
fun polygon(points: List<Point3>): Path = polyline(points).close()

/** Closes a 3D contour with a `line` back to its start. */
fun Path.close(): Path = lineTo(start)

// --- Current point ----------------------------------------------------------

/** The end of the last edge, or `start` for an empty contour. */
val Profile.current: Point2
    get() = edges.lastOrNull()?.let { curve ->
        when (curve) {
            is Curve2.Line -> curve.to
            is Curve2.Arc -> curve.to
            is Curve2.Spline -> curve.points.last()
        }
    } ?: start

/** The end of the last edge, or `start` for an empty contour. */
val Path.current: Point3
    get() = edges.lastOrNull()?.let { curve ->
        when (curve) {
            is Curve3.Line -> curve.to
            is Curve3.Arc -> curve.to
            is Curve3.Spline -> curve.points.last()
            is Curve3.Helix -> error("helix has no explicit end point; use lineTo/arcTo after it")
        }
    } ?: start
