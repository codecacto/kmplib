package br.com.codecacto.kmplib.platform.permission

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Desktop (2.353.0): o alvo `jvm` da lib não implementa microfone, câmera, localização, telefone nem
 * notificação agendada — então nenhuma dessas permissões é concedida: [checkPermission] e o pedido
 * respondem [PermissionStatus.DENIED]. Não é "a pessoa negou", é "este computador não oferece pela lib".
 */
internal object DesktopPermissionManager : PermissionManager {
    override fun checkPermission(permission: AppPermission): PermissionStatus = PermissionStatus.DENIED
    override fun requestPermission(permission: AppPermission): Flow<PermissionStatus> = flowOf(PermissionStatus.DENIED)
}

actual fun createPermissionManager(): PermissionManager = DesktopPermissionManager
