package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Uma foto da faixa — **a que já subiu, a que está subindo e a que falhou**.
 *
 * O [id] é a chave da miniatura e precisa ser estável desde o instante da escolha, ANTES de existir
 * URL: é ele que permite ao quadradinho nascer no lugar certo, com o indicador girando, e depois
 * virar a imagem sem a lista remontar. Um `id` derivado da URL só nasceria no fim do upload — e aí
 * a miniatura só apareceria quando não fosse mais precisa.
 *
 * @param id chave estável da miniatura.
 * @param url `null` enquanto sobe; preenchida quando o servidor devolve o endereço.
 * @param failed o upload falhou — a miniatura fica com o aviso e o toque tenta de novo.
 */
data class PhotoStripItem(
    val id: String,
    val url: String? = null,
    val failed: Boolean = false,
) {
    /** Está subindo: sem URL e sem falha. */
    val uploading: Boolean get() = url == null && !failed
}

/**
 * Faixa horizontal de fotos de um formulário, com o **quadradinho de adicionar em PRIMEIRO lugar**.
 *
 * ```kotlin
 * val seletor = rememberMultiImagePickerLauncher(
 *     onImagesPicked = { fotos -> viewModel.onAction(Acao.FotosEscolhidas(fotos)) },
 *     onError = { viewModel.onAction(Acao.FalhaNaFoto(it)) },
 * )
 *
 * PhotoStrip(
 *     items = state.fotos,
 *     onAdd = { seletor.launch() },
 *     onRemove = { viewModel.onAction(Acao.RemoverFoto(it.id)) },
 *     onMakeCover = { viewModel.onAction(Acao.UsarComoCapa(it.id)) },
 * )
 * ```
 *
 * ## Por que o "+" fica em PRIMEIRO, e não no fim
 * A faixa rola na horizontal. Com o "+" no fim, quem já subiu oito fotos precisa **rolar até o fim
 * da lista** para subir a nona — e como ninguém rola atrás de um botão que não sabe que existe, a
 * leitura de quem está na tela é que não dá para adicionar mais. Em primeiro, ele está sempre no
 * campo de visão, na mesma posição, desde o formulário vazio até a vigésima foto (fundador,
 * 15/set/2026).
 *
 * ## Por que não existe botão "Adicionar foto" embaixo
 * O quadradinho **é** o botão, e tem a medida da miniatura: é ele que diz, sem legenda, que ali
 * cabem fotos e que aquele é o tamanho delas. Um botão largo embaixo repetia a mesma ação com outra
 * forma e empurrava a faixa para fora da tela.
 *
 * ## A miniatura nasce ANTES de a foto subir
 * Quem escolhe cinco fotos na galeria volta para o formulário e vê **cinco quadradinhos girando**,
 * na ordem em que escolheu. Sem isso, a tela fica igual à de antes por vários segundos e a leitura
 * é que a escolha se perdeu — a pessoa volta à galeria e escolhe tudo de novo. É o que o
 * [PhotoStripItem.uploading] desenha.
 *
 * ## Falha não some, e não trava
 * A foto que falhou fica na faixa com a marca de recarregar: o toque chama [onRetry], e o "x"
 * continua removendo. Sumir em silêncio faria a pessoa contar quatro fotos onde escolheu cinco sem
 * nunca saber qual não foi.
 *
 * @param items as fotos, na ordem de exibição. A primeira é a capa.
 * @param onAdd toque no quadradinho de adicionar.
 * @param onRemove toque no "x" de uma miniatura.
 * @param onMakeCover toque numa miniatura já pronta. `null` = a capa não se escolhe nesta tela, e
 *   aí a miniatura não fica clicável nem anuncia ação que não existe.
 * @param onRetry toque numa miniatura que falhou. `null` = sem nova tentativa; resta remover.
 * @param canAdd o quadradinho de adicionar aparece. `false` esconde só ele — as fotos continuam.
 * @param itemSize o lado do quadrado, igual para a miniatura e para o "+".
 */
@Composable
fun PhotoStrip(
    items: List<PhotoStripItem>,
    onAdd: () -> Unit,
    onRemove: (PhotoStripItem) -> Unit,
    modifier: Modifier = Modifier,
    onMakeCover: ((PhotoStripItem) -> Unit)? = null,
    onRetry: ((PhotoStripItem) -> Unit)? = null,
    canAdd: Boolean = true,
    itemSize: Dp = 104.dp,
    addLabel: String = "Adicionar",
    coverLabel: String = "Capa",
    removeLabel: String = "Remover foto",
    retryLabel: String = "Tentar de novo",
) {
    val scheme = MaterialTheme.colorScheme
    val canto = RoundedCornerShape(12.dp)

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // ⚠️ Padding SIMÉTRICO, e não só à direita: a faixa tem borda e selo encostados na quina,
        // e um padding de um lado só recorta o outro. É a mesma armadilha do `overflow-y-auto` na
        // web (constituição, §"armadilhas de web").
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
    ) {
        if (canAdd) {
            // `key` fixa, e FORA do `items`: é ela que mantém o quadradinho do "+" no lugar quando
            // a lista de fotos muda inteira.
            item(key = "kmplib-photo-strip-add") {
                Column(
                    modifier = Modifier
                        .size(itemSize)
                        .clip(canto)
                        .background(scheme.surfaceVariant.copy(alpha = 0.4f))
                        .border(1.dp, scheme.outlineVariant, canto)
                        .clickable(onClick = onAdd)
                        .semantics { contentDescription = addLabel },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = addLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                    )
                }
            }
        }

        items(items, key = { it.id }) { foto ->
            val ehCapa = foto.id == items.firstOrNull()?.id && foto.url != null
            Box(
                modifier = Modifier
                    .size(itemSize)
                    .clip(canto)
                    .background(scheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(
                        width = if (ehCapa) 2.dp else 1.dp,
                        color = if (ehCapa) scheme.primary else scheme.outlineVariant,
                        shape = canto,
                    )
                    .then(
                        when {
                            foto.failed && onRetry != null ->
                                Modifier.clickable { onRetry(foto) }
                                    .semantics { contentDescription = retryLabel }
                            foto.url != null && onMakeCover != null ->
                                Modifier.clickable { onMakeCover(foto) }
                                    .semantics { contentDescription = coverLabel }
                            else -> Modifier
                        },
                    ),
            ) {
                when {
                    foto.failed -> Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = scheme.error,
                        modifier = Modifier.align(Alignment.Center).size(24.dp),
                    )

                    foto.url == null -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(24.dp),
                        strokeWidth = 2.dp,
                        color = scheme.primary,
                    )

                    else -> AsyncImage(
                        model = foto.url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // O "x" existe inclusive durante o upload: uma foto escolhida por engano não
                // precisa esperar o envio terminar para sair da frente.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(scheme.scrim.copy(alpha = 0.55f))
                        .clickable { onRemove(foto) }
                        .semantics { contentDescription = removeLabel },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = scheme.inverseOnSurface,
                        modifier = Modifier.align(Alignment.Center).size(14.dp),
                    )
                }

                if (ehCapa) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = coverLabel,
                        tint = scheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .size(16.dp),
                    )
                }
            }
        }
    }
}
