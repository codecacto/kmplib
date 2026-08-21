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
 * Por que a foto NAO veio — o que o app precisa dizer na tela.
 *
 * Sem isto, cada motivo terminava num `printStackTrace()` ou num `if (granted)` sem `else`: o toque
 * em "Tirar foto" nao produzia efeito NENHUM, e a leitura de quem usa e "o botao esta quebrado".
 * Foi o que aconteceu no NeuroCoreX (21/ago/2026), onde o app nao declarava `CAMERA` no manifest —
 * permissao nao declarada e negada pelo sistema na hora, sem nem mostrar o dialogo.
 */
enum class ImagePickerError {
    /**
     * A pessoa negou a camera — ou o app **nao declarou** `android.permission.CAMERA` no manifest,
     * caso em que o sistema nega sem perguntar. Os dois chegam aqui iguais, de proposito: para quem
     * esta na tela a diferenca nao existe, e para quem desenvolve o log da lib nomeia o caso.
     */
    CAMERA_PERMISSION_DENIED,

    /** A camera nao abriu (sem app de camera, `FileProvider` ausente, falha do sistema). */
    CAMERA_UNAVAILABLE,

    /** A imagem escolhida nao pode ser lida ou decodificada. */
    IMAGE_UNREADABLE,
}

/**
 * Cria e lembra um [ImagePickerLauncher] que retorna os bytes da imagem selecionada.
 *
 * Uso:
 * ```
 * val picker = rememberImagePickerLauncher(
 *     onImageSelected = { bytes -> /* processar bytes da imagem */ },
 *     onError = { motivo -> /* virar aviso na tela — NUNCA silencio */ },
 * )
 *
 * Button(onClick = { picker.launch() }) {
 *     Text("Adicionar foto")
 * }
 * ```
 *
 * ## Requisito de manifest (Android)
 *
 * A opcao "Tirar foto" exige `<uses-permission android:name="android.permission.CAMERA" />` no
 * manifest do APP. O `FileProvider` (authority `${applicationId}.fileprovider`) ja vem declarado
 * pela lib — **nao redeclarar**, dois `FILE_PROVIDER_PATHS` na mesma authority param o merge.
 */
@Composable
expect fun rememberImagePickerLauncher(
    onImageSelected: (ByteArray) -> Unit,
    onError: (ImagePickerError) -> Unit,
): ImagePickerLauncher

/**
 * Sobrecarga sem tratamento de erro. **Prefira a de dois parametros**: aqui, camera negada e imagem
 * ilegivel sao silencio na tela.
 */
@Composable
fun rememberImagePickerLauncher(
    onImageSelected: (ByteArray) -> Unit,
): ImagePickerLauncher = rememberImagePickerLauncher(onImageSelected, onError = {})
