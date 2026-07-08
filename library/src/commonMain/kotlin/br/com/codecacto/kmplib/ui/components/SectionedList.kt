package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Seção de uma [SectionedList]: título + itens + [key] estável (para o cabeçalho da seção).
 *
 * @param T tipo do item da seção.
 */
data class Section<T>(
    val title: String,
    val items: List<T>,
    val key: Any,
)

/**
 * Lista **agrupada por seções** (cabeçalho por seção + itens), genérica sobre [T]. Cada item é
 * renderizado por [itemContent] e identificado por [key] (estável, para animações/recomposição).
 * Sobre `LazyColumn` (responsiva, rola). Segue a filosofia data+slot dos demais componentes de
 * lista da kmplib (`TimelineList`, `MultiSelectList`, `DensityGrid`).
 *
 * Cores/tipografia via tokens do tema (`MaterialTheme` — sem hardcode). Cabeçalho da seção em
 * `titleSmall`/SemiBold na cor primária.
 *
 * ```kotlin
 * SectionedList(
 *     sections = grupos.map { (titulo, itens) -> Section(titulo, itens, key = titulo) },
 *     key = { it.id },
 * ) { item -> ItemRow(item) }
 * ```
 *
 * @param sections seções a exibir (cabeçalho + itens).
 * @param key identidade estável de cada item.
 * @param itemContent renderiza um item da seção.
 * @param modifier modificador externo.
 * @param contentPadding padding do conteúdo da lista.
 * @param verticalSpacing espaçamento vertical entre linhas.
 */
@Composable
fun <T> SectionedList(
    sections: List<Section<T>>,
    key: (T) -> Any,
    itemContent: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    verticalSpacing: androidx.compose.ui.unit.Dp = 6.dp,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
    ) {
        sections.forEach { section ->
            item(key = "section-${section.key}") {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            items(section.items, key = { key(it) }) { item -> itemContent(item) }
        }
    }
}
