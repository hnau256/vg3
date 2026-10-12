package org.hnau.ktcad.solid

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.toNonEmptyListOrNull
import org.hnau.commons.kotlin.foldNullable
import org.hnau.ktcad.Solid
import org.hnau.ktcad.bool
import org.hnau.ktcad.ir.BooleanKind

fun NonEmptyList<Solid>.bool(
    kind: BooleanKind,
): Solid = tail
    .toNonEmptyListOrNull()
    .foldNullable(
        ifNull = { head },
        ifNotNull = { nonEmptyTail ->
            bool(
                kind = kind,
                arguments = nonEmptyListOf(head),
                tools = nonEmptyTail,
            )
        },
    )

fun NonEmptyList<Solid>.fuse(): Solid = bool(
    kind = BooleanKind.FUSE,
)

operator fun Solid.plus(
    other: List<Solid>,
): Solid = NonEmptyList(
    head = this,
    tail = other,
).fuse()

operator fun Solid.plus(
    other: Solid,
): Solid = plus(
    other = listOf(other)
)

fun NonEmptyList<Solid>.cut(): Solid = bool(
    kind = BooleanKind.CUT,
)

operator fun Solid.minus(
    other: List<Solid>,
): Solid = NonEmptyList(
    head = this,
    tail = other,
).cut()

operator fun Solid.minus(
    other: Solid,
): Solid = minus(
    other = listOf(other)
)

fun NonEmptyList<Solid>.common(): Solid = bool(
    kind = BooleanKind.COMMON,
)

operator fun Solid.times(
    other: List<Solid>,
): Solid = NonEmptyList(
    head = this,
    tail = other,
).common()

operator fun Solid.times(
    other: Solid,
): Solid = times(
    other = listOf(other)
)