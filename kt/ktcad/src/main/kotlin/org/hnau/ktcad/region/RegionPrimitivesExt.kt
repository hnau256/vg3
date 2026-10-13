package org.hnau.ktcad.region

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.toNonEmptyListOrThrow
import arrow.core.toNonEmptySetOrNull
import org.hnau.commons.kotlin.foldBoolean
import org.hnau.commons.kotlin.foldNullable
import org.hnau.ktcad.Region
import org.hnau.ktcad.Solid
import org.hnau.ktcad.axis.A2
import org.hnau.ktcad.axis.A2Values
import org.hnau.ktcad.axis.A3Values
import org.hnau.ktcad.axis.create
import org.hnau.ktcad.axis.direction
import org.hnau.ktcad.axis.times
import org.hnau.ktcad.axis.toVec2
import org.hnau.ktcad.bbox.length
import org.hnau.ktcad.polygon
import org.hnau.ktcad.solid.box
import org.hnau.ktcad.solid.translate
import kotlin.math.PI
import kotlin.math.cos

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
    value = bounds
        .map(ClosedRange<Double>::start)
)

fun polygon2(
    points: NonEmptyList<A2Values<Double>>,
): Region = polygon(
    points = points.map(A2Values<Double>::toVec2),
)

fun polygon2(
    first: A2Values<Double>,
    second: A2Values<Double>,
    vararg tail: A2Values<Double>,
): Region = polygon2(
    points = nonEmptyListOf(
        first,
        second,
        *tail,
    ),
)

fun ngon(
    count: Int,
    maxRadius: Double,
): Region {
    val angleStep = PI * 2 / count
    return polygon2(
        points = (0..count)
            .map { i ->
                A2Values.create(
                    angle = angleStep * i,
                    radius = maxRadius,
                )
            }
            .toNonEmptyListOrThrow()
    )
}

fun ngonByMinRadius(
    count: Int,
    minRadius: Double,
): Region = ngon(
    count = count,
    maxRadius = minRadius / cos(PI / count),
)