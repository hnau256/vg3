package org.hnau.ktcad.bbox

import org.hnau.ktcad.Region
import org.hnau.ktcad.axis.A2
import org.hnau.ktcad.axis.A2Values
import org.hnau.ktcad.axis.fold
import org.hnau.ktcad.fold
import org.hnau.ktcad.ir.BooleanKind
import org.hnau.ktcad.ir.Curve2
import org.hnau.ktcad.ir.TransformOp2
import org.hnau.ktcad.ir.Vec2
import org.hnau.ktcad.ir.fold
import org.hnau.commons.kotlin.foldBoolean
import org.hnau.ktcad.region.end
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
val Region.bbox: A2Values<ClosedRange<Double>>
    get() = fold(
        ifBool = { arguments, kind, tools ->
            val operands = (kind == BooleanKind.CUT).foldBoolean(
                ifTrue = { arguments },
                ifFalse = { arguments + tools },
            )
            operands.map { it.bbox }.reduce { a, b -> a.cover(b) }
        },
        ifCircle = { radius -> A2Values.create { -radius..radius } },
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
private fun Curve2.controlPoints(
    from: Vec2,
): List<Vec2> = fold(
    ifLine = { to ->
        listOf(
            from,
            to,
        )
    },
    ifArc = { to, via ->
        listOf(
            from,
            via,
            to,
        )
    },
    ifSpline = { points, _, _ -> listOf(from) + points },
    ifBezier = { points -> listOf(from) + points },
)

private fun pointsBox(
    points: Iterable<Vec2>,
): A2Values<ClosedRange<Double>> =
    A2Values.create { axis -> points.minOf { it[axis] }..points.maxOf { it[axis] } }

private fun A2Values<ClosedRange<Double>>.cover(
    other: A2Values<ClosedRange<Double>>,
): A2Values<ClosedRange<Double>> = combineWith(other = other) { a, b -> a.cover(b) }

private fun A2Values<ClosedRange<Double>>.grow(
    distance: Double,
): A2Values<ClosedRange<Double>> =
    map {
        it.grow(
            maxOf(
                a = 0.0,
                b = distance,
            ),
        )
    }

private fun A2Values<ClosedRange<Double>>.transform(
    op: TransformOp2,
): A2Values<ClosedRange<Double>> = pointsBox(corners().map(op::transformPoint))

private fun A2Values<ClosedRange<Double>>.corners(): List<Vec2> =
    A2.entries.fold(initial = listOf(vec2 { 0.0 })) { corners, axis ->
        corners.flatMap { corner ->
            listOf(
                this[axis].start,
                this[axis].endInclusive,
            ).map { value -> corner.copy(axis = axis) { value } }
        }
    }

private fun TransformOp2.transformPoint(
    point: Vec2,
): Vec2 = fold(
    ifTranslate = { value -> point + value },
    ifScale = { value -> point * value },
    ifMirror = { center, normal ->
        val n = normalized(normal)
        point - n * (2.0 * (point - center).dot(n))
    },
    ifRotate = { angle, center ->
        val v = point - center
        val cosine = cos(angle)
        val sine = sin(angle)
        vec2 { axis ->
            axis.fold(
                ifX = { v[A2.X] * cosine - v[A2.Y] * sine },
                ifY = { v[A2.X] * sine + v[A2.Y] * cosine },
            )
        } + center
    },
)

private fun Vec2.dot(
    other: Vec2,
): Double = A2.entries.sumOf { get(it) * other[it] }

private fun normalized(
    vector: Vec2,
): Vec2 {
    val length = sqrt(vector.dot(vector))
    return vector.map { it / length }
}
