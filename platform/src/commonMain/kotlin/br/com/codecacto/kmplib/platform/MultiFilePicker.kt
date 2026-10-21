package br.com.codecacto.kmplib.platform

import androidx.compose.runtime.Composable

/**
 * Um arquivo escolhido no seletor de **vários** arquivos ([rememberMultiFilePicker], 2.311.0).
 *
 * É uma **referência**, não os bytes: a lista chega com nome, tipo e tamanho, e o conteúdo só é lido
 * quando o app chama [read] — um por vez, de preferência logo antes de enviar. Ler os 30 arquivos de
 * uma seleção para a memória de uma vez (o que um `List<FileData>` obrigaria) é `OutOfMemoryError`
 * garantido num aparelho de entrada, e `OutOfMemoryError` não é `Exception`: o `try/catch` da tela não
 * pega.
 *
 * A referência vale **enquanto o app está aberto** (Android: a permissão temporária do
 * `ACTION_OPEN_DOCUMENT`; iOS: a URL com escopo de segurança do seletor). Não a guarde para ler
 * depois de o processo morrer — para isso, leia e grave numa fila durável.
 *
 * O construtor é público para o app montar um dublê em teste de ViewModel.
 *
 * @property name nome de exibição do arquivo (pode conter dado pessoal — ver [toString]).
 * @property mimeType tipo informado pelo sistema; `application/octet-stream` quando ele não sabe.
 * @property sizeBytes tamanho informado pelo provedor; `-1` quando ele não informa (a leitura com teto
 *   decide nesse caso).
 */
class PickedFile(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    private val reader: suspend (maxBytes: Long) -> FilePickResult,
) {
    /** Extensão do nome, sem o ponto (`""` quando não há). */
    val extension: String get() = name.substringAfterLast('.', "")

    /**
     * `true` quando o tamanho **conhecido** passa de [maxBytes] — dá para recusar antes de ler.
     * Tamanho desconhecido (`-1`) responde `false`: quem decide é o [read], que lê com teto.
     */
    fun exceeds(maxBytes: Long): Boolean = exceedsFilePickLimit(sizeBytes, resolveMaxFileBytes(maxBytes))

    /**
     * Lê o conteúdo, **com teto**, fora da thread principal.
     *
     * Devolve [FilePickResult.Picked], [FilePickResult.TooLarge] ou [FilePickResult.Failed] — nunca
     * [FilePickResult.Cancelled] (a desistência já aconteceu no seletor). Nunca lança, salvo
     * cancelamento da corrotina. `maxBytes <= 0` cai em [DEFAULT_MAX_FILE_BYTES].
     */
    suspend fun read(maxBytes: Long = DEFAULT_MAX_FILE_BYTES): FilePickResult =
        reader(resolveMaxFileBytes(maxBytes))

    /** Sem o nome: "cnh-joao-silva.pdf" é dado pessoal e não pode ir para log/GlitchTip. */
    override fun toString(): String = "PickedFile(mimeType=$mimeType, sizeBytes=$sizeBytes)"
}

/**
 * Seletor de arquivos do sistema com **múltipla escolha** (2.311.0) — qualquer tipo por default.
 *
 * - **Android:** `ActivityResultContracts.OpenMultipleDocuments` (o seletor de documentos do sistema,
 *   o mesmo do [rememberFilePicker], com seleção múltipla). Nome, tipo e tamanho são lidos do
 *   provedor fora da thread principal.
 * - **iOS:** `UIDocumentPickerViewController(forOpeningContentTypes:)` com
 *   `allowsMultipleSelection = true`, apresentado do controlador no topo da janela; a leitura usa o
 *   escopo de segurança e `NSFileCoordinator` (arquivo do iCloud ainda não baixado é baixado na
 *   leitura, como a Apple recomenda).
 *
 * ```kotlin
 * val escolher = rememberMultiFilePicker { arquivos ->
 *     if (arquivos.isNotEmpty()) vm.dispatch(Action.Enviar(arquivos)) // vazio = desistiu: silêncio
 * }
 * // no ViewModel, um por vez:
 * when (val r = arquivo.read(maxBytes = 20L * 1024 * 1024)) {
 *     is FilePickResult.Picked -> api.enviar(r.file)
 *     is FilePickResult.TooLarge -> avisar(arquivo.name)
 *     is FilePickResult.Failed -> avisar(arquivo.name)
 *     FilePickResult.Cancelled -> Unit
 * }
 * ```
 *
 * @param mimeTypes filtro de tipos (Android: MIME; iOS: hoje sempre "qualquer item"). Vazio ou
 *   `*` + `/` + `*` = qualquer arquivo.
 * @param onResult a lista na ordem escolhida. **Lista vazia = o usuário fechou o seletor** — não é
 *   erro, não mostre mensagem.
 * @return a função que abre o seletor.
 */
@Composable
expect fun rememberMultiFilePicker(
    mimeTypes: List<String> = listOf("*/*"),
    onResult: (List<PickedFile>) -> Unit,
): () -> Unit
