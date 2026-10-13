package br.com.codecacto.kmplib.core.util

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
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

    actual val appVersionName: String?
        get() = packageInfo()?.versionName?.trim()?.takeIf { it.isNotEmpty() }

    actual val appBuildNumber: String?
        get() {
            val info = packageInfo() ?: return null
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            return code.takeIf { it > 0 }?.toString()
        }

    /** O `PackageInfo` do próprio app; `null` sem contexto ou se o sistema recusar (nunca lança). */
    private fun packageInfo(): PackageInfo? {
        val context = AndroidAppContext.get() ?: return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        }.getOrNull()
    }
}
