package br.com.codecacto.kmplib.core.util

import kotlin.experimental.ExperimentalNativeApi

actual object BuildInfo {
    @OptIn(ExperimentalNativeApi::class)
    actual val isDebug: Boolean = Platform.isDebugBinary
}
