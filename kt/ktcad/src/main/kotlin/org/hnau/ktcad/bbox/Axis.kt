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
    get() = vec2 { if (it == this) 1.0 else 0.0 }

@Fold
@EnumValues
enum class Axis3 { X, Y, Z }

/** The positive unit direction of the axis (`Axis3.X` → `+X`, …). */
val Axis3.direction: Vec3
    get() = vec3 { if (it == this) 1.0 else 0.0 }
