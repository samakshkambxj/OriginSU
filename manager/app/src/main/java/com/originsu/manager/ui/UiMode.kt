package com.originsu.manager.ui

import androidx.compose.runtime.staticCompositionLocalOf

enum class UiMode(val value: String) {
    Material("material"),
    Miuix("miuix");

    companion object {
        fun fromValue(value: String): UiMode = when (value) {
            Miuix.value -> Miuix
            else -> Material
        }

        val DEFAULT_VALUE = Material.value
    }
}

val LocalUiMode = staticCompositionLocalOf { UiMode.Material }
