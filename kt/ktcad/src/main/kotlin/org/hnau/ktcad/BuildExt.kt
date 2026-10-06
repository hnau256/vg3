package org.hnau.ktcad

import arrow.core.NonEmptyList
import org.hnau.ktcad.ir.Continuity
import org.hnau.ktcad.ir.FilletKind
import org.hnau.ktcad.ir.Parametrization
import org.hnau.ktcad.ir.Path
import org.hnau.ktcad.ir.RadiusSpec
import org.hnau.ktcad.ir.SweepMode
import org.hnau.ktcad.ir.TransitionKind

/**
 * DSL sugar for turning planar regions into solids, and for `fillet`/`chamfer`.
 *
 * Every function maps to a generated factory (no new IR): `Region.extrude` → `extrude(...)`, etc.
 */

/** Extrude a region along `+Z` by [height] (`height > 0`). */
fun Region.extrude(height: Double): Solid = extrude(height = height, profile = this)

/** Revolve a region around the Y axis by [angle] (radians); the region must stay on one side. */
fun Region.revolve(angle: Double): Solid = revolve(angle = angle, profile = this)

/**
 * Sweep a region along [path]; [mode] defaults to the engine's `follow`, [transition] to the
 * right-corner join at spine fractures.
 */
fun Region.sweep(
    path: Path,
    mode: SweepMode = SweepMode.FOLLOW,
    transition: TransitionKind = TransitionKind.RIGHT_CORNER,
): Solid = sweep(mode = mode, path = path, profile = this, transition = transition)

/**
 * Loft through [this] sections (`size >= 2`). [ruled] switches from smooth to ruled surfaces; the
 * rest tune the approximation (`smoothing`, `continuity`, `parametrization`, `maxDegree`), and
 * [skipCompatibility] disables the automatic section orientation check (OCCT checks by default).
 */
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

/** Fillet every edge with a constant [radius] (`RadiusSpec.All`; chamfer with `kind = CHAMFER`). */
fun Solid.filletAll(radius: Double, kind: FilletKind = FilletKind.FILLET): Solid =
    fillet(kind = kind, radius = RadiusSpec.All(radius = radius), target = this)

/** Fillet every edge with a per-edge Rhai [expression] returning the radius (`RadiusSpec.Expression`). */
fun Solid.filletExpression(expression: String, kind: FilletKind = FilletKind.FILLET): Solid =
    fillet(kind = kind, radius = RadiusSpec.Expression(expression = expression), target = this)

/** Fillet only the edges selected by a boolean [expression], with a constant [radius] (`RadiusSpec.Selected`). */
fun Solid.filletSelected(expression: String, radius: Double, kind: FilletKind = FilletKind.FILLET): Solid =
    fillet(kind = kind, radius = RadiusSpec.Selected(expression = expression, radius = radius), target = this)
