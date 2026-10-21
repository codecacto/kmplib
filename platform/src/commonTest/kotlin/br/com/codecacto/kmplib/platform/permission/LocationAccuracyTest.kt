package br.com.codecacto.kmplib.platform.permission

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals

class LocationAccuracyTest {

    @Test
    fun precisaoPelasDuasPermissoes() {
        val g = PermissionStatus.GRANTED
        val d = PermissionStatus.DENIED
        assertEquals(LocationAccuracy.PRECISE, locationAccuracyOf(location = g, preciseLocation = g))
        assertEquals(LocationAccuracy.APPROXIMATE, locationAccuracyOf(location = g, preciseLocation = d))
        assertEquals(LocationAccuracy.NONE, locationAccuracyOf(location = d, preciseLocation = PermissionStatus.NOT_REQUESTED))
        assertEquals(LocationAccuracy.NONE, locationAccuracyOf(PermissionStatus.PERMANENTLY_DENIED, PermissionStatus.PERMANENTLY_DENIED))
        // A precisa concedida basta (no Android a FINE implica a COARSE).
        assertEquals(LocationAccuracy.PRECISE, locationAccuracyOf(location = d, preciseLocation = g))
    }

    @Test
    fun defaultDoManagerUsaOCheckDasDuas() {
        val fake = object : PermissionManager {
            val estados = mutableMapOf(
                AppPermission.LOCATION to PermissionStatus.GRANTED,
                AppPermission.PRECISE_LOCATION to PermissionStatus.DENIED,
            )
            override fun checkPermission(permission: AppPermission) = estados[permission] ?: PermissionStatus.NOT_REQUESTED
            override fun requestPermission(permission: AppPermission): Flow<PermissionStatus> = flowOf(checkPermission(permission))
        }
        assertEquals(LocationAccuracy.APPROXIMATE, fake.locationAccuracy())
        fake.estados[AppPermission.PRECISE_LOCATION] = PermissionStatus.GRANTED
        assertEquals(LocationAccuracy.PRECISE, fake.locationAccuracy())
    }

    @Test
    fun chaveDePropositoDoIosConfiguravel() {
        assertEquals("PreciseLocation", LocationAccuracySettings.DEFAULT_TEMPORARY_FULL_ACCURACY_PURPOSE_KEY)
        val antes = LocationAccuracySettings.temporaryFullAccuracyPurposeKey
        try {
            LocationAccuracySettings.temporaryFullAccuracyPurposeKey = "Ronda"
            assertEquals("Ronda", LocationAccuracySettings.temporaryFullAccuracyPurposeKey)
        } finally {
            LocationAccuracySettings.temporaryFullAccuracyPurposeKey = antes
        }
    }
}
