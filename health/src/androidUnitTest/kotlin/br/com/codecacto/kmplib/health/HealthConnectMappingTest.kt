package br.com.codecacto.kmplib.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import br.com.codecacto.kmplib.workout.mapping.ExerciseCategory
import br.com.codecacto.kmplib.workout.mapping.HealthPlatformMapping
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O `kmplib-workout` devolve o NOME da constante do Health Connect (ele não pode depender do SDK, que
 * não compila em watchOS/jvm). Aqui, com o SDK real no classpath, cada nome tem de existir e de ser
 * a constante que o gateway grava.
 */
class HealthConnectMappingTest {

    @Test
    fun `cada categoria grava a constante que o mapeamento nomeia`() {
        for (category in ExerciseCategory.entries) {
            val name = HealthPlatformMapping.healthConnectExerciseTypeName(category)
            val constant = ExerciseSessionRecord::class.java.getField(name).getInt(null)
            assertEquals(constant, exerciseTypeFor(category), "categoria $category ($name)")
        }
    }
}
