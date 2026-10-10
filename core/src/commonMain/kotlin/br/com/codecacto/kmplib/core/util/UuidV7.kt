package br.com.codecacto.kmplib.core.util

import kotlin.random.Random

/**
 * **UUID versão 7** (RFC 9562 §5.7) em texto canônico minúsculo — `018f3c2a-7b1e-7c3d-9a4b-…` (2.287.0).
 *
 * É o id que o **cliente** gera para tornar uma escrita idempotente: a mensagem da conversa, o
 * comentário de revisão, o envio da fila. O servidor guarda o id que recebeu; o reenvio do mesmo id
 * devolve o registro existente em vez de criar outro. Por isso ele nasce **antes** da primeira
 * tentativa e é persistido junto da pendência — gerar de novo a cada tentativa duplicaria.
 *
 * Por que v7 e não v4: os 48 bits iniciais são o instante em milissegundos (Unix), então ids gerados
 * em sequência **ordenam por criação** — índice B-tree do Postgres sem fragmentação, e ordem estável
 * para o cliente desempatar duas mensagens do mesmo segundo. Os 74 bits restantes são aleatórios
 * (`Random.Default`, semeado pelo sistema): colisão exige dois aparelhos no mesmo milissegundo com
 * os mesmos 74 bits.
 *
 * Não é segredo nem capacidade: não use como token.
 *
 * @param nowMillis instante Unix em ms (injetável para teste).
 * @param random fonte dos bits aleatórios (injetável para teste).
 */
fun newUuidV7(nowMillis: Long = currentTimeMillis(), random: Random = Random.Default): String {
    require(nowMillis >= 0) { "instante negativo" }
    val bytes = ByteArray(16)
    random.nextBytes(bytes)
    val ts = nowMillis and 0xFFFF_FFFF_FFFFL
    for (i in 0 until 6) {
        bytes[i] = ((ts ushr (8 * (5 - i))) and 0xFF).toByte()
    }
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte() // versão 7
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte() // variante RFC 9562 (10xx)
    val hex = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-" +
        hex.substring(16, 20) + "-" + hex.substring(20, 32)
}

/** `true` se [value] é um UUID em texto canônico (qualquer versão, maiúsculas aceitas). */
fun isCanonicalUuid(value: String): Boolean =
    value.length == 36 && value.withIndex().all { (i, c) ->
        if (i == 8 || i == 13 || i == 18 || i == 23) c == '-' else c.isDigit() || c.lowercaseChar() in 'a'..'f'
    }

/** O instante (ms Unix) gravado num UUID v7, ou `null` se [value] não for v7 canônico. */
fun uuidV7TimestampMillis(value: String): Long? {
    if (!isCanonicalUuid(value) || value[14] != '7') return null
    val hex = value.substring(0, 8) + value.substring(9, 13)
    return hex.toLongOrNull(16)
}
