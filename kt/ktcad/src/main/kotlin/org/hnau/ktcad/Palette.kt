package org.hnau.ktcad

import org.hnau.ktcad.ir.Color

/**
 * Deterministic preview colors from the elementary OS brand palette (base "500" shades).
 *
 * A `Part` without an explicit color gets `PALETTE[floorMod(name.hashCode(), PALETTE.size)]`, so
 * distinct names usually get distinct colors and the mapping is stable across runs and orderings.
 * The palette order is part of the contract: reordering changes every preview.
 */
internal val PALETTE: List<Color> = listOf(
    0xC6262E, // Strawberry
    0xF37329, // Orange
    0xF9C440, // Banana
    0x68B723, // Lime
    0x28BCA3, // Mint
    0x3689E6, // Blueberry
    0xA56DE2, // Grape
    0xDE3E80, // Bubblegum
    0xCFA25E, // Latte
    0x715344, // Cocoa
    0x485A6C, // Slate
).map(::colorFromRgbHex)

/** The palette color for [name] (`floorMod` keeps negative hashes inside the palette). */
internal fun paletteColor(name: String): Color =
    PALETTE[Math.floorMod(name.hashCode(), PALETTE.size)]

private fun colorFromRgbHex(hex: Int): Color = Color(
    r = ((hex shr 16) and 0xFF) / 255.0,
    g = ((hex shr 8) and 0xFF) / 255.0,
    b = (hex and 0xFF) / 255.0,
)
