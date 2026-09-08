package com.songpad.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** §14 radii: sm 8 · md 16 · lg 28 · pill 999. */
object Radii {
    val sm = 8.dp
    val md = 16.dp
    val lg = 28.dp
    val pill = 999.dp
}

val SongPadShapes = Shapes(
    extraSmall = RoundedCornerShape(Radii.sm),
    small = RoundedCornerShape(Radii.sm),
    medium = RoundedCornerShape(Radii.md),
    large = RoundedCornerShape(Radii.lg),
    extraLarge = RoundedCornerShape(Radii.lg),
)

/** 4dp scale (the prototype's 4.4px base rounded to the dp grid). */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    val screen = 20.dp
}

object Dimens {
    /** Minimum interactive target (Phase 0 §17, spec §14). Every tappable component enforces this. */
    val minTouchTarget = 48.dp
    val recordFab = 72.dp
    val recordFabCompact = 56.dp
    val glassPillHeight = 64.dp
    val ribbonHeight = 72.dp
    val listItemMinHeight = 64.dp
    val waveformHeight = 96.dp
}
