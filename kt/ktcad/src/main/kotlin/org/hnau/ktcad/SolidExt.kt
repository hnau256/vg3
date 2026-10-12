package org.hnau.ktcad

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.toNonEmptyListOrThrow
import org.hnau.ktcad.axis.Axis3
import org.hnau.ktcad.axis.Axis3Values
import org.hnau.ktcad.axis.direction
import org.hnau.ktcad.axis.times
import org.hnau.ktcad.bbox.bbox
import org.hnau.ktcad.bbox.interpolate
import org.hnau.ktcad.axis.toVec3
import org.hnau.ktcad.ir.BooleanKind
import org.hnau.ktcad.ir.FaceSelection
import org.hnau.ktcad.ir.JoinKind
import org.hnau.ktcad.ir.TransformOp

/**
 * DSL sugar over [Solid]: booleans and transforms. Every function returns a **new** `Solid`
 * (the domain graph is immutable); nothing is mutated.
 *
 * Canonical axis convention (the engine is dimensionless, angles in radians): `Axis3.X` = `+X`,
 * `Axis3.Y` = `+Y`, `Axis3.Z` = `+Z`; a negative distance/angle reverses the sign.
 *
 * Transforms wrap the operand in the `transform` IR body (there is a single canonical way to place
 * a shape); the engine applies `ops` left to right.
 */

// --- Booleans ---------------------------------------------------------------

/**
 * Union of [parts] (mirrors OCCT's two groups: the first part is the object, the rest are tools —
 * OCCT requires both groups non-empty).
 */
fun fuse(
    parts: List<Solid>,
): Solid = solidBool(
    kind = BooleanKind.FUSE,
    parts = parts,
    name = "fuse",
)

/** Intersection of [parts] (see [fuse] for the object/tools split). */
fun common(
    parts: List<Solid>,
): Solid = solidBool(
    kind = BooleanKind.COMMON,
    parts = parts,
    name = "common",
)

/** `base` minus every solid in [tools]. */
fun cut(
    base: Solid,
    tools: NonEmptyList<Solid>,
): Solid = bool(
    kind = BooleanKind.CUT,
    arguments = nonEmptyListOf(base),
    tools = tools,
)

private fun solidBool(
    kind: BooleanKind,
    parts: List<Solid>,
    name: String,
): Solid {
    require(value = parts.size >= 2) { "$name needs at least two solids" }
    return bool(
        kind = kind,
        arguments = nonEmptyListOf(parts.first()),
        tools = parts.drop(1).toNonEmptyListOrThrow(),
    )
}

/** `fuse`: union of two solids. */
operator fun Solid.plus(
    other: Solid,
): Solid = fuse(
    listOf(
        this,
        other,
    ),
)

/** `cut`: [other] subtracted from this solid. */
operator fun Solid.minus(
    other: Solid,
): Solid = cut(
    base = this,
    tools = nonEmptyListOf(other),
)

/** `common`: intersection of two solids. */
operator fun Solid.times(
    other: Solid,
): Solid = common(
    listOf(
        this,
        other,
    ),
)

// --- Translation ------------------------------------------------------------

/** Translate by an axis value (e.g. `Axis3.Z.direction * 5.0`). */
fun Solid.translate(
    value: Axis3Values<Double>,
): Solid =
    transform(
        op = TransformOp.Translate(value = value.toVec3()),
        target = this,
    )

fun Solid.put(
    target: Solid,
    axis: Axis3,
    targetFraction: Double,
    thisFraction: Double,
): Solid {
    val targetPosition = target.bbox[axis].interpolate(targetFraction)
    val thisPosition = this.bbox[axis].interpolate(thisFraction)
    val delta = targetPosition - thisPosition
    return translate(axis.direction * delta)
}

// --- Scale ------------------------------------------------------------------

/** Uniform scale by `factor`. */
fun Solid.scale(
    factor: Double,
): Solid = scale(Axis3Values.create { factor })

/** Non-uniform scale by an axis value (e.g. `Axis3.X.direction * 2.0`). */
fun Solid.scale(
    value: Axis3Values<Double>,
): Solid =
    transform(
        op = TransformOp.Scale(value = value.toVec3()),
        target = this,
    )

// --- Rotation ---------------------------------------------------------------

/** Rotate by `angle` (radians) about `axis`, around `center` (default: origin). */
fun Solid.rotate(
    axis: Axis3Values<Double>,
    angle: Double,
    center: Axis3Values<Double> = ORIGIN,
): Solid = transform(
    op = TransformOp.Rotate(
        center = center.toVec3(),
        axis = axis.toVec3(),
        angle = angle,
    ),
    target = this,
)

// --- Mirror -----------------------------------------------------------------

/** Mirror across the plane through `center` (default: origin) with the given `normal`. */
fun Solid.mirror(
    normal: Axis3Values<Double>,
    center: Axis3Values<Double> = ORIGIN,
): Solid =
    transform(
        op = TransformOp.Mirror(
            center = center.toVec3(),
            normal = normal.toVec3(),
        ),
        target = this,
    )

// --- Offset -----------------------------------------------------------------

/** Grow (positive [distance]) or shrink (negative) the solid by offsetting its shells. */
fun Solid.offset(
    distance: Double,
): Solid = offset(
    distance = distance,
    target = this,
)

/** As [offset], joining the offset shells with [join]. */
fun Solid.offset(
    distance: Double,
    join: JoinKind,
): Solid =
    offset(
        distance = distance,
        join = join,
        target = this,
    )

// --- Thick solid ------------------------------------------------------------

/**
 * Hollow [this] into a shell of wall thickness [offset] (negative = inward, as in OCCT), opening
 * the faces selected by a boolean Rhai [expression] (variable `face`, plus `box` for the body's
 * bounding box).
 */
fun Solid.thickSolid(
    offset: Double,
    expression: String,
): Solid =
    thickSolid(
        offset = offset,
        faces = FaceSelection.Selected(expression = expression),
        target = this,
    )

/** As [thickSolid], joining the offset shells with [join]. */
fun Solid.thickSolid(
    offset: Double,
    expression: String,
    join: JoinKind,
): Solid = thickSolid(
    offset = offset,
    faces = FaceSelection.Selected(expression = expression),
    join = join,
    target = this,
)

private val ORIGIN = Axis3Values.create { 0.0 }
