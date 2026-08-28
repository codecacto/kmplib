package br.com.codecacto.kmplib.sync

import br.com.codecacto.kmplib.core.util.currentInstant

/**
 * Timestamp ISO-8601 (UTC) corrente — usado como updatedAt local provisório até o
 * servidor devolver o canônico. O servidor é a fonte de verdade do tempo.
 *
 * Reusa [currentInstant] do módulo `core/util` (kotlinx.datetime).
 */
fun isoNow(): String = currentInstant().toString()
