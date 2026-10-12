package org.hnau.ktcad

import org.hnau.ktcad.axis.Axis2Values
import org.hnau.ktcad.axis.Axis3Values
import org.hnau.ktcad.axis.minus
import org.hnau.ktcad.axis.plus
import org.hnau.ktcad.axis.times
import org.hnau.ktcad.axis.toAxis2Values
import org.hnau.ktcad.axis.toAxis3Values
import org.hnau.ktcad.axis.toVec2
import org.hnau.ktcad.axis.toVec3
import kotlin.test.Test
import kotlin.test.assertEquals

class AxisValuesExtTest {

    @Test
    fun arithmetic_combines_components() {
        val a = Axis3Values(x = 1.0, y = 2.0, z = 3.0)
        val b = Axis3Values(x = 4.0, y = 5.0, z = 6.0)
        assertEquals(Axis3Values(x = 5.0, y = 7.0, z = 9.0), a + b)
        assertEquals(Axis3Values(x = -3.0, y = -3.0, z = -3.0), a - b)
        assertEquals(Axis3Values(x = 4.0, y = 10.0, z = 18.0), a * b)
        assertEquals(Axis3Values(x = 2.0, y = 4.0, z = 6.0), a * 2.0)
    }

    @Test
    fun model_round_trip_preserves_the_value() {
        val v3 = Axis3Values(x = 1.0, y = 2.0, z = 3.0)
        assertEquals(v3, v3.toVec3().toAxis3Values())

        val v2 = Axis2Values(x = 4.0, y = 5.0)
        assertEquals(v2, v2.toVec2().toAxis2Values())
    }
}
