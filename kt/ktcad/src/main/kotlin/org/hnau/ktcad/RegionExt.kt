package org.hnau.ktcad

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import org.hnau.ktcad.ir.BooleanKind
import org.hnau.ktcad.ir.Curve2
import org.hnau.ktcad.ir.JoinKind
import org.hnau.ktcad.ir.RadiusSpec
import org.hnau.ktcad.ir.TransformOp2
import org.hnau.ktcad.ir.Vec2

/**
 * DSL sugar over [Region] (the planar sketch domain): primitives, a free contour, booleans and
 * planar transforms. Every function returns a **new** `Region` (the domain graph is immutable).
 */

// --- Primitives -------------------------------------------------------------
//
// `circle` is an OpenCASCADE primitive (`gp_Circ`) and maps straight to `Region.Circle`. The IR has
// no `rect` node (OpenCASCADE has no rectangle primitive), so `rect` is pure DSL sugar: a `Polygon`.

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
    val dx = if (centerX) -width / 2 else 0.0
    val dy = if (centerY) -height / 2 else 0.0
    return if (dx == 0.0 && dy == 0.0) rectangle else rectangle.translate(dx, dy)
}

/** A full circle of [radius] centred at the origin. */
fun circle(radius: Double): Region = Region.Circle(radius = radius)

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

// --- Fillet / offset --------------------------------------------------------

/** Round every corner of the region with a constant [radius] (`RadiusSpec.All`, `BRepFilletAPI_MakeFillet2d`). */
fun Region.fillet2dAll(radius: Double): Region =
    Region.Fillet2d(target = this, radius = RadiusSpec.All(radius = radius))

/** Round every corner with a per-corner Rhai [expression] returning the radius (`RadiusSpec.Expression`). */
fun Region.fillet2dExpression(expression: String): Region =
    Region.Fillet2d(target = this, radius = RadiusSpec.Expression(expression = expression))

/** Round only the corners selected by a boolean [expression] (variable `vertex`), with [radius] (`RadiusSpec.Selected`). */
fun Region.fillet2dSelected(expression: String, radius: Double): Region =
    Region.Fillet2d(target = this, radius = RadiusSpec.Selected(expression = expression, radius = radius))

/** Grow (positive [distance]) or shrink (negative) the region's contour (`BRepOffsetAPI_MakeOffset`). */
fun Region.offset2d(distance: Double): Region = Region.Offset2d(target = this, distance = distance)

/** As [offset2d], joining the offset contour with [join]. */
fun Region.offset2d(distance: Double, join: JoinKind): Region =
    Region.Offset2d(target = this, distance = distance, join = join)
