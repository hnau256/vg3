package org.hnau.ktcad

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import org.hnau.ktcad.ir.BooleanKind
import org.hnau.ktcad.ir.Curve2
import org.hnau.ktcad.ir.TransformOp2
import org.hnau.ktcad.ir.Vec2

/**
 * DSL sugar over [Region] (the planar sketch domain): primitives, a free contour, booleans and
 * planar transforms. Every function returns a **new** `Region` (the domain graph is immutable).
 */

// --- Primitives -------------------------------------------------------------
//
// The IR has no `rect`/`circle` node (OpenCASCADE has no such primitives), so these are pure DSL
// sugar: `rect` is a `Polygon`, `circle` is a `Contour` of two arcs.

/**
 * An axis-aligned rectangle with its corner at the origin, extending into the `+` quadrant. A
 * `center*` flag shifts it by half its size along that axis so it is centred there instead.
 */
fun rect(
    width: Double,
    height: Double,
    centerX: Boolean = false,
    centerY: Boolean = false,
): Region {
    val rectangle = Region.Polygon(
        points = nonEmptyListOf(
            Vec2(x = 0.0, y = 0.0),
            Vec2(x = width, y = 0.0),
            Vec2(x = width, y = height),
            Vec2(x = 0.0, y = height),
        ),
    )
    return rectangle.translate(
        if (centerX) -width / 2 else 0.0,
        if (centerY) -height / 2 else 0.0,
    )
}

/** A full circle of [radius] centred at the origin, as two `arc`s. */
fun circle(radius: Double): Region = Region.Contour(
    start = Vec2(x = radius, y = 0.0),
    edges = nonEmptyListOf(
        Curve2.Arc(via = Vec2(x = 0.0, y = radius), to = Vec2(x = -radius, y = 0.0)),
        Curve2.Arc(via = Vec2(x = 0.0, y = -radius), to = Vec2(x = radius, y = 0.0)),
    ),
)

fun polygon(first: Vec2, second: Vec2, vararg tail: Vec2): Region =
    Region.Polygon(points = nonEmptyListOf(first, second, *tail))

fun contour(start: Vec2, edges: NonEmptyList<Curve2>): Region =
    Region.Contour(start = start, edges = edges)

fun contour(start: Vec2, initial: Curve2, vararg additional: Curve2): Region =
    contour(start, nonEmptyListOf(initial, *additional))

// --- Booleans ---------------------------------------------------------------

fun Region.union(other: Region): Region = regionBool(BooleanKind.FUSE, this, other)

fun Region.cut(other: Region): Region = regionBool(BooleanKind.CUT, this, other)

fun Region.intersect(other: Region): Region = regionBool(BooleanKind.COMMON, this, other)

operator fun Region.plus(other: Region): Region = union(other)

operator fun Region.minus(other: Region): Region = cut(other)

operator fun Region.times(other: Region): Region = intersect(other)

private fun regionBool(kind: BooleanKind, first: Region, second: Region): Region = Region.Bool(
    kind = kind,
    arguments = nonEmptyListOf(first),
    tools = nonEmptyListOf(second),
)

// --- Transforms -------------------------------------------------------------

private val REGION_ORIGIN = Vec2(x = 0.0, y = 0.0)

fun Region.translate(dx: Double, dy: Double): Region =
    Region.Transform(target = this, op = TransformOp2.Translate(value = Vec2(dx, dy)))

fun Region.rotate(angle: Double, center: Vec2 = REGION_ORIGIN): Region =
    Region.Transform(target = this, op = TransformOp2.Rotate(center = center, angle = angle))

fun Region.mirror(normal: Vec2, center: Vec2 = REGION_ORIGIN): Region =
    Region.Transform(target = this, op = TransformOp2.Mirror(center = center, normal = normal))

fun Region.scale(x: Double, y: Double): Region =
    Region.Transform(target = this, op = TransformOp2.Scale(value = Vec2(x = x, y = y)))
