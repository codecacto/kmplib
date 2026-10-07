package br.com.codecacto.kmplib.platform.audience

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.ShareHandler
import br.com.codecacto.kmplib.platform.UrlLauncher

/**
 * [UrlLauncher] que passa cada saída pelo [ParentalGate] (2.259.0). É o que `getUrlLauncher()`
 * devolve desde então — fora do modo infantil o portão não existe e a chamada vai direto.
 *
 * Para um `UrlLauncher` próprio do app, [withParentalGate].
 */
internal class ParentalGatedUrlLauncher(private val delegate: UrlLauncher) : UrlLauncher {
    override fun openUrl(url: String) = ParentalGate.guard { delegate.openUrl(url) }
    override fun openEmail(to: String, subject: String, body: String) =
        ParentalGate.guard { delegate.openEmail(to, subject, body) }
    override fun openPhone(phoneNumber: String) = ParentalGate.guard { delegate.openPhone(phoneNumber) }
    override fun openWhatsApp(phone: String, message: String) =
        ParentalGate.guard { delegate.openWhatsApp(phone, message) }
    override fun openStorePage(androidPackage: String?, iosAppId: String?) =
        ParentalGate.guard { delegate.openStorePage(androidPackage, iosAppId) }
    override fun openMap(query: String) = ParentalGate.guard { delegate.openMap(query) }
    override fun openSubscriptionManagement() = ParentalGate.guard { delegate.openSubscriptionManagement() }
    override fun openAppSettings() = ParentalGate.guard { delegate.openAppSettings() }
    override fun openNotificationSettings() = ParentalGate.guard { delegate.openNotificationSettings() }
}

/**
 * [ShareHandler] que passa cada compartilhamento pelo [ParentalGate] (2.259.0). A folha de
 * compartilhar manda conteúdo para fora do app — em app infantil, só depois do adulto.
 *
 * Fora do modo infantil, idêntico ao original (inclusive a exceção síncrona do contrato de erro).
 * No modo infantil a ação roda depois do portão, longe de quem chamou: falha vira log, não exceção.
 * [ShareHandler.clearSharedFiles] não é saída e vai direto.
 */
internal class ParentalGatedShareHandler(private val delegate: ShareHandler) : ShareHandler {
    override fun shareText(text: String, title: String) = gated { delegate.shareText(text, title) }
    override fun shareImage(imageBytes: ByteArray, fileName: String, title: String) =
        gated { delegate.shareImage(imageBytes, fileName, title) }
    override fun shareFile(fileBytes: ByteArray, fileName: String, mimeType: String, title: String) =
        gated { delegate.shareFile(fileBytes, fileName, mimeType, title) }
    override fun shareLink(url: String, message: String, title: String) =
        gated { delegate.shareLink(url, message, title) }
    override fun clearSharedFiles(olderThanMillis: Long): Int = delegate.clearSharedFiles(olderThanMillis)

    private fun gated(action: () -> Unit) {
        if (!ParentalGate.isRequired) {
            action()
            return
        }
        ParentalGate.guard {
            try {
                action()
            } catch (e: Exception) {
                AppLogger.e("ShareHandler", "Falha ao compartilhar depois do portão de pais", e)
            }
        }
    }
}

/** Embrulha um [UrlLauncher] próprio do app no [ParentalGate]. Idempotente. */
fun UrlLauncher.withParentalGate(): UrlLauncher =
    if (this is ParentalGatedUrlLauncher) this else ParentalGatedUrlLauncher(this)

/** Embrulha um [ShareHandler] próprio do app no [ParentalGate]. Idempotente. */
fun ShareHandler.withParentalGate(): ShareHandler =
    if (this is ParentalGatedShareHandler) this else ParentalGatedShareHandler(this)
