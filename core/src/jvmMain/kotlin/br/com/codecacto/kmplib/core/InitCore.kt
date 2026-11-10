package br.com.codecacto.kmplib.core

import br.com.codecacto.kmplib.core.context.DesktopAppContext
import br.com.codecacto.kmplib.core.context.DesktopAppInfo

/**
 * Inicializa o `kmplib-core` no DESKTOP (alvo `jvm`, 2.353.0) — o par do `initKmpLibCore(context)`
 * do Android. Chame UMA vez no `main()`, antes de criar `ownAuth(...)`, `appPreferences()` ou
 * `createBlobStore()`:
 *
 * ```kotlin
 * fun main() {
 *     initKmpLibCore(DesktopAppInfo(appId = "statushub-desktop", appName = "StatusHub", versionName = "1.0.0"))
 *     application { … }
 * }
 * ```
 */
fun initKmpLibCore(appInfo: DesktopAppInfo) {
    DesktopAppContext.init(appInfo)
}
