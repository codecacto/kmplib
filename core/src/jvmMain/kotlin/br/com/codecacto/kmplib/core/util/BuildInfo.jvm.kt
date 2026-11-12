package br.com.codecacto.kmplib.core.util

import br.com.codecacto.kmplib.core.context.DesktopAppContext

/** Desktop: o que o app declarou em `initKmpLibCore(DesktopAppInfo(...))` (a JVM não tem manifesto do app). */
actual object BuildInfo {
    actual val isDebug: Boolean
        get() = DesktopAppContext.get()?.isDebug ?: false

    actual val appName: String?
        get() = DesktopAppContext.get()?.appName?.trim()?.takeIf { it.isNotEmpty() }

    actual val appVersionName: String?
        get() = DesktopAppContext.get()?.versionName?.trim()?.takeIf { it.isNotEmpty() }

    actual val appBuildNumber: String?
        get() = DesktopAppContext.get()?.buildNumber?.trim()?.takeIf { it.isNotEmpty() }

    actual val appId: String?
        get() = DesktopAppContext.get()?.appId?.trim()?.takeIf { it.isNotEmpty() }
}
