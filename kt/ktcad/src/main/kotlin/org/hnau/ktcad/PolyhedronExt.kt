package org.hnau.ktcad

import org.hnau.ktcad.ir.Point3

/**
 * Builds a polyhedron from its faces. Each face is a list of points given in order; equal points
 * (structural `equals`) share one position, so callers never deal with indices.
 *
 * The model body stores a flat point list plus faces as index lists; this sugar computes both.
 */
fun polyhedron(faces: List<List<Point3>>): Solid {
    val points = mutableListOf<Point3>()
    val indexOf = HashMap<Point3, Int>()
    val faceIndices = faces.map { face ->
        face.map { point ->
            indexOf.getOrPut(point) {
                points += point
                points.lastIndex
            }
        }
    }
    return Solid.Polyhedron(points = points, faces = faceIndices)
}

/** Variadic convenience: `polyhedron(face0, face1, …)`. */
fun polyhedron(vararg faces: List<Point3>): Solid = polyhedron(faces.toList())
