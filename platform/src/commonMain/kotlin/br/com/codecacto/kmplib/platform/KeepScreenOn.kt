package br.com.codecacto.kmplib.platform

import androidx.compose.runtime.Composable

/**
 * **Impede a tela de apagar sozinha** enquanto esta parte da interface estiver na composição.
 *
 * Existe para as telas em que apagar a tela **desliga o controle no meio do uso**: lanterna acesa,
 * tela-como-luz, leitura de código de barras, cronômetro em andamento.
 *
 * A liberação é garantida pelo próprio Compose: ao sair da composição (navegou, fechou o modo), a
 * trava cai no `onDispose`. É de propósito que **não exista uma versão imperativa** —
 * `acquire()`/`release()` soltos são exatamente como se esquece a tela acesa a viagem inteira,
 * queimando a bateria de quem confiou no app.
 *
 * ```kotlin
 * if (state.isOn) KeepScreenOn()
 * // ou, sem `if` na árvore:
 * KeepScreenOn(enabled = state.isOn)
 * ```
 *
 * Implementação (padrão-ouro de cada plataforma):
 * - **Android:** `View.keepScreenOn` — o caminho recomendado pelo próprio Android, preferível a um
 *   `PowerManager.WakeLock` (que exige permissão, sobrevive à tela e vaza quando alguém esquece de
 *   soltar).
 * - **iOS:** `UIApplication.isIdleTimerDisabled`.
 *
 * @param enabled `false` devolve o comportamento normal do aparelho sem tirar o componente da tela.
 */
@Composable
expect fun KeepScreenOn(enabled: Boolean = true)
