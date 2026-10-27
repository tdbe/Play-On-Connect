package com.tdbe.playonconnect.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- Font scale variables: adjust these to taste. ----
// Rules kept: smallest text == 16sp, steps between tiers >= 4sp,
// app title = old (22sp) + 8sp.
val FontSmallest = 16.sp // summaries, captions, small hints, eyebrow headers
val FontSmall = 20.sp    // secondary labels, button text
val FontMedium = 24.sp   // card titles, trigger/action type lines
val FontLarge = 30.sp    // screen / app title

// Set extra-small fonts for Material components that we don't display.
private val Unused = TextStyle(fontSize = FontSmallest)

val Typography = Typography(
    labelSmall = Unused,
    bodySmall = TextStyle(fontSize = FontSmallest),
    labelMedium = TextStyle(fontSize = FontSmallest),
    titleSmall = Unused,
    bodyMedium = TextStyle(fontSize = FontSmall),
    labelLarge = TextStyle(fontSize = FontSmall),
    titleMedium = TextStyle(fontSize = FontMedium),
    bodyLarge = TextStyle(fontSize = FontMedium),
    titleLarge = TextStyle(fontSize = FontLarge),
)
