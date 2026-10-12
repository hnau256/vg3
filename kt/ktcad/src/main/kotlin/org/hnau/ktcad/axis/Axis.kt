package org.hnau.ktcad.axis

import org.hnau.commons.gen.enumvalues.annotations.EnumValues
import org.hnau.commons.gen.fold.annotations.Fold
import org.hnau.commons.kotlin.foldBoolean

@Fold
@EnumValues
enum class Axis2 { X, Y }

/** The positive unit direction of the axis (`Axis2.X` → `+X`, …). */
val Axis2.direction: Axis2Values<Double>
    get() = Axis2Values.create { axis ->
        (axis == this).foldBoolean(
            ifTrue = { 1.0 },
            ifFalse = { 0.0 },
        )
    }

@Fold
@EnumValues
enum class Axis3 { X, Y, Z }

/** The positive unit direction of the axis (`Axis3.X` → `+X`, …). */
val Axis3.direction: Axis3Values<Double>
    get() = Axis3Values.create { axis ->
        (axis == this).foldBoolean(
            ifTrue = { 1.0 },
            ifFalse = { 0.0 },
        )
    }
