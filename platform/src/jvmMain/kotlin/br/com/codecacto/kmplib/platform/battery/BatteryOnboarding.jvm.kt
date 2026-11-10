package br.com.codecacto.kmplib.platform.battery

// Desktop: não há gerenciador de bateria de fabricante que mate o app — o onboarding não existe.
actual fun needsBatteryOnboarding(): Boolean = false

actual fun deviceBatteryVendor(): BatteryVendor? = null

actual fun isIgnoringBatteryOptimizations(): Boolean? = null

actual fun openBatteryOptimizationRequest(): Boolean = false

actual fun openAppBatterySettings(): Boolean = false
