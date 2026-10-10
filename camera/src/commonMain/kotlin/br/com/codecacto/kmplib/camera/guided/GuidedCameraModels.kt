package br.com.codecacto.kmplib.camera.guided

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import br.com.codecacto.kmplib.platform.permission.PermissionStatus

/** Qual câmera do aparelho. */
enum class CameraLens {
    /** A de trás — a do fotógrafo (o personal fotografando o aluno). */
    BACK,

    /** A da frente — a de quem fotografa a si mesmo. */
    FRONT,
    ;

    /** A outra câmera. */
    fun toggled(): CameraLens = if (this == BACK) FRONT else BACK
}

/** Modo do flash na hora da foto. O botão percorre na ordem [OFF] → [AUTO] → [ON]. */
enum class CameraFlashMode {
    OFF,
    AUTO,
    ON,
    ;

    /** O próximo modo do ciclo do botão. */
    fun next(): CameraFlashMode = when (this) {
        OFF -> AUTO
        AUTO -> ON
        ON -> OFF
    }
}

/**
 * O guia desenhado **sobre** o preview — a silhueta de corpo de frente/costas/lado, a moldura de um
 * documento, o contorno de um rosto.
 *
 * Duas formas, a mesma regra de posição:
 * - [CameraGuide.painter] — um `Painter` (de `painterResource(Res.drawable.silhueta_frente)` ou de
 *   `rememberVectorPainter(imageVector)`), encaixado na moldura por [contentScale];
 * - [CameraGuide.drawing] — um bloco de `DrawScope`, quando o desenho depende da medida (linhas de
 *   alinhamento, grade). O `size` do `DrawScope` é o da moldura, não o da tela.
 *
 * A **moldura** é a fração [widthFraction] × [heightFraction] do preview, posicionada por
 * [alignment]. O preview mostra exatamente o que a foto vai conter (ver [GuidedCamera]): o que está
 * dentro da silhueta na tela está dentro da silhueta na foto.
 *
 * @property alpha opacidade do guia (0..1). O padrão 0,6 deixa ver a pessoa através da silhueta.
 * @property tint cor aplicada ao `Painter` (silhueta monocromática na cor da marca, por exemplo);
 *   `null` = as cores do próprio desenho. Não se aplica a [drawing], que escolhe as próprias cores.
 */
@Immutable
class CameraGuide private constructor(
    internal val painter: Painter?,
    internal val onDraw: (DrawScope.() -> Unit)?,
    val alignment: Alignment,
    val widthFraction: Float,
    val heightFraction: Float,
    val alpha: Float,
    val tint: Color?,
    val contentScale: ContentScale,
) {
    companion object {
        /** Guia a partir de um `Painter` (imagem ou vetor da silhueta). */
        fun painter(
            painter: Painter,
            alignment: Alignment = Alignment.Center,
            widthFraction: Float = DEFAULT_WIDTH_FRACTION,
            heightFraction: Float = DEFAULT_HEIGHT_FRACTION,
            alpha: Float = DEFAULT_ALPHA,
            tint: Color? = null,
            contentScale: ContentScale = ContentScale.Fit,
        ): CameraGuide = CameraGuide(
            painter = painter,
            onDraw = null,
            alignment = alignment,
            widthFraction = coerceGuideFraction(widthFraction),
            heightFraction = coerceGuideFraction(heightFraction),
            alpha = coerceGuideAlpha(alpha),
            tint = tint,
            contentScale = contentScale,
        )

        /** Guia desenhado à mão: [onDraw] recebe um `DrawScope` do tamanho da moldura. */
        fun drawing(
            alignment: Alignment = Alignment.Center,
            widthFraction: Float = DEFAULT_WIDTH_FRACTION,
            heightFraction: Float = DEFAULT_HEIGHT_FRACTION,
            alpha: Float = DEFAULT_ALPHA,
            onDraw: DrawScope.() -> Unit,
        ): CameraGuide = CameraGuide(
            painter = null,
            onDraw = onDraw,
            alignment = alignment,
            widthFraction = coerceGuideFraction(widthFraction),
            heightFraction = coerceGuideFraction(heightFraction),
            alpha = coerceGuideAlpha(alpha),
            tint = null,
            contentScale = ContentScale.Fit,
        )

        /** Largura da moldura quando o app não diz outra: 80% do preview. */
        const val DEFAULT_WIDTH_FRACTION: Float = 0.8f

        /** Altura da moldura quando o app não diz outra: 90% do preview (corpo inteiro). */
        const val DEFAULT_HEIGHT_FRACTION: Float = 0.9f

        /** Opacidade quando o app não diz outra. */
        const val DEFAULT_ALPHA: Float = 0.6f
    }
}

