package org.hnau.ktcad.bbox

import org.hnau.ktcad.axis.Axis2
import org.hnau.ktcad.axis.Axis3
import org.hnau.ktcad.ir.Vec2
import org.hnau.ktcad.ir.Vec3

// Per-axis sugar over `Vec2`/`Vec3`, mirroring the `@EnumValues`-generated `Axis2Values`/
// `Axis3Values` API (same operations, with the element type fixed to `Double`).

// --- Vec2 -------------------------------------------------------------------

internal operator fun Vec2.get(
    axis: Axis2,
): Double = when (axis) {
    Axis2.X -> x
    Axis2.Y -> y
}

internal operator fun Vec2.times(
    factor: Double,
): Vec2 =
    map { value -> if (value == 0.0) 0.0 else value * factor }

internal operator fun Double.times(
    vector: Vec2,
): Vec2 = vector * this

internal operator fun Vec2.plus(
    other: Vec2,
): Vec2 = combineWith(other = other) { a, b -> a + b }

internal operator fun Vec2.minus(
    other: Vec2,
): Vec2 = combineWith(other = other) { a, b -> a - b }

internal operator fun Vec2.times(
    other: Vec2,
): Vec2 = combineWith(other = other) { a, b -> a * b }

internal inline fun Vec2.map(
    transform: (axis: Axis2, value: Double) -> Double,
): Vec2 = Vec2(
    x = transform(
        Axis2.X,
        x,
    ),
    y = transform(
        Axis2.Y,
        y,
    ),
)

internal inline fun Vec2.map(
    transform: (value: Double) -> Double,
): Vec2 =
    map { _, value -> transform(value) }

internal inline fun Vec2.combineWith(
    other: Vec2,
    combine: (axis: Axis2, value: Double, other: Double) -> Double,
): Vec2 = Vec2(
    x = combine(
        Axis2.X,
        x,
        other.x,
    ),
    y = combine(
        Axis2.Y,
        y,
        other.y,
    ),
)

internal inline fun Vec2.combineWith(
    other: Vec2,
    combine: (value: Double, other: Double) -> Double,
): Vec2 = combineWith(other = other) { _, value, other ->
    combine(
        value,
        other,
    )
}

internal inline fun vec2(
    createValue: (axis: Axis2) -> Double,
): Vec2 = Vec2(
    x = createValue(Axis2.X),
    y = createValue(Axis2.Y),
)

internal inline fun Vec2.copy(
    axis: Axis2,
    transform: (value: Double) -> Double,
): Vec2 = when (axis) {
    Axis2.X -> copy(x = transform(x))
    Axis2.Y -> copy(y = transform(y))
}

// --- Vec3 -------------------------------------------------------------------

internal operator fun Vec3.get(
    axis: Axis3,
): Double = when (axis) {
    Axis3.X -> x
    Axis3.Y -> y
    Axis3.Z -> z
}

internal operator fun Vec3.times(
    factor: Double,
): Vec3 =
    map { value -> if (value == 0.0) 0.0 else value * factor }

internal operator fun Double.times(
    vector: Vec3,
): Vec3 = vector * this

internal operator fun Vec3.plus(
    other: Vec3,
): Vec3 = combineWith(other = other) { a, b -> a + b }

internal operator fun Vec3.minus(
    other: Vec3,
): Vec3 = combineWith(other = other) { a, b -> a - b }

internal operator fun Vec3.times(
    other: Vec3,
): Vec3 = combineWith(other = other) { a, b -> a * b }

internal inline fun Vec3.map(
    transform: (axis: Axis3, value: Double) -> Double,
): Vec3 = Vec3(
    x = transform(
        Axis3.X,
        x,
    ),
    y = transform(
        Axis3.Y,
        y,
    ),
    z = transform(
        Axis3.Z,
        z,
    ),
)

internal inline fun Vec3.map(
    transform: (value: Double) -> Double,
): Vec3 =
    map { _, value -> transform(value) }

internal inline fun Vec3.combineWith(
    other: Vec3,
    combine: (axis: Axis3, value: Double, other: Double) -> Double,
): Vec3 = Vec3(
    x = combine(
        Axis3.X,
        x,
        other.x,
    ),
    y = combine(
        Axis3.Y,
        y,
        other.y,
    ),
    z = combine(
        Axis3.Z,
        z,
        other.z,
    ),
)

internal inline fun Vec3.combineWith(
    other: Vec3,
    combine: (value: Double, other: Double) -> Double,
): Vec3 = combineWith(other = other) { _, value, other ->
    combine(
        value,
        other,
    )
}

internal inline fun vec3(
    createValue: (axis: Axis3) -> Double,
): Vec3 = Vec3(
    x = createValue(Axis3.X),
    y = createValue(Axis3.Y),
    z = createValue(Axis3.Z),
)

internal inline fun Vec3.copy(
    axis: Axis3,
    transform: (value: Double) -> Double,
): Vec3 = when (axis) {
    Axis3.X -> copy(x = transform(x))
    Axis3.Y -> copy(y = transform(y))
    Axis3.Z -> copy(z = transform(z))
}
