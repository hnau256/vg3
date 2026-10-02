package org.hnau.ktcad

import org.hnau.ktcad.ir.Normal3
import org.hnau.ktcad.ir.Point3
import org.hnau.ktcad.ir.TransformOp
import org.hnau.ktcad.ir.Vector3

/**
 * DSL sugar over [Solid]: booleans and transforms. Every function returns a **new** `Solid`
 * (the domain graph is immutable); nothing is mutated.
 *
 * Canonical axis convention (the engine is dimensionless, angles in radians):
 * - `up` = `+Z`, `down` = `−Z`
 * - `right` = `+X`, `left` = `−X`
 * - `forward` = `+Y`, `back` = `−Y`
 *
 * Transforms wrap the operand in the `transform` IR node (there is a single canonical way to place
 * a shape); the engine applies `ops` left to right.
 */

// --- Booleans ---------------------------------------------------------------

/** `fuse`: union of two solids. */
operator fun Solid.plus(other: Solid): Solid = fuse(listOf(this, other))

/** `cut`: [other] subtracted from this solid. */
operator fun Solid.minus(other: Solid): Solid = cut(base = this, tools = listOf(other))

/** `common`: intersection of two solids. */
operator fun Solid.times(other: Solid): Solid = common(listOf(this, other))

// --- Translation ------------------------------------------------------------

/** Translate by `(dx, dy, dz)`. */
fun Solid.translate(dx: Double, dy: Double, dz: Double): Solid =
    translate(Vector3(dx = dx, dy = dy, dz = dz))

/** Translate by a [Vector3]. */
fun Solid.translate(value: Vector3): Solid =
    transform(op = TransformOp.Translate(value = value), target = this)

fun Solid.right(distance: Double): Solid = translate(distance, 0.0, 0.0)

fun Solid.left(distance: Double): Solid = translate(-distance, 0.0, 0.0)

fun Solid.forward(distance: Double): Solid = translate(0.0, distance, 0.0)

fun Solid.back(distance: Double): Solid = translate(0.0, -distance, 0.0)

fun Solid.up(distance: Double): Solid = translate(0.0, 0.0, distance)

fun Solid.down(distance: Double): Solid = translate(0.0, 0.0, -distance)

// --- Scale ------------------------------------------------------------------

/** Uniform scale by `factor`. */
fun Solid.scale(factor: Double): Solid = scale(factor, factor, factor)

/** Non-uniform scale. */
fun Solid.scale(x: Double, y: Double, z: Double): Solid =
    transform(op = TransformOp.Scale(x = x, y = y, z = z), target = this)

fun Solid.scaleX(factor: Double): Solid = scale(factor, 1.0, 1.0)

fun Solid.scaleY(factor: Double): Solid = scale(1.0, factor, 1.0)

fun Solid.scaleZ(factor: Double): Solid = scale(1.0, 1.0, factor)

// --- Rotation ---------------------------------------------------------------

/** Rotate by `angle` (radians) about `axis`, around `center` (default: origin). */
fun Solid.rotate(
    axis: Normal3,
    angle: Double,
    center: Point3 = ORIGIN,
): Solid = transform(
    op = TransformOp.Rotate(center = center, axis = axis, angle = angle),
    target = this,
)

fun Solid.rotateX(angle: Double, center: Point3 = ORIGIN): Solid = rotate(AXIS_X, angle, center)

fun Solid.rotateY(angle: Double, center: Point3 = ORIGIN): Solid = rotate(AXIS_Y, angle, center)

fun Solid.rotateZ(angle: Double, center: Point3 = ORIGIN): Solid = rotate(AXIS_Z, angle, center)

// --- Mirror -----------------------------------------------------------------

/** Mirror across the XY plane (normal `+Z`). */
fun Solid.mirrorXY(): Solid = mirror(normal = AXIS_Z)

/** Mirror across the XZ plane (normal `+Y`). */
fun Solid.mirrorXZ(): Solid = mirror(normal = AXIS_Y)

/** Mirror across the YZ plane (normal `+X`). */
fun Solid.mirrorYZ(): Solid = mirror(normal = AXIS_X)

/** Mirror across a plane through `center` with the given `normal`. */
fun Solid.mirror(normal: Normal3, center: Point3 = ORIGIN): Solid =
    transform(
        op = TransformOp.Mirror(center = center, normal = normal),
        target = this,
    )

// --- Offset -----------------------------------------------------------------

/** Grow (positive [distance]) or shrink (negative) the solid by offsetting its shells. */
fun Solid.offset(distance: Double): Solid = offset(distance = distance, target = this)

private val ORIGIN = Point3(x = 0.0, y = 0.0, z = 0.0)
private val AXIS_X = Normal3(dx = 1.0, dy = 0.0, dz = 0.0)
private val AXIS_Y = Normal3(dx = 0.0, dy = 1.0, dz = 0.0)
private val AXIS_Z = Normal3(dx = 0.0, dy = 0.0, dz = 1.0)
