package br.com.codecacto.kmplib.camera

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Implementação Android do [PlateOcrAnalyzer] usando **ML Kit Text
 * Recognition** (on-device, sem rede).
 *
 * Decodifica os bytes em [android.graphics.Bitmap], roda o reconhecedor latino
 * e delega a extração da placa a [extractPlate] (commonMain), que aplica
 * `normalizePlate` + `isValidPlate`.
 *
 * Nunca lança em falha de OCR: qualquer erro de decodificação/reconhecimento
 * resulta em `null`.
 */
actual class PlateOcrAnalyzer actual constructor() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    actual suspend fun analyzePlate(imageBytes: ByteArray): String? {
        val bitmap = runCatching {
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }.getOrNull() ?: return null

        val image = InputImage.fromBitmap(bitmap, 0)

        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    cont.resume(extractPlate(result.text))
                }
                .addOnFailureListener {
                    // OCR falhou — sem placa, sem crash.
                    cont.resume(null)
                }
        }
    }
}
