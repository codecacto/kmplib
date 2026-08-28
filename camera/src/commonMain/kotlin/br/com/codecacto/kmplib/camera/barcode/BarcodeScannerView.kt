package br.com.codecacto.kmplib.camera.barcode

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.core.util.currentTimeMillis
import br.com.codecacto.kmplib.platform.permission.AppPermission
import br.com.codecacto.kmplib.platform.permission.PermissionState
import br.com.codecacto.kmplib.platform.permission.rememberPermissionState
import br.com.codecacto.kmplib.ui.components.AppButton
import br.com.codecacto.kmplib.ui.components.AppOutlinedButton

/**
 * **Scanner de código de barras pronto para uso** — preview + mira + lanterna + estados de
 * permissão + anti-repetição + confirmação de leitura.
 *
 * É o componente que uma tela de "escanear produto" deve usar. O app cuida do topo (voltar,
 * alternar para digitação) e do que fazer com o código; tudo o que é câmera fica aqui.
 *
 * ## Leitura contínua, sem repetir
 *
 * O preview reconhece o mesmo código em **todo frame**. O [BarcodeScanDebouncer] (regras em
 * [debounce]) garante que [onBarcodeScanned] dispare **uma vez** por leitura, e que um código
 * **diferente** possa ser lido logo em seguida **sem recriar a tela** — que é exatamente o modo
 * "escanear vários seguidos". Depois de gravar o item, chame [BarcodeScannerHandle.resetDebounce]
 * (via [onReady]) se quiser permitir reler o **mesmo** código na hora (duas caixas do mesmo
 * produto com validades diferentes).
 *
 * ## Nunca é um beco sem saída
 *
 * Permissão não concedida, negada em definitivo, aparelho sem câmera e falha de inicialização são
 * **estados nomeados** ([BarcodeScannerState]), cada um com o seu caminho: pedir de novo, **abrir
 * as Configurações do app** ou cair na **digitação manual** ([onManualEntry]).
 *
 * ## Exemplo
 *
 * ```kotlin
 * BarcodeScannerView(
 *     onBarcodeScanned = { codigo -> viewModel.onCodigoLido(codigo.toGtin13() ?: codigo.value) },
 *     modifier = Modifier.fillMaxSize(),
 *     debounce = BarcodeScanDebounce.SEQUENCE,     // modo "vários seguidos"
 *     onManualEntry = { navegarParaDigitacao() },
 * ) {
 *     // Slot de overlay: confirmação do item anterior, contador, botão "Concluir"...
 *     UltimoItemSalvoBanner(state.ultimoSalvo, Modifier.align(Alignment.BottomCenter))
 * }
 * ```
 *
 * @param onBarcodeScanned chamado **na main thread**, já filtrado por [debounce], com o código
 *   normalizado e validado.
 * @param formats simbologias a decodificar. Default [BarcodeFormats.RETAIL] — peça só o que for
 *   ler (cada formato a mais custa taxa de leitura).
 * @param debounce regras de anti-repetição da leitura contínua.
 * @param feedback confirmação física da leitura (vibração ligada, som desligado por padrão).
 * @param texts textos visíveis; por padrão seguem o **idioma do aparelho**.
 * @param showTorchButton exibe o botão de lanterna quando o aparelho tiver uma (gôndola escura).
 * @param onManualEntry quando informado, os estados de erro/permissão oferecem "Digitar código
 *   manualmente" — o caminho que o produto sempre precisa ter.
 * @param onStateChange espelha o [BarcodeScannerState] para o app (habilitar/desabilitar ações do
 *   top bar, telemetria).
 * @param onReady entrega o [BarcodeScannerHandle] para controle imperativo (resetar o debounce,
 *   alternar a lanterna a partir do top bar do app).
 * @param overlayContent slot desenhado **sobre** o preview (confirmação do item anterior,
 *   contador, botão "Concluir").
 */
