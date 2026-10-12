package org.hnau.ktcad.axis

import org.hnau.ktcad.ir.Vec2
import org.hnau.ktcad.ir.Vec3

// User-space sugar over `A2Values<Double>`/`A3Values<Double>`: the model counterparts and
// scalar multiplication. The `@EnumValues` class already provides `get`/`map`/`combineWith`/`copy`.

/** The model `Vec2` counterpart of a user-space per-axis value. */
fun A2Values<Double>.toVec2(): Vec2 = Vec2(
    x = x,
    y = y,
)

/** The model `Vec3` counterpart of a user-space per-axis value. */
fun A3Values<Double>.toVec3(): Vec3 = Vec3(
    x = x,
    y = y,
    z = z,
)

/** The user-space per-axis value of a model `Vec2`. */
fun Vec2.toA2Values(): A2Values<Double> = A2Values(
    x = x,
    y = y,
)

/** The user-space per-axis value of a model `Vec3`. */
fun Vec3.toA3Values(): A3Values<Double> = A3Values(
    x = x,
    y = y,
    z = z,
)

operator fun A2Values<Double>.times(
    factor: Double,
): A2Values<Double> =
    map { value -> if (value == 0.0) 0.0 else value * factor }

operator fun A3Values<Double>.times(
    factor: Double,
): A3Values<Double> =
    map { value -> if (value == 0.0) 0.0 else value * factor }

operator fun Double.times(
    vector: A2Values<Double>,
): A2Values<Double> = vector * this

operator fun Double.times(
    vector: A3Values<Double>,
): A3Values<Double> = vector * this

operator fun A2Values<Double>.plus(
    other: A2Values<Double>,
): A2Values<Double> = combineWith(
    other = other,
    combine = Double::plus,
)

operator fun A3Values<Double>.plus(
    other: A3Values<Double>,
): A3Values<Double> = combineWith(
    other = other,
    combine = Double::plus,
)

operator fun A2Values<Double>.minus(
    other: A2Values<Double>,
): A2Values<Double> = combineWith(
    other = other,
    combine = Double::minus,
)

operator fun A3Values<Double>.minus(
    other: A3Values<Double>,
): A3Values<Double> = combineWith(
    other = other,
    combine = Double::minus,
)

operator fun A2Values<Double>.times(
    other: A2Values<Double>,
): A2Values<Double> = combineWith(
    other = other,
    combine = Double::times,
)

operator fun A3Values<Double>.times(
    other: A3Values<Double>,
): A3Values<Double> = combineWith(
    other = other,
    combine = Double::times,
)
