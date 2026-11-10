package br.com.codecacto.kmplib.platform.motion

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// Desktop (2.353.0): a JVM não lê a preferência de animação do SO ("Efeitos de animação" do Windows).
// `false` = animação normal; o app pode prover `LocalReduceMotion` se tiver a preferência própria.
actual fun isReduceMotionEnabled(): Boolean = false

internal actual fun platformReduceMotionChanges(): Flow<Boolean> = flowOf(false)
