package br.com.codecacto.kmplib.platform.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Preferência de acessibilidade **"reduzir movimento"** do sistema — o par mobile do
 * `prefers-reduced-motion` da web. Desde 2.228.0.
 *
 * - **Android:** "Remover animações" (Acessibilidade) — que zera as escalas de animação do sistema —
 *   ou as escalas zeradas à mão nas Opções do desenvolvedor. Lido em
 *   `Settings.Global.ANIMATOR_DURATION_SCALE` e `TRANSITION_ANIMATION_SCALE`: qualquer uma em `0`
 *   liga a preferência. Mudança acompanhada por `ContentObserver` nas duas chaves.
 * - **iOS:** Ajustes → Acessibilidade → Movimento → **Reduzir Movimento**
 *   (`UIAccessibilityIsReduceMotionEnabled()`), acompanhado por
 *   `UIAccessibilityReduceMotionStatusDidChangeNotification`.
 *
 * ## O que o app faz com isso
 *
 * Troca o movimento **decorativo** (parallax, partícula, lua girando, transição que desliza) por um
 * corte seco ou um *fade* curto — **sem esconder informação**: o que a animação comunicava continua
 * na tela. Não é "desligar a tela bonita", é tirar o que pode causar enjoo (distúrbio vestibular).
 *
 * ⚠️ No Android, as animações do Compose (`animate*AsState`, `AnimatedVisibility`) **já** respeitam a
 * escala do animador: com ela em 0 terminam na hora. **Não** respeitam: animação infinita feita com
 * `withFrameNanos`/`Canvas` e **nada** no iOS — é para esses casos, e para escolher uma alternativa
 * em vez de só acelerar, que esta preferência existe.
 *
 * ## Como ler
 *
 * - Numa tela: `val reduzir by rememberReduceMotion()`.
 * - Em qualquer lugar da árvore: [LocalReduceMotion] — o `AppTheme` (`kmplib-ui`) já o provê, então
 *   todo app da fábrica tem o valor vivo sem configurar nada. Fora do `AppTheme`, [ProvideReduceMotion].
 * - Fora do Compose (ViewModel): [reduceMotionChanges].
 */
expect fun isReduceMotionEnabled(): Boolean

/**
 * Fluxo da preferência: emite o valor atual ao coletar e depois cada mudança (sem repetir o mesmo
 * valor). O observador do sistema existe só enquanto houver coletor.
 *
 * No Android, sem `initKmpLibPlatform(context)`/`KmpLib.init`, emite `false` e não observa nada.
 */
fun reduceMotionChanges(): Flow<Boolean> = platformReduceMotionChanges().distinctUntilChanged()

internal expect fun platformReduceMotionChanges(): Flow<Boolean>

/**
 * A preferência como [State] da composição, atualizada ao vivo (a pessoa liga "Reduzir movimento"
 * com o app aberto, volta, e a tela já responde).
 */
@Composable
fun rememberReduceMotion(): State<Boolean> {
    val flow = remember { reduceMotionChanges() }
    val initial = remember { isReduceMotionEnabled() }
    return flow.collectAsState(initial = initial)
}

/**
 * `true` quando o sistema pede movimento reduzido. Default `false` fora de um provedor — o `AppTheme`
 * da `kmplib-ui` já provê o valor real; [ProvideReduceMotion] para árvores sem ele.
 */
val LocalReduceMotion = compositionLocalOf { false }

/** Provê [LocalReduceMotion] com o valor vivo do sistema para [content]. */
@Composable
fun ProvideReduceMotion(content: @Composable () -> Unit) {
    val reduce by rememberReduceMotion()
    CompositionLocalProvider(LocalReduceMotion provides reduce, content = content)
}

/**
 * Regra do Android, pura: "reduzir movimento" quando o animador **ou** as transições estão em escala
 * zero. `null` (chave ausente/ilegível) conta como a escala padrão, 1.
 */
internal fun reduceMotionFromAnimationScales(animatorScale: Float?, transitionScale: Float?): Boolean =
    (animatorScale ?: 1f) == 0f || (transitionScale ?: 1f) == 0f
