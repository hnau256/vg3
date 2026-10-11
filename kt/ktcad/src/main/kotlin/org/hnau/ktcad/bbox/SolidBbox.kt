package org.hnau.ktcad.bbox

import org.hnau.ktcad.Region
import org.hnau.ktcad.Solid
import org.hnau.ktcad.fold
import org.hnau.ktcad.ir.BooleanKind
import org.hnau.ktcad.ir.Curve3
import org.hnau.ktcad.ir.Path
import org.hnau.ktcad.ir.TransformOp
import org.hnau.ktcad.ir.Vec3
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
val Solid.bbox: Axis3Values<ClosedRange<Double>>
    get() = fold(
        ifBool = { arguments, kind, tools ->
            val operands = if (kind == BooleanKind.CUT) arguments.toList() else arguments.toList() + tools
            operands.map { it.bbox }.reduce { a, b -> a.cover(b) }
        },
        ifBox = { height, length, width -> box(0.0..width, 0.0..length, 0.0..height) },
        ifCone = { height, radius_bottom, radius_top, _ ->
            val radius = maxOf(radius_bottom, radius_top)
            box(-radius..radius, -radius..radius, 0.0..height)
        },
        ifCylinder = { height, radius, _ -> box(-radius..radius, -radius..radius, 0.0..height) },
        ifExtrude = { height, profile ->
            val profileBox = profile.bbox
            box(profileBox.x, profileBox.y, 0.0..height)
        },
        ifFillet = { _, target, _ -> target.bbox },
        ifHalfspace = { box(UNBOUNDED, UNBOUNDED, Double.NEGATIVE_INFINITY..0.0) },
        ifLoft = { sections, _, _, _, _, _, _ ->
            sections.map(::pathBox).reduce { a, b -> a.cover(b) }
        },
        ifOffset = { distance, target, _ -> target.bbox.grow(distance) },
        ifPolyhedron = { _, points -> pointsBox(points) },
        ifRevolve = { _, profile ->
            val profileBox = profile.bbox
            val radius = maxOf(abs(profileBox.x.start), abs(profileBox.x.endInclusive))
            box(-radius..radius, profileBox.y, -radius..radius)
        },
        ifSphere = { radius, _ -> box(-radius..radius, -radius..radius, -radius..radius) },
        ifSweep = { path, profile, _, _ -> pathBox(path).grow(profileRadius(profile)) },
        ifThickSolid = { _, offset, target, _ -> target.bbox.grow(offset) },
        ifTorus = { major_radius, minor_radius, _ ->
            val radius = major_radius + minor_radius
            box(-radius..radius, -radius..radius, -minor_radius..minor_radius)
        },
        ifTransform = { op, target -> target.bbox.transform(op) },
        ifWedge = { height, length, _, width -> box(0.0..width, 0.0..length, 0.0..height) },
    )

private val UNBOUNDED = Double.NEGATIVE_INFINITY..Double.POSITIVE_INFINITY

private fun box(
    x: ClosedRange<Double>,
    y: ClosedRange<Double>,
    z: ClosedRange<Double>,
): Axis3Values<ClosedRange<Double>> = Axis3Values(x = x, y = y, z = z)

private fun pointsBox(points: Iterable<Vec3>): Axis3Values<ClosedRange<Double>> =
    Axis3Values(
        x = points.minOf { it.x }..points.maxOf { it.x },
        y = points.minOf { it.y }..points.maxOf { it.y },
        z = points.minOf { it.z }..points.maxOf { it.z },
    )

private fun Axis3Values<ClosedRange<Double>>.cover(
    other: Axis3Values<ClosedRange<Double>>,
): Axis3Values<ClosedRange<Double>> = combineWith(other) { a, b -> a.cover(b) }

private fun Axis3Values<ClosedRange<Double>>.grow(distance: Double): Axis3Values<ClosedRange<Double>> =
    map { it.grow(maxOf(0.0, distance)) }

/** The radius of the profile about its own origin — how far a swept section can reach from the path. */
private fun profileRadius(profile: Region): Double {
    val profileBox = profile.bbox
    return maxOf(
        abs(profileBox.x.start),
        abs(profileBox.x.endInclusive),
        abs(profileBox.y.start),
        abs(profileBox.y.endInclusive),
    )
}

