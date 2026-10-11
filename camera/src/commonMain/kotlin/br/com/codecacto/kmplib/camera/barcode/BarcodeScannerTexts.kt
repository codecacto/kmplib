package br.com.codecacto.kmplib.camera.barcode

import androidx.compose.runtime.Composable
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_barcode_aim_hint
import br.com.codecacto.kmplib.generated.resources.kmplib_barcode_camera_unavailable
import br.com.codecacto.kmplib.generated.resources.kmplib_barcode_init_failed
import br.com.codecacto.kmplib.generated.resources.kmplib_barcode_manual_entry
import br.com.codecacto.kmplib.generated.resources.kmplib_barcode_open_settings
import br.com.codecacto.kmplib.generated.resources.kmplib_barcode_permission_allow
import br.com.codecacto.kmplib.generated.resources.kmplib_barcode_permission_denied_message
import br.com.codecacto.kmplib.generated.resources.kmplib_barcode_permission_message
import br.com.codecacto.kmplib.generated.resources.kmplib_barcode_permission_title
import br.com.codecacto.kmplib.generated.resources.kmplib_barcode_retry
import br.com.codecacto.kmplib.generated.resources.kmplib_barcode_starting
import br.com.codecacto.kmplib.generated.resources.kmplib_barcode_torch_off
import br.com.codecacto.kmplib.generated.resources.kmplib_barcode_torch_on
import br.com.codecacto.kmplib.ui.locale.kmpStringResource

/**
 * Textos visíveis do [BarcodeScannerView] (i18n).
 *
 * Segue as duas convenções do ecossistema ao mesmo tempo:
 * 1. **`*Texts` injetável**, como todo componente da lib — o app passa o seu objeto e sobrescreve
 *    o que quiser (ex.: "Aponte para o código do lote", num app que não é de varejo);
 * 2. quando o app **não** passa nada, os defaults vêm dos **Compose Multiplatform Resources** da
 *    própria lib, traduzidos em **pt-BR, pt-PT, en e es** — ou seja, seguem o **idioma do
 *    aparelho**, sem seletor e sem trabalho do app (ver [rememberBarcodeScannerTexts]).
 *
 * Os defaults literais desta `data class` são pt-BR e existem para quem constrói o objeto fora de
 * uma composição (teste, preview).
 */
data class BarcodeScannerTexts(
    val aimHint: String = "Aponte para o código de barras do produto",
    val starting: String = "Preparando a câmera…",
    val permissionTitle: String = "Precisamos da câmera",
    val permissionMessage: String = "A câmera é usada para ler o código de barras do produto.",
    val permissionAllow: String = "Permitir câmera",
    val permissionDeniedMessage: String =
        "O acesso à câmera está bloqueado. Libere nas Configurações do aparelho para escanear.",
    val openSettings: String = "Abrir Configurações",
    val cameraUnavailable: String = "Câmera indisponível neste aparelho.",
    val initializationFailed: String = "Não foi possível iniciar a câmera.",
    val retry: String = "Tentar novamente",
    val manualEntry: String = "Digitar código manualmente",
    val torchOn: String = "Ligar lanterna",
    val torchOff: String = "Desligar lanterna",
)

/**
 * [BarcodeScannerTexts] no **idioma do aparelho**, lido dos Compose Resources da lib
 * (pt-BR / pt-PT / en / es).
 *
 * É o default do [BarcodeScannerView] — o app só precisa passar `texts` se quiser um vocabulário
 * próprio.
 */
@Composable
fun rememberBarcodeScannerTexts(): BarcodeScannerTexts = BarcodeScannerTexts(
    aimHint = kmpStringResource(Res.string.kmplib_barcode_aim_hint),
    starting = kmpStringResource(Res.string.kmplib_barcode_starting),
    permissionTitle = kmpStringResource(Res.string.kmplib_barcode_permission_title),
    permissionMessage = kmpStringResource(Res.string.kmplib_barcode_permission_message),
    permissionAllow = kmpStringResource(Res.string.kmplib_barcode_permission_allow),
    permissionDeniedMessage = kmpStringResource(Res.string.kmplib_barcode_permission_denied_message),
    openSettings = kmpStringResource(Res.string.kmplib_barcode_open_settings),
    cameraUnavailable = kmpStringResource(Res.string.kmplib_barcode_camera_unavailable),
    initializationFailed = kmpStringResource(Res.string.kmplib_barcode_init_failed),
    retry = kmpStringResource(Res.string.kmplib_barcode_retry),
    manualEntry = kmpStringResource(Res.string.kmplib_barcode_manual_entry),
    torchOn = kmpStringResource(Res.string.kmplib_barcode_torch_on),
    torchOff = kmpStringResource(Res.string.kmplib_barcode_torch_off),
)
