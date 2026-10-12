package org.hnau.ktcad.solid

import org.hnau.ktcad.Solid
import org.hnau.ktcad.fillet
import org.hnau.ktcad.ir.FilletKind
import org.hnau.ktcad.ir.RadiusSpec

fun Solid.filletAll(
    radius: Double,
    kind: FilletKind = FilletKind.FILLET,
): Solid =
    fillet(
        kind = kind,
        radius = RadiusSpec.All(radius = radius),
        target = this,
    )

/** Fillet every edge with a per-edge Rhai [expression] returning the radius (`RadiusSpec.Expression`). */
fun Solid.filletExpression(
    expression: String,
    kind: FilletKind = FilletKind.FILLET,
): Solid =
    fillet(
        kind = kind,
        radius = RadiusSpec.Expression(expression = expression),
        target = this,
    )

fun Solid.filletSelected(
    expression: String,
    radius: Double,
    kind: FilletKind = FilletKind.FILLET,
): Solid =
    fillet(
        kind = kind,
        radius = RadiusSpec.Selected(
            expression = expression,
            radius = radius,
        ),
        target = this,
    )