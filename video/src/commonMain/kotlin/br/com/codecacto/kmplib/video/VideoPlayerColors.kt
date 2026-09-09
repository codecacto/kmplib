package br.com.codecacto.kmplib.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * As cores do *chrome* do player.
 *
 * ⚠️ **Este é o único lugar da lib onde a cor NÃO vem do tema, e é de propósito.** O fundo aqui não
 * é a `surface` do app: é o vídeo, que é uma imagem qualquer. Branco sobre véu escuro é o que todo
 * player usa (YouTube, Netflix, a AVKit da Apple) porque é o que se enxerga sobre qualquer quadro —
 * pintar o botão de pausa com a `primary` da marca dá um ícone lilás invisível sobre uma quadra de
 * saibro.
 *
 * O [accent] é a exceção da exceção: a parte já assistida da barra pode, sim, ser a cor da marca,
 * porque é um traço fino sobre o véu. Por isso o default o lê do tema.
 */
@Immutable
data class VideoPlayerColors(
    /** Ícones e texto sobre o vídeo. */
    val chrome: Color = Color.White,
    /** Véu por trás dos controles, para o ícone não sumir num quadro claro. */
    val scrim: Color = Color.Black.copy(alpha = 0.45f),
    /** Fundo enquanto não há quadro (carregando, erro). */
    val background: Color = Color.Black,
    /** A parte já assistida da barra de progresso, e o botão "tentar de novo". */
    val accent: Color = Color.White,
    /** O trilho ainda não assistido. */
    val track: Color = Color.White.copy(alpha = 0.24f),
)

/** As cores default, com o [VideoPlayerColors.accent] vindo da marca do app. */
@Composable
fun defaultVideoPlayerColors(
    accent: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
): VideoPlayerColors = VideoPlayerColors(accent = accent)
