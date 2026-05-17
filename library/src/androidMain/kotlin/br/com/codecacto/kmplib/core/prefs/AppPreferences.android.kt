package br.com.codecacto.kmplib.core.prefs

import android.content.Context
import android.content.SharedPreferences
import br.com.codecacto.kmplib.platform.UrlLauncherHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

actual fun appPreferences(): AppPreferences = AndroidAppPreferences()

internal class AndroidAppPreferences : AppPreferences {

    private val prefs: SharedPreferences = run {
        val context = UrlLauncherHolder.getContext()
            ?: throw IllegalStateException(
                "KmpLib não foi inicializado. Chame KmpLib.init(context) no Application.onCreate()."
            )
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }

    override suspend fun getString(key: String, default: String): String =
        withContext(Dispatchers.IO) { prefs.getString(key, default) ?: default }

    override suspend fun setString(key: String, value: String) {
        withContext(Dispatchers.IO) { prefs.edit().putString(key, value).apply() }
        changes.tryEmit(key)
    }

    override suspend fun getBoolean(key: String, default: Boolean): Boolean =
        withContext(Dispatchers.IO) { prefs.getBoolean(key, default) }

    override suspend fun setBoolean(key: String, value: Boolean) {
        withContext(Dispatchers.IO) { prefs.edit().putBoolean(key, value).apply() }
        changes.tryEmit(key)
    }

    override suspend fun getInt(key: String, default: Int): Int =
        withContext(Dispatchers.IO) { prefs.getInt(key, default) }

    override suspend fun setInt(key: String, value: Int) {
        withContext(Dispatchers.IO) { prefs.edit().putInt(key, value).apply() }
        changes.tryEmit(key)
    }

    override suspend fun getLong(key: String, default: Long): Long =
        withContext(Dispatchers.IO) { prefs.getLong(key, default) }

    override suspend fun setLong(key: String, value: Long) {
        withContext(Dispatchers.IO) { prefs.edit().putLong(key, value).apply() }
        changes.tryEmit(key)
    }

    override suspend fun getFloat(key: String, default: Float): Float =
        withContext(Dispatchers.IO) { prefs.getFloat(key, default) }

    override suspend fun setFloat(key: String, value: Float) {
        withContext(Dispatchers.IO) { prefs.edit().putFloat(key, value).apply() }
        changes.tryEmit(key)
    }

    override suspend fun has(key: String): Boolean =
        withContext(Dispatchers.IO) { prefs.contains(key) }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) { prefs.edit().remove(key).apply() }
        changes.tryEmit(key)
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { prefs.edit().clear().apply() }
        changes.tryEmit(WILDCARD)
    }

    override fun observeString(key: String, default: String): Flow<String> = flow {
        emit(prefs.getString(key, default) ?: default)
        changes.filter { it == key || it == WILDCARD }.collect {
            emit(prefs.getString(key, default) ?: default)
        }
    }.distinctUntilChanged()

    override fun observeBoolean(key: String, default: Boolean): Flow<Boolean> = flow {
        emit(prefs.getBoolean(key, default))
        changes.filter { it == key || it == WILDCARD }.collect {
            emit(prefs.getBoolean(key, default))
        }
    }.distinctUntilChanged()

    override fun observeInt(key: String, default: Int): Flow<Int> = flow {
        emit(prefs.getInt(key, default))
        changes.filter { it == key || it == WILDCARD }.collect {
            emit(prefs.getInt(key, default))
        }
    }.distinctUntilChanged()

    override fun observeLong(key: String, default: Long): Flow<Long> = flow {
        emit(prefs.getLong(key, default))
        changes.filter { it == key || it == WILDCARD }.collect {
            emit(prefs.getLong(key, default))
        }
    }.distinctUntilChanged()

    companion object {
        private const val WILDCARD = "*"
        private val changes = MutableSharedFlow<String>(extraBufferCapacity = 64)
    }
}
