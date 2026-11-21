package com.v2ray.ang.nox

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class NoxColors(
    val bgTop: Color, val bgBottom: Color, val accent: Color, val text: Color,
    val sub: Color, val card: Color, val cardBorder: Color, val star: Color, val off: Color
)

val NightColors = NoxColors(
    Color(0xFF0B0510), Color(0xFF2E0F2B), Color(0xFFFF5CAD), Color(0xFFF6E9F2),
    Color(0xFFB69AAE), Color(0x1AFFFFFF), Color(0x26FFFFFF), Color(0xFFFFFFFF), Color(0xFF8A7585)
)
val DayColors = NoxColors(
    Color(0xFF7CC4FF), Color(0xFFFFF1D6), Color(0xFFFF9F1C), Color(0xFF33260F),
    Color(0xFF7A6540), Color(0x99FFFFFF), Color(0xCCFFFFFF), Color(0xFFFFD36B), Color(0xFFB9C4CC)
)
val LocalNox = staticCompositionLocalOf { NightColors }

object NoxPrefs {
    private fun sp(c: Context) = c.getSharedPreferences("nox", Context.MODE_PRIVATE)
    fun night(c: Context) = sp(c).getBoolean("night", true)
    fun setNight(c: Context, v: Boolean) = sp(c).edit().putBoolean("night", v).apply()
    fun stars(c: Context) = sp(c).getBoolean("stars", true)
    fun setStars(c: Context, v: Boolean) = sp(c).edit().putBoolean("stars", v).apply()
    fun timeoutSec(c: Context) = sp(c).getInt("timeout", 3)
    fun setTimeoutSec(c: Context, v: Int) = sp(c).edit().putInt("timeout", v).apply()
    fun sortByPing(c: Context) = sp(c).getBoolean("sort", true)
    fun setSortByPing(c: Context, v: Boolean) = sp(c).edit().putBoolean("sort", v).apply()
    fun autoUpdate(c: Context) = sp(c).getBoolean("autoupd", true)
    fun setAutoUpdate(c: Context, v: Boolean) = sp(c).edit().putBoolean("autoupd", v).apply()
    fun favs(c: Context): Set<String> = sp(c).getStringSet("favs", emptySet()) ?: emptySet()
    fun setFavs(c: Context, v: Set<String>) = sp(c).edit().putStringSet("favs", v).apply()
}
