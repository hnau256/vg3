package org.hnau.ktcad.bbox

import org.hnau.commons.gen.enumvalues.annotations.EnumValues
import org.hnau.commons.gen.fold.annotations.Fold
import org.hnau.ktcad.ir.Vec2
import org.hnau.ktcad.ir.Vec3

@Fold
@EnumValues
enum class Axis2 { X, Y }

/** The positive unit direction of the axis (`Axis2.X` → `+X`, …). */
val Axis2.direction: Vec2
    get() = when (this) {
        Axis2.X -> Vec2(x = 1.0, y = 0.0)
        Axis2.Y -> Vec2(x = 0.0, y = 1.0)
    }

@Fold
@EnumValues
enum class Axis3 { X, Y, Z }

/** The positive unit direction of the axis (`Axis3.X` → `+X`, …). */
val Axis3.direction: Vec3
    get() = direction(1.0)

/** The vector along the axis with the signed [length] (`Axis3.X` + `d` → `(d, 0, 0)`). */
fun Axis3.direction(length: Double): Vec3 = when (this) {
    Axis3.X -> Vec3(x = length, y = 0.0, z = 0.0)
    Axis3.Y -> Vec3(x = 0.0, y = length, z = 0.0)
    Axis3.Z -> Vec3(x = 0.0, y = 0.0, z = length)
}
