package br.com.codecacto.kmplib.video.download

/**
 * **Espaço em disco: a conferência que acontece ANTES de começar.**
 *
 * Não basta olhar "cabe?": aparelho cheio até a última borda não é um aparelho funcionando — o
 * Android começa a matar processos e a câmera para de salvar foto. Por isso a conta desconta uma
 * **reserva** que a lib nunca ocupa.
 *
 * Quando o tamanho é desconhecido (`requiredBytes <= 0`, servidor sem `Content-Length`), a resposta
 * é [Unknown]: o download **é aceito** — recusar por não saber o tamanho impediria de baixar
 * qualquer coisa de um servidor mal configurado —, e se não couber ele falha no meio com
 * [MediaDownloadErrorKind.NoSpace].
 */
sealed interface MediaDownloadSpaceCheck {
    /** Cabe com folga. */
    data object Fits : MediaDownloadSpaceCheck

    /** Não cabe. [missingBytes] é quanto falta liberar — é o número que a tela mostra. */
    data class NotEnough(val missingBytes: Long) : MediaDownloadSpaceCheck

    /** O tamanho não é conhecido; segue-se em frente. */
    data object Unknown : MediaDownloadSpaceCheck
}

/**
 * 300 MB. A reserva que nunca é ocupada pelos downloads.
 *
 * Não é um número redondo por acaso: abaixo disso o Android dispara `ACTION_DEVICE_STORAGE_LOW`,
 * o sistema passa a recusar instalação e atualização de app e o `cacheDir` de todo mundo começa a
 * ser apagado. Encher o aparelho com aulas e deixar o celular do aluno nesse estado é pior do que
 * não baixar a aula.
 */
const val DEFAULT_RESERVED_SPACE_BYTES: Long = 300L * 1024 * 1024

/**
 * Confere se [requiredBytes] cabe em [availableBytes] preservando [reservedBytes]. Lógica pura.
 */
fun checkMediaDownloadSpace(
    requiredBytes: Long,
    availableBytes: Long,
    reservedBytes: Long = DEFAULT_RESERVED_SPACE_BYTES,
): MediaDownloadSpaceCheck {
    if (requiredBytes <= 0L) return MediaDownloadSpaceCheck.Unknown
    val utilizavel = availableBytes - reservedBytes.coerceAtLeast(0L)
    if (requiredBytes <= utilizavel) return MediaDownloadSpaceCheck.Fits
    return MediaDownloadSpaceCheck.NotEnough(missingBytes = requiredBytes - utilizavel.coerceAtLeast(0L))
}

/**
 * Quanto o aparelho tem de espaço livre, em bytes, no volume em que o app grava.
 *
 * Android: `StatFs` sobre `filesDir` (**não** o "espaço total do cartão" — o app grava no
 * armazenamento interno privado). iOS: `volumeAvailableCapacityForImportantUsageKey`, que é a
 * chave que a Apple manda usar para "conteúdo que o usuário pediu" — ela conta o que o sistema
 * pode liberar apagando caches de outros apps, e é bem maior (e mais honesta) que a capacidade
 * livre crua.
 *
 * `0` quando não dá para saber.
 */
expect fun availableStorageBytes(): Long

// ---------------------------------------------------------------------------------------------
// Expiração do direito de acesso
// ---------------------------------------------------------------------------------------------

/**
 * `true` se a cópia baixada já passou da validade.
 *
 * ⚠️ **Isto não é DRM, e a lib não finge que é.** O relógio é o do aparelho: quem atrasar a data do
 * celular passa por aqui. Existe para o caso honesto — estorno, reembolso, assinatura que venceu —
 * em que o aluno de boa-fé não deve continuar com a aula no bolso para sempre. A trava de verdade é
 * o servidor não renovar a URL assinada; esta é a metade que funciona **sem** rede, que é
 * justamente quando o servidor não pode ser consultado.
 *
 * `null` em [expiresAtMillis] = não expira.
 */
fun isMediaDownloadExpired(expiresAtMillis: Long?, nowMillis: Long): Boolean =
    expiresAtMillis != null && nowMillis >= expiresAtMillis

/** Os ids cuja validade já passou — o que [MediaDownloadManager.purgeExpired] apaga. */
fun expiredMediaDownloadIds(records: List<MediaDownloadRecord>, nowMillis: Long): List<String> =
    records.filter { isMediaDownloadExpired(it.expiresAtMillis, nowMillis) }.map { it.id }

// ---------------------------------------------------------------------------------------------
// Retomada depois de o app ser fechado
// ---------------------------------------------------------------------------------------------

/**
 * **Quem volta baixando quando o app abre de novo.**
 *
 * O requisito é este: trocar de tela, receber uma ligação ou o sistema matar o app **não pode**
 * fazer a aula recomeçar do zero nem parar para sempre. Os dois subsistemas nativos retomam
 * sozinhos o que estava em andamento; o que eles não sabem é o que o **produto** decidiu — o que
 * foi pausado à mão e o que expirou enquanto o app estava fechado.
 *
 * Fica de fora: o que terminou, o que o usuário pausou e o que expirou. Sobra o que estava na fila
 * ou baixando — que volta para a fila.
 */
fun mediaDownloadsToResume(
    records: List<MediaDownloadRecord>,
    nowMillis: Long,
): List<MediaDownloadRecord> = records.filter {
    !it.completed && !it.pausedByUser && !isMediaDownloadExpired(it.expiresAtMillis, nowMillis)
}

// ---------------------------------------------------------------------------------------------
// Renovação da URL assinada
// ---------------------------------------------------------------------------------------------

/**
 * Quantas vezes a URL assinada de um download é renovada automaticamente antes de a falha ir para
 * a tela.
 *
 * **Duas, igual ao player** (`MAX_RENOVACOES_DE_URL`), e pelo mesmo motivo: a primeira cobre o
 * token que venceu no meio da transferência — que num download de 40 minutos é o caso **normal**,
 * não a exceção —, a segunda cobre o relógio do aparelho adiantado. A terceira já é servidor
 * devolvendo lixo, e insistir vira laço: um download reenfileirado sem parar não trava tela nenhuma
 * e por isso ninguém percebe, até a conta do CDN chegar.
 */
const val MAX_DOWNLOAD_URL_RENEWALS: Int = 2

/**
 * Se vale pedir uma URL nova ao app, dada a falha e quantas renovações já foram gastas.
 *
 * Só [MediaDownloadErrorKind.Expired] renova. Erro de rede não: a URL está boa e pedir outra ao
 * servidor a cada oscilação de sinal transformaria um túnel de metrô numa enxurrada de requisições
 * de assinatura.
 */
fun shouldRenewDownloadUrl(
    kind: MediaDownloadErrorKind,
    renewalsSoFar: Int,
    maxRenewals: Int = MAX_DOWNLOAD_URL_RENEWALS,
): Boolean = kind == MediaDownloadErrorKind.Expired && renewalsSoFar < maxRenewals
