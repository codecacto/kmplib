package br.com.codecacto.kmplib.core.util

import android.content.pm.ApplicationInfo
import br.com.codecacto.kmplib.core.context.AndroidAppContext

actual object BuildInfo {
    actual val isDebug: Boolean
        get() {
            val context = AndroidAppContext.get() ?: return false
            return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        }

    actual val appName: String?
        get() {
            val context = AndroidAppContext.get() ?: return null
            return context.applicationInfo.loadLabel(context.packageManager)
                .toString().trim().takeIf { it.isNotEmpty() }
        }
}
