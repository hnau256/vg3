package org.hnau.ktcad.region

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.toNonEmptyListOrNull
import org.hnau.commons.kotlin.foldNullable
import org.hnau.ktcad.Region
import org.hnau.ktcad.ir.BooleanKind

fun NonEmptyList<Region>.bool(
    kind: BooleanKind,
): Region = tail
    .toNonEmptyListOrNull()
    .foldNullable(
        ifNull = { head },
        ifNotNull = { nonEmptyTail ->
            Region.Bool(
                kind = kind,
                arguments = nonEmptyListOf(head),
                tools = nonEmptyTail,
            )
        },
    )

fun NonEmptyList<Region>.fuse(): Region = bool(
    kind = BooleanKind.FUSE,
)

operator fun Region.plus(
    other: List<Region>,
): Region = NonEmptyList(
    head = this,
    tail = other,
).fuse()

operator fun Region.plus(
    other: Region,
): Region = plus(
    other = listOf(other),
)

fun NonEmptyList<Region>.cut(): Region = bool(
    kind = BooleanKind.CUT,
)

operator fun Region.minus(
    other: List<Region>,
): Region = NonEmptyList(
    head = this,
    tail = other,
).cut()

operator fun Region.minus(
    other: Region,
): Region = minus(
    other = listOf(other),
)

fun NonEmptyList<Region>.common(): Region = bool(
    kind = BooleanKind.COMMON,
)

operator fun Region.times(
    other: List<Region>,
): Region = NonEmptyList(
    head = this,
    tail = other,
).common()

operator fun Region.times(
    other: Region,
): Region = times(
    other = listOf(other),
)
