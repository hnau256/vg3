package org.hnau.ktcad.region

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import org.hnau.ktcad.Region
import org.hnau.ktcad.axis.A2Values
import org.hnau.ktcad.axis.plus
import org.hnau.ktcad.axis.toA2Values
import org.hnau.ktcad.axis.toVec2
import org.hnau.ktcad.ir.Curve2
import org.hnau.ktcad.ir.Vec2
import org.hnau.ktcad.ir.fold
import org.hnau.ktcad.utils.buildChain

typealias ContourSegment = (() -> A2Values<Double>) -> Curve2

fun line2To(
    to: A2Values<Double>,
): ContourSegment = {
    Curve2.Line(
        to = to.toVec2(),
    )
}

fun line2Rel(
    delta: A2Values<Double>,
): ContourSegment = { current ->
    Curve2.Line(
        to = (current() + delta).toVec2(),
    )
}

fun arc2To(
    via: A2Values<Double>,
    to: A2Values<Double>,
): ContourSegment = {
    Curve2.Arc(
        via = via.toVec2(),
        to = to.toVec2(),
    )
}

fun arc2Rel(
    via: A2Values<Double>,
    to: A2Values<Double>,
): ContourSegment = { current ->
    val point = current()
    Curve2.Arc(
        via = (point + via).toVec2(),
        to = (point + to).toVec2(),
    )
}

fun spline2To(
    initial: A2Values<Double>,
    vararg additional: A2Values<Double>,
    tangentStart: A2Values<Double>? = null,
    tangentEnd: A2Values<Double>? = null,
): ContourSegment = {
    Curve2.Spline(
        points = nonEmptyListOf(
            initial,
            *additional,
        ).map(A2Values<Double>::toVec2),
        tangent_start = tangentStart?.toVec2(),
        tangent_end = tangentEnd?.toVec2(),
    )
}

fun spline2Rel(
    initial: A2Values<Double>,
    vararg additional: A2Values<Double>,
    tangentStart: A2Values<Double>? = null,
    tangentEnd: A2Values<Double>? = null,
): ContourSegment = { current ->
    val point = current()
    Curve2.Spline(
        points = nonEmptyListOf(
            point + initial,
            *(additional.map { point + it }.toTypedArray()),
        ).map(A2Values<Double>::toVec2),
        tangent_start = tangentStart?.toVec2(),
        tangent_end = tangentEnd?.toVec2(),
    )
}

fun bezier2To(
    first: A2Values<Double>,
    second: A2Values<Double>,
    vararg additional: A2Values<Double>,
): ContourSegment = {
    Curve2.Bezier(
        points = nonEmptyListOf(
            first,
            second,
            *additional,
        ).map(A2Values<Double>::toVec2),
    )
}

fun bezier2Rel(
    first: A2Values<Double>,
    second: A2Values<Double>,
    vararg additional: A2Values<Double>,
): ContourSegment = { current ->
    val point = current()
    Curve2.Bezier(
        points = nonEmptyListOf(
            point + first,
            point + second,
            *(additional.map { point + it }.toTypedArray()),
        ).map(A2Values<Double>::toVec2),
    )
}

fun contour(
    start: A2Values<Double>,
    segments: NonEmptyList<ContourSegment>,
): Region = Region.Contour(
    start = start.toVec2(),
    edges = buildChain(
        start = start,
        segments = segments,
    ) {
        it.end().toA2Values()
    },
)

fun contour(
    start: A2Values<Double>,
    initial: ContourSegment,
    vararg additional: ContourSegment,
): Region = contour(
    start = start,
    segments = nonEmptyListOf(
        initial,
        *additional,
    ),
)

internal fun Curve2.end(): Vec2 = fold(
    ifLine = { to -> to },
    ifArc = { to, _ -> to },
    ifSpline = { points, _, _ -> points.last() },
    ifBezier = { points -> points.last() },
)
