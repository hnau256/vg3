package org.hnau.ktcad.region

import org.hnau.ktcad.Region
import org.hnau.ktcad.ir.RadiusSpec

fun Region.fillet2d(
    radius: RadiusSpec,
): Region = Region.Fillet2d(
    target = this,
    radius = radius,
)

fun Region.fillet2dAll(
    radius: Double,
): Region = fillet2d(
    radius = RadiusSpec.All(
        radius = radius,
    ),
)

fun Region.fillet2dExpression(
    resolveEdgeRadiusExpression: String,
): Region = fillet2d(
    radius = RadiusSpec.Expression(
        expression = resolveEdgeRadiusExpression,
    ),
)

fun Region.fillet2dSelected(
    selectEdgeExpression: String,
    radius: Double,
): Region = fillet2d(
    radius = RadiusSpec.Selected(
        expression = selectEdgeExpression,
        radius = radius,
    ),
)