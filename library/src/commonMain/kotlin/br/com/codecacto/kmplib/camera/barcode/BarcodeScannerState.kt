package br.com.codecacto.kmplib.camera.barcode

import br.com.codecacto.kmplib.platform.permission.PermissionStatus

/**
 * Situação da câmera reportada pela implementação de plataforma ao [BarcodeScannerView].
 *
 * Existe para que "não abriu a câmera" **nunca** seja uma tela preta silenciosa: cada caminho de
 * falha tem um estado nomeado, e a UI diz o que aconteceu e o que fazer.
 */
sealed interface BarcodeCameraStatus {

    /**
     * A sessão está rodando e entregando frames.
     *
     * @property torchAvailable se o dispositivo tem lanterna utilizável nesta câmera (câmera
     *   frontal e emuladores não têm). A UI esconde o botão quando `false`, em vez de oferecer um
     *   botão que não faz nada.
     */
    data class Ready(val torchAvailable: Boolean = false) : BarcodeCameraStatus

    /**
     * Não há câmera utilizável — dispositivo sem câmera traseira, ou plataforma cuja
     * implementação ainda não foi validada (ver
     * [br.com.codecacto.kmplib.platform.PlatformCapabilities]).
     */
    data object Unavailable : BarcodeCameraStatus

    /** A inicialização falhou (bind da sessão, configuração do decodificador). */
    data class Failed(val message: String? = null) : BarcodeCameraStatus
}

/**
 * Estado da tela de scanner — a máquina de estados **explícita** exigida pelo design: permissão,
 * câmera indisponível e falha de inicialização são estados nomeados, com saída, nunca um preview
 * preto.
 */
sealed interface BarcodeScannerState {

    /** Verificando permissão / subindo a sessão de câmera. */
    data object Starting : BarcodeScannerState

    /** Câmera ao vivo, lendo. */
    data object Scanning : BarcodeScannerState

    /**
     * Permissão de câmera ainda não concedida, mas **ainda é possível pedir** (nunca solicitada ou
     * negada uma vez). A UI oferece "Permitir câmera".
     */
    data object PermissionRequired : BarcodeScannerState

    /**
     * Permissão negada em definitivo ("não perguntar novamente" no Android; segunda negativa no
     * iOS). Pedir de novo não abre diálogo nenhum — o único caminho é **as Configurações do app**.
     */
    data object PermissionPermanentlyDenied : BarcodeScannerState

    /** Sem câmera utilizável neste dispositivo/plataforma. */
    data object CameraUnavailable : BarcodeScannerState

    /** A câmera existe e a permissão foi dada, mas a sessão não subiu. */
    data class InitializationFailed(val message: String? = null) : BarcodeScannerState

    /** `true` quando o preview deve estar visível. */
    val isLive: Boolean get() = this is Scanning

    /** `true` quando o problema é de permissão (qualquer um dos dois estados). */
    val isPermissionIssue: Boolean
        get() = this is PermissionRequired || this is PermissionPermanentlyDenied
}

/**
 * Deriva o [BarcodeScannerState] a partir da permissão e da situação da câmera.
 *
 * Função pura (sem Compose, sem plataforma) — é o que permite testar a máquina de estados inteira
 * sem device. A **permissão tem precedência**: sem ela a câmera nem é ligada, então não faz sentido
 * reportar "falha de inicialização" para quem só não deu acesso.
 *
 * @param permission último status conhecido ([PermissionStatus]).
 * @param camera situação reportada pela plataforma, ou `null` enquanto a sessão não respondeu.
 */
fun barcodeScannerStateOf(
    permission: PermissionStatus,
    camera: BarcodeCameraStatus?,
): BarcodeScannerState = when (permission) {
    PermissionStatus.PERMANENTLY_DENIED -> BarcodeScannerState.PermissionPermanentlyDenied
    PermissionStatus.DENIED, PermissionStatus.NOT_REQUESTED -> BarcodeScannerState.PermissionRequired
    PermissionStatus.GRANTED -> when (camera) {
        null -> BarcodeScannerState.Starting
        is BarcodeCameraStatus.Ready -> BarcodeScannerState.Scanning
        BarcodeCameraStatus.Unavailable -> BarcodeScannerState.CameraUnavailable
        is BarcodeCameraStatus.Failed -> BarcodeScannerState.InitializationFailed(camera.message)
    }
}
