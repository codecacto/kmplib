package br.com.codecacto.kmplib.ui.components.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import br.com.codecacto.kmplib.platform.getUrlLauncher

/**
 * Desktop (2.353.0): a JVM não tem player de vídeo embutível no Compose sem um runtime nativo
 * (VLC/JavaFX). O vídeo abre FORA, no navegador padrão (YouTube pela página `watch`, arquivo pela
 * URL dele) — a mesma saída honesta que a lib usa para [VideoSource.External].
 */
actual class VideoLauncher {
    actual fun play(source: VideoSource, compact: Boolean) {
        getUrlLauncher().openUrl(externalVideoUrl(source))
    }
}

internal fun externalVideoUrl(source: VideoSource): String = when (source) {
    is VideoSource.YouTube -> "https://www.youtube.com/watch?v=${source.videoId}"
    is VideoSource.File -> source.url
    is VideoSource.External -> source.url
}

@Composable
actual fun rememberVideoLauncher(): VideoLauncher = remember { VideoLauncher() }

/** Desktop: mostra a capa; o toque abre o vídeo no navegador (ver [VideoLauncher]). */
@Composable
actual fun VideoPlayerInline(
    source: VideoSource?,
    modifier: Modifier,
    onExternal: () -> Unit,
    montarDeSaida: Boolean,
    capa: @Composable (aoTocar: () -> Unit) -> Unit,
) {
    capa {
        if (source == null || source is VideoSource.External) onExternal()
        else getUrlLauncher().openUrl(externalVideoUrl(source))
    }
}
