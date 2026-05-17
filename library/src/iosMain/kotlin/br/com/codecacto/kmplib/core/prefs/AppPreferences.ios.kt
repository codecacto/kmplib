package br.com.codecacto.kmplib.core.prefs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import platform.Foundation.NSUserDefaults

actual fun appPreferences(): AppPreferences = IosAppPreferences()

internal class IosAppPreferences : AppPreferences {

    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun getString(key: String, default: String): String =
        defaults.stringForKey(key) ?: default

    override suspend fun setString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
        changes.tryEmit(key)
    }

    override suspend fun getBoolean(key: String, default: Boolean): Boolean =
        if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key)

    override suspend fun setBoolean(key: String, value: Boolean) {
        defaults.setBool(value, forKey = key)
        changes.tryEmit(key)
    }

    override suspend fun getInt(key: String, default: Int): Int =
        if (defaults.objectForKey(key) == null) default else defaults.integerForKey(key).toInt()

    override suspend fun setInt(key: String, value: Int) {
        defaults.setInteger(value.toLong(), forKey = key)
        changes.tryEmit(key)
    }

    override suspend fun getLong(key: String, default: Long): Long =
        if (defaults.objectForKey(key) == null) default else defaults.integerForKey(key)

    override suspend fun setLong(key: String, value: Long) {
        defaults.setInteger(value, forKey = key)
        changes.tryEmit(key)
    }

    override suspend fun getFloat(key: String, default: Float): Float =
        if (defaults.objectForKey(key) == null) default else defaults.floatForKey(key)

    override suspend fun setFloat(key: String, value: Float) {
        defaults.setFloat(value, forKey = key)
        changes.tryEmit(key)
    }

    override suspend fun has(key: String): Boolean = defaults.objectForKey(key) != null

    override suspend fun remove(key: String) {
        defaults.removeObjectForKey(key)
        changes.tryEmit(key)
    }

    override suspend fun clear() {
        @Suppress("UNCHECKED_CAST")
        val keys = (defaults.dictionaryRepresentation().keys as? Set<*>) ?: emptySet<Any?>()
        keys.forEach { rawKey -> (rawKey as? String)?.let { defaults.removeObjectForKey(it) } }
        changes.tryEmit(WILDCARD)
    }

    override fun observeString(key: String, default: String): Flow<String> = flow {
        emit(defaults.stringForKey(key) ?: default)
        changes.filter { it == key || it == WILDCARD }.collect {
            emit(defaults.stringForKey(key) ?: default)
        }
    }.distinctUntilChanged()

    override fun observeBoolean(key: String, default: Boolean): Flow<Boolean> = flow {
        emit(if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key))
        changes.filter { it == key || it == WILDCARD }.collect {
            emit(if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key))
        }
    }.distinctUntilChanged()

    override fun observeInt(key: String, default: Int): Flow<Int> = flow {
        emit(if (defaults.objectForKey(key) == null) default else defaults.integerForKey(key).toInt())
        changes.filter { it == key || it == WILDCARD }.collect {
            emit(if (defaults.objectForKey(key) == null) default else defaults.integerForKey(key).toInt())
        }
    }.distinctUntilChanged()

    override fun observeLong(key: String, default: Long): Flow<Long> = flow {
        emit(if (defaults.objectForKey(key) == null) default else defaults.integerForKey(key))
        changes.filter { it == key || it == WILDCARD }.collect {
            emit(if (defaults.objectForKey(key) == null) default else defaults.integerForKey(key))
        }
    }.distinctUntilChanged()

    companion object {
        private const val WILDCARD = "*"
        private val changes = MutableSharedFlow<String>(extraBufferCapacity = 64)
    }
}
