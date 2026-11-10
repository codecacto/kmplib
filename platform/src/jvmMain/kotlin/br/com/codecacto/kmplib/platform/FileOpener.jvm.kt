package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.platform.audience.withParentalGate
import br.com.codecacto.kmplib.core.util.AppLogger
import java.awt.Desktop
import java.nio.file.Files

/**
 * Desktop (2.353.0): grava numa pasta temporária da lib e abre com o app que o SO associou ao tipo
 * (`Desktop.open` — o "abrir com" padrão do Explorer/Finder). Sem app associado = [FileOpenResult.NoAppAvailable].
 */
internal class DesktopFileOpener : FileOpener {
    override fun open(fileBytes: ByteArray, fileName: String, mimeType: String): FileOpenResult {
        val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
        if (desktop == null || !desktop.isSupported(Desktop.Action.OPEN)) return FileOpenResult.NoAppAvailable
        return try {
            val arquivo = desktopTempFile(OPEN_TEMP_DIRECTORY, fileName)
            Files.write(arquivo, fileBytes)
            desktop.open(arquivo.toFile())
            FileOpenResult.Opened
        } catch (e: java.io.IOException) {
            // O Windows lança IOException quando não há aplicativo associado à extensão.
            AppLogger.w("FileOpener", "nenhum app abre este tipo: ${e::class.simpleName}")
            FileOpenResult.NoAppAvailable
        } catch (e: Exception) {
            AppLogger.e("FileOpener", "falha ao abrir arquivo: ${e::class.simpleName}")
            FileOpenResult.Failed
        }
    }
}

actual fun getFileOpener(): FileOpener = DesktopFileOpener().withParentalGate()
