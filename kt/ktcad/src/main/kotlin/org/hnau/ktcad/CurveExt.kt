package org.hnau.ktcad

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import org.hnau.ktcad.axis.Axis2Values
import org.hnau.ktcad.axis.Axis3Values
import org.hnau.ktcad.axis.plus
import org.hnau.ktcad.axis.toAxis2Values
import org.hnau.ktcad.axis.toAxis3Values
import org.hnau.ktcad.axis.toVec2
import org.hnau.ktcad.axis.toVec3
import org.hnau.ktcad.ir.Curve2
import org.hnau.ktcad.ir.Curve3
import org.hnau.ktcad.ir.Path
import org.hnau.ktcad.ir.Vec2
import org.hnau.ktcad.ir.Vec3

/**
 * DSL sugar for contours.
 *
 * A 2D contour is a [Region] with at least one edge (`edges` is a `NonEmptyList`), built by the
 * `contour(...)` factory from a start point and **segments**. A 3D path is built by `Path(...)`.
 * `…To` segments are absolute, `…Rel` are relative to the current point; a segment receives the
 * current point **lazily** (`() -> Point`), so an absolute segment does not force it — which lets an
 * absolute segment follow a `helix` (whose end the DSL cannot compute).
 */

typealias ContourSegment = (() -> Axis2Values<Double>) -> Curve2
typealias PathSegment = (() -> Axis3Values<Double>) -> Curve3

// --- 2D segments ------------------------------------------------------------

fun lineTo(
    to: Axis2Values<Double>,
): ContourSegment = { Curve2.Line(to = to.toVec2()) }

/** A `line` offset from the current point by `delta`. */
fun lineRel(
    delta: Axis2Values<Double>,
): ContourSegment = { current ->
    Curve2.Line(to = (current() + delta).toVec2())
}

fun arcTo(
    via: Axis2Values<Double>,
    to: Axis2Values<Double>,
): ContourSegment =
    {
        Curve2.Arc(
            via = via.toVec2(),
            to = to.toVec2(),
        )
    }

/** An `arc` whose `via` and `to` are offsets from the current point. */
fun arcRel(
    via: Axis2Values<Double>,
    to: Axis2Values<Double>,
): ContourSegment = { current ->
    val point = current()
    Curve2.Arc(
        via = (point + via).toVec2(),
        to = (point + to).toVec2(),
    )
}

fun splineTo(
    initial: Axis2Values<Double>,
    vararg additional: Axis2Values<Double>,
    tangentStart: Axis2Values<Double>? = null,
    tangentEnd: Axis2Values<Double>? = null,
): ContourSegment = {
    Curve2.Spline(
        points = nonEmptyListOf(
            initial,
            *additional,
        ).map { it.toVec2() },
        tangent_start = tangentStart?.toVec2(),
        tangent_end = tangentEnd?.toVec2(),
    )
}

/** A 2D Bezier: [first], [second] and [additional] are the poles after the current point. */
fun bezierTo(
    first: Axis2Values<Double>,
    second: Axis2Values<Double>,
    vararg additional: Axis2Values<Double>,
): ContourSegment =
    {
        Curve2.Bezier(
            points = nonEmptyListOf(
                first,
                second,
                *additional,
            ).map { it.toVec2() },
        )
    }

// --- 3D segments ------------------------------------------------------------

fun lineTo(
    to: Axis3Values<Double>,
): PathSegment = { Curve3.Line(to = to.toVec3()) }

/** A `line` offset from the current point by `delta`. */
fun lineRel(
    delta: Axis3Values<Double>,
): PathSegment = { current ->
    Curve3.Line(to = (current() + delta).toVec3())
}

fun arcTo(
    via: Axis3Values<Double>,
    to: Axis3Values<Double>,
): PathSegment =
    {
        Curve3.Arc(
            via = via.toVec3(),
            to = to.toVec3(),
        )
    }

/** An `arc` whose `via` and `to` are offsets from the current point. */
fun arcRel(
    via: Axis3Values<Double>,
    to: Axis3Values<Double>,
): PathSegment = { current ->
    val point = current()
    Curve3.Arc(
        via = (point + via).toVec3(),
        to = (point + to).toVec3(),
    )
}

