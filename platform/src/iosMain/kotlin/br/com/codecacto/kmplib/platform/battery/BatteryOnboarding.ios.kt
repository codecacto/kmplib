package br.com.codecacto.kmplib.platform.battery

import br.com.codecacto.kmplib.platform.getUrlLauncher

// iOS não tem gerenciador de bateria de fabricante: o que mantém um app vivo em segundo plano é o
// modo de fundo declarado (`UIBackgroundModes`) + a sessão oficial (CLBackgroundActivitySession,
// AlarmKit…), não um ajuste que o usuário precise fazer. Por isso o onboarding não existe aqui.

actual fun needsBatteryOnboarding(): Boolean = false

actual fun deviceBatteryVendor(): BatteryVendor? = null

actual fun isIgnoringBatteryOptimizations(): Boolean? = null

actual fun openBatteryOptimizationRequest(): Boolean = false

actual fun openAppBatterySettings(): Boolean {
    getUrlLauncher().openAppSettings()
    return true
}
