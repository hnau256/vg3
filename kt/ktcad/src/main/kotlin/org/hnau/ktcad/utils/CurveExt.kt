package org.hnau.ktcad.utils

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf

internal fun <P, C> buildChain(
    start: P,
    segments: NonEmptyList<(() -> P) -> C>,
    end: (C) -> P,
): NonEmptyList<C> {
    val first = segments.head { start }
    return segments
        .tail
        .fold(
            initial = nonEmptyListOf(first) to { end(first) },
        ) { (acc, current), segment ->
            val curve = segment(current)
            (acc + curve) to { end(curve) }
        }
        .first
}