@Composable
fun BarcodeScannerView(
    onBarcodeScanned: (ScannedBarcode) -> Unit,
    modifier: Modifier = Modifier,
    formats: Set<BarcodeFormat> = BarcodeFormats.RETAIL,
    debounce: BarcodeScanDebounce = BarcodeScanDebounce(),
    feedback: BarcodeScanFeedback = BarcodeScanFeedback(),
    texts: BarcodeScannerTexts = rememberBarcodeScannerTexts(),
    showTorchButton: Boolean = true,
    onManualEntry: (() -> Unit)? = null,
    onStateChange: ((BarcodeScannerState) -> Unit)? = null,
    onReady: ((BarcodeScannerHandle) -> Unit)? = null,
    overlayContent: @Composable BoxScope.() -> Unit = {},
) {
    val permission: PermissionState = rememberPermissionState(AppPermission.CAMERA)
    val currentOnScanned by rememberUpdatedState(onBarcodeScanned)
    val haptics = LocalHapticFeedback.current

    var cameraStatus by remember { mutableStateOf<BarcodeCameraStatus?>(null) }
    // "Tentar novamente" precisa RECRIAR a sessão de câmera. Zerar só o status deixaria a tela
    // presa em "Preparando…" — o bind da plataforma já aconteceu e não seria refeito. O token
    // entra como `key` do preview, o que descarta e remonta a sessão.
    var retryToken by remember { mutableStateOf(0) }
    var torchEnabled by rememberSaveable { mutableStateOf(false) }
    val debouncer = remember(debounce) { BarcodeScanDebouncer(debounce) }

    val handle = remember(debouncer) {
        object : BarcodeScannerHandle {
            override fun resetDebounce() = debouncer.reset()
            override fun setTorch(enabled: Boolean) { torchEnabled = enabled }
            override fun requestPermission() = permission.request()
        }
    }

    val state = barcodeScannerStateOf(permission.status, cameraStatus)
    val currentOnStateChange by rememberUpdatedState(onStateChange)
    LaunchedEffect(state) { currentOnStateChange?.invoke(state) }
    LaunchedEffect(handle) { onReady?.invoke(handle) }

    // A lanterna só faz sentido com a sessão viva; ao sair do estado de leitura ela é apagada para
    // não ficar acesa numa tela que não mostra mais o preview.
    LaunchedEffect(state) {
        if (!state.isLive && torchEnabled) torchEnabled = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (permission.isGranted) {
            key(retryToken) {
                BarcodeCameraPreview(
                    onBarcodesDetected = { detected ->
                        val now = currentTimeMillis()
                        val accepted = detected.firstOrNull { debouncer.accept(it.value, now) }
                        if (accepted != null) {
                            if (feedback.haptic) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            if (feedback.sound) playBarcodeScanBeep()
                            currentOnScanned(accepted)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    formats = formats,
                    torchEnabled = torchEnabled,
                    onCameraStatus = { cameraStatus = it },
                )
            }
        }

        when (state) {
            BarcodeScannerState.Scanning -> {
                BarcodeAimOverlay(
                    hint = texts.aimHint,
                    modifier = Modifier.fillMaxSize(),
                )
                val ready = cameraStatus as? BarcodeCameraStatus.Ready
                if (showTorchButton && ready?.torchAvailable == true) {
                    TorchButton(
                        enabled = torchEnabled,
                        onToggle = { torchEnabled = !torchEnabled },
                        texts = texts,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                    )
                }
            }

            BarcodeScannerState.Starting -> ScannerMessage(
                icon = Icons.Outlined.PhotoCamera,
                title = null,
                message = texts.starting,
                texts = texts,
            )

            BarcodeScannerState.PermissionRequired -> ScannerMessage(
                icon = Icons.Outlined.PhotoCamera,
                title = texts.permissionTitle,
                message = texts.permissionMessage,
                primaryLabel = texts.permissionAllow,
                onPrimary = permission::request,
                onManualEntry = onManualEntry,
                texts = texts,
            )

            BarcodeScannerState.PermissionPermanentlyDenied -> ScannerMessage(
                icon = Icons.Outlined.NoPhotography,
                title = texts.permissionTitle,
                message = texts.permissionDeniedMessage,
                primaryLabel = texts.openSettings,
                onPrimary = permission::openAppSettings,
                onManualEntry = onManualEntry,
                texts = texts,
            )

            BarcodeScannerState.CameraUnavailable -> ScannerMessage(
                icon = Icons.Outlined.NoPhotography,
                title = null,
                message = texts.cameraUnavailable,
                onManualEntry = onManualEntry,
                texts = texts,
            )

            is BarcodeScannerState.InitializationFailed -> ScannerMessage(
                icon = Icons.Outlined.NoPhotography,
                title = null,
                // A mensagem técnica do SDK ("bindToLifecycle falhou") é diagnóstico, não texto de
                // tela: fica no `BarcodeScannerState` (via onStateChange) para log/telemetria.
                message = texts.initializationFailed,
                primaryLabel = texts.retry,
                onPrimary = {
                    cameraStatus = null
                    retryToken++
                },
                onManualEntry = onManualEntry,
                texts = texts,
            )
        }

        overlayContent()
    }
}

/**
 * Controle imperativo do [BarcodeScannerView], entregue pelo callback `onReady`.
 *
 * Existe para as duas ações que a **tela** (e não o componente) decide: liberar a releitura do
 * mesmo código depois de gravar o item, e alternar a lanterna a partir de um botão do top bar do
 * app.
 */
interface BarcodeScannerHandle {

    /**
     * Esquece o histórico de leitura — o próximo código, **inclusive o último lido**, é aceito na
     * hora. Chame após gravar o item no modo "escanear vários seguidos".
     */
    fun resetDebounce()

    /** Liga/desliga a lanterna (no-op se o aparelho não tiver). */
    fun setTorch(enabled: Boolean)

    /** Pede a permissão de câmera de novo (no-op se já concedida). */
    fun requestPermission()
}

/**
 * Mira do scanner: escurece a área fora da janela de leitura e marca os quatro cantos.
 *
 * Desenhada em [Canvas] (sem imagem, sem cor fixa) para funcionar igual nas duas plataformas e
 * respeitar a paleta do app — o realce usa `colorScheme.primary`.
 */
@Composable
private fun BarcodeAimOverlay(
    hint: String,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val scrim = Color.Black.copy(alpha = 0.55f)

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val windowWidth = size.width * 0.82f
            // Proporção deitada: um código de barras é largo e baixo.
            val windowHeight = (windowWidth * 0.48f).coerceAtMost(size.height * 0.5f)
            val left = (size.width - windowWidth) / 2f
            val top = (size.height - windowHeight) / 2f - size.height * 0.06f
            val right = left + windowWidth
            val bottom = top + windowHeight

            drawRect(scrim, topLeft = Offset.Zero, size = Size(size.width, top))
            drawRect(scrim, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
            drawRect(scrim, topLeft = Offset(0f, top), size = Size(left, windowHeight))
            drawRect(scrim, topLeft = Offset(right, top), size = Size(size.width - right, windowHeight))

            val corner = windowWidth * 0.12f
            val strokeWidth = 4.dp.toPx()
            // Cantos (topo-esq, topo-dir, base-esq, base-dir).
            drawLine(accent, Offset(left, top), Offset(left + corner, top), strokeWidth)
            drawLine(accent, Offset(left, top), Offset(left, top + corner), strokeWidth)
            drawLine(accent, Offset(right, top), Offset(right - corner, top), strokeWidth)
            drawLine(accent, Offset(right, top), Offset(right, top + corner), strokeWidth)
            drawLine(accent, Offset(left, bottom), Offset(left + corner, bottom), strokeWidth)
            drawLine(accent, Offset(left, bottom), Offset(left, bottom - corner), strokeWidth)
            drawLine(accent, Offset(right, bottom), Offset(right - corner, bottom), strokeWidth)
            drawLine(accent, Offset(right, bottom), Offset(right, bottom - corner), strokeWidth)
            // Linha-guia central, onde o código deve ficar.
            drawLine(
                accent.copy(alpha = 0.7f),
                Offset(left + corner, (top + bottom) / 2f),
                Offset(right - corner, (top + bottom) / 2f),
                strokeWidth / 2f,
            )
        }

        Text(
            text = hint,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .widthIn(max = 420.dp)
                .clearAndSetSemantics { contentDescription = hint },
        )
    }
}

/** Botão de lanterna sobre o preview. */
@Composable
private fun TorchButton(
    enabled: Boolean,
    onToggle: () -> Unit,
    texts: BarcodeScannerTexts,
    modifier: Modifier = Modifier,
) {
    FilledIconButton(
        onClick = onToggle,
        modifier = modifier.size(48.dp),
        colors = if (enabled) {
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White,
            )
        },
    ) {
        Icon(
            imageVector = if (enabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
            contentDescription = if (enabled) texts.torchOff else texts.torchOn,
        )
    }
}

/**
 * Bloco de mensagem dos estados que **não** são leitura: permissão, câmera indisponível, falha.
 *
 * Segue o padrão do [br.com.codecacto.kmplib.ui.components.ErrorState] (ícone + texto + ação),
 * mas com **dois** caminhos de saída: a ação principal do estado e a digitação manual.
 */
@Composable
private fun ScannerMessage(
    icon: ImageVector,
    title: String?,
    message: String,
    texts: BarcodeScannerTexts,
    modifier: Modifier = Modifier,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    onManualEntry: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(56.dp),
            )
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (primaryLabel != null && onPrimary != null) {
                AppButton(
                    text = primaryLabel,
                    onClick = onPrimary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (onManualEntry != null) {
                AppOutlinedButton(
                    text = texts.manualEntry,
                    onClick = onManualEntry,
                )
            }
        }
    }
}