// --- Paths (3D) -------------------------------------------------------------

private fun pathBox(path: Path): Axis3Values<ClosedRange<Double>> {
    var box = pointsBox(listOf(path.start))
    var current = path.start
    path.edges.forEach { edge ->
        box = box.cover(edge.geometryBox(current))
        current = edge.endPoint(current)
    }
    return box
}

/** A segment's bounding box, from its [from] point (the previous endpoint). */
private fun Curve3.geometryBox(from: Vec3): Axis3Values<ClosedRange<Double>> = when (this) {
    is Curve3.Line -> pointsBox(listOf(from, to))
    is Curve3.Arc -> pointsBox(listOf(from, via, to))
    is Curve3.Spline -> pointsBox(listOf(from) + points)
    is Curve3.Bezier -> pointsBox(listOf(from) + points)
    is Curve3.Helix -> {
        val radius = hypot(from.x, from.y)
        val top = from.z + height
        box(-radius..radius, -radius..radius, minOf(from.z, top)..maxOf(from.z, top))
    }
}

private fun Curve3.endPoint(current: Vec3): Vec3 = when (this) {
    is Curve3.Line -> to
    is Curve3.Arc -> to
    is Curve3.Spline -> points.last()
    is Curve3.Bezier -> points.last()
    is Curve3.Helix -> {
        val radius = hypot(current.x, current.y)
        val phase = atan2(current.y, current.x)
        val direction = if (right_handed) 1.0 else -1.0
        val end = phase + direction * 2.0 * PI * (height / pitch)
        Vec3(x = radius * cos(end), y = radius * sin(end), z = current.z + height)
    }
}

// --- Transforms (3D) --------------------------------------------------------

private fun Axis3Values<ClosedRange<Double>>.transform(
    op: TransformOp,
): Axis3Values<ClosedRange<Double>> = pointsBox(corners().map(op::transformPoint))

private fun Axis3Values<ClosedRange<Double>>.corners(): List<Vec3> {
    val xs = listOf(x.start, x.endInclusive)
    val ys = listOf(y.start, y.endInclusive)
    val zs = listOf(z.start, z.endInclusive)
    return xs.flatMap { px -> ys.flatMap { py -> zs.map { pz -> Vec3(x = px, y = py, z = pz) } } }
}

private fun TransformOp.transformPoint(point: Vec3): Vec3 = when (this) {
    is TransformOp.Translate ->
        Vec3(x = point.x + value.x, y = point.y + value.y, z = point.z + value.z)
    is TransformOp.Scale ->
        Vec3(x = point.x * value.x, y = point.y * value.y, z = point.z * value.z)
    is TransformOp.Mirror -> {
        val n = normalized(normal)
        val projection =
            (point.x - center.x) * n.x + (point.y - center.y) * n.y + (point.z - center.z) * n.z
        Vec3(
            x = point.x - 2.0 * projection * n.x,
            y = point.y - 2.0 * projection * n.y,
            z = point.z - 2.0 * projection * n.z,
        )
    }
    is TransformOp.Rotate -> {
        val a = normalized(axis)
        val vx = point.x - center.x
        val vy = point.y - center.y
        val vz = point.z - center.z
        val c = cos(angle)
        val s = sin(angle)
        val dot = a.x * vx + a.y * vy + a.z * vz
        val crossX = a.y * vz - a.z * vy
        val crossY = a.z * vx - a.x * vz
        val crossZ = a.x * vy - a.y * vx
        Vec3(
            x = center.x + vx * c + crossX * s + a.x * dot * (1.0 - c),
            y = center.y + vy * c + crossY * s + a.y * dot * (1.0 - c),
            z = center.z + vz * c + crossZ * s + a.z * dot * (1.0 - c),
        )
    }
    is TransformOp.Matrix -> Vec3(
        x = m[0] * point.x + m[1] * point.y + m[2] * point.z + m[3],
        y = m[4] * point.x + m[5] * point.y + m[6] * point.z + m[7],
        z = m[8] * point.x + m[9] * point.y + m[10] * point.z + m[11],
    )
}

private fun normalized(vector: Vec3): Vec3 {
    val length = sqrt(vector.x * vector.x + vector.y * vector.y + vector.z * vector.z)
    return Vec3(x = vector.x / length, y = vector.y / length, z = vector.z / length)
}
