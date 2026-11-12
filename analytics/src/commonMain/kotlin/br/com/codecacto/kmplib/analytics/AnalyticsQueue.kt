package br.com.codecacto.kmplib.analytics

import br.com.codecacto.kmplib.core.storage.BlobStore
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Um evento na fila — o que é do MOMENTO (tela, nome, propriedades, hora, idioma). O que é do aparelho
 * e da instalação (sistema, tela, id anônimo, User-Agent) entra só no envio.
 *
 * @property id `<ms 13 dígitos>-<sequência 10 dígitos>-<8 hex>` ([newAnalyticsEventId]): ordena pela
 *   hora e, no mesmo milissegundo, pela ordem de chamada — é a ordem da fila em disco.
 * @property url caminho já saneado (`/app/…`).
 * @property name `null` = visita de tela (pageview); preenchido = evento.
 * @property timestamp segundos desde a época — vai no `timestamp` do Umami, então o evento que ficou
 *   na fila sem rede chega com a hora em que aconteceu, não com a do envio.
 */
@Serializable
internal data class QueuedAnalyticsEvent(
    val id: String,
    val url: String,
    val title: String? = null,
    val name: String? = null,
    val data: JsonObject? = null,
    val timestamp: Long,
    val language: String? = null,
)

/** A fila de eventos à espera de envio. Implementações nunca lançam (falha vira log + `false`/vazio). */
internal interface AnalyticsQueue {
    suspend fun append(event: QueuedAnalyticsEvent): Boolean

    /** Os [max] mais antigos, na ordem em que entraram. */
    suspend fun oldest(max: Int): List<QueuedAnalyticsEvent>

    suspend fun remove(ids: Collection<String>)

    suspend fun size(): Int
}

internal val analyticsJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

@OptIn(ExperimentalAtomicApi::class)
private val eventSequence = AtomicLong(0L)

/**
 * Id do evento, em ordem: milissegundos do [nowMillis] e, empatando, a sequência do processo (o UUID v7
 * não ordena dentro do mesmo milissegundo). O sufixo aleatório evita colisão entre processos.
 */
@OptIn(ExperimentalAtomicApi::class)
internal fun newAnalyticsEventId(nowMillis: Long): String {
    val ms = nowMillis.coerceAtLeast(0L).toString().padStart(13, '0')
    val seq = (eventSequence.fetchAndIncrement() % 10_000_000_000L).toString().padStart(10, '0')
    val rnd = Random.nextInt().toUInt().toString(16).padStart(8, '0')
    return "$ms-$seq-$rnd"
}

/** Fila em memória — teste, e o recuo quando o disco não está disponível. */
internal class InMemoryAnalyticsQueue : AnalyticsQueue {
    private val events = ArrayList<QueuedAnalyticsEvent>()

    override suspend fun append(event: QueuedAnalyticsEvent): Boolean {
        events += event
        return true
    }

    override suspend fun oldest(max: Int): List<QueuedAnalyticsEvent> = events.sortedBy { it.id }.take(max)

    override suspend fun remove(ids: Collection<String>) {
        val set = ids.toSet()
        events.removeAll { it.id in set }
    }

    override suspend fun size(): Int = events.size
}

/**
 * Fila em DISCO sobre o [BlobStore] da lib (fora do backup, `noBackupFilesDir`/`NSURLIsExcludedFromBackupKey`):
 * um arquivo por evento (`ev-<uuidv7>.json`), então o evento sobrevive ao app fechado e ao aparelho sem
 * rede, e ler/apagar um lote não reescreve a fila inteira. A ordem é a do nome (UUID v7 = hora).
 * Arquivo ilegível é apagado (não trava a fila para sempre).
 */
internal class BlobAnalyticsQueue(private val store: BlobStore) : AnalyticsQueue {

    override suspend fun append(event: QueuedAnalyticsEvent): Boolean = safe(false) {
        store.write(blobId(event.id), analyticsJson.encodeToString(QueuedAnalyticsEvent.serializer(), event).encodeToByteArray())
    }

    override suspend fun oldest(max: Int): List<QueuedAnalyticsEvent> = safe(emptyList()) {
        val ids = eventBlobIds().take(max)
        val out = ArrayList<QueuedAnalyticsEvent>(ids.size)
        for (blob in ids) {
            val bytes = store.read(blob)
            val event = bytes?.let {
                runCatching { analyticsJson.decodeFromString(QueuedAnalyticsEvent.serializer(), it.decodeToString()) }.getOrNull()
            }
            if (event == null) store.delete(blob) else out += event
        }
        out
    }

    override suspend fun remove(ids: Collection<String>) {
        safe(Unit) { ids.forEach { store.delete(blobId(it)) } }
    }

    override suspend fun size(): Int = safe(0) { eventBlobIds().size }

    private suspend fun eventBlobIds(): List<String> =
        store.ids().filter { it.startsWith(PREFIX) && it.endsWith(SUFFIX) }.sorted()

    private inline fun <T> safe(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        AppLogger.w(TAG, "Fila de analytics indisponível (${e::class.simpleName}).")
        fallback
    }

    companion object {
        private const val TAG = "Analytics"
        private const val PREFIX = "ev-"
        private const val SUFFIX = ".json"

        /** Diretório da fila (dentro da área privada do app, fora do backup). */
        const val DIRECTORY: String = "kmplib_analytics"

        fun blobId(eventId: String): String = "$PREFIX$eventId$SUFFIX"
    }
}
