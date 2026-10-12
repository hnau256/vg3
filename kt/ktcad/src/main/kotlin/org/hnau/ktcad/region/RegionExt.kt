package org.hnau.ktcad.region

import org.hnau.ktcad.Region
import org.hnau.ktcad.axis.A2
import org.hnau.ktcad.axis.A2Values
import org.hnau.ktcad.axis.direction
import org.hnau.ktcad.axis.times
import org.hnau.ktcad.axis.toVec2
import org.hnau.ktcad.axis.zero
import org.hnau.ktcad.bbox.bbox
import org.hnau.ktcad.bbox.interpolate
import org.hnau.ktcad.ir.JoinKind
import org.hnau.ktcad.ir.TransformOp2


fun Region.translate(
    value: A2Values<Double>,
): Region = Region.Transform(
    target = this,
    op = TransformOp2.Translate(value = value.toVec2()),
)

fun Region.put(
    target: Region,
    axis: A2,
    targetFraction: Double,
    thisFraction: Double,
): Region {
    val targetPosition = target.bbox[axis].interpolate(targetFraction)
    val thisPosition = this.bbox[axis].interpolate(thisFraction)
    val delta = targetPosition - thisPosition
    return translate(axis.direction * delta)
}

fun Region.rotate(
    angle: Double,
    center: A2Values<Double> = A2Values.zero,
): Region = Region.Transform(
    target = this,
    op = TransformOp2.Rotate(
        center = center.toVec2(),
        angle = angle,
    ),
)

fun Region.mirror(
    normal: A2Values<Double>,
    center: A2Values<Double> = A2Values.zero,
): Region = Region.Transform(
    target = this,
    op = TransformOp2.Mirror(
        center = center.toVec2(),
        normal = normal.toVec2(),
    ),
)

fun Region.scale(
    factor: Double,
): Region = scale(A2Values.create { factor })

fun Region.scale(
    value: A2Values<Double>,
): Region = Region.Transform(
    target = this,
    op = TransformOp2.Scale(value = value.toVec2()),
)


fun Region.offset2d(
    distance: Double,
    join: JoinKind = JoinKind.ARC,
): Region = Region.Offset2d(
    target = this,
    distance = distance,
    join = join,
)
