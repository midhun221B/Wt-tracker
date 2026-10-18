package wt.app.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** True on a phone that follows someone: every add, edit and delete control is hidden. */
val LocalReadOnly = staticCompositionLocalOf { false }
