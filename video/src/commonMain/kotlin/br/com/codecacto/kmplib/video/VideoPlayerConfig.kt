package br.com.codecacto.kmplib.video

/**
 * Como o player se comporta. Tudo tem default útil: um curso comum não configura nada.
 *
 * @param autoPlay dar play sozinho quando a mídia ficar pronta.
 * @param initialSpeed a velocidade em que a aula começa. O app costuma guardar a última escolha do
 *   aluno e devolvê-la aqui — a lib **não persiste** preferência, isso é do produto.
 * @param seekStepMillis o pulo dos botões de avançar/voltar. 10 s é o padrão da indústria.
 * @param keepScreenOn manter a tela acesa enquanto toca. Ligado: uma aula de 20 minutos sem toque
 *   na tela apagaria no meio. Usa o `KeepScreenOn` do `kmplib-platform`, que libera no descarte.
 * @param backgroundBehavior o que fazer quando o app vai para o segundo plano — ver
 *   [VideoBackgroundBehavior].
 * @param mediaSession publicar a sessão de mídia do sistema (título, artista, comandos de
 *   transporte). É o que faz o botão do fone de ouvido e os controles da tela de bloqueio
 *   funcionarem. Ligado por default; desligar só em vídeo curto de interface (uma apresentação de
 *   tela), onde aparecer na central de mídia é ruído.
 * @param positionReportIntervalMillis de quanto em quanto tempo [VideoPlayerState] chama o
 *   `onPosition` que o app usa para persistir "onde parou". `0` desliga. **Não é a taxa do relógio
 *   da tela** (essa é fixa e rápida): é a taxa do que vai ao servidor, e mandar isso a cada 250 ms
 *   seria um `PATCH` por quadro.
 * @param preferredSubtitleLanguage a legenda que já vem ligada, quando existir faixa nessa língua
 *   (`"pt-BR"`, `"pt"`, `"en"`). `null` = começar sem legenda.
 * @param loop recomeçar sozinho ao chegar ao fim, sem emenda visível — o vídeo curto de
 *   demonstração (um exercício, um gesto). Nunca chega a [VideoStatus.Ended]. Android:
 *   `Player.REPEAT_MODE_ONE`; iOS: `AVPlayerLooper` sobre `AVQueuePlayer`, que é a forma que a
 *   Apple indica para laço sem corte (o "voltar ao zero" no fim deixa um quadro preto entre as
 *   voltas). Desde 2.284.0.
 * @param startMuted nascer **sem som**. A pessoa pode ligar o som depois — pelo botão de som
 *   ([soundControl]) ou pelo app, com [VideoPlayerState.setMuted]. Enquanto mudo, o player
 *   **não interrompe a música** que a pessoa ouve em outro app: no Android não pede foco de áudio
 *   (volume `0`, `handleAudioFocus = false`); no iOS a sessão de áudio fica em `.ambient`, que mistura
 *   com os outros apps. Ao ligar o som, volta ao comportamento de mídia (foco no Android,
 *   `.playback` no iOS). Desde 2.284.0.
 * @param soundControl mostrar o botão de ligar/desligar o som. Default: ligado quando o vídeo nasce
 *   mudo — quem nasce mudo precisa de uma saída para ouvir; quem nasce com som usa o volume do
 *   aparelho, como sempre. Com `controls = false` no [VideoPlayer], o botão fica sozinho no canto
 *   inferior direito (é o único controle à mostra). Desde 2.284.0.
 * @param tapToTogglePlayback tocar no vídeo **pausa/retoma** em vez de mostrar/esconder os
 *   controles. É o gesto do vídeo de demonstração, que costuma vir com `controls = false`. Com
 *   controles ligados, o toque pausa e traz os controles (pausado, eles ficam). O leitor de tela
 *   ganha a mesma ação no quadro ("Pausar"/"Reproduzir"). Desde 2.284.0.
 *
 * ### O vídeo de demonstração em laço (mudo, toque para pausar)
 * ```kotlin
 * val demo = rememberVideoPlayerState(
 *     media = VideoMedia(url = exercicio.videoUrl),
 *     config = VideoPlayerConfig(
 *         loop = true,
 *         startMuted = true,
 *         tapToTogglePlayback = true,
 *         mediaSession = false,              // demonstração não vai para a central de mídia
 *         positionReportIntervalMillis = 0L, // nada a "retomar"
 *     ),
 * )
 * VideoPlayer(demo, Modifier.fillMaxWidth().aspectRatio(16f / 9f), controls = false)
 * ```
 */
data class VideoPlayerConfig(
    val autoPlay: Boolean = true,
    val initialSpeed: Float = VIDEO_SPEED_NORMAL,
    val seekStepMillis: Long = 10_000L,
    val keepScreenOn: Boolean = true,
    val backgroundBehavior: VideoBackgroundBehavior = VideoBackgroundBehavior.Pause,
    val mediaSession: Boolean = true,
    val positionReportIntervalMillis: Long = 10_000L,
    val preferredSubtitleLanguage: String? = null,
    val loop: Boolean = false,
    val startMuted: Boolean = false,
    val soundControl: Boolean = startMuted,
    val tapToTogglePlayback: Boolean = false,
)

/**
 * O que acontece quando o app sai do primeiro plano.
 *
 * Não há default "certo" universal, e é por isso que é configuração: numa aula em vídeo, sair do
 * app é sair da aula ([Pause]); num produto em que o áudio é o conteúdo (uma palestra), continuar
 * é o esperado ([ContinueAudio]).
 */
enum class VideoBackgroundBehavior {
    /** Pausa ao perder o primeiro plano e **não** retoma sozinho na volta — quem dá play é o dedo. */
    Pause,

    /**
     * O áudio continua com o app em segundo plano.
     *
     * ⚠️ **Exige preparo do app, nas duas plataformas**, e a lib não pode fazer por ele:
     * - **iOS:** a capability *Background Modes → Audio* no target (sem ela o sistema silencia o
     *   app ao sair, e não há erro). A `AVAudioSession` em `.playback` já é configurada aqui.
     * - **Android:** reprodução com o app fechado exige um `MediaSessionService` em primeiro plano
     *   (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`). Sem ele, o áudio continua enquanto o processo viver
     *   — que é o caso comum de "trocar de app por um minuto" —, mas o sistema pode encerrá-lo.
     *   Ver o CHANGELOG da 2.190.0 e `docs/backlog.md`.
     */
    ContinueAudio,
}