/** Fração de moldura válida: (0, 1]. Zero ou negativo some com o guia; acima de 1 sai da tela. */
internal fun coerceGuideFraction(value: Float): Float =
    if (value.isNaN()) 1f else value.coerceIn(0.05f, 1f)

/** Opacidade válida: 0..1. */
internal fun coerceGuideAlpha(value: Float): Float =
    if (value.isNaN()) CameraGuide.DEFAULT_ALPHA else value.coerceIn(0f, 1f)

/**
 * Por que a câmera guiada não entregou foto — o que o app pode registrar ou usar para oferecer
 * outro caminho (o seletor de galeria, por exemplo). A própria tela já mostra a mensagem e a saída
 * de cada caso; este callback é para o app, não para substituir a tela.
 */
enum class GuidedCameraError {
    /** A pessoa negou a câmera (ainda dá para pedir de novo). */
    PERMISSION_DENIED,

    /** Negada em definitivo: só pelas Configurações do aparelho. */
    PERMISSION_PERMANENTLY_DENIED,

    /** O aparelho não tem câmera utilizável (emulador sem câmera, simulador do iOS). */
    CAMERA_UNAVAILABLE,

    /** A câmera existe e a permissão foi dada, mas a sessão não subiu. */
    INITIALIZATION_FAILED,

    /** A sessão estava no ar, mas a foto não saiu (ou não pôde ser codificada). */
    CAPTURE_FAILED,
}

/** O que a plataforma relata sobre a sessão de câmera. */
internal sealed interface GuidedCameraStatus {
    /**
     * Sessão no ar.
     *
     * @property flashAvailable se a câmera EM USO tem flash (a frontal costuma não ter).
     * @property availableLenses as câmeras que o aparelho tem — o botão de trocar só aparece com as
     *   duas.
     * @property activeLens a câmera de fato aberta ([resolveCameraLens] sobre a pedida).
     */
    data class Ready(
        val flashAvailable: Boolean,
        val availableLenses: Set<CameraLens>,
        val activeLens: CameraLens,
    ) : GuidedCameraStatus

    data object Unavailable : GuidedCameraStatus

    data class Failed(val message: String?) : GuidedCameraStatus
}

/** O estado da tela da câmera guiada — cada caso com a sua saída, nunca um preview preto. */
sealed interface GuidedCameraState {
    /** Conferindo permissão / subindo a sessão. */
    data object Starting : GuidedCameraState

    /**
     * Câmera ao vivo.
     *
     * @property flashAvailable mostrar o botão de flash.
     * @property canSwitchLens mostrar o botão de trocar de câmera.
     */
    data class Live(val flashAvailable: Boolean, val canSwitchLens: Boolean) : GuidedCameraState

    /** Permissão ainda não concedida, mas dá para pedir. */
    data object PermissionRequired : GuidedCameraState

    /** Negada em definitivo — o caminho é abrir as Configurações. */
    data object PermissionPermanentlyDenied : GuidedCameraState

    /** Sem câmera utilizável. */
    data object CameraUnavailable : GuidedCameraState

    /** A sessão não subiu. [message] é diagnóstico (log), não texto de tela. */
    data class InitializationFailed(val message: String?) : GuidedCameraState
}

/**
 * Deriva o [GuidedCameraState]. Pura — é o que permite testar a máquina de estados sem aparelho.
 * A permissão tem precedência: sem ela a câmera nem é ligada.
 */
