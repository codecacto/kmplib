package br.com.codecacto.kmplib.core.util

import android.content.pm.ApplicationInfo
import br.com.codecacto.kmplib.platform.UrlLauncherHolder

actual object BuildInfo {
    actual val isDebug: Boolean
        get() {
            val context = UrlLauncherHolder.getContext() ?: return false
            return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        }
}
