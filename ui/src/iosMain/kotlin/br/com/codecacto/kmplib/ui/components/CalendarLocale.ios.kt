package br.com.codecacto.kmplib.ui.components

import androidx.compose.material3.CalendarLocale
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

/** Igual ao `defaultLocale()` do Material no iOS: o `NSLocale` atual do aparelho. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@ReadOnlyComposable
internal actual fun currentCalendarLocale(): CalendarLocale = NSLocale.currentLocale
