package br.com.codecacto.kmplib.core.prefs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow

/**
 * Implementação in-memory de [AppPreferences] — nada é gravado em disco, e o estado morre com o
 * processo.
 *
 * Comportamento espelha [AndroidAppPreferences]/[IosAppPreferences]:
 * - `observe*` re-emite quando `set*`/`remove`/`clear` é chamado.
 * - `clear()` emite o wildcard `"*"`.
 *
 * Mora no `commonMain`, e não no `commonTest`, por uma razão de modularização: como dublê de teste
 * ela só existia para quem compilava o mesmo módulo, e o teste de quota (`monetization`) já a usava
 * de fora. Um source set de teste não é publicado, então cada módulo novo precisaria de uma cópia.
 * Aqui ela serve aos três casos de uma vez — teste, `@Preview` do Compose e modo demonstração —,
 * que é como as bibliotecas oficiais tratam suas implementações in-memory.
 *
 * ```kotlin
 * val prefs = InMemoryAppPreferences()
 * prefs.setString("k", "v")
 * assertEquals("v", prefs.getString("k"))
 * ```
 */
class InMemoryAppPreferences : AppPreferences {

    private val data = mutableMapOf<String, Any>()
    private val changes = MutableSharedFlow<String>(extraBufferCapacity = 64)

    override suspend fun getString(key: String, default: String): String =
        data[key] as? String ?: default

    override suspend fun setString(key: String, value: String) {
        data[key] = value
        changes.tryEmit(key)
    }

    override suspend fun getBoolean(key: String, default: Boolean): Boolean =
        data[key] as? Boolean ?: default

    override suspend fun setBoolean(key: String, value: Boolean) {
        data[key] = value
        changes.tryEmit(key)
    }

    override suspend fun getInt(key: String, default: Int): Int =
        data[key] as? Int ?: default

    override suspend fun setInt(key: String, value: Int) {
        data[key] = value
        changes.tryEmit(key)
    }

    override suspend fun getLong(key: String, default: Long): Long =
        data[key] as? Long ?: default

    override suspend fun setLong(key: String, value: Long) {
        data[key] = value
        changes.tryEmit(key)
    }

    override suspend fun getFloat(key: String, default: Float): Float =
        data[key] as? Float ?: default

    override suspend fun setFloat(key: String, value: Float) {
        data[key] = value
        changes.tryEmit(key)
    }

    override suspend fun has(key: String): Boolean = data.containsKey(key)

    override suspend fun remove(key: String) {
        data.remove(key)
        changes.tryEmit(key)
    }

    override suspend fun clear() {
        data.clear()
        changes.tryEmit(WILDCARD)
    }

    override fun observeString(key: String, default: String): Flow<String> = flow {
        emit(data[key] as? String ?: default)
        changes.filter { it == key || it == WILDCARD }.collect {
            emit(data[key] as? String ?: default)
        }
    }.distinctUntilChanged()

    override fun observeBoolean(key: String, default: Boolean): Flow<Boolean> = flow {
        emit(data[key] as? Boolean ?: default)
        changes.filter { it == key || it == WILDCARD }.collect {
            emit(data[key] as? Boolean ?: default)
        }
    }.distinctUntilChanged()

    override fun observeInt(key: String, default: Int): Flow<Int> = flow {
        emit(data[key] as? Int ?: default)
        changes.filter { it == key || it == WILDCARD }.collect {
            emit(data[key] as? Int ?: default)
        }
    }.distinctUntilChanged()

    override fun observeLong(key: String, default: Long): Flow<Long> = flow {
        emit(data[key] as? Long ?: default)
        changes.filter { it == key || it == WILDCARD }.collect {
            emit(data[key] as? Long ?: default)
        }
    }.distinctUntilChanged()

    /** Snapshot do storage para inspeção em testes. */
    fun snapshot(): Map<String, Any> = data.toMap()

    private companion object {
        const val WILDCARD = "*"
    }
}
