package br.com.codecacto.kmplib.account

import br.com.codecacto.kmplib.account.DeletionLocalFileStep.CAMERA_ORIGINALS
import br.com.codecacto.kmplib.account.DeletionLocalFileStep.PRIVATE_PHOTO_CACHE
import br.com.codecacto.kmplib.account.DeletionLocalFileStep.SHARED_FILES
import kotlin.test.Test
import kotlin.test.assertEquals

class DeletionLocalFileStepsTest {

    @Test
    fun `sem purger os originais de camera saem junto das copias de compartilhamento`() {
        assertEquals(
            setOf(SHARED_FILES, CAMERA_ORIGINALS, PRIVATE_PHOTO_CACHE),
            deletionLocalFileSteps(clearSharedFiles = true, purgerRuns = false),
        )
    }

    @Test
    fun `sem purger e sem a flag os originais de camera e o cache ainda saem`() {
        assertEquals(
            setOf(CAMERA_ORIGINALS, PRIVATE_PHOTO_CACHE),
            deletionLocalFileSteps(clearSharedFiles = false, purgerRuns = false),
        )
    }

    @Test
    fun `com purger rodando a camera fica com ele`() {
        assertEquals(
            setOf(SHARED_FILES, PRIVATE_PHOTO_CACHE),
            deletionLocalFileSteps(clearSharedFiles = true, purgerRuns = true),
        )
        assertEquals(
            setOf(PRIVATE_PHOTO_CACHE),
            deletionLocalFileSteps(clearSharedFiles = false, purgerRuns = true),
        )
    }
}
