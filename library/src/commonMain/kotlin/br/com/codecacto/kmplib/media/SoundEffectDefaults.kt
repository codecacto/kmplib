package br.com.codecacto.kmplib.media

/**
 * Constantes e regras puras do módulo de efeitos sonoros — o que precisa valer **igual** nas duas
 * plataformas, escrito uma vez e coberto por teste sem aparelho.
 */
object SoundEffectDefaults {

    /**
     * Vozes simultâneas do pool (Android `SoundPool.Builder.setMaxStreams`).
     *
     * `1` — o default do `SoundPool` — **cortaria** o bipe anterior a cada novo disparo, que é
     * exatamente o sintoma que este módulo existe para não ter: com toques a ~300 ms o usuário
     * ouviria um clique picotado. Quatro cobre a sobreposição real de um efeito curto (~150 ms) sem
     * segurar canais de áudio à toa.
     */
    const val MAX_STREAMS: Int = 4

    /**
     * Acima disto o efeito deixa de ser "curto" e vira mídia — caso do
     * [AudioPlayer][br.com.codecacto.kmplib.media.AudioPlayer], não deste módulo.
     *
     * O `SoundPool` **decodifica para PCM em memória** (a doc do Android fala em ~1 MB de heap de
     * áudio por padrão) e o *System Sound Services* do iOS limita o som a 30 s. Passar disso não é
     * erro — carrega e toca —, mas rende aviso no log, porque o custo é RAM permanente enquanto o
     * app viver.
     */
    const val RECOMMENDED_MAX_BYTES: Int = 1 * 1024 * 1024

    /** Subdiretório criado no cache/temporário do app para os efeitos materializados. */
    const val CACHE_DIRECTORY: String = "kmplib_sfx"

    /** Tag de log do módulo. */
    internal const val TAG: String = "SoundEffect"

    /** `true` quando o áudio é grande demais para ser tratado como efeito curto. */
    fun isOversized(sizeBytes: Int): Boolean = sizeBytes > RECOMMENDED_MAX_BYTES

    /** Chave válida = não vazia depois de aparada. É o identificador do efeito no app. */
    fun isValidKey(key: String): Boolean = key.isNotBlank()

    /**
     * Nome do arquivo temporário de uma chave.
     *
     * A chave é do app (`"volta"`, `"meta atingida"`, `"pt-BR/beep"`), então **não** serve como nome
     * de arquivo: barra vira diretório inexistente, e caractere fora do ASCII varia por sistema de
     * arquivos. Aqui ela é reduzida a `[a-z0-9_-]` **mais um hash estável da chave original** — sem
     * o hash, `"a/b"` e `"a-b"` colidiriam no mesmo arquivo e o segundo `load` sobrescreveria o áudio
     * do primeiro efeito, calado.
     */
    fun fileNameFor(key: String, format: SoundEffectFormat): String {
        val slug = key.lowercase()
            .map { if (it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-') it else '_' }
            .joinToString("")
            .trim('_')
            .take(MAX_SLUG_LENGTH)
            .ifEmpty { "sfx" }
        return "${slug}_${stableHash(key)}.${format.fileExtension}"
    }

    private const val MAX_SLUG_LENGTH = 32

    /**
     * Hash determinístico (FNV-1a 32 bits) em hexadecimal.
     *
     * `String.hashCode()` **não** serve: o contrato do Kotlin não garante o mesmo valor entre JVM e
     * Kotlin/Native, e o nome do arquivo apareceria diferente por plataforma — o tipo de divergência
     * que só se descobre depurando no aparelho.
     */
    internal fun stableHash(value: String): String {
        var hash = 0x811C9DC5u
        for (char in value) {
            hash = hash xor (char.code.toUInt() and 0xFFu)
            hash *= 0x01000193u
        }
        return hash.toString(16).padStart(8, '0')
    }
}
