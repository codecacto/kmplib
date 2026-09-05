package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.AppColors
import coil3.compose.AsyncImage

/**
 * Estado de carregamento/envio de um item da [ImageGallery]. Sobreposto como overlay no thumb.
 */
enum class GalleryItemStatus {
    /** Item normal, sem overlay. */
    NONE,

    /** Em envio (spinner). */
    UPLOADING,

    /** Envio falhou (ícone de erro). */
    FAILED,

    /** Envio concluído (check). */
    UPLOADED,
}

/**
 * Item de imagem da [ImageGallery].
 *
 * @property id identificador estável (usado para seleção e `key`).
 * @property model fonte da imagem aceita pelo Coil (URL `String`, `ByteArray`, path, etc.).
 * @property status overlay de status (enviando/falhou/ok). Default [GalleryItemStatus.NONE].
 */
data class GalleryItem(
    val id: String,
    val model: Any?,
    val status: GalleryItemStatus = GalleryItemStatus.NONE,
)

/**
 * Grade de imagens (lazy load via Coil) com overlay de status por item e **multi-seleção**
 * opcional. Componente genérico de `ui/components` — atende galeria de fotos por etapa/linha do
 * tempo e o seletor de fotos para o relatório (multiSelect).
 *
 * - **Modo navegação** (default, `multiSelect = false`): toque dispara [onItemClick] (abrir foto).
 * - **Modo seleção** (`multiSelect = true`): toque alterna seleção; itens em [selectedIds] ganham
 *   borda/check de acento. O app mantém o `Set` e atualiza no [onItemClick].
 *
 * Cores 100% via tema (sem hardcode). Coil já é dependência da kmplib (mesmo motor de
 * `AsyncImage` usado em `ads/custom`).
 *
 * ### Rolagem
 * Por padrão a grade rola internamente (`LazyVerticalGrid`). **Dentro de uma coluna que já rola**
 * (formulário com `verticalScroll`), passe `scrollable = false`: a grade lazy pede altura infinita
 * ao pai, e num pai que oferece altura infinita ela **estoura em runtime** com
 * *"Vertically scrollable component was measured with an infinity maximum height constraints"*.
 * Não é preferência de layout, é queda de tela — e aconteceu duas vezes, em telas diferentes do
 * mesmo app, cada uma contornando com `BoxWithConstraints` e uma conta de célula × linhas na mão.
 * O parâmetro é o mesmo do [TimelineList], de propósito: é a régua da lib para lista dentro de
 * lista.
 *
 * Com `scrollable = false` a grade vira `Column`/`Row` (não-lazy) e cresce até a altura do conteúdo
 * — quem rola é o pai. A última linha incompleta mantém as células do mesmo tamanho das demais
 * (o espaço que sobra fica vazio, e não esticado).
 *
 * @param items itens a exibir.
 * @param onItemClick callback com o `id` do item tocado (navegar OU alternar seleção).
 * @param multiSelect ativa o modo seleção.
 * @param selectedIds ids atualmente selecionados (modo seleção).
 * @param columns nº de colunas fixas do grid.
 * @param spacing espaço entre células (nos dois eixos).
 * @param contentPadding padding em volta do conteúdo da grade.
 * @param scrollable `true` (default) = grade lazy com rolagem própria; `false` = grade que ocupa a
 *   altura do conteúdo, para usar dentro de um pai rolável.
 */
@Composable
fun ImageGallery(
    items: List<GalleryItem>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    multiSelect: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    columns: Int = 3,
    spacing: Dp = 4.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    scrollable: Boolean = true,
) {
    val colunas = columns.coerceAtLeast(1)
    if (scrollable) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(colunas),
            modifier = modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            items(items, key = { it.id }) { item ->
                GalleryCell(
                    item = item,
                    selected = multiSelect && item.id in selectedIds,
                    onClick = { onItemClick(item.id) },
                )
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth().padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            galeriaEmLinhas(items, colunas).forEach { linha ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    linha.forEach { item ->
                        Box(modifier = Modifier.weight(1f)) {
                            GalleryCell(
                                item = item,
                                selected = multiSelect && item.id in selectedIds,
                                onClick = { onItemClick(item.id) },
                            )
                        }
                    }
                    // O buraco da última linha é ESPAÇO, não célula esticada: sem isto, duas fotos
                    // numa grade de três colunas viram dois retângulos largos, e o quadrado da foto
                    // (que a grade lazy garante) se perde justamente na linha de baixo.
                    repeat(colunas - linha.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * As linhas da grade não-lazy: os itens em blocos de [colunas], na ordem em que chegaram.
 *
 * Fora do composable para poder ser testada — é a única conta do modo `scrollable = false`, e a
 * que decide se a última linha fica com células do tamanho certo.
 */
internal fun galeriaEmLinhas(items: List<GalleryItem>, colunas: Int): List<List<GalleryItem>> =
    if (items.isEmpty()) emptyList() else items.chunked(colunas.coerceAtLeast(1))

@Composable
private fun GalleryCell(
    item: GalleryItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = item.model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Overlay de status por item.
        when (item.status) {
            GalleryItemStatus.UPLOADING -> {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                }
            }
            GalleryItemStatus.FAILED -> {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = "Falha no envio",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            GalleryItemStatus.UPLOADED -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Enviado",
                    tint = AppColors.current.success,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp),
                )
            }
            GalleryItemStatus.NONE -> Unit
        }

        // Overlay de seleção (modo multiSelect).
        if (selected) {
            Box(
                modifier = Modifier.fillMaxSize().background(accent.copy(alpha = 0.30f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selecionada",
                    tint = accent,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
