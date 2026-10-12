package org.hnau.ktcad.bbox

import org.hnau.ktcad.axis.A2
import org.hnau.ktcad.axis.A3
import org.hnau.ktcad.axis.fold
import org.hnau.ktcad.ir.Vec2
import org.hnau.ktcad.ir.Vec3

// Per-axis sugar over `Vec2`/`Vec3`, mirroring the `@EnumValues`-generated `A2Values`/
// `A3Values` API (same operations, with the element type fixed to `Double`).

// --- Vec2 -------------------------------------------------------------------

internal operator fun Vec2.get(
    axis: A2,
): Double = axis.fold(
    ifX = { x },
    ifY = { y },
)

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
    transform: (axis: A2, value: Double) -> Double,
): Vec2 = Vec2(
    x = transform(
        A2.X,
        x,
    ),
    y = transform(
        A2.Y,
        y,
    ),
)

internal inline fun Vec2.map(
    transform: (value: Double) -> Double,
): Vec2 =
    map { _, value -> transform(value) }

internal inline fun Vec2.combineWith(
    other: Vec2,
    combine: (axis: A2, value: Double, other: Double) -> Double,
): Vec2 = Vec2(
    x = combine(
        A2.X,
        x,
        other.x,
    ),
    y = combine(
        A2.Y,
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
    createValue: (axis: A2) -> Double,
): Vec2 = Vec2(
    x = createValue(A2.X),
    y = createValue(A2.Y),
)

internal inline fun Vec2.copy(
    axis: A2,
    transform: (value: Double) -> Double,
): Vec2 = axis.fold(
    ifX = { copy(x = transform(x)) },
    ifY = { copy(y = transform(y)) },
)

// --- Vec3 -------------------------------------------------------------------

internal operator fun Vec3.get(
    axis: A3,
): Double = axis.fold(
    ifX = { x },
    ifY = { y },
    ifZ = { z },
)

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
    transform: (axis: A3, value: Double) -> Double,
): Vec3 = Vec3(
    x = transform(
        A3.X,
        x,
    ),
    y = transform(
        A3.Y,
        y,
    ),
    z = transform(
        A3.Z,
        z,
    ),
)

internal inline fun Vec3.map(
    transform: (value: Double) -> Double,
): Vec3 =
    map { _, value -> transform(value) }

internal inline fun Vec3.combineWith(
    other: Vec3,
    combine: (axis: A3, value: Double, other: Double) -> Double,
): Vec3 = Vec3(
    x = combine(
        A3.X,
        x,
        other.x,
    ),
    y = combine(
        A3.Y,
        y,
        other.y,
    ),
    z = combine(
        A3.Z,
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
    createValue: (axis: A3) -> Double,
): Vec3 = Vec3(
    x = createValue(A3.X),
    y = createValue(A3.Y),
    z = createValue(A3.Z),
)

internal inline fun Vec3.copy(
    axis: A3,
    transform: (value: Double) -> Double,
): Vec3 = axis.fold(
    ifX = { copy(x = transform(x)) },
    ifY = { copy(y = transform(y)) },
    ifZ = { copy(z = transform(z)) },
)
