package org.hnau.ktcad

import org.hnau.ktcad.ir.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolyhedronExtTest {

    @Test
    fun shared_points_are_deduplicated_by_equals() {
        val a = Vec3(0.0, 0.0, 0.0)
        val b = Vec3(1.0, 0.0, 0.0)
        val c = Vec3(0.0, 1.0, 0.0)
        val d = Vec3(0.0, 0.0, 1.0)

        // A tetrahedron: each vertex appears in three faces.
        val solid = polyhedron(
            listOf(a, b, c),
            listOf(a, c, d),
            listOf(a, d, b),
            listOf(b, d, c),
        )

        assertTrue(solid is Solid.Polyhedron)
        assertEquals(4, solid.points.size)
        assertEquals(listOf(a, b, c, d), solid.points.toList())
        assertTrue(solid.faces.all { face -> face.all { it in 0..3 } })
        // The first face keeps the order (a, b, c) -> (0, 1, 2).
        assertEquals(listOf(0, 1, 2), solid.faces.first())
    }
}
