package br.com.codecacto.kmplib.platform.brand

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * Desktop: a JVM não tem "ícone do app" registrado no SO — quem define é o próprio app, no `Window(icon = …)`.
 * `null` = a marca cai no nome, como em qualquer plataforma sem ícone.
 */
@Composable
actual fun rememberAppIconPainter(): Painter? = null
