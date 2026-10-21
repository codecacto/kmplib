package br.com.codecacto.kmplib.platform.permission

import android.Manifest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PreciseLocationAndroidTest {

    @Test
    fun precisaPedeFineJuntoComCoarse() {
        assertContentEquals(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            permissionsToRequest(AppPermission.PRECISE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
        )
        assertContentEquals(
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
            permissionsToRequest(AppPermission.LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
    }

    @Test
    fun statusDoPedidoDaPrecisa() {
        assertEquals(PermissionStatus.GRANTED, preciseLocationResultStatus(fineGranted = true, canShowRationale = false))
        // Escolheu "Aproximada" (ou negou uma vez): ainda dá para pedir a troca.
        assertEquals(PermissionStatus.DENIED, preciseLocationResultStatus(fineGranted = false, canShowRationale = true))
        assertEquals(PermissionStatus.PERMANENTLY_DENIED, preciseLocationResultStatus(fineGranted = false, canShowRationale = false))
    }
}
