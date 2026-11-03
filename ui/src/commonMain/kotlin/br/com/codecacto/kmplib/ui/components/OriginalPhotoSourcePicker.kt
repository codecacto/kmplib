package br.com.codecacto.kmplib.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.photos.OriginalPhoto
import br.com.codecacto.kmplib.platform.photos.OriginalPhotoPickResult
import br.com.codecacto.kmplib.platform.photos.OriginalPhotoSource
import br.com.codecacto.kmplib.platform.photos.rememberOriginalPhotoFilePicker
import br.com.codecacto.kmplib.platform.photos.rememberOriginalPhotoPicker as rememberOriginalGalleryPicker

/**
 * O que a escolha de foto ORIGINAL vai fazer para uma lista de origens — regra pura, testada.
 *
 * @property showsSheet `true` = mais de uma origem: a folha padrão; `false` = abre a única direto.
 * @property options as origens da folha (ou a única), na ordem pedida, sem repetição.
 */
internal data class OriginalPhotoPickerPlan(
    val showsSheet: Boolean,
    val options: List<AttachmentSource>,
)

internal fun originalPhotoPickerPlan(sources: List<OriginalPhotoSource>): OriginalPhotoPickerPlan {
    // Lista vazia não pode virar um botão que não abre nada: cai no padrão da fábrica.
    val origens = sources.distinct().ifEmpty { OriginalPhotoSource.Standard }
    val opcoes = origens.map {
        when (it) {
            OriginalPhotoSource.GALLERY -> AttachmentSource.GALLERY
            OriginalPhotoSource.FILE -> AttachmentSource.FILE
        }
    }
    return OriginalPhotoPickerPlan(showsSheet = opcoes.size > 1, options = opcoes)
}

/**
 * **Fotos ORIGINAIS pela folha padrão da lib** (2.340.0): as mesmas linhas, ícones, textos (4 idiomas)
 * e ids (`anexo-folha`, `anexo-opcao-galeria`, `anexo-opcao-arquivo`) do [rememberAttachmentPicker] —
 * só que a entrega é a do original: [OriginalPhoto] com caminho de arquivo, bytes intactos (HEIC fica
 * HEIC, RAW fica RAW, EXIF da câmera junto), copiado por stream, pronto para a `DirectUploadOutbox`.
 *
 * - **Galeria** → a fototeca (Photo Picker / `PHPicker` em representação atual).
 * - **Arquivo** → o gerenciador de arquivos filtrado para imagem (SAF / `UIDocumentPicker` com
 *   `asCopy`) — é por onde o fotógrafo traz o JPG/RAW exportado da câmera.
 *
 * Com uma origem só na lista, ela abre **direto**, sem folha. **Câmera não é oferecida**, de propósito:
 * a câmera do sistema não entrega o arquivo original no iOS (devolve imagem decodificada — gravá-la é
 * recodificar); ver `OriginalPhotoSource`. Foto para anexo/formulário continua sendo o
 * [rememberAttachmentPicker].
 *
 * ```kotlin
 * val escolher = rememberOriginalPhotoPicker(OriginalPhotoSource.Standard) { r ->
 *     if (!r.isCancelled) vm.onAction(Action.Escolhidas(r.photos, r.failedCount))
 * }
 * AppButton("Escolher fotos", onClick = escolher)
 * ```
 *
 * Mesmo nome da função de `kmplib-platform` (`platform.photos.rememberOriginalPhotoPicker`, só galeria,
 * sem folha) — e sem ambiguidade com ela: aqui [sources] vem PRIMEIRO e é obrigatório, então a chamada
 * antiga continua indo para a de lá, e importar as duas não quebra nada.
 *
 * @param sources as origens, na ordem da folha — o padrão da fábrica é [OriginalPhotoSource.Standard]
 *   (Galeria · Arquivo). Lista vazia = o padrão.
 * @param maxItems quantas no máximo (`0` = o que o sistema permitir; `1` = uma só). No seletor de
 *   arquivos o sistema não limita: a lib fica com as primeiras.
 * @param texts título e rótulos da folha; default nos 4 idiomas ("Adicionar foto", "Galeria", "Arquivo").
 * @param onResult chamado na thread principal, uma vez por escolha; desistir chega com
 *   [OriginalPhotoPickResult.isCancelled] (fechar a FOLHA não chama nada).
 * @return a função que abre a folha (ou a única origem).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberOriginalPhotoPicker(
    sources: List<OriginalPhotoSource>,
    maxItems: Int = 0,
    texts: AttachmentPickerTexts = rememberAttachmentPickerTexts(AttachmentAccept.ImagesOnly),
    onResult: (OriginalPhotoPickResult) -> Unit,
): () -> Unit {
    if (sources.isEmpty()) AppLogger.w(TAG, "rememberOriginalPhotoPicker sem origens; usando Galeria · Arquivo")
    val plan = remember(sources) { originalPhotoPickerPlan(sources) }
    val entregar by rememberUpdatedState(onResult)
    var folhaAberta by remember { mutableStateOf(false) }

    val galeria = rememberOriginalGalleryPicker(maxItems = maxItems) { entregar(it) }
    val arquivos = rememberOriginalPhotoFilePicker(maxItems = maxItems) { entregar(it) }

    fun abrir(origem: AttachmentSource) {
        when (origem) {
            AttachmentSource.GALLERY -> galeria()
            AttachmentSource.FILE -> arquivos()
            AttachmentSource.CAMERA -> Unit // nunca está no plano (ver KDoc)
        }
    }

    if (folhaAberta) {
        AppModalBottomSheet(
            onDismissRequest = { folhaAberta = false },
            modifier = Modifier.testTag(AttachmentPickerTestTags.FOLHA),
        ) {
            AttachmentSourceSheetContent(
                title = texts.title,
                options = plan.options,
                texts = texts,
                onSelect = { origem ->
                    folhaAberta = false
                    abrir(origem)
                },
            )
        }
    }

    return remember(plan, galeria, arquivos) {
        { if (plan.showsSheet) folhaAberta = true else abrir(plan.options.first()) }
    }
}

private const val TAG = "KmpLibOriginalPhotoPicker"
