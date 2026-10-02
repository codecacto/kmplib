package br.com.codecacto.kmplib.platform.automation

import android.app.ActivityManager
import android.os.Build

internal actual fun isDeviceInTestHarness(): Boolean = try {
    val harness = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ActivityManager.isRunningInUserTestHarness()
    harness || ActivityManager.isUserAMonkey()
} catch (_: RuntimeException) {
    // Teste JVM sem o framework Android (stub "Stub!"): não é aparelho em Test Harness.
    false
}
