package br.com.codecacto.kmplib.core.prefs

import br.com.codecacto.kmplib.core.context.DesktopAppContext
import br.com.codecacto.kmplib.core.storage.writeFileDurably
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop (2.353.0): `preferences.json` na pasta de dados do app (`DesktopAppContext.appDataDirectory`).
 *
 * Mesmo papel do `SharedPreferences`/`NSUserDefaults`: preferência comum, em TEXTO — **nunca segredo**
 * (token vai no `secureTokenStorage` do `kmplib-auth`). Não é `java.util.prefs` de propósito: lá a
 * chave tem teto de 80 caracteres e o valor de 8 KB, e no Windows o dado vai para o registro.
 * Gravação atômica e durável ([writeFileDurably]); a instância é única por arquivo no processo, então
 * `observe*` vê o `set*` de qualquer chamador.
 */
actual fun appPreferences(): AppPreferences =
    DesktopAppPreferences.forFile(DesktopAppContext.appDataDirectory().resolve(DESKTOP_PREFERENCES_FILE))

internal const val DESKTOP_PREFERENCES_FILE = "preferences.json"

internal class DesktopAppPreferences private constructor(private val file: Path) : AppPreferences {

    private val lock = Mutex()
    private val changes = MutableSharedFlow<String>(extraBufferCapacity = 64)

    @Volatile
    private var cache: Map<String, JsonPrimitive>? = null

    private fun load(): Map<String, JsonPrimitive> {
        cache?.let { return it }
        val lido: Map<String, JsonPrimitive> = runCatching {
            if (!Files.isRegularFile(file)) return@runCatching emptyMap()
            Json.parseToJsonElement(Files.readString(file)).jsonObject
                .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.let { k to it } }.toMap()
        }.getOrElse {
            AppLogger.w(TAG, "preferences.json ilegível; começando vazio: ${it::class.simpleName}")
            emptyMap()
        }
        cache = lido
        return lido
    }

    private suspend fun read(key: String): JsonPrimitive? = withContext(Dispatchers.IO) { lock.withLock { load()[key] } }

    private suspend fun mutate(key: String, change: (MutableMap<String, JsonPrimitive>) -> Unit) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                val novo = load().toMutableMap().also(change)
                val json = JsonObject(novo as Map<String, JsonElement>).toString()
                writeFileDurably(file, json.toByteArray(Charsets.UTF_8))
                cache = novo
            }
        }
        changes.tryEmit(key)
    }

    override suspend fun getString(key: String, default: String): String = read(key)?.contentOrNull ?: default
    override suspend fun setString(key: String, value: String) = mutate(key) { it[key] = JsonPrimitive(value) }
    override suspend fun getBoolean(key: String, default: Boolean): Boolean = read(key)?.booleanOrNull ?: default
    override suspend fun setBoolean(key: String, value: Boolean) = mutate(key) { it[key] = JsonPrimitive(value) }
    override suspend fun getInt(key: String, default: Int): Int = read(key)?.intOrNull ?: default
    override suspend fun setInt(key: String, value: Int) = mutate(key) { it[key] = JsonPrimitive(value) }
    override suspend fun getLong(key: String, default: Long): Long = read(key)?.longOrNull ?: default
    override suspend fun setLong(key: String, value: Long) = mutate(key) { it[key] = JsonPrimitive(value) }
    override suspend fun getFloat(key: String, default: Float): Float = read(key)?.floatOrNull ?: default
    override suspend fun setFloat(key: String, value: Float) = mutate(key) { it[key] = JsonPrimitive(value) }
    override suspend fun has(key: String): Boolean = read(key) != null
    override suspend fun remove(key: String) = mutate(key) { it.remove(key) }
    override suspend fun clear() = mutate(WILDCARD) { it.clear() }

    private fun <T> observe(key: String, value: suspend () -> T): Flow<T> = flow {
        emit(value())
        changes.filter { it == key || it == WILDCARD }.collect { emit(value()) }
    }.distinctUntilChanged()

    override fun observeString(key: String, default: String): Flow<String> = observe(key) { getString(key, default) }
    override fun observeBoolean(key: String, default: Boolean): Flow<Boolean> = observe(key) { getBoolean(key, default) }
    override fun observeInt(key: String, default: Int): Flow<Int> = observe(key) { getInt(key, default) }
    override fun observeLong(key: String, default: Long): Flow<Long> = observe(key) { getLong(key, default) }

    companion object {
        private const val TAG = "AppPreferences"
        private const val WILDCARD = "*"
        private val instances = ConcurrentHashMap<Path, DesktopAppPreferences>()

        /** A instância única do [file] no processo. */
        fun forFile(file: Path): DesktopAppPreferences =
            instances.computeIfAbsent(file.toAbsolutePath().normalize()) { DesktopAppPreferences(it) }
    }
}
