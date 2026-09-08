package com.songpad.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.songpad.core.ui.R

/** Prototype fonts: Figtree (body, variable weight) and Caprasimo (display). Both OFL, bundled in res/font. */
object SongPadFonts {
    val body: FontFamily = FontFamily(
        Font(R.font.figtree_variable, FontWeight.Normal),
        Font(R.font.figtree_variable, FontWeight.Medium),
        Font(R.font.figtree_variable, FontWeight.SemiBold),
        Font(R.font.figtree_variable, FontWeight.Bold),
    )
    val heading: FontFamily = FontFamily(Font(R.font.caprasimo_regular, FontWeight.Normal))
}

/** Consistent hierarchy (Phase 0 §17). sp everywhere so large type scales; previews assert at 1.3× (see preview harness). */
val SongPadTypography = Typography(
    displayLarge = TextStyle(fontFamily = SongPadFonts.heading, fontSize = 40.sp, lineHeight = 46.sp),
    displayMedium = TextStyle(fontFamily = SongPadFonts.heading, fontSize = 32.sp, lineHeight = 38.sp),
    headlineLarge = TextStyle(fontFamily = SongPadFonts.heading, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = SongPadFonts.heading, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = SongPadFonts.heading, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = SongPadFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = SongPadFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = SongPadFonts.body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = SongPadFonts.body, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = SongPadFonts.body, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = SongPadFonts.body, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = SongPadFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = SongPadFonts.body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = SongPadFonts.body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)
