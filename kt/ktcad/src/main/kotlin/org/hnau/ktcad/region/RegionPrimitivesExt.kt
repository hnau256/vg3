package org.hnau.ktcad.region

import arrow.core.nonEmptyListOf
import arrow.core.toNonEmptySetOrNull
import org.hnau.commons.kotlin.foldBoolean
import org.hnau.commons.kotlin.foldNullable
import org.hnau.ktcad.Region
import org.hnau.ktcad.Solid
import org.hnau.ktcad.axis.A2
import org.hnau.ktcad.axis.A2Values
import org.hnau.ktcad.axis.A3Values
import org.hnau.ktcad.axis.direction
import org.hnau.ktcad.axis.times
import org.hnau.ktcad.axis.toVec2
import org.hnau.ktcad.bbox.length
import org.hnau.ktcad.polygon
import org.hnau.ktcad.solid.box
import org.hnau.ktcad.solid.translate

fun rect(
    size: A2Values<Double>,
    centered: Set<A2> = emptySet(),
): Region {
    val rectangle = polygon2(
        A2Values.create { 0.0 },
        size * A2.X.direction,
        size,
        size * A2.Y.direction,
    )
    return centered
        .toNonEmptySetOrNull()
        .foldNullable(
            ifNull = { rectangle },
            ifNotNull = { nonEmptyCentered ->
                rectangle.translate(
                    size.map { a2, value ->
                        (a2 in nonEmptyCentered).foldBoolean(
                            ifTrue = { -value / 2 },
                            ifFalse = { 0.0 }
                        )
                    }
                )
            }
        )
}

fun rect(
    bounds: A2Values<ClosedRange<Double>>,
): Region = rect(
    size = bounds
        .map(ClosedRange<Double>::length)
).translate(
    bounds
        .map(ClosedRange<Double>::start)
        .map(Double::unaryMinus)
)

fun polygon2(
    first: A2Values<Double>,
    second: A2Values<Double>,
    vararg tail: A2Values<Double>,
): Region = polygon(
    points = nonEmptyListOf(
        first,
        second,
        *tail,
    ).map(
        transform = A2Values<Double>::toVec2,
    ),
)