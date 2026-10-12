package org.hnau.ktcad.axis

import org.hnau.ktcad.ir.Vec2
import org.hnau.ktcad.ir.Vec3

// User-space sugar over `Axis2Values<Double>`/`Axis3Values<Double>`: the model counterparts and
// scalar multiplication. The `@EnumValues` class already provides `get`/`map`/`combineWith`/`copy`.

/** The model `Vec2` counterpart of a user-space per-axis value. */
fun Axis2Values<Double>.toVec2(): Vec2 = Vec2(
    x = x,
    y = y,
)

/** The model `Vec3` counterpart of a user-space per-axis value. */
fun Axis3Values<Double>.toVec3(): Vec3 = Vec3(
    x = x,
    y = y,
    z = z,
)

/** The user-space per-axis value of a model `Vec2`. */
fun Vec2.toAxis2Values(): Axis2Values<Double> = Axis2Values(
    x = x,
    y = y,
)

/** The user-space per-axis value of a model `Vec3`. */
fun Vec3.toAxis3Values(): Axis3Values<Double> = Axis3Values(
    x = x,
    y = y,
    z = z,
)

operator fun Axis2Values<Double>.times(
    factor: Double,
): Axis2Values<Double> =
    map { value -> if (value == 0.0) 0.0 else value * factor }

operator fun Axis3Values<Double>.times(
    factor: Double,
): Axis3Values<Double> =
    map { value -> if (value == 0.0) 0.0 else value * factor }

operator fun Double.times(
    vector: Axis2Values<Double>,
): Axis2Values<Double> = vector * this

operator fun Double.times(
    vector: Axis3Values<Double>,
): Axis3Values<Double> = vector * this

operator fun Axis2Values<Double>.plus(
    other: Axis2Values<Double>,
): Axis2Values<Double> = combineWith(
    other = other,
    combine = Double::plus,
)

operator fun Axis3Values<Double>.plus(
    other: Axis3Values<Double>,
): Axis3Values<Double> = combineWith(
    other = other,
    combine = Double::plus,
)

operator fun Axis2Values<Double>.minus(
    other: Axis2Values<Double>,
): Axis2Values<Double> = combineWith(
    other = other,
    combine = Double::minus,
)

operator fun Axis3Values<Double>.minus(
    other: Axis3Values<Double>,
): Axis3Values<Double> = combineWith(
    other = other,
    combine = Double::minus,
)

operator fun Axis2Values<Double>.times(
    other: Axis2Values<Double>,
): Axis2Values<Double> = combineWith(
    other = other,
    combine = Double::times,
)

operator fun Axis3Values<Double>.times(
    other: Axis3Values<Double>,
): Axis3Values<Double> = combineWith(
    other = other,
    combine = Double::times,
)
