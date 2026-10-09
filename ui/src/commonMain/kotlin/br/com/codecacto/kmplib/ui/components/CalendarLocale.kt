package br.com.codecacto.kmplib.ui.components

import androidx.compose.material3.CalendarLocale
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * O idioma/região do calendário — o mesmo que o `rememberDatePickerState` do Material lê por dentro
 * (o `defaultLocale()` dele é `internal`). Existe para o [AppDatePickerDialog] efêmero (2.271.0)
 * montar o `DatePickerState(locale, …)` sem passar pelo `rememberSaveable`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@ReadOnlyComposable
internal expect fun currentCalendarLocale(): CalendarLocale
