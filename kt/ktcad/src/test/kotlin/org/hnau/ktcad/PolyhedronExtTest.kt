package org.hnau.ktcad

import org.hnau.ktcad.axis.A3Values
import org.hnau.ktcad.axis.toVec3
import org.hnau.ktcad.solid.PolyhedronFace
import org.hnau.ktcad.solid.polyhedron
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolyhedronExtTest {

    @Test
    fun shared_points_are_deduplicated_by_equals() {
        val a = A3Values(
            x = 0.0,
            y = 0.0,
            z = 0.0,
        )
        val b = A3Values(
            x = 1.0,
            y = 0.0,
            z = 0.0,
        )
        val c = A3Values(
            x = 0.0,
            y = 1.0,
            z = 0.0,
        )
        val d = A3Values(
            x = 0.0,
            y = 0.0,
            z = 1.0,
        )

        // A tetrahedron: each vertex appears in three faces.
        val solid = polyhedron(
            PolyhedronFace(
                a,
                b,
                c,
            ),
            PolyhedronFace(
                a,
                c,
                d,
            ),
            PolyhedronFace(
                a,
                d,
                b,
            ),
            PolyhedronFace(
                b,
                d,
                c,
            ),
        )

        assertTrue(solid is Solid.Polyhedron)
        assertEquals(
            expected = 4,
            actual = solid.points.size,
        )
        assertEquals(
            expected = listOf(
                a,
                b,
                c,
                d,
            ).map { it.toVec3() },
            actual = solid.points,
        )
        assertTrue(solid.faces.all { face -> face.all { it in 0..3 } })
        // The first face keeps the order (a, b, c) -> (0, 1, 2).
        assertEquals(
            expected = listOf(
                0,
                1,
                2,
            ),
            actual = solid.faces.first(),
        )
    }
}
