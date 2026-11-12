@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package br.com.codecacto.kmplib.widget

import br.com.codecacto.kmplib.core.util.AppLogger
import platform.Foundation.NSUserDefaults
import kotlin.native.ObjCName

private class AppGroupWidgetDataStore(private val defaults: NSUserDefaults) : WidgetDataStore {
    override fun putString(key: String, value: String) = defaults.setObject(value, forKey = key)
    override fun getString(key: String): String? = defaults.stringForKey(key)
    override fun remove(key: String) = defaults.removeObjectForKey(key)
}

actual fun createWidgetDataStore(appGroupId: String?): WidgetDataStore {
    require(!appGroupId.isNullOrBlank()) { "iOS: informe o App Group (group.br.com.codecacto.<app>) — a extensão só lê dele" }
    val defaults = NSUserDefaults(suiteName = appGroupId)
    return AppGroupWidgetDataStore(defaults)
}

/**
 * No Swift do app (uma vez, no `init` do `App`/`AppDelegate`):
 * ```swift
 * final class Reloader: NSObject, WidgetReloader {
 *     func reloadAllTimelines() { WidgetCenter.shared.reloadAllTimelines() }
 * }
 * Widgets.shared.setReloader(reloader: Reloader())
 * ```
 */
@ObjCName("Widgets")
actual object Widgets {
    private var reloader: WidgetReloader? = null

    /** O Swift registra aqui quem chama o `WidgetCenter`. */
    fun setReloader(reloader: WidgetReloader?) {
        this.reloader = reloader
    }

    actual fun requestUpdate() {
        reloader?.reloadAllTimelines() ?: AppLogger.w("Widgets", "nenhum WidgetReloader registrado pelo Swift do app")
    }

    actual fun scheduleDailyRefresh(enabled: Boolean) = Unit
}
