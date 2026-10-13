package org.hnau.ktcad.bbox

import org.hnau.ktcad.axis.A2Values
import org.hnau.ktcad.axis.A3Values

internal fun ClosedRange<Double>.cover(
    other: ClosedRange<Double>,
): ClosedRange<Double> = minOf(
    a = start,
    b = other.start,
)..maxOf(
    a = endInclusive,
    b = other.endInclusive,
)

fun ClosedRange<Double>.growOrShrink(
    distance: Double,
): ClosedRange<Double> = (start - distance)..(endInclusive + distance)

fun A2Values<ClosedRange<Double>>.growOrShrink(
    distance: Double,
): A2Values<ClosedRange<Double>> = map { range ->
    range.growOrShrink(distance)
}

fun A3Values<ClosedRange<Double>>.growOrShrink(
    distance: Double,
): A3Values<ClosedRange<Double>> = map { range ->
    range.growOrShrink(distance)
}
