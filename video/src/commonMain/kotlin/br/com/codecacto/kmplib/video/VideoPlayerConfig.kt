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
