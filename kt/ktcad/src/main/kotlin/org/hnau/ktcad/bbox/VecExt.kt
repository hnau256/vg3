package org.hnau.ktcad.bbox

import org.hnau.ktcad.ir.Vec2
import org.hnau.ktcad.ir.Vec3

// Per-axis sugar over `Vec2`/`Vec3`, mirroring the `@EnumValues`-generated `Axis2Values`/
// `Axis3Values` API (same operations, with the element type fixed to `Double`).

// --- Vec2 -------------------------------------------------------------------

operator fun Vec2.get(axis: Axis2): Double = when (axis) {
    Axis2.X -> x
    Axis2.Y -> y
}

operator fun Vec2.times(factor: Double): Vec2 =
    map { value -> if (value == 0.0) 0.0 else value * factor }

operator fun Double.times(vector: Vec2): Vec2 = vector * this

inline fun Vec2.map(transform: (axis: Axis2, value: Double) -> Double): Vec2 = Vec2(
    x = transform(Axis2.X, x),
    y = transform(Axis2.Y, y),
)

inline fun Vec2.map(transform: (value: Double) -> Double): Vec2 =
    map { _, value -> transform(value) }

inline fun Vec2.combineWith(
    other: Vec2,
    combine: (axis: Axis2, value: Double, other: Double) -> Double,
): Vec2 = Vec2(
    x = combine(Axis2.X, x, other.x),
    y = combine(Axis2.Y, y, other.y),
)

inline fun Vec2.combineWith(
    other: Vec2,
    combine: (value: Double, other: Double) -> Double,
): Vec2 = combineWith(other) { _, value, other -> combine(value, other) }

inline fun vec2(createValue: (axis: Axis2) -> Double): Vec2 = Vec2(
    x = createValue(Axis2.X),
    y = createValue(Axis2.Y),
)

inline fun Vec2.copy(axis: Axis2, transform: (value: Double) -> Double): Vec2 = when (axis) {
    Axis2.X -> copy(x = transform(x))
    Axis2.Y -> copy(y = transform(y))
}

// --- Vec3 -------------------------------------------------------------------

operator fun Vec3.get(axis: Axis3): Double = when (axis) {
    Axis3.X -> x
    Axis3.Y -> y
    Axis3.Z -> z
}

operator fun Vec3.times(factor: Double): Vec3 =
    map { value -> if (value == 0.0) 0.0 else value * factor }

operator fun Double.times(vector: Vec3): Vec3 = vector * this

inline fun Vec3.map(transform: (axis: Axis3, value: Double) -> Double): Vec3 = Vec3(
    x = transform(Axis3.X, x),
    y = transform(Axis3.Y, y),
    z = transform(Axis3.Z, z),
)

inline fun Vec3.map(transform: (value: Double) -> Double): Vec3 =
    map { _, value -> transform(value) }

inline fun Vec3.combineWith(
    other: Vec3,
    combine: (axis: Axis3, value: Double, other: Double) -> Double,
): Vec3 = Vec3(
    x = combine(Axis3.X, x, other.x),
    y = combine(Axis3.Y, y, other.y),
    z = combine(Axis3.Z, z, other.z),
)

inline fun Vec3.combineWith(
    other: Vec3,
    combine: (value: Double, other: Double) -> Double,
): Vec3 = combineWith(other) { _, value, other -> combine(value, other) }

inline fun vec3(createValue: (axis: Axis3) -> Double): Vec3 = Vec3(
    x = createValue(Axis3.X),
    y = createValue(Axis3.Y),
    z = createValue(Axis3.Z),
)

inline fun Vec3.copy(axis: Axis3, transform: (value: Double) -> Double): Vec3 = when (axis) {
    Axis3.X -> copy(x = transform(x))
    Axis3.Y -> copy(y = transform(y))
    Axis3.Z -> copy(z = transform(z))
}
