package br.com.codecacto.kmplib.ads.custom

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.size.Size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * Espera a **arte** do intersticial estar decodificada antes de o anúncio entrar na tela (2.246.0).
 *
 * Até a 2.245.0 o diálogo abria assim que a LISTA chegava, e a imagem começava a baixar só depois:
 * durante o download (1,3–1,6 s do Firebase Storage para uma arte 1440×2560, mais em rede móvel ou
 * no emulador) a pessoa via uma **tela preta com só o "X"** — e, com a URL fora do ar, via isso para
 * sempre, com a impressão já contada. Agora:
 *
 * - URL em branco → `false` sem tentar;
 * - carga terminou com sucesso dentro de [timeout] → `true` (a arte fica no cache de memória e o
 *   `AsyncImage` do diálogo a pinta no primeiro frame);
 * - erro de rede/decodificação, ou o teto estourou → `false`: o intersticial é **pulado**, sem
 *   impressão e sem `onShown`.
 *
 * [load] é a carga de fato (Coil no app; um dublê no teste) — devolve `true` se a imagem decodificou.
 */
internal suspend fun awaitInterstitialCreative(
    imageUrl: String,
    timeout: Duration,
    load: suspend (String) -> Boolean,
): Boolean {
    if (imageUrl.isBlank()) return false
    return withTimeoutOrNull(timeout) {
        try {
            load(imageUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
    } ?: false
}

/**
 * A carga real pelo Coil, no **tamanho da janela** (o intersticial ocupa a tela toda): a arte
 * entra no cache de memória já no tamanho em que o `AsyncImage` vai pedi-la (precisão inexata, então
 * o cache serve), sem decodificar os 1440×2560 originais (~15 MB de bitmap) à toa.
 */
@Composable
internal fun rememberCreativeLoader(): suspend (String) -> Boolean {
    val context: PlatformContext = LocalPlatformContext.current
    val window = LocalWindowInfo.current.containerSize
    return loader@{ url ->
        val request = ImageRequest.Builder(context)
            .data(url)
            .apply {
                if (window.width > 0 && window.height > 0) size(Size(window.width, window.height))
            }
            .precision(Precision.INEXACT)
            .build()
        SingletonImageLoader.get(context).execute(request) is SuccessResult
    }
}
