package br.com.codecacto.kmplib.sync

import br.com.codecacto.kmplib.sync.PurgeLocalFileStep.CAMERA_ORIGINALS
import br.com.codecacto.kmplib.sync.PurgeLocalFileStep.PRIVATE_PHOTO_CACHE
import br.com.codecacto.kmplib.sync.PurgeLocalFileStep.SHARED_FILES
import kotlin.test.Test
import kotlin.test.assertEquals

class PurgeLocalFileStepsTest {

    @Test
    fun `flag desligada pula so as copias de compartilhamento`() {
        assertEquals(setOf(CAMERA_ORIGINALS, PRIVATE_PHOTO_CACHE), purgeLocalFileSteps(clearSharedFiles = false))
    }

    @Test
    fun `flag ligada limpa as tres`() {
        assertEquals(setOf(SHARED_FILES, CAMERA_ORIGINALS, PRIVATE_PHOTO_CACHE), purgeLocalFileSteps(clearSharedFiles = true))
    }
}
