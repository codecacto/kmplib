package br.com.codecacto.kmplib.camera.barcode

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * **Preview de câmera cru com detecção contínua de código de barras** — sem mira, sem permissão,
 * sem anti-repetição.
 *
 * É a peça de baixo nível para quem quer compor a própria tela. **Na maioria dos casos use o
 * [BarcodeScannerView]**, que embrulha isto com máquina de estados de permissão, mira, lanterna,
 * feedback de leitura e anti-repetição.
 *
 * Contrato:
 * - [onBarcodesDetected] é chamado **na main thread**, uma vez por frame em que houver detecção —
 *   ou seja, **dezenas de vezes por segundo** enquanto o código estiver na mira. Aplicar
 *   [BarcodeScanDebouncer] é responsabilidade de quem chama.
 * - Os valores já vêm normalizados e validados por [parseBarcode] (dígito verificador conferido);
 *   uma detecção que não fecha simplesmente não é reportada.
 * - A permissão de câmera **deve** estar concedida antes; sem ela a sessão não sobe e o
 *   [onCameraStatus] reporta [BarcodeCameraStatus.Failed]/[BarcodeCameraStatus.Unavailable].
 *
 * Padrão-ouro por plataforma:
 * - **Android:** **CameraX** (`Preview` + `ImageAnalysis` com `STRATEGY_KEEP_ONLY_LATEST`)
 *   alimentando o **ML Kit Barcode Scanning**. Mesma base de câmera do
 *   [br.com.codecacto.kmplib.camera.CameraView] (OCR de placa), compartilhada em
 *   `CameraXPreviewSession`.
 * - **iOS:** **AVFoundation** com **`AVCaptureMetadataOutput`** — a API que a Apple oferece para
 *   leitura de códigos **ao vivo**: a decodificação acontece dentro do pipeline de captura
 *   (acelerada, sem copiar frame para a CPU), o que importa num app usado durante um turno
 *   inteiro. O `VNDetectBarcodesRequest` (Vision) é o caminho oficial para **imagem parada** e é o
 *   que o [BarcodeAnalyzer] usa.
 *
 * @param onBarcodesDetected códigos válidos do frame corrente (nunca vazio quando chamado).
 * @param formats simbologias a decodificar (veja [BarcodeFormats]).
 * @param torchEnabled liga/desliga a lanterna. Ignorado se o dispositivo não tiver
 *   (`BarcodeCameraStatus.Ready.torchAvailable == false`).
 * @param onCameraStatus reporta que a sessão subiu, falhou ou não existe.
 */
@Composable
expect fun BarcodeCameraPreview(
    onBarcodesDetected: (List<ScannedBarcode>) -> Unit,
    modifier: Modifier = Modifier,
    formats: Set<BarcodeFormat> = BarcodeFormats.RETAIL,
    torchEnabled: Boolean = false,
    onCameraStatus: (BarcodeCameraStatus) -> Unit = {},
)
