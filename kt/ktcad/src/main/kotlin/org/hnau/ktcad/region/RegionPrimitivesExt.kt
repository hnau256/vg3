package org.hnau.ktcad.region

import arrow.core.nonEmptyListOf
import arrow.core.toNonEmptySetOrNull
import org.hnau.commons.kotlin.foldBoolean
import org.hnau.commons.kotlin.foldNullable
import org.hnau.ktcad.Region
import org.hnau.ktcad.axis.A2
import org.hnau.ktcad.axis.A2Values
import org.hnau.ktcad.axis.direction
import org.hnau.ktcad.axis.times
import org.hnau.ktcad.axis.toVec2

fun rect(
    size: A2Values<Double>,
    centered: Set<A2> = emptySet(),
): Region {
    val rectangle = polygon(
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

fun circle(
    radius: Double,
): Region = Region.Circle(
    radius = radius,
)

fun polygon(
    first: A2Values<Double>,
    second: A2Values<Double>,
    vararg tail: A2Values<Double>,
): Region = Region.Polygon(
    points = nonEmptyListOf(
        first,
        second,
        *tail,
    ).map(
        transform = A2Values<Double>::toVec2,
    ),
)