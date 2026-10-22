package br.com.codecacto.kmplib.inspection.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.camera.guided.CameraFlashMode
import br.com.codecacto.kmplib.camera.guided.CameraGuide
import br.com.codecacto.kmplib.camera.guided.GuidedCameraCountdown
import br.com.codecacto.kmplib.camera.guided.GuidedCameraError
import br.com.codecacto.kmplib.camera.guided.GuidedCameraState
import br.com.codecacto.kmplib.camera.guided.GuidedCameraViewport
import br.com.codecacto.kmplib.camera.guided.GuidedShutterButton
import br.com.codecacto.kmplib.camera.guided.rememberGuidedCameraController
import br.com.codecacto.kmplib.camera.guided.rememberGuidedCameraTexts
import br.com.codecacto.kmplib.inspection.InspectionTexts
import br.com.codecacto.kmplib.inspection.capture.CapturedMedia
import br.com.codecacto.kmplib.inspection.capture.InspectionCaptureError
import br.com.codecacto.kmplib.inspection.capture.InspectionCaptureResult
import br.com.codecacto.kmplib.inspection.capture.InspectionCaptureWriter
import br.com.codecacto.kmplib.inspection.fmt
import br.com.codecacto.kmplib.inspection.icons.InspectionIcon
import br.com.codecacto.kmplib.inspection.icons.InspectionIconView
import br.com.codecacto.kmplib.inspection.model.InspectionMediaThumb
import br.com.codecacto.kmplib.inspection.rememberInspectionTexts
import br.com.codecacto.kmplib.inspection.theme.InspectionTheme
import br.com.codecacto.kmplib.inspection.theme.InspectionTokens
import br.com.codecacto.kmplib.inspection.ui.InspectionTestTags
import br.com.codecacto.kmplib.inspection.ui.KitIconButton
import br.com.codecacto.kmplib.inspection.ui.dashedBorder
import br.com.codecacto.kmplib.inspection.ui.inspectionTextStyle
import br.com.codecacto.kmplib.ui.components.rememberPhotoSourceRequest
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * **Câmera de vistoria** (§2.10, LK-M10) — o motor é o da `GuidedCamera` da lib (preview que É a foto,
 * flash, temporizador, estados de permissão/sem câmera/falha com saída), recomposto com o CROMO do kit
 * pelas peças públicas [rememberGuidedCameraController] + [GuidedCameraViewport] (a resposta à pergunta
 * da especificação: o cromo é substituível por composição, não por sobreposição).
 *
 * Tela escura fixa, independente do tema. Topo: fechar · flash · temporizador. Na SAÍDA, a foto da
 * ENTRADA vem sobreposta ([reference], 45 % por padrão) com a pílula "Alinhe com a foto da entrada" e a
 * régua de transparência (0–100 %). Contador "Mínimo: 2 fotos · 1 de 2" (verde ao cumprir), fila de
 * captura (última foto 52 · disparo 80 · OK) e as miniaturas do item (56×42; a vaga seguinte tracejada).
 *
 * **Cada disparo é GRAVADO antes de chegar ao produto** ([writer]: disco + SHA-256 + hora + modelo +
 * GPS best-effort). Falha de escrita mostra "Pouco espaço no aparelho" + "Liberar espaço"
 * ([onFreeSpace]) e não perde a câmera.
 *
 * Sem "trocar lente" no topo (D9: lente traseira; vídeo tem câmera própria — `VideoRecorderCamera`).
 *
 * @param captured as mídias já gravadas deste item (miniaturas e contador).
 * @param minPhotos mínimo exigido (0 = sem contador).
 */
