package org.hnau.ktcad.region

import org.hnau.ktcad.Region
import org.hnau.ktcad.Solid
import org.hnau.ktcad.axis.A2
import org.hnau.ktcad.axis.A2Values
import org.hnau.ktcad.axis.A3
import org.hnau.ktcad.axis.A3Values
import org.hnau.ktcad.axis.direction
import org.hnau.ktcad.axis.times
import org.hnau.ktcad.axis.toVec2
import org.hnau.ktcad.axis.zero
import org.hnau.ktcad.bbox.bbox
import org.hnau.ktcad.bbox.interpolate
import org.hnau.ktcad.ir.JoinKind
import org.hnau.ktcad.ir.TransformOp2
import org.hnau.ktcad.offset2d
import org.hnau.ktcad.solid.translate
import org.hnau.ktcad.transform

fun Region.transform(
    transform: TransformOp2,
): Region.Transform = transform(
    target = this,
    op = transform,
)

fun Region.translate(
    value: A2Values<Double>,
): Region = transform(
    transform = TransformOp2.Translate(value = value.toVec2()),
)

fun Region.calcOffsetTo(
    target: Region,
    axis: A2,
    targetFraction: Double,
    thisFraction: Double,
): A2Values<Double> {
    val targetPosition = target.bbox[axis].interpolate(targetFraction)
    val thisPosition = this.bbox[axis].interpolate(thisFraction)
    val delta = targetPosition - thisPosition
    return axis.direction * delta
}

fun Region.put(
    target: Region,
    axis: A2,
    targetFraction: Double,
    thisFraction: Double,
): Region = translate(
    value = calcOffsetTo(
        target = target,
        axis = axis,
        targetFraction = targetFraction,
        thisFraction = thisFraction,
    )
)

fun Region.rotate(
    angle: Double,
    center: A2Values<Double> = A2Values.zero,
): Region = transform(
    transform = TransformOp2.Rotate(
        center = center.toVec2(),
        angle = angle,
    ),
)

fun Region.mirror(
    normal: A2Values<Double>,
    center: A2Values<Double> = A2Values.zero,
): Region = transform(
    transform = TransformOp2.Mirror(
        center = center.toVec2(),
        normal = normal.toVec2(),
    ),
)

fun Region.scale(
    factor: Double,
): Region = scale(
    value = A2Values.create { factor },
)

fun Region.scale(
    value: A2Values<Double>,
): Region = transform(
    transform = TransformOp2.Scale(
        value = value.toVec2(),
    ),
)


fun Region.offset2d(
    distance: Double,
    join: JoinKind = JoinKind.ARC,
): Region = offset2d(
    target = this,
    distance = distance,
    join = join,
)
