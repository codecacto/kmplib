package br.com.codecacto.kmplib.sync.rest

import kotlinx.io.RawSource
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/** Caminho absoluto a partir de um caminho ou de uma URL `file:`. */
private fun pathOf(reference: String): String =
    if (reference.startsWith("file:", ignoreCase = true)) java.io.File(java.net.URI(reference)).path else reference

internal actual fun openLocalFileSource(reference: String): RawSource =
    SystemFileSystem.source(Path(pathOf(reference)))

internal actual fun localFileSizeOrNull(reference: String): Long? = runCatching {
    SystemFileSystem.metadataOrNull(Path(pathOf(reference)))?.size
}.getOrNull()?.takeIf { it >= 0L }