@Composable
fun InspectionCamera(
    writer: InspectionCaptureWriter,
    onCaptured: (CapturedMedia) -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    captured: List<InspectionMediaThumb> = emptyList(),
    minPhotos: Int = 0,
    reference: Painter? = null,
    referenceInitialOpacity: Float = DEFAULT_REFERENCE_OPACITY,
    onFreeSpace: (() -> Unit)? = null,
    onError: (GuidedCameraError) -> Unit = {},
    onCaptureError: (InspectionCaptureError) -> Unit = {},
    maxDimension: Int = DEFAULT_MAX_DIMENSION,
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    val t = InspectionTheme.tokens
    val scope = rememberCoroutineScope()
    val atualOnCaptured by rememberUpdatedState(onCaptured)
    val atualOnCaptureError by rememberUpdatedState(onCaptureError)
    var gravando by remember { mutableStateOf(false) }
    var erroGravacao by remember { mutableStateOf<InspectionCaptureError?>(null) }
    var opacidade by rememberSaveable { mutableFloatStateOf(referenceInitialOpacity.coerceIn(0f, 1f)) }
    val cameraTexts = rememberGuidedCameraTexts()

    val controller = rememberGuidedCameraController(
        onCaptured = { foto ->
            gravando = true
            scope.launch {
                when (val r = writer.persist(foto)) {
                    is InspectionCaptureResult.Saved -> {
                        erroGravacao = null
                        atualOnCaptured(r.media)
                    }
                    is InspectionCaptureResult.Failed -> {
                        erroGravacao = r.error
                        atualOnCaptureError(r.error)
                    }
                }
                gravando = false
            }
        },
        allowLensSwitch = false,
        onError = onError,
    )
    LaunchedEffect(erroGravacao) {
        if (erroGravacao == InspectionCaptureError.EMPTY) {
            delay(3_000)
            erroGravacao = null
        }
    }

    val aoVivo = controller.state is GuidedCameraState.Live
    Column(
        modifier
            .fillMaxSize()
            .background(InspectionTokens.CameraBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Topo 56: fechar à esquerda; flash e temporizador à direita.
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KitIconButton(
                InspectionIcon.Close,
                texts.common.close,
                onClose,
                tint = InspectionTokens.OnPhoto,
                size = 26.dp,
                testTag = InspectionTestTags.CAMERA_CLOSE,
            )
            Spacer(Modifier.weight(1f))
            if (aoVivo && (controller.state as GuidedCameraState.Live).flashAvailable) {
                KitIconButton(
                    icon = when (controller.flashMode) {
                        CameraFlashMode.OFF -> InspectionIcon.FlashOff
                        CameraFlashMode.AUTO -> InspectionIcon.FlashAuto
                        CameraFlashMode.ON -> InspectionIcon.FlashOn
                    },
                    description = cameraTexts.flashLabel(controller.flashMode),
                    onClick = controller::cycleFlash,
                    tint = InspectionTokens.OnPhoto,
                    testTag = InspectionTestTags.CAMERA_FLASH,
                )
            }
            if (aoVivo) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button, enabled = controller.countdown == null, onClick = controller::cycleTimer)
                        .semantics { contentDescription = cameraTexts.timerLabel(controller.timerSeconds) }
                        .testTag(InspectionTestTags.CAMERA_TIMER),
                    contentAlignment = Alignment.Center,
                ) {
                    if (controller.timerSeconds > 0) {
                        Text("${controller.timerSeconds}s", style = inspectionTextStyle(14.sp, FontWeight.Bold, InspectionTokens.OnPhoto))
                    } else {
                        InspectionIconView(InspectionIcon.Timer, size = 24.dp, tint = InspectionTokens.OnPhoto)
                    }
                }
            }
        }

        if (erroGravacao == InspectionCaptureError.STORAGE) {
            LowStorageBanner(texts, onFreeSpace)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            GuidedCameraViewport(
                controller = controller,
                modifier = Modifier.fillMaxSize(),
                guide = reference?.let {
                    CameraGuide.painter(it, widthFraction = 1f, heightFraction = 1f, alpha = opacidade, contentScale = ContentScale.Crop)
                },
                maxDimension = maxDimension,
                texts = cameraTexts,
            ) {
                if (reference != null) {
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(InspectionTokens.PhotoPillBackground)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        InspectionIconView(InspectionIcon.Layers, size = 18.dp, tint = InspectionTokens.OnPhoto)
                        Text(texts.camera.alignWithEntry, style = inspectionTextStyle(14.sp, color = InspectionTokens.OnPhoto))
                    }
                }
            }
            GuidedCameraCountdown(controller, Modifier.align(Alignment.Center), cameraTexts)
        }

        if (reference != null && aoVivo) {
            OpacitySlider(opacidade, { opacidade = it }, texts)
        }

        if (aoVivo) {
            Column(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (minPhotos > 0) MinimumCounter(captured.size, minPhotos, texts)
                CaptureRow(
                    last = captured.lastOrNull(),
                    busy = gravando,
                    texts = texts,
                    shutter = {
                        GuidedShutterButton(
                            controller = controller,
                            size = 80.dp,
                            texts = cameraTexts,
                            modifier = Modifier.testTag(InspectionTestTags.CAMERA_SHUTTER),
                        )
                    },
                    onDone = onDone,
                )
                CapturedThumbs(captured, minPhotos)
            }
        }
    }
}

/** Opacidade inicial da foto da entrada sobreposta. */
const val DEFAULT_REFERENCE_OPACITY: Float = 0.45f

/** Lado maior da foto de vistoria: 2048 px (o detalhe de um rodapé aparece; 1024 do seletor é pouco). */
const val DEFAULT_MAX_DIMENSION: Int = 2048

