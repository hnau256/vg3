package org.hnau.ktcad.bbox

import org.hnau.commons.kotlin.foldNullable

val ClosedRange<Double>.length: Double
    get() = endInclusive - start

fun ClosedRange<Double>.interpolate(
    fraction: Double,
): Double = length
    .takeIf { it > 0 }
    .foldNullable(
        ifNull = { start },
        ifNotNull = { start + fraction * it },
    )
