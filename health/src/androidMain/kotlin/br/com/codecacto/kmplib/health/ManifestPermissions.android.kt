package br.com.codecacto.kmplib.health

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * A permissão está declarada no manifesto FINAL do app (o merge de todos os manifestos)? Sem a
 * declaração, o Android e o Health Connect negam sem mostrar diálogo — o pedido parece recusado pela
 * pessoa quando é o app que não pediu. Usado para avisar no logcat em vez de falhar mudo.
 */
internal fun declaredInManifest(context: Context, permission: String): Boolean {
    val info = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        return true // não dá para conferir: não bloqueia o pedido
    }
    return info.requestedPermissions?.contains(permission) ?: false
}
