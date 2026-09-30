package br.com.codecacto.kmplib.ui.components

import br.com.codecacto.kmplib.platform.permission.PermissionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PermissionBannerActionTest {

    @Test
    fun `concedida esconde a faixa`() {
        assertNull(permissionBannerAction(PermissionStatus.GRANTED))
        assertNull(permissionBannerAction(PermissionStatus.GRANTED, showWhenNotRequested = true))
    }

    @Test
    fun `negada oferece pedir de novo`() {
        assertEquals(PermissionBannerAction.REQUEST, permissionBannerAction(PermissionStatus.DENIED))
    }

    @Test
    fun `negada em definitivo oferece as configuracoes`() {
        assertEquals(PermissionBannerAction.OPEN_SETTINGS, permissionBannerAction(PermissionStatus.PERMANENTLY_DENIED))
    }

    @Test
    fun `nunca pedida so aparece quando o app quer`() {
        assertNull(permissionBannerAction(PermissionStatus.NOT_REQUESTED))
        assertEquals(
            PermissionBannerAction.REQUEST,
            permissionBannerAction(PermissionStatus.NOT_REQUESTED, showWhenNotRequested = true),
        )
    }
}
