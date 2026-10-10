package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.core.util.AppLogger

/**
 * No iOS só o compartilhamento grava arquivo: o `PdfViewer` lê com `PDFDocument(data:)` e a
 * impressão entrega o `NSData` em `printingItem` — nenhum dos dois toca o disco.
 */
internal actual fun clearPlatformTemporaryFiles(olderThanMillis: Long): Int =
    runCatching { IosShareHandler().clearSharedFiles(olderThanMillis) }
        .onFailure { AppLogger.w("KmpLibTempFiles", "falha ao limpar temporários: ${it::class.simpleName}") }
        .getOrDefault(0)
