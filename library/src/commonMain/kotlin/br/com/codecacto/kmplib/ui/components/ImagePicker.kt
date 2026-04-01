package br.com.codecacto.kmplib.ui.components

import androidx.compose.runtime.Composable

/**
 * Launcher para selecao de imagens via camera ou galeria.
 *
 * Ao chamar [launch], exibe um seletor com as opcoes:
 * - Tirar foto (camera)
 * - Escolher da galeria
 *
 * A imagem selecionada e retornada como ByteArray (JPEG, max 1024px, 85% quality).
 */
expect class ImagePickerLauncher {
    fun launch()
}

/**
 * Cria e lembra um [ImagePickerLauncher] que retorna os bytes da imagem selecionada.
 *
 * Uso:
 * ```
 * val picker = rememberImagePickerLauncher { bytes ->
 *     // processar bytes da imagem
 * }
 *
 * Button(onClick = { picker.launch() }) {
 *     Text("Adicionar foto")
 * }
 * ```
 */
@Composable
expect fun rememberImagePickerLauncher(
    onImageSelected: (ByteArray) -> Unit
): ImagePickerLauncher
