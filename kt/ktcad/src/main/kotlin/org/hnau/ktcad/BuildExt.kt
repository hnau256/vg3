package org.hnau.ktcad

import arrow.core.NonEmptyList
import org.hnau.ktcad.ir.FilletKind
import org.hnau.ktcad.ir.Path
import org.hnau.ktcad.ir.RadiusSpec
import org.hnau.ktcad.ir.SweepMode

/**
 * DSL sugar for turning planar regions into solids, and for `fillet`/`chamfer`.
 *
 * Every function maps to a generated factory (no new IR): `Region.extrude` → `extrude(...)`, etc.
 */

/** Extrude a region along `+Z` by [height] (`height > 0`). */
fun Region.extrude(height: Double): Solid = extrude(height = height, profile = this)

/** Revolve a region around the Y axis by [angle] (radians); the region must stay on one side. */
fun Region.revolve(angle: Double): Solid = revolve(angle = angle, profile = this)

/** Sweep a region along [path]; [mode] defaults to the engine's `follow`. */
fun Region.sweep(path: Path, mode: SweepMode? = null): Solid =
    sweep(mode = mode, path = path, profile = this)

/** Loft through [this] sections (`size >= 2`); [ruled] switches from smooth to ruled surfaces. */
fun NonEmptyList<Path>.loft(ruled: Boolean = false): Solid = loft(ruled = ruled, sections = this)

/** Fillet every edge with a constant [radius] (or chamfer with `kind = CHAMFER`). */
fun Solid.fillet(radius: Double, kind: FilletKind = FilletKind.FILLET): Solid =
    fillet(kind = kind, radius = RadiusSpec.All(radius = radius), target = this)

/** Fillet every edge with a per-edge Rhai [expression] (variable `edge`). */
fun Solid.fillet(expression: String, kind: FilletKind = FilletKind.FILLET): Solid =
    fillet(kind = kind, radius = RadiusSpec.Expression(expression = expression), target = this)

/** Fillet only the edges selected by a boolean [expression], with a constant [radius]. */
fun Solid.fillet(expression: String, radius: Double, kind: FilletKind = FilletKind.FILLET): Solid =
    fillet(kind = kind, radius = RadiusSpec.Selected(expression = expression, radius = radius), target = this)
