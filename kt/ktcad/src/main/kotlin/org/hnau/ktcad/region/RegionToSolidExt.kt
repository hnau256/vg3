package org.hnau.ktcad.region

import arrow.core.NonEmptyList
import org.hnau.ktcad.Region
import org.hnau.ktcad.Solid
import org.hnau.ktcad.extrude
import org.hnau.ktcad.fillet
import org.hnau.ktcad.ir.Continuity
import org.hnau.ktcad.ir.FilletKind
import org.hnau.ktcad.ir.Parametrization
import org.hnau.ktcad.ir.Path
import org.hnau.ktcad.ir.RadiusSpec
import org.hnau.ktcad.ir.SweepMode
import org.hnau.ktcad.ir.TransitionKind
import org.hnau.ktcad.loft
import org.hnau.ktcad.revolve
import org.hnau.ktcad.sweep

fun Region.extrude(
    height: Double,
): Solid = extrude(
    height = height,
    profile = this,
)

fun Region.revolve(
    angle: Double,
): Solid = revolve(
    angle = angle,
    profile = this,
)

fun Region.sweep(
    path: Path,
    mode: SweepMode = SweepMode.FOLLOW,
    transition: TransitionKind = TransitionKind.RIGHT_CORNER,
): Solid = sweep(
    mode = mode,
    path = path,
    profile = this,
    transition = transition,
)

fun NonEmptyList<Path>.loft(
    ruled: Boolean = false,
    smoothing: Boolean = false,
    continuity: Continuity? = null,
    parametrization: Parametrization? = null,
    maxDegree: Int? = null,
    skipCompatibility: Boolean = false,
): Solid = loft(
    ruled = ruled,
    sections = this,
    smoothing = smoothing,
    continuity = continuity,
    parametrization = parametrization,
    max_degree = maxDegree,
    skip_compatibility = skipCompatibility,
)
