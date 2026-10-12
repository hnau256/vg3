package org.hnau.ktcad.solid

import org.hnau.ktcad.Solid
import org.hnau.ktcad.axis.A3
import org.hnau.ktcad.axis.A3Values
import org.hnau.ktcad.axis.direction
import org.hnau.ktcad.axis.times
import org.hnau.ktcad.axis.toVec3
import org.hnau.ktcad.axis.zero
import org.hnau.ktcad.bbox.bbox
import org.hnau.ktcad.bbox.interpolate
import org.hnau.ktcad.ir.FaceSelection
import org.hnau.ktcad.ir.JoinKind
import org.hnau.ktcad.ir.TransformOp
import org.hnau.ktcad.offset
import org.hnau.ktcad.thickSolid
import org.hnau.ktcad.transform

fun Solid.translate(
    value: A3Values<Double>,
): Solid =
    transform(
        op = TransformOp.Translate(value = value.toVec3()),
        target = this,
    )

fun Solid.put(
    target: Solid,
    axis: A3,
    targetFraction: Double,
    thisFraction: Double,
): Solid {
    val targetPosition = target.bbox[axis].interpolate(targetFraction)
    val thisPosition = this.bbox[axis].interpolate(thisFraction)
    val delta = targetPosition - thisPosition
    return translate(axis.direction * delta)
}

fun Solid.scale(
    factor: Double,
): Solid = scale(
    value = A3Values.create { factor },
)

fun Solid.scale(
    value: A3Values<Double>,
): Solid = transform(
    op = TransformOp.Scale(
        value = value.toVec3()
    ),
    target = this,
)

fun Solid.rotate(
    axis: A3Values<Double>,
    angle: Double,
    center: A3Values<Double> = A3Values.zero,
): Solid = transform(
    op = TransformOp.Rotate(
        center = center.toVec3(),
        axis = axis.toVec3(),
        angle = angle,
    ),
    target = this,
)

fun Solid.mirror(
    normal: A3Values<Double>,
    center: A3Values<Double> = A3Values.zero,
): Solid = transform(
    op = TransformOp.Mirror(
        center = center.toVec3(),
        normal = normal.toVec3(),
    ),
    target = this,
)

fun Solid.offset(
    distance: Double,
    join: JoinKind = JoinKind.ARC,
): Solid = offset(
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
): Solid = thickSolid(
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
