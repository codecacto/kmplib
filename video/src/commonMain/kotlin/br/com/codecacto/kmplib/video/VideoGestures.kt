package br.com.codecacto.kmplib.video

/**
 * O que um toque no quadro do [VideoPlayer] faz. Decisão pura, separada do gesto para ser testada.
 */
internal enum class VideoTapAction {
    /** Mostra/esconde os controles — o player de aula. */
    ToggleControls,

    /** Pausa/retoma — o vídeo de demonstração ([VideoPlayerConfig.tapToTogglePlayback]). */
    TogglePlayback,

    /** Nada: sem controles e sem toque para pausar (vitrine em autoplay). */
    None,
}

internal fun videoTapActionOf(controls: Boolean, tapToTogglePlayback: Boolean): VideoTapAction = when {
    tapToTogglePlayback -> VideoTapAction.TogglePlayback
    controls -> VideoTapAction.ToggleControls
    else -> VideoTapAction.None
}

/** Onde o botão de som aparece agora. */
internal enum class VideoSoundButtonPlacement {
    /** Não aparece. */
    None,

    /** Na barra inferior dos controles, ao lado da velocidade. */
    ControlBar,

    /** Sozinho no canto inferior direito — quando o player não tem controles. */
    Corner,
}

/**
 * Onde desenhar o botão de som. Com controles, ele mora na barra e some junto com ela; sem
 * controles, fica sempre no canto — é a única forma de quem nasceu mudo conseguir ouvir.
 * Em erro não aparece: o painel de "tentar de novo" é o que importa ali.
 */
internal fun videoSoundButtonPlacementOf(
    soundControl: Boolean,
    controls: Boolean,
    controlsVisible: Boolean,
    isError: Boolean,
): VideoSoundButtonPlacement = when {
    !soundControl || isError -> VideoSoundButtonPlacement.None
    !controls -> VideoSoundButtonPlacement.Corner
    controlsVisible -> VideoSoundButtonPlacement.ControlBar
    else -> VideoSoundButtonPlacement.None
}

/** Os ids de automação (Maestro) do player. Desde 2.284.0. */
object VideoPlayerTestTags {
    /** O quadro do vídeo — é nele que se toca para pausar com `tapToTogglePlayback`. */
    const val FRAME: String = "video-quadro"

    /** O botão de ligar/desligar o som. */
    const val SOUND: String = "video-btn-som"
}
