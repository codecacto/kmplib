package br.com.codecacto.kmplib.ui.components.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * O vídeo **em tela cheia**, sobre fundo preto, com um X para sair.
 *
 * ## Por que não embutir o player no meio da tela
 *
 * Foi a primeira tentativa, e ela falha de um jeito específico: dentro de uma coluna com
 * `verticalScroll`, a view nativa de vídeo divide a árvore de composição com o conteúdo que rola por
 * cima dela. O resultado relatado é sempre o mesmo trio — **pisca, fica preta e o áudio toca por
 * baixo**, sem controles alcançáveis. E o pior: sem um jeito óbvio de PARAR, porque os controles do
 * player estão dentro do retângulo que não está sendo desenhado.
 *
 * Em tela cheia nada disso existe: a superfície é só do vídeo, os controles do próprio player ficam
 * acessíveis, e o X garante a saída mesmo que o player não desenhe nada.
 *
 * ## Como se usa
 *
 * O gatilho continua sendo um cartão com a capa do vídeo — é ele que ocupa o lugar na tela e não
 * custa um processo de renderização a quem talvez nem vá assistir:
 *
 * ```tsx
 * var assistindo by remember { mutableStateOf(false) }
 *
 * CapaDoVideo(onClick = { assistindo = true })
 *
 * if (assistindo) {
 *     VideoPlayerDialog(source = fonte, onDismiss = { assistindo = false })
 * }
 * ```
 *
 * Sair fecha e **descarta o player** — é o `onRelease` de cada plataforma que interrompe a
 * reprodução. Sem ele o áudio continuaria tocando depois do X.
 */
@Composable
fun VideoPlayerDialog(
    source: VideoSource,
    onDismiss: () -> Unit,
    autoPlay: Boolean = true,
) {
    // `External` não se toca aqui: quem chama abre no navegador. Um diálogo preto e vazio seria
    // pior do que sair do app.
    if (source is VideoSource.External) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            // Sem isto o diálogo herda a largura de "caixinha de alerta" do sistema, e o vídeo fica
            // num selo no meio da tela.
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            VideoPlayerView(
                source = source,
                autoPlay = autoPlay,
                // 16:9 centrado: esticar para a tela inteira deformaria o quadro em aparelho alto.
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )

            // O X é a garantia de saída: se o player não desenhar — rede fora, vídeo removido —,
            // ele continua sendo o caminho para fora. Fica no alto, fora do quadro do vídeo em
            // retrato, e por cima dele em paisagem.
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fechar o vídeo",
                    tint = Color.White,
                )
            }
        }
    }
}
