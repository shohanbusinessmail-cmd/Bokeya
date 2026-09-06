package com.shohan.bokeya.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Bokeya's colour system.
 *
 * The identity is a deep midnight-navy paired with a single emerald accent —
 * navy reads as trustworthy and bank-like, emerald signals money cleared. Only
 * these two hues carry brand weight; everything else is a neutral or a
 * semantic status colour, which is what keeps the UI from looking childish.
 *
 * Status colours are deliberately muted (no pure #FF0000): an overdue debt is
 * stressful enough without the interface shouting.
 */

// ---- Brand ----------------------------------------------------------------
val Navy900 = Color(0xFF0B1220)
val Navy800 = Color(0xFF0E1A2B)
val Navy700 = Color(0xFF152438)
val Navy600 = Color(0xFF1E3149)
val Navy500 = Color(0xFF2B415C)

val Emerald50 = Color(0xFFECFDF5)
val Emerald100 = Color(0xFFD1FAE5)
val Emerald200 = Color(0xFFA7F3D0)
val Emerald300 = Color(0xFF6EE7B7)
val Emerald400 = Color(0xFF34D399)
val Emerald500 = Color(0xFF10B981)
val Emerald600 = Color(0xFF059669)
val Emerald700 = Color(0xFF047857)
val Emerald800 = Color(0xFF065F46)
val Emerald900 = Color(0xFF064E3B)

// ---- Neutrals -------------------------------------------------------------
val Slate50 = Color(0xFFF8FAFC)
val Slate100 = Color(0xFFF1F5F9)
val Slate200 = Color(0xFFE2E8F0)
val Slate300 = Color(0xFFCBD5E1)
val Slate400 = Color(0xFF94A3B8)
val Slate500 = Color(0xFF64748B)
val Slate600 = Color(0xFF475569)
val Slate700 = Color(0xFF334155)
val Slate800 = Color(0xFF1E293B)
val Slate900 = Color(0xFF0F172A)

// ---- Semantic status ------------------------------------------------------
val WarnAmber = Color(0xFFD97706)
val WarnAmberLight = Color(0xFFFEF3C7)
val WarnAmberDark = Color(0xFFFBBF24)

val DangerRose = Color(0xFFBE123C)
val DangerRoseLight = Color(0xFFFFE4E6)
val DangerRoseDark = Color(0xFFFB7185)

val InfoBlue = Color(0xFF2563EB)
val InfoBlueLight = Color(0xFFDBEAFE)
val InfoBlueDark = Color(0xFF60A5FA)

val SuccessGreen = Color(0xFF059669)
val SuccessGreenLight = Color(0xFFD1FAE5)
val SuccessGreenDark = Color(0xFF34D399)

// ---- Account type accents -------------------------------------------------
// Each debt category gets a stable hue so users recognise them at a glance.
val ShopViolet = Color(0xFF7C3AED)
val ShopVioletDark = Color(0xFFA78BFA)
val LoanBlue = Color(0xFF2563EB)
val LoanBlueDark = Color(0xFF60A5FA)
val EmiTeal = Color(0xFF0D9488)
val EmiTealDark = Color(0xFF2DD4BF)
val PersonAmber = Color(0xFFD97706)
val PersonAmberDark = Color(0xFFFBBF24)
