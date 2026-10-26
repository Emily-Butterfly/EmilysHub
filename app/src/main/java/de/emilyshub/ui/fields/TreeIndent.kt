package de.emilyshub.ui.fields

import androidx.compose.ui.unit.Dp

/**
 * How far in an entry at [depth] in a tree, such as a goal or a folder, shows: [step] for each of the first levels, half
 * of it for each one deeper, so that each level still shows deeper while the name still fits.
 */
internal fun treeIndent(depth: Int, step: Dp): Dp = step * minOf(depth, FULL_STEPS) + step / 2 * maxOf(depth - FULL_STEPS, 0)

private const val FULL_STEPS = 4
