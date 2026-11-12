@file:OptIn(kotlin.time.ExperimentalTime::class)

package br.com.codecacto.kmplib.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.codecacto.kmplib.core.context.AndroidAppContext
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.time.Clock
import java.util.concurrent.TimeUnit

private class PrefsWidgetDataStore(context: Context) : WidgetDataStore {
    private val prefs = context.applicationContext.getSharedPreferences(WIDGET_PREFERENCES_NAME, Context.MODE_PRIVATE)
    // commit(): o widget pode ser redesenhado logo em seguida, em outro contexto — o dado já tem de estar no disco.
    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).commit() }
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun remove(key: String) { prefs.edit().remove(key).commit() }
}

actual fun createWidgetDataStore(appGroupId: String?): WidgetDataStore = PrefsWidgetDataStore(AndroidAppContext.require())

/** O mesmo armazenamento, a partir do `Context` que o Glance entrega (`provideGlance(context, id)`). */
object WidgetDataStores {
    fun from(context: Context): WidgetDataStore = PrefsWidgetDataStore(context)
}

actual object Widgets {
    private const val TAG = "Widgets"
    private const val WORK_NAME = "kmplib.widget.midnight"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var refresher: (suspend (Context) -> Unit)? = null

    /**
     * Registra o que atualizar (no `Application.onCreate`, antes de tudo): ex.
     * `Widgets.registerRefresher { ctx -> VersiculoWidget().updateAll(ctx) }`. O processo que o
     * WorkManager acorda à meia-noite passa pelo `onCreate` e encontra o registro.
     */
    fun registerRefresher(block: suspend (Context) -> Unit) {
        refresher = block
    }

    internal suspend fun runRefresher(context: Context) {
        val r = refresher ?: return AppLogger.w(TAG, "nenhum refresher registrado — chame Widgets.registerRefresher no Application")
        runCatching { r(context) }.onFailure { AppLogger.w(TAG, "atualização do widget falhou: ${it::class.simpleName}") }
    }

    actual fun requestUpdate() {
        val ctx = AndroidAppContext.get() ?: return
        scope.launch { runRefresher(ctx) }
    }

    actual fun scheduleDailyRefresh(enabled: Boolean) {
        val ctx = AndroidAppContext.get() ?: return
        val wm = WorkManager.getInstance(ctx)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        enqueueNext(ctx)
    }

    internal fun enqueueNext(context: Context) {
        val delay = (nextMidnight().toEpochMilliseconds() - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(60_000)
        val req = OneTimeWorkRequestBuilder<WidgetMidnightWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, req)
    }
}

/** Roda o refresher do app à meia-noite e agenda a próxima. */
class WidgetMidnightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Widgets.runRefresher(applicationContext)
        Widgets.enqueueNext(applicationContext)
        return Result.success()
    }
}