/** Régua de transparência: ícone `opacity`, trilha 4 dp (`#3A3F48`/branco), polegar 22, valor mono "45%". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpacitySlider(value: Float, onChange: (Float) -> Unit, texts: InspectionTexts) {
    val percentual = (value * 100).roundToInt()
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InspectionIconView(InspectionIcon.Opacity, size = 20.dp, tint = InspectionTokens.OnPhoto)
        val cores = SliderDefaults.colors(
            thumbColor = InspectionTokens.OnPhoto,
            activeTrackColor = InspectionTokens.OnPhoto,
            inactiveTrackColor = InspectionTokens.CameraTrack,
        )
        Slider(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = texts.camera.overlayOpacity
                    stateDescription = "$percentual%"
                }
                .testTag(InspectionTestTags.CAMERA_OPACITY),
            colors = cores,
            thumb = {
                Box(Modifier.size(22.dp).clip(CircleShape).background(InspectionTokens.OnPhoto))
            },
            track = { estado ->
                SliderDefaults.Track(
                    sliderState = estado,
                    modifier = Modifier.height(4.dp),
                    colors = cores,
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp,
                )
            },
        )
        Text(
            "$percentual%",
            style = inspectionTextStyle(14.sp, color = InspectionTokens.OnPhoto, mono = true),
            modifier = Modifier.width(36.dp).clearAndSetSemantics { },
        )
    }
}

/** "Mínimo: 2 fotos · 1 de 2" (15/600 branco, tabular); cumprido → ✓ verde. */
@Composable
private fun MinimumCounter(count: Int, minimum: Int, texts: InspectionTexts) {
    val t = InspectionTheme.tokens
    val cumprido = count >= minimum
    Row(
        Modifier.testTag(InspectionTestTags.CAMERA_COUNTER).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (cumprido) InspectionIconView(InspectionIcon.CheckCircle, filled = true, size = 18.dp, tint = t.good.fg)
        Text(
            if (cumprido) texts.camera.minimumDone.fmt(count, minimum) else texts.camera.minimumProgress.fmt(minimum, count),
            style = inspectionTextStyle(15.sp, FontWeight.SemiBold, InspectionTokens.OnPhoto, tabular = true),
        )
    }
}

/** Fila de captura: última foto 52×52 (borda 2 branca) · disparo 80 · "OK". */
@Composable
private fun CaptureRow(
    last: InspectionMediaThumb?,
    busy: Boolean,
    texts: InspectionTexts,
    shutter: @Composable () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            if (last?.source != null) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(2.dp, InspectionTokens.OnPhoto, RoundedCornerShape(6.dp))
                        .semantics { contentDescription = texts.camera.lastPhoto },
                ) {
                    AsyncImage(rememberPhotoSourceRequest(last.source), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            if (busy) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), color = InspectionTokens.OnPhoto, strokeWidth = 2.dp)
            }
        }
        shutter()
        Box(
            Modifier
                .heightIn(min = 44.dp)
                .width(52.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Button, onClick = onDone)
                .testTag(InspectionTestTags.CAMERA_DONE),
            contentAlignment = Alignment.Center,
        ) {
            Text(texts.camera.done, style = inspectionTextStyle(15.sp, FontWeight.SemiBold, InspectionTokens.OnPhoto))
        }
    }
}

/** Miniaturas do item (56×42): a atual com borda 2 na marca; vagas até o mínimo, tracejadas. */
@Composable
private fun CapturedThumbs(captured: List<InspectionMediaThumb>, minPhotos: Int) {
    val t = InspectionTheme.tokens
    val vagas = (minPhotos - captured.size).coerceAtLeast(if (captured.isEmpty()) 1 else 0)
    LazyRow(
        Modifier.fillMaxWidth().padding(bottom = 30.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(captured, key = { _, m -> m.id }) { i, m ->
            val atual = i == captured.lastIndex
            Box(
                Modifier
                    .size(width = 56.dp, height = 42.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .then(if (atual) Modifier.border(2.dp, t.brand, RoundedCornerShape(4.dp)) else Modifier),
            ) {
                m.source?.let { AsyncImage(rememberPhotoSourceRequest(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
        }
        items(vagas) {
            Box(Modifier.size(width = 56.dp, height = 42.dp).dashedBorder(InspectionTokens.CameraEmptySlot, width = 1.dp, radius = 4.dp))
        }
    }
}

@Composable
private fun LowStorageBanner(texts: InspectionTexts, onFreeSpace: (() -> Unit)?) {
    val t = InspectionTheme.tokens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(t.bad.soft)
            .padding(start = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InspectionIconView(InspectionIcon.Warning, filled = true, size = 18.dp, tint = t.bad.fg)
        Text(
            texts.camera.lowStorage,
            style = inspectionTextStyle(14.sp, FontWeight.SemiBold, t.bad.fg),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 10.dp),
        )
        if (onFreeSpace != null) {
            Box(
                Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onFreeSpace).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(texts.camera.freeSpace, style = inspectionTextStyle(14.sp, FontWeight.SemiBold, t.bad.fg))
            }
        }
    }
}
