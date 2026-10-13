package org.hnau.ktcad.solid

import arrow.core.nonEmptyListOf
import arrow.core.toNonEmptyListOrThrow
import arrow.core.toNonEmptySetOrNull
import org.hnau.commons.kotlin.foldBoolean
import org.hnau.commons.kotlin.foldNullable
import org.hnau.ktcad.Lowering
import org.hnau.ktcad.Solid
import org.hnau.ktcad.axis.A3
import org.hnau.ktcad.axis.A3Values
import org.hnau.ktcad.axis.direction
import org.hnau.ktcad.axis.div
import org.hnau.ktcad.axis.times
import org.hnau.ktcad.axis.toVec3
import org.hnau.ktcad.axis.unaryMinus
import org.hnau.ktcad.bbox.length
import org.hnau.ktcad.box
import org.hnau.ktcad.cylinder
import org.hnau.ktcad.ir.Vec3
import org.hnau.ktcad.path.helix
import org.hnau.ktcad.polyhedron

fun box(
    size: A3Values<Double>,
    centered: Set<A3> = emptySet(),
): Solid {
    val box = box(
        width = size[A3.X],
        length = size[A3.Y],
        height = size[A3.Z],
    )
    return centered
        .toNonEmptySetOrNull()
        .foldNullable(
            ifNull = { box },
            ifNotNull = { nonEmptyCentered ->
                box.translate(
                    size.map { a3, value ->
                        (a3 in nonEmptyCentered).foldBoolean(
                            ifTrue = { -value / 2 },
                            ifFalse = { 0.0 }
                        )
                    }
                )
            }
        )
}

fun box(
    bounds: A3Values<ClosedRange<Double>>,
): Solid = box(
    size = bounds
        .map(ClosedRange<Double>::length)
).translate(
    value = bounds
        .map(ClosedRange<Double>::start)
)

fun cylinder(
    radius: Double,
    height: Double,
    centerZ: Boolean,
    angle: Double? = null,
): Solid {
    val cylinder = cylinder(
        radius = radius,
        height = height,
        angle = angle,
    )
    return centerZ.foldBoolean(
        ifFalse = { cylinder },
        ifTrue = { cylinder.translate(-A3.Z.direction * height / 2.0) }
    )
}

fun cylinder(
    radius: Double,
    zRange: ClosedRange<Double>,
    angle: Double? = null,
): Solid = cylinder(
    radius = radius,
    height = zRange.length,
    angle = angle,
).translate(
    value = A3.Z.direction * zRange.start,
)

data class PolyhedronFace(
    val first: A3Values<Double>,
    val second: A3Values<Double>,
    val other: List<A3Values<Double>>,
) {

    constructor(
        first: A3Values<Double>,
        second: A3Values<Double>,
        vararg other: A3Values<Double>,
    ) : this(
        first = first,
        second = second,
        other = other.toList(),
    )
}

fun polyhedron(
    firstFace: PolyhedronFace,
    secondFace: PolyhedronFace,
    otherFaces: List<PolyhedronFace>,
): Solid {
    val points = Lowering<Vec3, Int, Vec3>(wrap = { it }) { point, _ -> point }
    val faceIndices = nonEmptyListOf(firstFace, secondFace)
        .plus(otherFaces)
        .map { face ->
            nonEmptyListOf(face.first, face.second)
                .plus(face.other)
                .map(A3Values<Double>::toVec3)
                .map(points::indexOf)
        }
    return polyhedron(
        points = points.nodes.toNonEmptyListOrThrow(),
        faces = faceIndices,
    )
}

fun polyhedron(
    firstFace: PolyhedronFace,
    secondFace: PolyhedronFace,
    vararg otherFaces: PolyhedronFace,
): Solid = polyhedron(
    firstFace = firstFace,
    secondFace = secondFace,
    otherFaces = otherFaces.toList(),
)
