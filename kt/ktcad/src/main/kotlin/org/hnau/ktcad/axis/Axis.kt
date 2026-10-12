package org.hnau.ktcad.axis

import org.hnau.commons.gen.enumvalues.annotations.EnumValues
import org.hnau.commons.gen.fold.annotations.Fold
import org.hnau.commons.kotlin.foldBoolean

@Fold
@EnumValues
enum class A2 { X, Y }

/** The positive unit direction of the axis (`A2.X` → `+X`, …). */
val A2.direction: A2Values<Double>
    get() = A2Values.create { axis ->
        (axis == this).foldBoolean(
            ifTrue = { 1.0 },
            ifFalse = { 0.0 },
        )
    }

@Fold
@EnumValues
enum class A3 { X, Y, Z }

/** The positive unit direction of the axis (`A3.X` → `+X`, …). */
val A3.direction: A3Values<Double>
    get() = A3Values.create { axis ->
        (axis == this).foldBoolean(
            ifTrue = { 1.0 },
            ifFalse = { 0.0 },
        )
    }
