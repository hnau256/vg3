package org.hnau.ktcad.region

import org.hnau.ktcad.Region
import org.hnau.ktcad.ir.RadiusSpec

fun Region.fillet2dAll(
    radius: Double,
): Region = Region.Fillet2d(
    target = this,
    radius = RadiusSpec.All(
        radius = radius,
    ),
)

fun Region.fillet2dExpression(
    expression: String,
): Region = Region.Fillet2d(
    target = this,
    radius = RadiusSpec.Expression(
        expression = expression,
    ),
)

fun Region.fillet2dSelected(
    expression: String,
    radius: Double,
): Region = Region.Fillet2d(
    target = this,
    radius = RadiusSpec.Selected(
        expression = expression,
        radius = radius,
    ),
)