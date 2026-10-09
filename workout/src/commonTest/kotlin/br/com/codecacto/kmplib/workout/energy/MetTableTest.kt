package br.com.codecacto.kmplib.workout.energy

import kotlin.test.Test
import kotlin.test.assertEquals

/** Os valores do 2024 Adult Compendium usados pelo produto (RF-CAL-02), um a um. */
class MetTableTest {

    @Test
    fun `musculacao leve moderada e vigorosa`() {
        assertEquals(3.5, MetTable.metFor(WorkoutType.STRENGTH, WorkoutIntensity.LIGHT))
        assertEquals(5.0, MetTable.metFor(WorkoutType.STRENGTH, WorkoutIntensity.MODERATE))
        assertEquals(6.0, MetTable.metFor(WorkoutType.STRENGTH, WorkoutIntensity.VIGOROUS))
    }

    @Test
    fun `circuito de 5 a 7,5`() {
        assertEquals(5.0, MetTable.metFor(WorkoutType.CIRCUIT, WorkoutIntensity.LIGHT))
        assertEquals(6.25, MetTable.metFor(WorkoutType.CIRCUIT, WorkoutIntensity.MODERATE))
        assertEquals(7.5, MetTable.metFor(WorkoutType.CIRCUIT, WorkoutIntensity.VIGOROUS))
    }

    @Test
    fun `HIIT de 7 a 11`() {
        assertEquals(7.0, MetTable.metFor(WorkoutType.HIIT, WorkoutIntensity.LIGHT))
        assertEquals(9.0, MetTable.metFor(WorkoutType.HIIT, WorkoutIntensity.MODERATE))
        assertEquals(11.0, MetTable.metFor(WorkoutType.HIIT, WorkoutIntensity.VIGOROUS))
    }

    @Test
    fun `esforco ate 4 leve, 5 a 7 moderado, 8 ou mais vigoroso`() {
        assertEquals(WorkoutIntensity.LIGHT, MetTable.intensityFromEffort(1))
        assertEquals(WorkoutIntensity.LIGHT, MetTable.intensityFromEffort(4))
        assertEquals(WorkoutIntensity.MODERATE, MetTable.intensityFromEffort(5))
        assertEquals(WorkoutIntensity.MODERATE, MetTable.intensityFromEffort(7))
        assertEquals(WorkoutIntensity.VIGOROUS, MetTable.intensityFromEffort(8))
        assertEquals(WorkoutIntensity.VIGOROUS, MetTable.intensityFromEffort(10))
    }
}
