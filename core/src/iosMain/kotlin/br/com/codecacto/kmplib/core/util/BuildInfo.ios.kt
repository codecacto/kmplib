package br.com.codecacto.kmplib.core.util

import platform.Foundation.NSBundle
import kotlin.experimental.ExperimentalNativeApi

actual object BuildInfo {
    @OptIn(ExperimentalNativeApi::class)
    actual val isDebug: Boolean = Platform.isDebugBinary

    actual val appName: String?
        get() {
            val bundle = NSBundle.mainBundle
            val nome = bundle.objectForInfoDictionaryKey("CFBundleDisplayName") as? String
                ?: bundle.objectForInfoDictionaryKey("CFBundleName") as? String
            return nome?.trim()?.takeIf { it.isNotEmpty() }
        }

    actual val appVersionName: String?
        get() = bundleString("CFBundleShortVersionString")

    actual val appBuildNumber: String?
        get() = bundleString("CFBundleVersion")

    private fun bundleString(key: String): String? =
        (NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }
}
