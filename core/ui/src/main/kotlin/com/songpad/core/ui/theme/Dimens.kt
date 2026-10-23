package com.songpad.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** §14 radii: sm 8 · tag 12 · md 16 · card 24 · lg 28 · sheet 32 · pill 999. */
object Radii {
    val sm = 8.dp
    val tag = 12.dp
    val md = 16.dp
    val card = 24.dp
    val lg = 28.dp
    val sheet = 32.dp
    val pill = 999.dp
}

val SongPadShapes = Shapes(
    extraSmall = RoundedCornerShape(Radii.sm),
    small = RoundedCornerShape(Radii.tag),
    medium = RoundedCornerShape(Radii.card),
    large = RoundedCornerShape(Radii.lg),
    extraLarge = RoundedCornerShape(Radii.sheet),
)

/** 4.4px prototype base mapped cleanly to dp. */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val cardGap = 11.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    val screen = 18.dp
}

object Dimens {
    /** Minimum interactive target (Phase 0 §17, spec §14). Every tappable component enforces this. */
    val minTouchTarget = 48.dp
    val recordFab = 70.dp
    val recordFabLarge = 80.dp
    val recordFabDetail = 62.dp
    val recordFabCompact = 44.dp
    val glassPillHeight = 56.dp
    val ribbonHeight = 56.dp
    val listItemMinHeight = 64.dp
    val waveformHeight = 78.dp
}
