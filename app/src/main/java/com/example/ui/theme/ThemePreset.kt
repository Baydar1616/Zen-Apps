package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class ThemePreset(
    val displayName: String,
    val skyColors: List<Color>,
    val waterColors: List<Color>,
    val rayColors: List<Color>,
    val sandColor: Color,
    val hillColor1: Color,
    val hillColor2: Color,
    val sunMoonColor: Color,
    val isNight: Boolean,
    val description: String,
    val primaryTextColor: Color
) {
    CLASSIC_ABYSSAL(
        displayName = "Classic Abyssal",
        skyColors = listOf(Color(0xFF1D3557), Color(0xFF457B9D), Color(0xFFA8DADC)),
        waterColors = listOf(Color(0xFF0077B6), Color(0xFF03045E)),
        rayColors = listOf(Color.White, Color.Transparent),
        sandColor = Color(0xFF1D2440),
        hillColor1 = Color(0xFF3F5E73),
        hillColor2 = Color(0xFF2C4352),
        sunMoonColor = Color(0xFFFFFDF0),
        isNight = false,
        description = "Deep peaceful ocean depths with silver morning rays.",
        primaryTextColor = Color(0xFF52B788)
    ),
    GHIBLI_SUNSET(
        displayName = "Ghibli Sunset",
        skyColors = listOf(Color(0xFFE76F51), Color(0xFFF4A261), Color(0xFFE9C46A)),
        waterColors = listOf(Color(0xFFCE5A21), Color(0xFF7A2E11), Color(0xFF381507)),
        rayColors = listOf(Color(0xFFFFEA7A), Color.Transparent),
        sandColor = Color(0xFF3E1202),
        hillColor1 = Color(0xFF7E3B6C),
        hillColor2 = Color(0xFF541F45),
        sunMoonColor = Color(0xFFFFF6B8),
        isNight = false,
        description = "Cinematic sunset, warm hills, glowing lanterns and Ghibli warmth.",
        primaryTextColor = Color(0xFFFF9F1C)
    ),
    MOSSY_EMERALD(
        displayName = "Mossy Emerald",
        skyColors = listOf(Color(0xFF1B4332), Color(0xFF2D6A4F), Color(0xFF52B788)),
        waterColors = listOf(Color(0xFF133F31), Color(0xFF071F17)),
        rayColors = listOf(Color(0xFFCCFF99), Color.Transparent),
        sandColor = Color(0xFF0D211A),
        hillColor1 = Color(0xFF1A3E2F),
        hillColor2 = Color(0xFF0B1F16),
        sunMoonColor = Color(0xFFE8F5E9),
        isNight = false,
        description = "Teal-emerald forest pond under a quiet, misty morning sky.",
        primaryTextColor = Color(0xFF74C69D)
    ),
    ASTRAL_MIDNIGHT(
        displayName = "Astral Midnight",
        skyColors = listOf(Color(0xFF0F0C20), Color(0xFF1D1A39), Color(0xFF312E5C)),
        waterColors = listOf(Color(0xFF23144B), Color(0xFF0D0621)),
        rayColors = listOf(Color(0xFF80FFE8), Color.Transparent),
        sandColor = Color(0xFF0C031A),
        hillColor1 = Color(0xFF1E1735),
        hillColor2 = Color(0xFF0D0A1C),
        sunMoonColor = Color(0xFFFFE57F),
        isNight = true,
        description = "Mystical cosmic purple waters under a glowing crescent moon and stars.",
        primaryTextColor = Color(0xFF9B5DE5)
    );
}
