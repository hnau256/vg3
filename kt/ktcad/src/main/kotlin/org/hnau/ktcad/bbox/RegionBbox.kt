package org.hnau.ktcad.bbox

import org.hnau.ktcad.Region
import org.hnau.ktcad.end
import org.hnau.ktcad.fold
import org.hnau.ktcad.ir.BooleanKind
import org.hnau.ktcad.ir.Curve2
import org.hnau.ktcad.ir.TransformOp2
import org.hnau.ktcad.ir.Vec2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * An **approximate** axis-aligned bounding box of the region, per axis, computed from the IR alone
 * (no OCCT). It is a cheap conservative over-approximation, not the exact geometric extent:
 * - `polygon` — exact (the points);
 * - `circle` — exact (`[-radius, radius]` on both axes);
 * - `contour` — every edge is bounded by its control points (`line`: endpoints; `arc`: start/via/end;
 *   `spline`/`bezier`: start + poles), i.e. exact for a `line` and conservative otherwise;
 * - `transform` — the box's four corners are transformed and re-bounded (exact for the box, an
 *   over-approximation for the shape);
 * - `bool` — for `cut` the union of the objects (the tools only remove material, so they are
 *   ignored); otherwise the union of objects and tools;
 * - `fillet2d` — the target's box (rounding corners only removes material);
 * - `offset2d` — the target's box grown by a positive `distance` (a shrink stays inside it).
 */
val Region.bbox: Axis2Values<ClosedRange<Double>>
    get() = fold(
        ifBool = { arguments, kind, tools ->
            val operands = if (kind == BooleanKind.CUT) arguments.toList() else arguments.toList() + tools
            operands.map { it.bbox }.reduce { a, b -> a.cover(b) }
        },
        ifCircle = { radius -> Axis2Values(x = -radius..radius, y = -radius..radius) },
        ifContour = { edges, start ->
            var box = pointsBox(listOf(start))
            var current = start
            edges.forEach { edge ->
                box = box.cover(pointsBox(edge.controlPoints(current)))
                current = edge.end()
            }
            box
        },
        ifFillet2d = { _, target -> target.bbox },
        ifOffset2d = { distance, target, _ -> target.bbox.grow(distance) },
        ifPolygon = { points -> pointsBox(points) },
        ifTransform = { op, target -> target.bbox.transform(op) },
    )

/** A segment's control points, including its [from] point (the previous endpoint). */
private fun Curve2.controlPoints(from: Vec2): List<Vec2> = when (this) {
    is Curve2.Line -> listOf(from, to)
    is Curve2.Arc -> listOf(from, via, to)
    is Curve2.Spline -> listOf(from) + points
    is Curve2.Bezier -> listOf(from) + points
}

private fun pointsBox(points: Iterable<Vec2>): Axis2Values<ClosedRange<Double>> =
    Axis2Values(
        x = points.minOf { it.x }..points.maxOf { it.x },
        y = points.minOf { it.y }..points.maxOf { it.y },
    )

private fun Axis2Values<ClosedRange<Double>>.cover(
    other: Axis2Values<ClosedRange<Double>>,
): Axis2Values<ClosedRange<Double>> = combineWith(other) { a, b -> a.cover(b) }

private fun Axis2Values<ClosedRange<Double>>.grow(distance: Double): Axis2Values<ClosedRange<Double>> =
    map { it.grow(maxOf(0.0, distance)) }

private fun Axis2Values<ClosedRange<Double>>.transform(
    op: TransformOp2,
): Axis2Values<ClosedRange<Double>> {
    val corners = listOf(
        Vec2(x = x.start, y = y.start),
        Vec2(x = x.endInclusive, y = y.start),
        Vec2(x = x.endInclusive, y = y.endInclusive),
        Vec2(x = x.start, y = y.endInclusive),
    )
    return pointsBox(corners.map(op::transformPoint))
}

private fun TransformOp2.transformPoint(point: Vec2): Vec2 = when (this) {
    is TransformOp2.Translate ->
        point.combineWith(value) { a, b -> a + b }
    is TransformOp2.Rotate -> {
        val v = point.combineWith(center) { p, c -> p - c }
        val cosine = cos(angle)
        val sine = sin(angle)
        vec2 { axis ->
            when (axis) {
                Axis2.X -> v[Axis2.X] * cosine - v[Axis2.Y] * sine
                Axis2.Y -> v[Axis2.X] * sine + v[Axis2.Y] * cosine
            }
        }.combineWith(center) { r, c -> r + c }
    }
    is TransformOp2.Mirror -> {
        val n = normalized(normal)
        val projection = point.combineWith(center) { p, c -> p - c }.dot(n)
        point.combineWith(n) { p, ni -> p - 2.0 * projection * ni }
    }
    is TransformOp2.Scale ->
        point.combineWith(value) { a, b -> a * b }
}

private fun Vec2.dot(other: Vec2): Double = Axis2.entries.sumOf { get(it) * other[it] }

private fun normalized(vector: Vec2): Vec2 {
    val length = sqrt(vector.dot(vector))
    return vector.map { it / length }
}
