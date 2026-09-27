package br.com.codecacto.kmplib.ui.components

import br.com.codecacto.kmplib.core.context.AndroidAppContext
import coil3.PlatformContext

internal actual fun defaultPhotoPlatformContext(): PlatformContext? = AndroidAppContext.get()
