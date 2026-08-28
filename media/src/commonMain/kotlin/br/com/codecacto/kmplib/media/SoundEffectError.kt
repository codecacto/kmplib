package br.com.codecacto.kmplib.media

/**
 * **Por que o efeito não carregou ou não tocou** — tipado, nunca exceção crua chegando à tela.
 *
 * O som é uma das vias de confirmação de uma ação (som + vibração + sinal visual), nunca a única:
 * derrubar a contagem porque o áudio falhou é pior do que ficar mudo. Por isso nenhuma operação
 * lança; todas devolvem [SoundEffectOutcome] e registram log.
 *
 * A lib não traz texto de usuário para estes casos de propósito — a frase é do produto, e o idioma
 * vem do aparelho (padrão da fábrica).
 */
sealed interface SoundEffectError {

    /**
     * Android sem `KmpLib.init(context)` no `Application.onCreate()`, ou plataforma sem mecanismo
     * de efeito sonoro. Nada toca e nada estoura.
     */
    data object NotInitialized : SoundEffectError

    /**
     * Os bytes não são um áudio utilizável: vazios, truncados, formato não reconhecido
     * ([SoundEffectFormat.UNKNOWN]) ou recusados pelo decodificador da plataforma.
     */
    data object InvalidAudio : SoundEffectError

    /** Não foi possível materializar o áudio no diretório temporário do app (disco cheio, I/O). */
    data object StorageFailure : SoundEffectError

    /** `play` de uma chave que nunca foi carregada (ou que já saiu por `unload`). */
    data object NotLoaded : SoundEffectError

    /** A instância já sofreu `release`; criar outra é o caminho. */
    data object Released : SoundEffectError

    /** Chave em branco — não identifica efeito nenhum. */
    data object InvalidKey : SoundEffectError

    /** Falha não classificada; [message] é diagnóstico (log), não texto de tela. */
    data class Unknown(val message: String?) : SoundEffectError
}

/**
 * Resultado de uma operação de efeito sonoro. Sucesso ou [SoundEffectError] — sem `throw`, porque
 * `play` é chamado no caminho mais quente do app (a cada toque) e ninguém envolve um bipe em
 * `try/catch`.
 */
sealed interface SoundEffectOutcome {

    /** A operação foi aplicada. */
    data object Success : SoundEffectOutcome

    /** A operação não foi aplicada; [error] diz por quê. */
    data class Failure(val error: SoundEffectError) : SoundEffectOutcome

    /** Atalho para `is Success`. */
    val isSuccess: Boolean get() = this is Success

    /** O erro, quando houve; `null` no sucesso. */
    val errorOrNull: SoundEffectError? get() = (this as? Failure)?.error
}

/** Atalho interno para falhar com um erro tipado. */
internal fun failWith(error: SoundEffectError): SoundEffectOutcome =
    SoundEffectOutcome.Failure(error)
