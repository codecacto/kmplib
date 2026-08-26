package br.com.codecacto.kmplib.media

/**
 * Índice `chave → identificador nativo` compartilhado pelas duas implementações.
 *
 * Existe em `commonMain` porque a parte que **vaza recurso quando erra** é a mesma nos dois lados:
 * recarregar uma chave já carregada precisa devolver o identificador antigo para quem o criou
 * descarregá-lo (no Android um `sampleId` do `SoundPool`, no iOS um `SystemSoundID`). Perder essa
 * devolução é um vazamento silencioso — o áudio continua tocando certo, e a memória só aparece
 * depois de N recargas. É essa invariante que a suíte protege, sem aparelho.
 *
 * [H] é o identificador nativo (`Int` no Android, `UInt` no iOS).
 *
 * **Não é thread-safe**: `load` é `suspend` e pertence a um dono só (a coroutine que prepara a
 * sessão); `play` apenas lê.
 */
internal class SoundEffectRegistry<H : Any> {

    private val handles = LinkedHashMap<String, H>()

    private var releasedFlag = false

    /** `true` depois de [releaseAll]. A instância não deve ser reutilizada. */
    val isReleased: Boolean get() = releasedFlag

    /** Chaves carregadas, na ordem em que entraram. */
    val keys: Set<String> get() = LinkedHashSet(handles.keys)

    /** Quantidade de efeitos carregados. */
    val size: Int get() = handles.size

    fun isLoaded(key: String): Boolean = handles.containsKey(key)

    fun handleOf(key: String): H? = handles[key]

    /**
     * Registra [handle] em [key] e devolve o **identificador anterior**, se havia — o chamador é
     * obrigado a descarregá-lo. Devolve `null` quando a chave é nova.
     */
    fun put(key: String, handle: H): H? {
        val previous = handles.put(key, handle)
        return previous
    }

    /** Remove [key] e devolve o identificador a descarregar, ou `null` se não havia. */
    fun remove(key: String): H? = handles.remove(key)

    /**
     * Marca a instância como liberada e devolve **todos** os identificadores para descarregar.
     * Chamadas seguintes devolvem lista vazia (release é idempotente).
     */
    fun releaseAll(): List<H> {
        val all = handles.values.toList()
        handles.clear()
        releasedFlag = true
        return all
    }

    /**
     * Valida a pré-condição comum de `load`/`play`: instância viva e chave utilizável.
     * Devolve `null` quando está tudo certo.
     */
    fun rejectionFor(key: String): SoundEffectError? = when {
        releasedFlag -> SoundEffectError.Released
        !SoundEffectDefaults.isValidKey(key) -> SoundEffectError.InvalidKey
        else -> null
    }
}
