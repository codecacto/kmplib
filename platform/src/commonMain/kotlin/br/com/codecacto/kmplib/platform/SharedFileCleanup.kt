package br.com.codecacto.kmplib.platform

/**
 * Regras (puras) da limpeza dos arquivos que o [ShareHandler] cria para compartilhar.
 *
 * ### Por que isto existe
 *
 * `shareFile`/`shareImage` **precisam** materializar um arquivo: no Android o `ACTION_SEND` entrega
 * uma `content://` do `FileProvider`, no iOS o `UIActivityViewController` recebe uma `file://`. Até a
 * kmplib 2.104.0 esse arquivo **nunca era apagado** — nem depois do share, nem no boot seguinte.
 *
 * Isso é vazamento de dado, não só lixo: o app exporta um cofre/relatório em texto claro, o usuário
 * depois **apaga o registro dentro do app** — que é o que a Política de Privacidade manda fazer — e a
 * cópia exportada continua no armazenamento do app, numa pasta que nenhuma tela mostra e que nenhuma
 * ação do app remove.
 *
 * ### A semântica escolhida: purgar por IDADE, não "apagar depois do share"
 *
 * Apagar logo após disparar o chooser **quebraria o compartilhamento**: o `ACTION_SEND` é assíncrono
 * e o app receptor lê a URI depois (WhatsApp, Drive e e-mail leem em segundo plano, às vezes minutos
 * depois, com o nosso processo já em background ou morto). Não existe, no Android, callback de "o
 * receptor terminou de ler".
 *
 * Então:
 * - **Android** — o arquivo novo é gravado num diretório dedicado e, **antes** de gravar, tudo o que
 *   já passou de [DEFAULT_SHARED_FILE_TTL_MILLIS] é apagado. O arquivo do share em curso é sempre o
 *   mais novo, então nunca é a vítima da própria limpeza. Somado ao [ShareHandler.clearSharedFiles]
 *   chamado no bootstrap, a cópia vive no máximo até o próximo uso do app.
 * - **iOS** — além da purga por idade, o `UIActivityViewController` tem
 *   `completionWithItemsHandler`, que é o sinal **oficial** de que a folha de compartilhamento
 *   terminou; ali o arquivo é apagado na hora. É o único lugar onde a plataforma permite ser preciso,
 *   e a lib usa (não confiar no "o sistema limpa o `NSTemporaryDirectory()` eventualmente" — e
 *   "eventualmente" não é coisa que se escreva numa política de privacidade).
 */

/**
 * Idade a partir da qual um arquivo compartilhado é considerado resíduo (1 hora).
 *
 * Generoso de propósito: cobre o app receptor que faz upload demorado (anexo de e-mail grande, Drive
 * em rede ruim) sem deixar a cópia sobreviver ao dia.
 */
const val DEFAULT_SHARED_FILE_TTL_MILLIS: Long = 60L * 60L * 1000L

/** Nome do diretório dedicado onde os arquivos de compartilhamento são gravados. */
const val SHARED_FILES_DIRECTORY: String = "shared_files"

/** Nome usado quando o nome informado não sobrevive à sanitização. */
const val FALLBACK_SHARED_FILE_NAME: String = "arquivo"

/** Teto de tamanho do nome (a extensão é preservada — é ela que decide o app que abre). */
private const val MAX_SHARED_FILE_NAME_LENGTH = 128

/**
 * Decide se um arquivo do diretório de compartilhamento deve ser apagado.
 *
 * @param olderThanMillis `0` (ou negativo) apaga **tudo** — use só em ação explícita de "limpar
 *   dados", nunca no meio de um share em curso.
 */
internal fun shouldPurgeSharedFile(
    lastModifiedMillis: Long,
    nowMillis: Long,
    olderThanMillis: Long,
): Boolean {
    if (olderThanMillis <= 0L) return true
    // Data no futuro (relógio do aparelho alterado) preserva o arquivo: melhor manter lixo por um
    // ciclo que apagar o arquivo de um share que está acontecendo agora.
    val idade = nowMillis - lastModifiedMillis
    return idade >= olderThanMillis
}

/**
 * Torna o nome informado pelo app seguro como nome de arquivo.
 *
 * O nome vem do chamador (às vezes de dado do usuário: "Cofre de João/Maria.json"). Sem isto, um
 * separador escreveria **fora** do diretório de compartilhamento — e `..` escreveria fora do
 * sandbox do app.
 */
fun sanitizeSharedFileName(fileName: String): String {
    val limpo = fileName.trim()
        .map { c ->
            when {
                c == '/' || c == '\\' || c == ':' || c.code < 0x20 -> '_'
                else -> c
            }
        }
        .joinToString("")
        .trim('.', ' ')
    // Nome que sobrou só com o caractere de substituição ("///" → "___") não diz nada a quem recebe
    // o arquivo: vale mais o fallback legível.
    if (limpo.isBlank() || limpo.all { it == '_' }) return FALLBACK_SHARED_FILE_NAME
    if (limpo.length <= MAX_SHARED_FILE_NAME_LENGTH) return limpo

    val extensao = limpo.substringAfterLast('.', "")
    return if (extensao.isNotEmpty() && extensao.length < MAX_SHARED_FILE_NAME_LENGTH - 1) {
        val base = limpo.removeSuffix(".$extensao")
        base.take(MAX_SHARED_FILE_NAME_LENGTH - extensao.length - 1) + "." + extensao
    } else {
        limpo.take(MAX_SHARED_FILE_NAME_LENGTH)
    }
}
