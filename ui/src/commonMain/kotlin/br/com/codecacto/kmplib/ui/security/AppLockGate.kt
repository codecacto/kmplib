package br.com.codecacto.kmplib.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import br.com.codecacto.kmplib.platform.BiometricAuth
import br.com.codecacto.kmplib.platform.getBiometricAuth
import br.com.codecacto.kmplib.platform.privacy.HideFromRecents
import br.com.codecacto.kmplib.ui.components.AppButton
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Textos do portão de bloqueio (i18n; defaults pt-BR).
 *
 * ⚠️ **A tela de bloqueio não explica nada.** Nada de "seus dados estão protegidos", nada de nome
 * de módulo, nada de recado ao usuário: quem está com o aparelho na mão pode não ser o dono, e a
 * própria frase entrega do que o app trata. Por isso só existe rótulo de botão e o texto que o
 * SISTEMA exige no diálogo de biometria — nenhum título na tela.
 */
data class AppLockTexts(
    /** Rótulo do único botão da tela de bloqueio. */
    val unlockButton: String = "Desbloquear",
    /** Título do diálogo do sistema (Android exige um; iOS usa como motivo). */
    val promptTitle: String = "Desbloquear",
    /** Subtítulo do diálogo do sistema. Vazio = o sistema mostra só o título. */
    val promptSubtitle: String = "",
)

/**
 * **Portão de bloqueio do app** — a segunda metade do modo discreto: ao voltar do segundo plano
 * depois de [graceMillis], cobre o app e só descobre com **biometria ou a trava de tela**.
 *
 * Envolva o conteúdo do app no root, dentro do `AppTheme`:
 * ```kotlin
 * AppTheme {
 *     AppLockGate(enabled = ajustes.modoDiscreto) {
 *         AppNavHost()
 *     }
 * }
 * ```
 *
 * O que ele faz, e por que assim:
 * - **Nasce trancado.** Processo novo = ninguém provou quem é. Rotação **não** tranca (ver
 *   [AppLockSession]).
 * - **Esconde da multitarefa junto** ([hideFromRecents], ligado por default quando o portão está
 *   ligado): trancar sem esconder deixa a foto da última tela visível no seletor de apps, que é
 *   metade do que se queria proteger.
 * - **O conteúdo continua composto por baixo da cobertura**, opaca e que engole todo toque. Tirar
 *   o conteúdo da composição perderia a pilha de navegação: a pessoa destravaria e cairia na tela
 *   inicial, como se o app tivesse esquecido onde ela estava.
 * - **Pede a digital sozinho** ao aparecer, e o botão fica para quem cancelou ou errou.
 * - **Aparelho sem biometria E sem trava de tela abre.** Não há o que conferir ali, e manter a
 *   cobertura só entregaria um app que não abre mais. A proteção da multitarefa continua valendo.
 * - Ligar o modo no meio do uso **não tranca na hora** — quem acabou de ligar está com o app na
 *   mão; a trava passa a valer na próxima volta do segundo plano.
 *
 * @param enabled o interruptor do modo discreto (normalmente vindo das preferências do usuário).
 *   `false` desenha só o conteúdo.
 * @param graceMillis folga desde que o app foi para segundo plano. Default 60 s — sair para copiar
 *   um código do SMS não deve pedir digital na volta. `0` tranca em qualquer saída.
 * @param hideFromRecents também aplica `FLAG_SECURE`/desfoque (ver
 *   [br.com.codecacto.kmplib.platform.privacy.HideFromRecents]). ⚠️ No Android isso **bloqueia
 *   print e gravação de tela** — efeito desejado num app de dado sensível, mas o usuário precisa
 *   saber pela sua tela de ajustes.
 * @param texts textos (i18n; defaults pt-BR).
 * @param mark o que aparece na tela de bloqueio. Default: um cadeado. Pode receber a logo do app —
 *   **e nada além dela**.
 * @param onUnlockFailed erro do autenticador (não é chamado quando o usuário cancela). Serve para
 *   telemetria; a tela não muda.
 * @param content o app.
 */
