package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.platform.audience.withParentalGate
import br.com.codecacto.kmplib.core.util.AppLogger
import java.awt.Desktop
import java.nio.file.Files

/**
 * Desktop (2.353.0). O Windows/macOS/Linux não expõem à JVM uma "folha de compartilhamento" do
 * sistema (a do Windows é WinRT `DataTransferManager`, fora do alcance da JVM). O equivalente honesto:
 *
 * - texto e link: vão para a ÁREA DE TRANSFERÊNCIA (o "copiar link" que todo app desktop oferece) —
 *   o app mostra o aviso "copiado";
 * - arquivo e imagem: gravados numa pasta temporária da lib e REVELADOS no gerenciador de arquivos
 *   (Explorer/Finder) com o arquivo selecionado, para a pessoa arrastar para o e-mail/WhatsApp Web;
 *   onde o SO não revela, abre com o app padrão.
 */
internal class DesktopShareHandler : ShareHandler {

    override fun shareText(text: String, title: String) {
        getClipboard().copy(text, label = title.ifBlank { "Texto" })
    }

    override fun shareImage(imageBytes: ByteArray, fileName: String, title: String) =
        shareFile(imageBytes, fileName, "image/*", title)

    override fun shareFile(fileBytes: ByteArray, fileName: String, mimeType: String, title: String) {
        try {
            val arquivo = desktopTempFile(SHARE_TEMP_DIRECTORY, fileName)
            Files.write(arquivo, fileBytes)
            val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
            when {
                desktop?.isSupported(Desktop.Action.BROWSE_FILE_DIR) == true -> desktop.browseFileDirectory(arquivo.toFile())
                desktop?.isSupported(Desktop.Action.OPEN) == true -> desktop.open(arquivo.parent.toFile())
                else -> AppLogger.w(TAG, "o SO não revela arquivos; o arquivo ficou na pasta temporária da lib")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "falha ao compartilhar arquivo: ${e::class.simpleName}")
        }
    }

    override fun clearSharedFiles(olderThanMillis: Long): Int = clearPlatformTemporaryFiles(olderThanMillis)

    private companion object {
        const val TAG = "ShareHandler"
    }
}

actual fun getShareHandler(): ShareHandler = DesktopShareHandler().withParentalGate()
