package org.hnau.ktcad.solid

import org.hnau.ktcad.Solid
import org.hnau.ktcad.fillet
import org.hnau.ktcad.ir.FilletKind
import org.hnau.ktcad.ir.RadiusSpec

fun Solid.filletAll(
    radius: Double,
    kind: FilletKind = FilletKind.FILLET,
): Solid = fillet(
    kind = kind,
    radius = RadiusSpec.All(
        radius = radius,
    ),
    target = this,
)

fun Solid.filletExpression(
    resolveEdgeRadiusExpression: String,
    kind: FilletKind = FilletKind.FILLET,
): Solid = fillet(
    kind = kind,
    radius = RadiusSpec.Expression(
        expression = resolveEdgeRadiusExpression,
    ),
    target = this,
)

fun Solid.filletSelected(
    selectEdgeExpression: String,
    radius: Double,
    kind: FilletKind = FilletKind.FILLET,
): Solid = fillet(
    kind = kind,
    radius = RadiusSpec.Selected(
        expression = selectEdgeExpression,
        radius = radius,
    ),
    target = this,
)