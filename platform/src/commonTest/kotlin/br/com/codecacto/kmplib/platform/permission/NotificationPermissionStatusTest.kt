package br.com.codecacto.kmplib.platform.permission

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationPermissionStatusTest {

    @Test
    fun `permissao concedida com o interruptor desligado e negacao definitiva`() {
        assertEquals(
            PermissionStatus.PERMANENTLY_DENIED,
            combineNotificationStatus(PermissionStatus.GRANTED, notificationsEnabledInSystem = false),
        )
    }

    @Test
    fun `concedida e ligada continua concedida`() {
        assertEquals(PermissionStatus.GRANTED, combineNotificationStatus(PermissionStatus.GRANTED, true))
    }

    @Test
    fun `nao concedida mantem o status da permissao de runtime`() {
        PermissionStatus.entries.filter { it != PermissionStatus.GRANTED }.forEach { status ->
            assertEquals(status, combineNotificationStatus(status, notificationsEnabledInSystem = false))
            assertEquals(status, combineNotificationStatus(status, notificationsEnabledInSystem = true))
        }
    }

    @Test
    fun `negacao definitiva conhecida nao regride para negada`() {
        assertEquals(
            PermissionStatus.PERMANENTLY_DENIED,
            mergeRefreshedStatus(PermissionStatus.PERMANENTLY_DENIED, PermissionStatus.DENIED),
        )
        assertEquals(
            PermissionStatus.PERMANENTLY_DENIED,
            mergeRefreshedStatus(PermissionStatus.PERMANENTLY_DENIED, PermissionStatus.NOT_REQUESTED),
        )
    }

    @Test
    fun `negacao definitiva sai quando a permissao volta concedida`() {
        assertEquals(
            PermissionStatus.GRANTED,
            mergeRefreshedStatus(PermissionStatus.PERMANENTLY_DENIED, PermissionStatus.GRANTED),
        )
    }

    @Test
    fun `status comum adota a leitura nova`() {
        assertEquals(PermissionStatus.DENIED, mergeRefreshedStatus(PermissionStatus.NOT_REQUESTED, PermissionStatus.DENIED))
        assertEquals(PermissionStatus.PERMANENTLY_DENIED, mergeRefreshedStatus(PermissionStatus.GRANTED, PermissionStatus.PERMANENTLY_DENIED))
    }

    @Test
    fun `currentStatus default delega ao checkPermission`() = kotlinx.coroutines.test.runTest {
        val fake = object : PermissionManager {
            override fun checkPermission(permission: AppPermission) = PermissionStatus.DENIED
            override fun requestPermission(permission: AppPermission) =
                kotlinx.coroutines.flow.flowOf(PermissionStatus.DENIED)
        }
        assertEquals(PermissionStatus.DENIED, fake.currentStatus(AppPermission.CAMERA))
    }
}
