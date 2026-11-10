package br.com.codecacto.kmplib.platform

import java.nio.file.Path

/**
 * Seletor nativo de arquivo do desktop (2.353.0), exposto aos OUTROS módulos da lib (`kmplib-ui`).
 * Não é API de app: o app usa `rememberFilePicker`/`rememberMultiFilePicker`/`rememberImagePickerLauncher`.
 */
@KmpLibPlatformInternalApi
fun desktopChooseFiles(title: String, mimeTypes: List<String>, multiple: Boolean): List<Path> =
    DesktopFiles.chooseFiles(title, mimeTypes, multiple)

/** Tipo MIME de um arquivo no desktop (SO, depois extensão). Interno da lib. */
@KmpLibPlatformInternalApi
fun desktopMimeTypeOf(path: Path): String = DesktopFiles.mimeTypeOf(path)

/** Pasta temporária da lib deste app (`<tmp>/kmplib-<appId>/<name>`). Interno da lib. */
@KmpLibPlatformInternalApi
fun desktopTempDirectory(name: String): Path = DesktopFiles.tempDirectory(name)
