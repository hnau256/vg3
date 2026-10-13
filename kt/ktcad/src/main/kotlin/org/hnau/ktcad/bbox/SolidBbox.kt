package org.hnau.ktcad.bbox

import org.hnau.ktcad.Region
import org.hnau.ktcad.Solid
import org.hnau.ktcad.axis.A2
import org.hnau.ktcad.axis.A3
import org.hnau.ktcad.axis.A3Values
import org.hnau.ktcad.axis.fold
import org.hnau.ktcad.fold
import org.hnau.ktcad.ir.BooleanKind
import org.hnau.ktcad.ir.Curve3
import org.hnau.ktcad.ir.Path
import org.hnau.ktcad.ir.TransformOp
import org.hnau.ktcad.ir.Vec3
import org.hnau.ktcad.ir.fold
import org.hnau.commons.kotlin.foldBoolean
import org.hnau.ktcad.axis.A2Values
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * An **approximate** axis-aligned bounding box of the solid, per axis, computed from the IR alone
 * (no OCCT). It is a cheap conservative over-approximation, not the exact geometric extent:
 * - primitives — exact for the full shape; an `angle` wedge is bounded by the full primitive;
 *   `halfspace` is honestly unbounded;
 * - `polyhedron` — exact (the points);
 * - `extrude` — the profile's box, swept in `+Z`;
 * - `revolve` — the profile's box revolved about `Y` (the radial reach bounds `X` and `Z`);
 * - `sweep` — the path's box grown by the profile's radius;
 * - `loft` — the union of the sections' boxes;
 * - `transform` — the box's eight corners are transformed and re-bounded (exact for the box, an
 *   over-approximation for the shape; undefined for an unbounded `halfspace`);
 * - `bool` — for `cut` the union of the objects (tools only remove material); otherwise the union
 *   of objects and tools;
 * - `fillet` — the target's box (a fillet/chamfer only removes material);
 * - `offset`/`thick_solid` — the target's box grown by a positive distance (a shrink stays inside).
 */
val Solid.bbox: A3Values<ClosedRange<Double>>
    get() = fold(
        ifBool = { arguments, kind, tools ->
            val operands = (kind == BooleanKind.CUT).foldBoolean(
                ifTrue = { arguments },
                ifFalse = { arguments + tools },
            )
            operands.map { it.bbox }.reduce { a, b -> a.cover(b) }
        },
        ifBox = { height, length, width ->
            box(
                x = 0.0..width,
                y = 0.0..length,
                z = 0.0..height,
            )
        },
        ifCone = { height, radius_bottom, radius_top, _ ->
            val radius = maxOf(
                a = radius_bottom,
                b = radius_top,
            )
            box(
                x = -radius..radius,
                y = -radius..radius,
                z = 0.0..height,
            )
        },
        ifCylinder = { height, radius, _ ->
            box(
                x = -radius..radius,
                y = -radius..radius,
                z = 0.0..height,
            )
        },
        ifExtrude = { height, profile ->
            val profileBox = profile.bbox
            box(
                x = profileBox.x,
                y = profileBox.y,
                z = 0.0..height,
            )
        },
        ifFillet = { _, target, _ -> target.bbox },
        ifHalfspace = {
            box(
                x = UNBOUNDED,
                y = UNBOUNDED,
                z = Double.NEGATIVE_INFINITY..0.0,
            )
        },
        ifLoft = { sections, _, _, _, _, _, _ ->
            sections.map(::pathBox).reduce { a, b -> a.cover(b) }
        },
        ifOffset = { distance, target, _ -> target.bbox.growNotShrink(distance) },
        ifPolyhedron = { _, points -> pointsBox(points) },
        ifRevolve = { _, profile ->
            val profileBox = profile.bbox
            val radius = maxOf(
                a = abs(profileBox.x.start),
                b = abs(profileBox.x.endInclusive),
            )
            box(
                x = -radius..radius,
                y = profileBox.y,
                z = -radius..radius,
            )
        },
        ifSphere = { radius, _ ->
            box(
                x = -radius..radius,
                y = -radius..radius,
                z = -radius..radius,
            )
        },
        ifSweep = { path, profile, _, _ -> pathBox(path).growNotShrink(profileRadius(profile)) },
        ifThickSolid = { _, offset, target, _ -> target.bbox.growNotShrink(offset) },
        ifTorus = { major_radius, minor_radius, _ ->
            val radius = major_radius + minor_radius
            box(
                x = -radius..radius,
                y = -radius..radius,
                z = -minor_radius..minor_radius,
            )
        },
        ifTransform = { op, target -> target.bbox.transform(op) },
        ifWedge = { height, length, _, width ->
            box(
                x = 0.0..width,
                y = 0.0..length,
                z = 0.0..height,
            )
        },
    )

private val UNBOUNDED = Double.NEGATIVE_INFINITY..Double.POSITIVE_INFINITY

private fun box(
    x: ClosedRange<Double>,
    y: ClosedRange<Double>,
    z: ClosedRange<Double>,
): A3Values<ClosedRange<Double>> = A3Values(
    x = x,
    y = y,
    z = z,
)

