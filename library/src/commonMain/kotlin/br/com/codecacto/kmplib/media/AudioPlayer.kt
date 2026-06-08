package br.com.codecacto.kmplib.media

import kotlinx.coroutines.flow.StateFlow

/**
 * Estado de alto nível do [AudioPlayer].
 *
 * - [IDLE]: nenhum arquivo carregado / player recém-criado ou liberado.
 * - [LOADING]: preparando a fonte de áudio (decodificação/buffer).
 * - [PLAYING]: reproduzindo.
 * - [PAUSED]: pausado, posição preservada.
 * - [COMPLETED]: chegou ao fim do arquivo.
 * - [ERROR]: falha ao preparar/reproduzir (ver [AudioPlayer.lastError]).
 */
enum class AudioPlayerState {
    IDLE,
    LOADING,
    PLAYING,
    PAUSED,
    COMPLETED,
    ERROR
}

/**
 * Reprodutor de áudio multiplataforma para arquivos **locais** (M4A/AAC, MP3, WAV...).
 *
 * Implementações:
 * - Android: `MediaPlayer` ([br.com.codecacto.kmplib.media.AndroidAudioPlayer]).
 * - iOS: `AVAudioPlayer` ([br.com.codecacto.kmplib.media.IosAudioPlayer]).
 *
 * Obtenha uma instância via [createAudioPlayer]. Em ViewModels, lembre de chamar [release]
 * em `onCleared()` para liberar os recursos nativos.
 *
 * Exemplo (em um BaseViewModel da kmplib):
 * ```kotlin
 * private val player = createAudioPlayer()
 *
 * init {
 *     // observar progresso/estado para o State do MVI
 *     player.currentPosition.onEach { setState { copy(positionMs = it) } }.launchIn(viewModelScope)
 *     player.isPlaying.onEach { setState { copy(isPlaying = it) } }.launchIn(viewModelScope)
 * }
 *
 * fun onPlay(path: String) = player.play(path)
 * fun onPause() = player.pause()
 * fun onSeek(ms: Long) = player.seekTo(ms)
 *
 * override fun onCleared() { player.release(); super.onCleared() }
 * ```
 *
 * Threading: as chamadas são seguras de invocar a partir da main thread; os [StateFlow]
 * são atualizados pela implementação (a posição é emitida periodicamente enquanto toca).
 */
interface AudioPlayer {

    /** Posição atual de reprodução, em milissegundos. */
    val currentPosition: StateFlow<Long>

    /** Duração total do arquivo carregado, em milissegundos (0 enquanto desconhecida). */
    val duration: StateFlow<Long>

    /** `true` enquanto está efetivamente reproduzindo. */
    val isPlaying: StateFlow<Boolean>

    /** Estado de alto nível do player. */
    val state: StateFlow<AudioPlayerState>

    /** Última mensagem de erro quando [state] == [AudioPlayerState.ERROR]; caso contrário, `null`. */
    val lastError: StateFlow<String?>

    /**
     * Carrega e inicia a reprodução do arquivo em [filePath].
     *
     * Se já houver um arquivo carregado e for o mesmo caminho, retoma a partir da posição atual.
     * Se for um caminho diferente, troca a fonte e reinicia do começo.
     *
     * @param filePath Caminho **absoluto** do arquivo local (ex.: vindo do `FilePicker` /
     *   diretório de gravações do app). No iOS, aceita caminho de arquivo ou URL `file://`.
     */
    fun play(filePath: String)

    /** Pausa a reprodução, preservando a posição atual. Sem efeito se não estiver tocando. */
    fun pause()

    /**
     * Posiciona a reprodução em [positionMs] (clampado entre 0 e [duration]).
     * Funciona tanto pausado quanto tocando.
     */
    fun seekTo(positionMs: Long)

    /**
     * Para a reprodução e zera a posição, mantendo o arquivo carregado.
     * Use [release] para liberar recursos nativos definitivamente.
     */
    fun stop()

    /**
     * Libera os recursos nativos do player. Após chamar, a instância não deve ser reutilizada;
     * crie outra via [createAudioPlayer]. Obrigatório no `onCleared()` do ViewModel.
     */
    fun release()
}

/**
 * Cria a implementação de [AudioPlayer] para a plataforma atual.
 *
 * No Android, requer que [AudioPlayerHolder.init] tenha sido chamado no `Application.onCreate()`.
 */
expect fun createAudioPlayer(): AudioPlayer