@Composable
fun AppLockGate(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    graceMillis: Long = 60_000L,
    hideFromRecents: Boolean = enabled,
    texts: AppLockTexts = AppLockTexts(),
    mark: @Composable () -> Unit = { DefaultLockMark() },
    onUnlockFailed: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val biometric: BiometricAuth = remember { getBiometricAuth() }
    AppLockGate(
        enabled = enabled,
        biometric = biometric,
        modifier = modifier,
        graceMillis = graceMillis,
        hideFromRecents = hideFromRecents,
        texts = texts,
        mark = mark,
        onUnlockFailed = onUnlockFailed,
        content = content,
    )
}

/**
 * Overload com [BiometricAuth] explícito — para o app que já tem um no Koin, e para dublê de teste.
 */
@OptIn(ExperimentalTime::class)
@Composable
fun AppLockGate(
    enabled: Boolean,
    biometric: BiometricAuth,
    modifier: Modifier = Modifier,
    graceMillis: Long = 60_000L,
    hideFromRecents: Boolean = enabled,
    texts: AppLockTexts = AppLockTexts(),
    mark: @Composable () -> Unit = { DefaultLockMark() },
    onUnlockFailed: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (hideFromRecents) HideFromRecents()

    val locked = enabled && AppLockSession.isLocked
    val onFailed by rememberUpdatedState(onUnlockFailed)
    // Enquanto o diálogo do sistema está aberto, não adianta abrir outro: no Android o segundo
    // `authenticate` derruba o primeiro com ERROR_CANCELED, e o usuário vê o leitor sumir sozinho.
    var prompting by remember { mutableStateOf(false) }

    val requestUnlock: () -> Unit = requestUnlock@{
        if (prompting) return@requestUnlock
        prompting = true
        biometric.authenticate(
            title = texts.promptTitle,
            subtitle = texts.promptSubtitle,
            allowDeviceCredential = true,
            onSuccess = {
                prompting = false
                AppLockSession.unlock()
            },
            onError = {
                prompting = false
                onFailed?.invoke()
            },
            onCancel = { prompting = false },
        )
    }

    // Ligar o modo com o app na mão não tranca — quem acabou de ligar o interruptor está aqui.
    LaunchedEffect(enabled) { if (!enabled) AppLockSession.unlock() }

    // ON_STOP/ON_START, e não ON_PAUSE/ON_RESUME: o próprio diálogo do sistema, a barra de
    // notificações puxada e a caixa de permissão pausam a tela sem que o app tenha saído — com
    // ON_PAUSE o portão se trancaria sozinho no meio do uso, inclusive por cima da biometria.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        AppLockSession.onBackground(Clock.System.now().toEpochMilliseconds())
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (enabled) AppLockSession.onForeground(Clock.System.now().toEpochMilliseconds(), graceMillis)
    }

    LaunchedEffect(locked) {
        if (!locked) return@LaunchedEffect
        // Aparelho sem biometria e sem trava de tela: não existe o que conferir, e insistir só
        // deixaria o dono do aparelho para fora do próprio app.
        if (!biometric.isDeviceSecured()) {
            AppLockSession.unlock()
            return@LaunchedEffect
        }
        requestUnlock()
    }

    Box(modifier.fillMaxSize()) {
        content()
        if (locked) {
            AppLockCover(
                texts = texts,
                mark = mark,
                onUnlock = requestUnlock,
            )
        }
    }
}

/**
 * A cobertura: opaca, ocupa tudo e **engole o toque no passe `Initial`**, antes que qualquer coisa
 * embaixo dela possa reagir. Sem isso, o conteúdo continuaria rolando e recebendo cliques debaixo
 * de uma tela que o usuário acha que está trancada.
 */
@Composable
private fun AppLockCover(
    texts: AppLockTexts,
    mark: @Composable () -> Unit,
    onUnlock: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            mark()
            AppButton(
                text = texts.unlockButton,
                onClick = onUnlock,
                modifier = Modifier.padding(top = 32.dp).widthIn(max = 320.dp),
            )
        }
    }
}

/** Cadeado neutro — não diz nada sobre o app, que é o ponto. */
@Composable
private fun DefaultLockMark() {
    Icon(
        imageVector = Icons.Filled.Lock,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(64.dp),
    )
}
