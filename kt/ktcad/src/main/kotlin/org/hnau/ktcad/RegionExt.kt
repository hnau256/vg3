package org.hnau.ktcad

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import org.hnau.ktcad.axis.Axis2
import org.hnau.ktcad.axis.Axis2Values
import org.hnau.ktcad.axis.direction
import org.hnau.ktcad.axis.times
import org.hnau.ktcad.axis.toVec2
import org.hnau.ktcad.ir.BooleanKind
import org.hnau.ktcad.ir.JoinKind
import org.hnau.ktcad.ir.RadiusSpec
import org.hnau.ktcad.ir.TransformOp2

/**
 * DSL sugar over [Region] (the planar sketch domain): primitives, a free contour, booleans and
 * planar transforms. Every function returns a **new** `Region` (the domain graph is immutable).
 */

// --- Primitives -------------------------------------------------------------
//
// `circle` is an OpenCASCADE primitive (`gp_Circ`) and maps straight to `Region.Circle`. The IR has
// no `rect` node (OpenCASCADE has no rectangle primitive), so `rect` is pure DSL sugar: a `Polygon`.

/**
 * An axis-aligned rectangle with its corner at the origin, extending along `+X`/`+Y` by [size]. A
 * `centered` flag shifts it by half its size along that axis so it is centred there instead.
 */
fun rect(
    size: Axis2Values<Double>,
    centered: Axis2Values<Boolean> = Axis2Values.create { false },
): Region {
    val rectangle = polygon(
        Axis2Values.create { 0.0 },
        size * Axis2.X.direction,
        size,
        size * Axis2.Y.direction,
    )
    if (Axis2.entries.none { centered[it] }) return rectangle
    return rectangle.translate(size.combineWith(centered) { value, isCentered -> if (isCentered) -value / 2 else 0.0 })
}

/** A full circle of [radius] centred at the origin. */
fun circle(radius: Double): Region = Region.Circle(radius = radius)

fun polygon(first: Axis2Values<Double>, second: Axis2Values<Double>, vararg tail: Axis2Values<Double>): Region =
    Region.Polygon(points = nonEmptyListOf(first, second, *tail).map { it.toVec2() })

// --- Booleans ---------------------------------------------------------------

fun Region.fuse(other: Region): Region = regionBool(BooleanKind.FUSE, this, other)

fun Region.cut(other: Region): Region = regionBool(BooleanKind.CUT, this, other)

fun Region.common(other: Region): Region = regionBool(BooleanKind.COMMON, this, other)

operator fun Region.plus(other: Region): Region = fuse(other)

operator fun Region.minus(other: Region): Region = cut(other)

operator fun Region.times(other: Region): Region = common(other)

private fun regionBool(kind: BooleanKind, first: Region, second: Region): Region = Region.Bool(
    kind = kind,
    arguments = nonEmptyListOf(first),
    tools = nonEmptyListOf(second),
)

// --- Transforms -------------------------------------------------------------

private val REGION_ORIGIN = Axis2Values.create { 0.0 }

/** Translate by an axis value. */
fun Region.translate(value: Axis2Values<Double>): Region =
    Region.Transform(target = this, op = TransformOp2.Translate(value = value.toVec2()))

fun Region.rotate(angle: Double, center: Axis2Values<Double> = REGION_ORIGIN): Region =
    Region.Transform(target = this, op = TransformOp2.Rotate(center = center.toVec2(), angle = angle))

/** Mirror across the line through `center` (default: origin) with the given `normal`. */
fun Region.mirror(normal: Axis2Values<Double>, center: Axis2Values<Double> = REGION_ORIGIN): Region =
    Region.Transform(target = this, op = TransformOp2.Mirror(center = center.toVec2(), normal = normal.toVec2()))

/** Uniform scale by `factor`. */
fun Region.scale(factor: Double): Region = scale(Axis2Values.create { factor })

/** Non-uniform scale by an axis value. */
fun Region.scale(value: Axis2Values<Double>): Region =
    Region.Transform(target = this, op = TransformOp2.Scale(value = value.toVec2()))

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
