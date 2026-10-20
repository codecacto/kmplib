package br.com.codecacto.kmplib.platform.battery

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import br.com.codecacto.kmplib.core.context.AndroidAppContext
import br.com.codecacto.kmplib.core.util.AppLogger

private const val TAG = "BatteryOnboarding"

actual fun needsBatteryOnboarding(): Boolean =
    batteryOnboardingNeeded(deviceBatteryVendor(), isIgnoringBatteryOptimizations())

actual fun deviceBatteryVendor(): BatteryVendor? = batteryVendorOf(Build.MANUFACTURER, Build.BRAND)

actual fun isIgnoringBatteryOptimizations(): Boolean? {
    val context = AndroidAppContext.get() ?: return null
    val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return null
    return power.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Pedido direto quando o APP declarou `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (permissão "normal":
 * declarada = concedida na instalação); senão a lista do sistema; senão os detalhes do app.
 */
@SuppressLint("BatteryLife") // O pedido direto é o objetivo — e só sai com a permissão declarada pelo app.
actual fun openBatteryOptimizationRequest(): Boolean {
    val context = AndroidAppContext.get() ?: return false
    val pkg = context.packageName
    val declared = context.packageManager.checkPermission(
        Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        pkg,
    ) == PackageManager.PERMISSION_GRANTED
    if (declared && launch(context, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")))) {
        return true
    }
    if (launch(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) return true
    return openAppBatterySettings()
}

actual fun openAppBatterySettings(): Boolean {
    val context = AndroidAppContext.get() ?: return false
    return launch(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )
}

/** Abre a tela fora de Activity (contexto da aplicação → nova tarefa). `false` se ninguém atende. */
private fun launch(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    AppLogger.w(TAG, "Tela de bateria indisponível: ${intent.action}")
    false
} catch (e: SecurityException) {
    AppLogger.w(TAG, "Tela de bateria recusada: ${intent.action}")
    false
}
