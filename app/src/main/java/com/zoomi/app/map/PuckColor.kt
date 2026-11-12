package com.zoomi.app.map

/**
 * Colours for the position arrow and its accuracy circle. Every colour has the same OKLCH lightness as
 * Zoomi's violet (0.55 on the light map, 0.67 on the dark map), so they all stand out equally.
 */
enum class PuckColor(val label: String, private val light: Long, private val dark: Long) {
    VIOLET("Violet", 0xFF5B4BFF, 0xFF8C80FF),
    BLUE("Blue", 0xFF006FD7, 0xFF3E95FE),
    TEAL("Teal", 0xFF028187, 0xFF0EAAB0),
    GREEN("Green", 0xFF01893D, 0xFF0CB354),
    AMBER("Amber", 0xFF9D6300, 0xFFCD8305),
    RED("Red", 0xFFD50317, 0xFFF15E54),
    PINK("Pink", 0xFFC80184, 0xFFE35DA5);

    fun argb(dark: Boolean): Int = (if (dark) this.dark else light).toInt()
}
