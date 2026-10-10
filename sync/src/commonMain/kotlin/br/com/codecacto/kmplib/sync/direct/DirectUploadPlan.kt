package br.com.codecacto.kmplib.sync.direct

import br.com.codecacto.kmplib.core.storage.isValidBlobId

/**
 * Regras puras da fila de envio direto — sem disco, sem rede, sem relógio implícito. Tudo o que a
 * [DirectUploadOutbox] decide passa por aqui, e é aqui que os testes travam o comportamento.
 */

/** Margem antes do vencimento em que uma URL pré-assinada já não é usada (a parte pode demorar). */
const val DIRECT_UPLOAD_URL_SAFETY_MARGIN_MILLIS: Long = 2L * 60_000L

/** Teto de partes por envio (o do S3/R2 é 10.000). */
const val DIRECT_UPLOAD_MAX_PARTS: Int = 10_000

/** Menor parte que o S3/R2 aceita (exceto a última): 5 MiB. */
const val DIRECT_UPLOAD_MIN_PART_BYTES: Long = 5L * 1024 * 1024

/** Byte inicial e tamanho da parte [partNumber] (1-based) de um arquivo de [fileSize] bytes. */
fun directUploadPartRange(partNumber: Int, partSizeBytes: Long, fileSize: Long): LongRange? {
    if (partNumber < 1 || partSizeBytes <= 0L || fileSize <= 0L) return null
    val start = (partNumber - 1).toLong() * partSizeBytes
    if (start >= fileSize) return null
    val endExclusive = minOf(start + partSizeBytes, fileSize)
    return start until endExclusive
}

/**
 * `null` se a sessão devolvida pelo servidor serve para este arquivo; senão o motivo.
 *
 * Confere que o número de partes cobre o arquivo EXATAMENTE (nem parte vazia no fim, nem byte de
 * fora): enviar com uma sessão que não fecha só se descobre no `complete`, depois de subir tudo.
 */
fun directUploadSessionProblem(session: DirectUploadSession, fileSize: Long): String? = when {
    session.uploadId.isBlank() -> "uploadId em branco"
    session.partSizeBytes <= 0L -> "partSizeBytes inválido"
    session.partCount < 1 || session.partCount > DIRECT_UPLOAD_MAX_PARTS -> "partCount fora de 1..$DIRECT_UPLOAD_MAX_PARTS"
    fileSize <= 0L -> "arquivo vazio"
    (session.partCount - 1).toLong() * session.partSizeBytes >= fileSize -> "partes demais para o arquivo"
    session.partCount.toLong() * session.partSizeBytes < fileSize -> "partes de menos para o arquivo"
    else -> null
}

/** Partes que ainda faltam, em ordem crescente. */
fun directUploadMissingParts(job: DirectUploadJob): List<Int> {
    val session = job.session ?: return emptyList()
    return (1..session.partCount).filter { it !in job.completedParts }
}

/** Bytes já aceitos pelo storage (soma do tamanho real de cada parte concluída). */
fun directUploadedBytes(job: DirectUploadJob): Long {
    val session = job.session ?: return 0L
    return job.completedParts.keys.sumOf { n ->
        directUploadPartRange(n, session.partSizeBytes, job.sizeBytes)?.let { it.last - it.first + 1 } ?: 0L
    }
}

/** A URL da parte [partNumber], se existe e ainda vale com folga em [nowMillis]; senão `null`. */
fun directUploadUsableUrl(
    session: DirectUploadSession,
    partNumber: Int,
    nowMillis: Long,
    marginMillis: Long = DIRECT_UPLOAD_URL_SAFETY_MARGIN_MILLIS,
): String? {
    val url = session.partUrls[partNumber]?.takeIf { it.isNotBlank() } ?: return null
    val vence = session.urlsExpireAtMillis ?: return url
    return url.takeIf { nowMillis + marginMillis < vence }
}

/** As partes concluídas na forma do `complete`, em ordem. */
fun directUploadCompletedParts(job: DirectUploadJob): List<DirectUploadCompletedPart> =
    job.completedParts.entries.sortedBy { it.key }.map { DirectUploadCompletedPart(it.key, it.value) }

/** `true` se o envio pode subir agora para [accountId], pela conta, estado e recuo. */
fun directUploadIsDue(job: DirectUploadJob, accountId: String?, nowMillis: Long): Boolean =
    accountId != null && job.accountId == accountId && job.status != DirectUploadStatus.FAILED &&
        job.nextAttemptAtMillis <= nowMillis

/**
 * ETag como o storage devolveu, sem espaço em volta. As aspas **ficam**: o S3 as devolve e as aceita
 * de volta no `CompleteMultipartUpload`; tirá-las é o que alguns SDKs fazem e outros recusam.
 */
fun normalizeDirectUploadEtag(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() && it != "\"\"" }

/** `true` se [id] serve de nome de pasta do envio (mesma régua do `BlobStore`). */
fun isValidDirectUploadId(id: String): Boolean = isValidBlobId(id) && !id.startsWith(".")

/** A ordem de drenagem: mais antigo primeiro, desempate pelo id. */
internal fun directUploadDrainOrder(jobs: Collection<DirectUploadJob>): List<DirectUploadJob> =
    jobs.sortedWith(compareBy<DirectUploadJob> { it.createdAtMillis }.thenBy { it.id })
