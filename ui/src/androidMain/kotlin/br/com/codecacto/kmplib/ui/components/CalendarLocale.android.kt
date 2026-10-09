package br.com.codecacto.kmplib.ui.components

import androidx.compose.material3.CalendarLocale
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import java.util.Locale

/** Igual ao `defaultLocale()` do Material no Android: o 1º idioma da configuração da tela. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@ReadOnlyComposable
internal actual fun currentCalendarLocale(): CalendarLocale =
    ConfigurationCompat.getLocales(LocalConfiguration.current).get(0) ?: Locale.getDefault()
