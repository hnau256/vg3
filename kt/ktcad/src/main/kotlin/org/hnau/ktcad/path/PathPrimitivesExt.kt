package org.hnau.ktcad.path

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import org.hnau.ktcad.axis.A3Values
import org.hnau.ktcad.axis.plus
import org.hnau.ktcad.axis.toA3Values
import org.hnau.ktcad.axis.toVec3
import org.hnau.ktcad.ir.Curve3
import org.hnau.ktcad.ir.Path
import org.hnau.ktcad.ir.Vec3
import org.hnau.ktcad.ir.fold
import org.hnau.ktcad.utils.buildChain

typealias PathSegment = (() -> A3Values<Double>) -> Curve3

fun lineTo(
    to: A3Values<Double>,
): PathSegment = {
    Curve3.Line(to = to.toVec3())
}

fun lineRel(
    delta: A3Values<Double>,
): PathSegment = { current ->
    Curve3.Line(to = (current() + delta).toVec3())
}

fun arcTo(
    via: A3Values<Double>,
    to: A3Values<Double>,
): PathSegment = {
    Curve3.Arc(
        via = via.toVec3(),
        to = to.toVec3(),
    )
}

fun arcRel(
    via: A3Values<Double>,
    to: A3Values<Double>,
): PathSegment = { current ->
    val point = current()
    Curve3.Arc(
        via = (point + via).toVec3(),
        to = (point + to).toVec3(),
    )
}

fun splineTo(
    initial: A3Values<Double>,
    vararg additional: A3Values<Double>,
    tangentStart: A3Values<Double>? = null,
    tangentEnd: A3Values<Double>? = null,
): PathSegment = {
    Curve3.Spline(
        points = nonEmptyListOf(
            initial,
            *additional,
        ).map(A3Values<Double>::toVec3),
        tangent_start = tangentStart?.toVec3(),
        tangent_end = tangentEnd?.toVec3(),
    )
}

fun splineRel(
    initial: A3Values<Double>,
    vararg additional: A3Values<Double>,
    tangentStart: A3Values<Double>? = null,
    tangentEnd: A3Values<Double>? = null,
): PathSegment = { current ->
    val point = current()
    Curve3.Spline(
        points = nonEmptyListOf(
            point + initial,
            *(additional.map { point + it }.toTypedArray()),
        ).map(A3Values<Double>::toVec3),
        tangent_start = tangentStart?.toVec3(),
        tangent_end = tangentEnd?.toVec3(),
    )
}

fun bezierTo(
    first: A3Values<Double>,
    second: A3Values<Double>,
    vararg additional: A3Values<Double>,
): PathSegment = {
    Curve3.Bezier(
        points = nonEmptyListOf(
            first,
            second,
            *additional,
        ).map(A3Values<Double>::toVec3),
    )
}

fun bezierRel(
    first: A3Values<Double>,
    second: A3Values<Double>,
    vararg additional: A3Values<Double>,
): PathSegment = { current ->
    val point = current()
    Curve3.Bezier(
        points = nonEmptyListOf(
            point + first,
            point + second,
            *(additional.map { point + it }.toTypedArray()),
        ).map(A3Values<Double>::toVec3),
    )
}

fun helix(
    pitch: Double,
    height: Double,
    rightHanded: Boolean = true,
): PathSegment = {
    Curve3.Helix(
        pitch = pitch,
        height = height,
        right_handed = rightHanded,
    )
}

fun polygon(
    first: A3Values<Double>,
    second: A3Values<Double>,
    vararg tail: A3Values<Double>,
): Path = polyline(
    first,
    second,
    *tail,
).close()

fun path(
    start: A3Values<Double>,
    segments: NonEmptyList<PathSegment>,
): Path = Path(
    start = start.toVec3(),
    edges = buildChain(
        start = start,
        segments = segments,
    ) { it.end().toA3Values() },
)

fun path(
    start: A3Values<Double>,
    initial: PathSegment,
    vararg additional: PathSegment,
): Path = path(
    start = start,
    segments = nonEmptyListOf(
        initial,
        *additional,
    ),
)

fun polyline(
    first: A3Values<Double>,
    second: A3Values<Double>,
    vararg tail: A3Values<Double>,
): Path = path(
    first,
    lineTo(second),
    *tail.map { lineTo(it) }.toTypedArray(),
)

fun Path.close(): Path = copy(
    edges = edges + Curve3.Line(to = start),
)

private fun Curve3.end(): Vec3 = fold(
    ifLine = { to -> to },
    ifArc = { to, _ -> to },
    ifSpline = { points, _, _ -> points.last() },
    ifBezier = { points -> points.last() },
    ifHelix = { _, _, _ -> error("a helix cannot be followed by a relative segment") },
)