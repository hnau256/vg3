package org.hnau.ktcad.bbox

/** The smallest range covering both [this] and [other]. */
internal fun ClosedRange<Double>.cover(
    other: ClosedRange<Double>,
): ClosedRange<Double> =
    minOf(
        a = start,
        b = other.start,
    )..maxOf(
        a = endInclusive,
        b = other.endInclusive,
    )

/** [this] grown by [distance] on both ends (a negative [distance] shrinks it). */
internal fun ClosedRange<Double>.grow(
    distance: Double,
): ClosedRange<Double> =
    (start - distance)..(endInclusive + distance)
