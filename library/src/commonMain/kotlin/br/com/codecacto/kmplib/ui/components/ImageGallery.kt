package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
 * @param items itens a exibir.
 * @param onItemClick callback com o `id` do item tocado (navegar OU alternar seleção).
 * @param multiSelect ativa o modo seleção.
 * @param selectedIds ids atualmente selecionados (modo seleção).
 * @param columns nº de colunas fixas do grid.
 */
@Composable
fun ImageGallery(
    items: List<GalleryItem>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    multiSelect: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    columns: Int = 3,
    spacing: androidx.compose.ui.unit.Dp = 4.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing),
    ) {
        items(items, key = { it.id }) { item ->
            GalleryCell(
                item = item,
                selected = multiSelect && item.id in selectedIds,
                onClick = { onItemClick(item.id) },
            )
        }
    }
}

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