fun splineTo(
    initial: Axis3Values<Double>,
    vararg additional: Axis3Values<Double>,
    tangentStart: Axis3Values<Double>? = null,
    tangentEnd: Axis3Values<Double>? = null,
): PathSegment = {
    Curve3.Spline(
        points = nonEmptyListOf(
            initial,
            *additional,
        ).map { it.toVec3() },
        tangent_start = tangentStart?.toVec3(),
        tangent_end = tangentEnd?.toVec3(),
    )
}

/** A 3D Bezier: [first], [second] and [additional] are the poles after the current point. */
fun bezierTo(
    first: Axis3Values<Double>,
    second: Axis3Values<Double>,
    vararg additional: Axis3Values<Double>,
): PathSegment =
    {
        Curve3.Bezier(
            points = nonEmptyListOf(
                first,
                second,
                *additional,
            ).map { it.toVec3() },
        )
    }

/**
 * A helix around the Z axis through the origin, starting at the current point (its radius and phase
 * are taken from there). [rightHanded] selects the winding; the number of turns is `height / pitch`.
 */
fun helix(
    pitch: Double,
    height: Double,
    rightHanded: Boolean = true,
): PathSegment =
    {
        Curve3.Helix(
            pitch = pitch,
            height = height,
            right_handed = rightHanded,
        )
    }

// --- Contour factory --------------------------------------------------------

/** A planar region from a start point and segments (relative segments see the current point). */
fun contour(
    start: Axis2Values<Double>,
    segments: NonEmptyList<ContourSegment>,
): Region =
    Region.Contour(
        start = start.toVec2(),
        edges = buildChain(start = start, segments = segments) { it.end().toAxis2Values() },
    )

fun contour(
    start: Axis2Values<Double>,
    initial: ContourSegment,
    vararg additional: ContourSegment,
): Region =
    contour(
        start = start,
        segments = nonEmptyListOf(
            initial,
            *additional,
        ),
    )

fun Path(
    start: Axis3Values<Double>,
    segments: NonEmptyList<PathSegment>,
): Path =
    Path(
        start = start.toVec3(),
        edges = buildChain(start = start, segments = segments) { it.end().toAxis3Values() },
    )

fun Path(
    start: Axis3Values<Double>,
    initial: PathSegment,
    vararg additional: PathSegment,
): Path =
    Path(
        start = start,
        segments = nonEmptyListOf(
            initial,
            *additional,
        ),
    )

/**
 * Chains [segments] into edges: each segment is given the current point **lazily** (`() -> Point`),
 * so an absolute segment does not force it, and [end] tracks the running endpoint.
 */
private fun <P, C> buildChain(
    start: P,
    segments: NonEmptyList<(() -> P) -> C>,
    end: (C) -> P,
): NonEmptyList<C> {
    val first = segments.head({ start })
    val (edges, _) = segments.tail.fold(initial = nonEmptyListOf(first) to { end(first) }) { (acc, current), segment ->
        val curve = segment(current)
        (acc + curve) to { end(curve) }
    }
    return edges
}

// --- Ready-made 3D contours -------------------------------------------------

/** An open 3D polyline through the given absolute points. */
fun polyline(
    first: Axis3Values<Double>,
    second: Axis3Values<Double>,
    vararg tail: Axis3Values<Double>,
): Path =
    Path(
        first,
        lineTo(second),
        *tail.map { lineTo(it) }.toTypedArray(),
    )

/** A closed 3D polygon through the given absolute points. */
fun polygon(
    first: Axis3Values<Double>,
    second: Axis3Values<Double>,
    vararg tail: Axis3Values<Double>,
): Path =
    polyline(
        first,
        second,
        *tail,
    ).close()

/** Closes a 3D contour with a `line` back to its start. */
fun Path.close(): Path = copy(edges = edges + Curve3.Line(to = start))

// --- Segment endpoints ------------------------------------------------------

internal fun Curve2.end(): Vec2 = fold(
    ifLine = { to -> to },
    ifArc = { to, _ -> to },
    ifSpline = { points, _, _ -> points.last() },
    ifBezier = { points -> points.last() },
)

private fun Curve3.end(): Vec3 = fold(
    ifLine = { to -> to },
    ifArc = { to, _ -> to },
    ifSpline = { points, _, _ -> points.last() },
    ifBezier = { points -> points.last() },
    ifHelix = { _, _, _ -> error("a helix cannot be followed by a relative segment") },
)