private fun pointsBox(
    points: Iterable<Vec3>,
): A3Values<ClosedRange<Double>> =
    A3Values.create { axis -> points.minOf { it[axis] }..points.maxOf { it[axis] } }

private fun A3Values<ClosedRange<Double>>.cover(
    other: A3Values<ClosedRange<Double>>,
): A3Values<ClosedRange<Double>> = combineWith(other = other) { a, b -> a.cover(b) }

private fun A3Values<ClosedRange<Double>>.growNotShrink(
    distance: Double,
): A3Values<ClosedRange<Double>> = growOrShrink(
    distance = maxOf(
        a = 0.0,
        b = distance,
    ),
)

/** The radius of the profile about its own origin — how far a swept section can reach from the path. */
private fun profileRadius(
    profile: Region,
): Double {
    val profileBox = profile.bbox
    return A2.entries.maxOf { axis ->
        maxOf(
            a = abs(profileBox[axis].start),
            b = abs(profileBox[axis].endInclusive),
        )
    }
}

// --- Paths (3D) -------------------------------------------------------------

private fun pathBox(
    path: Path,
): A3Values<ClosedRange<Double>> {
    var box = pointsBox(listOf(path.start))
    var current = path.start
    path.edges.forEach { edge ->
        box = box.cover(edge.geometryBox(current))
        current = edge.endPoint(current)
    }
    return box
}

/** A segment's bounding box, from its [from] point (the previous endpoint). */
private fun Curve3.geometryBox(
    from: Vec3,
): A3Values<ClosedRange<Double>> = fold(
    ifLine = { to ->
        pointsBox(
            listOf(
                from,
                to,
            ),
        )
    },
    ifArc = { to, via ->
        pointsBox(
            listOf(
                from,
                via,
                to,
            ),
        )
    },
    ifSpline = { points, _, _ -> pointsBox(listOf(from) + points) },
    ifBezier = { points -> pointsBox(listOf(from) + points) },
    ifHelix = { height, _, _ ->
        val radius = hypot(
            x = from.x,
            y = from.y,
        )
        val top = from.z + height
        box(
            x = -radius..radius,
            y = -radius..radius,
            z = minOf(
                a = from.z,
                b = top,
            )..maxOf(
                a = from.z,
                b = top,
            ),
        )
    },
)

private fun Curve3.endPoint(
    current: Vec3,
): Vec3 = fold(
    ifLine = { to -> to },
    ifArc = { to, _ -> to },
    ifSpline = { points, _, _ -> points.last() },
    ifBezier = { points -> points.last() },
    ifHelix = { height, pitch, right_handed ->
        val radius = hypot(
            x = current.x,
            y = current.y,
        )
        val phase = atan2(
            y = current.y,
            x = current.x,
        )
        val direction = right_handed.foldBoolean(
            ifTrue = { 1.0 },
            ifFalse = { -1.0 },
        )
        val end = phase + direction * 2.0 * PI * (height / pitch)
        Vec3(
            x = radius * cos(end),
            y = radius * sin(end),
            z = current.z + height,
        )
    },
)

// --- Transforms (3D) --------------------------------------------------------

private fun A3Values<ClosedRange<Double>>.transform(
    op: TransformOp,
): A3Values<ClosedRange<Double>> = pointsBox(corners().map(op::transformPoint))

private fun A3Values<ClosedRange<Double>>.corners(): List<Vec3> =
    A3.entries.fold(initial = listOf(vec3 { 0.0 })) { corners, axis ->
        corners.flatMap { corner ->
            listOf(
                this[axis].start,
                this[axis].endInclusive,
            ).map { value -> corner.copy(axis = axis) { value } }
        }
    }

private fun TransformOp.transformPoint(
    point: Vec3,
): Vec3 = fold(
    ifTranslate = { value -> point + value },
    ifScale = { value -> point * value },
    ifMirror = { center, normal ->
        val n = normalized(normal)
        point - n * (2.0 * (point - center).dot(n))
    },
    ifRotate = { angle, axis, center ->
        val a = normalized(axis)
        val v = point - center
        val cosine = cos(angle)
        val sine = sin(angle)
        v * cosine + a.cross(v) * sine + a * (v.dot(a) * (1.0 - cosine)) + center
    },
    ifMatrix = { m ->
        vec3 { axis ->
            val row = axis.fold(
                ifX = { 0 },
                ifY = { 4 },
                ifZ = { 8 },
            )
            m[row] * point[A3.X] +
                m[row + 1] * point[A3.Y] +
                m[row + 2] * point[A3.Z] +
                m[row + 3]
        }
    },
)

private fun Vec3.dot(
    other: Vec3,
): Double = A3.entries.sumOf { get(it) * other[it] }

private fun Vec3.cross(
    other: Vec3,
): Vec3 = vec3 { axis ->
    axis.fold(
        ifX = { this[A3.Y] * other[A3.Z] - this[A3.Z] * other[A3.Y] },
        ifY = { this[A3.Z] * other[A3.X] - this[A3.X] * other[A3.Z] },
        ifZ = { this[A3.X] * other[A3.Y] - this[A3.Y] * other[A3.X] },
    )
}

private fun normalized(
    vector: Vec3,
): Vec3 {
    val length = sqrt(vector.dot(vector))
    return vector.map { it / length }
}
