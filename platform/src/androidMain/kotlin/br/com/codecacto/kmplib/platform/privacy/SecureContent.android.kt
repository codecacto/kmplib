package br.com.codecacto.kmplib.platform.privacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

// O FLAG_SECURE (via HideFromRecents) já faz o sistema bloquear a captura: nada a cobrir.
@Composable
internal actual fun rememberScreenCaptured(): State<Boolean> = remember { mutableStateOf(false) }
