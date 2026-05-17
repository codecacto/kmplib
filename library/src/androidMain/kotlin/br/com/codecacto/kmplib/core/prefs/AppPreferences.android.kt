package br.com.codecacto.kmplib.core.prefs

import android.content.Context
import android.content.SharedPreferences
import br.com.codecacto.kmplib.platform.UrlLauncherHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

actual class AppPreferences {

    private val prefs: SharedPreferences

    actual constructor() {
        val context = UrlLauncherHolder.getContext()
            ?: throw IllegalStateException(
                "KmpLib não foi inicializado. Chame KmpLib.init(context) no Application.onCreate()."
            )
        prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }

    actual suspend fun getString(key: String, default: String): String =
        withContext(Dispatchers.IO) { prefs.getString(key, default) ?: default }

    actual suspend fun setString(key: String, value: String) {
        withContext(Dispatchers.IO) { prefs.edit().putString(key, value).apply() }
        changes.tryEmit(key)
    }

    actual suspend fun getBoolean(key: String, default: Boolean): Boolean =
        withContext(Dispatchers.IO) { prefs.getBoolean(key, default) }

    actual suspend fun setBoolean(key: String, value: Boolean) {
        withContext(Dispatchers.IO) { prefs.edit().putBoolean(key, value).apply() }
        changes.tryEmit(key)
    }

    actual suspend fun getInt(key: String, default: Int): Int =
        withContext(Dispatchers.IO) { prefs.getInt(key, default) }

    actual suspend fun setInt(key: String, value: Int) {
        withContext(Dispatchers.IO) { prefs.edit().putInt(key, value).apply() }
        changes.tryEmit(key)
    }

    actual suspend fun getLong(key: String, default: Long): Long =
        withContext(Dispatchers.IO) { prefs.getLong(key, default) }

    actual suspend fun setLong(key: String, value: Long) {
        withContext(Dispatchers.IO) { prefs.edit().putLong(key, value).apply() }
        changes.tryEmit(key)
    }

    actual suspend fun getFloat(key: String, default: Float): Float =
        withContext(Dispatchers.IO) { prefs.getFloat(key, default) }

    actual suspend fun setFloat(key: String, value: Float) {
        withContext(Dispatchers.IO) { prefs.edit().putFloat(key, value).apply() }
        changes.tryEmit(key)
    }

    actual suspend fun has(key: String): Boolean =
        withContext(Dispatchers.IO) { prefs.contains(key) }

    actual suspend fun remove(key: String) {
        withContext(Dispatchers.IO) { prefs.edit().remove(key).apply() }
        changes.tryEmit(key)
    }

    actual suspend fun clear() {
        withContext(Dispatchers.IO) { prefs.edit().clear().apply() }
        changes.tryEmit(WILDCARD)
    }

    actual fun observeString(key: String, default: String): Flow<String> = flow {
        emit(prefs.getString(key, default) ?: default)
        changes.filter { it == key || it == WILDCARD }.collect {
            emit(prefs.getString(key, default) ?: default)
        }
    }.distinctUntilChanged()

    actual fun observeBoolean(key: String, default: Boolean): Flow<Boolean> = flow {
        emit(prefs.getBoolean(key, default))
        changes.filter { it == key || it == WILDCARD }.collect {
            emit(prefs.getBoolean(key, default))
        }
    }.distinctUntilChanged()

    actual fun observeInt(key: String, default: Int): Flow<Int> = flow {
        emit(prefs.getInt(key, default))
        changes.filter { it == key || it == WILDCARD }.collect {
            emit(prefs.getInt(key, default))
        }
    }.distinctUntilChanged()

    actual fun observeLong(key: String, default: Long): Flow<Long> = flow {
        emit(prefs.getLong(key, default))
        changes.filter { it == key || it == WILDCARD }.collect {
            emit(prefs.getLong(key, default))
        }
    }.distinctUntilChanged()

    companion object {
        private const val WILDCARD = "*"
        // Flow compartilhado: múltiplas instâncias enxergam mudanças umas das outras
        private val changes = MutableSharedFlow<String>(extraBufferCapacity = 64)
    }
}
