package org.hnau.ktcad

import org.hnau.ktcad.ir.Vec3

/**
 * Builds a polyhedron from its faces. Each face is a list of points given in order; equal points
 * (structural `equals`) share one position, so callers never deal with indices.
 *
 * The model body stores a flat point list plus faces as index lists; this sugar computes both,
 * reusing the same deduplicating [Lowering] arena as the IR lowering.
 */
fun polyhedron(faces: List<List<Vec3>>): Solid {
    val points = Lowering<Vec3, Int, Vec3>({ it }) { point, _ -> point }
    val faceIndices = faces.map { face -> face.map(points::indexOf) }
    return Solid.Polyhedron(points = points.nodes, faces = faceIndices)
}

/** Variadic convenience: `polyhedron(face0, face1, …)`. */
fun polyhedron(vararg faces: List<Vec3>): Solid = polyhedron(faces.toList())
