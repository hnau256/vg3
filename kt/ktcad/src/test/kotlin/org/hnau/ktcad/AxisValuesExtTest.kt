package org.hnau.ktcad

import org.hnau.ktcad.axis.A2Values
import org.hnau.ktcad.axis.A3Values
import org.hnau.ktcad.axis.minus
import org.hnau.ktcad.axis.plus
import org.hnau.ktcad.axis.times
import org.hnau.ktcad.axis.toA2Values
import org.hnau.ktcad.axis.toA3Values
import org.hnau.ktcad.axis.toVec2
import org.hnau.ktcad.axis.toVec3
import kotlin.test.Test
import kotlin.test.assertEquals

class AxisValuesExtTest {

    @Test
    fun arithmetic_combines_components() {
        val a = A3Values(
            x = 1.0,
            y = 2.0,
            z = 3.0,
        )
        val b = A3Values(
            x = 4.0,
            y = 5.0,
            z = 6.0,
        )
        assertEquals(
            expected = A3Values(
                x = 5.0,
                y = 7.0,
                z = 9.0,
            ),
            actual = a + b,
        )
        assertEquals(
            expected = A3Values(
                x = -3.0,
                y = -3.0,
                z = -3.0,
            ),
            actual = a - b,
        )
        assertEquals(
            expected = A3Values(
                x = 4.0,
                y = 10.0,
                z = 18.0,
            ),
            actual = a * b,
        )
        assertEquals(
            expected = A3Values(
                x = 2.0,
                y = 4.0,
                z = 6.0,
            ),
            actual = a * 2.0,
        )
    }

    @Test
    fun model_round_trip_preserves_the_value() {
        val v3 = A3Values(
            x = 1.0,
            y = 2.0,
            z = 3.0,
        )
        assertEquals(
            expected = v3,
            actual = v3.toVec3().toA3Values(),
        )

        val v2 = A2Values(
            x = 4.0,
            y = 5.0,
        )
        assertEquals(
            expected = v2,
            actual = v2.toVec2().toA2Values(),
        )
    }
}
