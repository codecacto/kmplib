package br.com.codecacto.kmplib.workout.mapping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HealthPlatformMappingTest {

    @Test
    fun `toda categoria tem tipo de sessao - segmento e atividade do HealthKit`() {
        for (category in ExerciseCategory.entries) {
            assertTrue(HealthPlatformMapping.healthConnectExerciseTypeName(category).startsWith("EXERCISE_TYPE_"))
            assertTrue(HealthPlatformMapping.healthConnectSegmentTypeName(category).startsWith("EXERCISE_SEGMENT_TYPE_"))
            assertTrue(HealthPlatformMapping.healthKitActivityTypeName(category).isNotBlank())
        }
    }

    @Test
    fun `musculacao vira forca nas tres plataformas`() {
        val c = ExerciseCategory.STRENGTH_TRAINING
        assertEquals("EXERCISE_TYPE_STRENGTH_TRAINING", HealthPlatformMapping.healthConnectExerciseTypeName(c))
        assertEquals("EXERCISE_SEGMENT_TYPE_STRENGTH_TRAINING", HealthPlatformMapping.healthConnectSegmentTypeName(c))
        assertEquals("traditionalStrengthTraining", HealthPlatformMapping.healthKitActivityTypeName(c))
    }

    @Test
    fun `cardio no HealthKit e mixedCardio - nunca danca`() {
        assertEquals("mixedCardio", HealthPlatformMapping.healthKitActivityTypeName(ExerciseCategory.CARDIO))
        assertEquals("EXERCISE_TYPE_OTHER_WORKOUT", HealthPlatformMapping.healthConnectExerciseTypeName(ExerciseCategory.CARDIO))
        assertEquals("EXERCISE_SEGMENT_TYPE_AEROBIC", HealthPlatformMapping.healthConnectSegmentTypeName(ExerciseCategory.CARDIO))
    }

    @Test
    fun `mobilidade - circuito e HIIT`() {
        assertEquals("flexibility", HealthPlatformMapping.healthKitActivityTypeName(ExerciseCategory.MOBILITY))
        assertEquals("EXERCISE_TYPE_STRETCHING", HealthPlatformMapping.healthConnectExerciseTypeName(ExerciseCategory.MOBILITY))
        assertEquals("EXERCISE_SEGMENT_TYPE_STRETCHING", HealthPlatformMapping.healthConnectSegmentTypeName(ExerciseCategory.MOBILITY))
        for (c in listOf(ExerciseCategory.CIRCUIT, ExerciseCategory.HIIT)) {
            assertEquals("highIntensityIntervalTraining", HealthPlatformMapping.healthKitActivityTypeName(c))
            assertEquals("EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING", HealthPlatformMapping.healthConnectExerciseTypeName(c))
            assertEquals("EXERCISE_SEGMENT_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING", HealthPlatformMapping.healthConnectSegmentTypeName(c))
        }
    }

    @Test
    fun `exercicio fora do catalogo Garmin fica sinalizado com null - nunca com chave inventada`() {
        val catalog = mapOf("squat" to "SQUAT_BARBELL_BACK_SQUAT")
        assertEquals("SQUAT_BARBELL_BACK_SQUAT", HealthPlatformMapping.garminCatalogKey("squat", catalog))
        assertNull(HealthPlatformMapping.garminCatalogKey("prancha-lateral", catalog))
    }
}
