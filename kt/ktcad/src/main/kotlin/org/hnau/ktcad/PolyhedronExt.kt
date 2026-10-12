package org.hnau.ktcad

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.toNonEmptyListOrThrow
import org.hnau.ktcad.axis.Axis3Values
import org.hnau.ktcad.axis.toVec3
import org.hnau.ktcad.ir.Vec3

/**
 * Builds a polyhedron from its faces. There is at least one face; each face is a list of points
 * given in order (the engine needs at least three). Equal points (structural `equals`) share one
 * position, so callers never deal with indices.
 *
 * The model body stores a flat point list plus faces as index lists; this sugar computes both,
 * reusing the same deduplicating [Lowering] arena as the IR lowering.
 */
fun polyhedron(
    faces: NonEmptyList<List<Axis3Values<Double>>>,
): Solid {
    val points = Lowering<Vec3, Int, Vec3>(wrap = { it }) { point, _ -> point }
    val faceIndices = faces.map { face -> face.map { points.indexOf(it.toVec3()) } }
    return Solid.Polyhedron(
        points = points.nodes.toNonEmptyListOrThrow(),
        faces = faceIndices,
    )
}

/** Variadic convenience: `polyhedron(face0, face1, …)`. */
fun polyhedron(
    first: List<Axis3Values<Double>>,
    vararg additional: List<Axis3Values<Double>>,
): Solid =
    polyhedron(
        nonEmptyListOf(
            first,
            *additional,
        ),
    )