internal fun guidedCameraStateOf(
    permission: PermissionStatus,
    camera: GuidedCameraStatus?,
    allowLensSwitch: Boolean,
): GuidedCameraState = when (permission) {
    PermissionStatus.PERMANENTLY_DENIED -> GuidedCameraState.PermissionPermanentlyDenied
    PermissionStatus.DENIED, PermissionStatus.NOT_REQUESTED -> GuidedCameraState.PermissionRequired
    PermissionStatus.GRANTED -> when (camera) {
        null -> GuidedCameraState.Starting
        is GuidedCameraStatus.Ready -> GuidedCameraState.Live(
            flashAvailable = camera.flashAvailable,
            canSwitchLens = allowLensSwitch && camera.availableLenses.containsAll(CameraLens.entries),
        )
        GuidedCameraStatus.Unavailable -> GuidedCameraState.CameraUnavailable
        is GuidedCameraStatus.Failed -> GuidedCameraState.InitializationFailed(camera.message)
    }
}

/**
 * O erro a relatar ao app num estado — ou `null`. "Ainda não pediu" ([PermissionStatus.NOT_REQUESTED])
 * não é erro: a tela está pedindo.
 */
internal fun guidedCameraErrorOf(
    permission: PermissionStatus,
    state: GuidedCameraState,
): GuidedCameraError? = when (state) {
    GuidedCameraState.PermissionRequired ->
        if (permission == PermissionStatus.DENIED) GuidedCameraError.PERMISSION_DENIED else null
    GuidedCameraState.PermissionPermanentlyDenied -> GuidedCameraError.PERMISSION_PERMANENTLY_DENIED
    GuidedCameraState.CameraUnavailable -> GuidedCameraError.CAMERA_UNAVAILABLE
    is GuidedCameraState.InitializationFailed -> GuidedCameraError.INITIALIZATION_FAILED
    GuidedCameraState.Starting, is GuidedCameraState.Live -> null
}

/**
 * A câmera a usar: a pedida, se o aparelho a tiver; senão a outra; `null` sem nenhuma. Quem pede a
 * frontal num aparelho só com a traseira (tablet antigo) recebe a traseira, não uma tela de erro.
 */
internal fun resolveCameraLens(requested: CameraLens, available: Set<CameraLens>): CameraLens? = when {
    requested in available -> requested
    requested.toggled() in available -> requested.toggled()
    else -> null
}

/**
 * Proporção do preview (largura/altura): a da FOTO, 4:3 — 3:4 em retrato, 4:3 em paisagem.
 *
 * É ela que garante que o preview mostra exatamente o que a foto vai conter: a sessão captura em
 * 4:3 (a proporção nativa do sensor) e o preview é desenhado inteiro (sem corte) numa caixa da
 * mesma proporção. Com o preview cortado para encher a tela — o padrão das câmeras —, a silhueta
 * alinhada na tela cairia em outro lugar na foto.
 */
internal fun guidedPreviewAspectRatio(containerWidth: Float, containerHeight: Float): Float =
    if (containerWidth > containerHeight) CAPTURE_ASPECT_LONG / CAPTURE_ASPECT_SHORT
    else CAPTURE_ASPECT_SHORT / CAPTURE_ASPECT_LONG

private const val CAPTURE_ASPECT_LONG = 4f
private const val CAPTURE_ASPECT_SHORT = 3f

/**
 * Identificadores estáveis para o Maestro (`tapOn: id: …`) — nunca o texto, que muda nos 4 idiomas.
 */
object GuidedCameraTestTags {
    const val ROOT: String = "camera-guiada"
    const val PREVIEW: String = "camera-guiada-preview"
    const val GUIDE: String = "camera-guiada-guia"
    const val CAPTURE: String = "camera-guiada-btn-capturar"
    const val SWITCH_LENS: String = "camera-guiada-btn-trocar"
    const val FLASH: String = "camera-guiada-btn-flash"
    const val CLOSE: String = "camera-guiada-btn-fechar"
    const val PRIMARY_ACTION: String = "camera-guiada-btn-acao"
    const val MESSAGE: String = "camera-guiada-mensagem"
}
