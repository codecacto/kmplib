package br.com.codecacto.kmplib.workout.mapping

/**
 * Nosso tipo de exercício (do catálogo da plataforma) -> segmento do Health Connect / tipo de
 * atividade do HealthKit / chave do catálogo Garmin. Usado na escrita no repositório de saúde
 * (`kmplib-health`) e, na Onda 3, na Training API da Garmin. Mora aqui (não em `kmplib-health`)
 * porque também é consumido pelo backend (alvo `jvm`) ao conciliar planejado x feito.
 */
enum class ExerciseMechanic { COMPOUND, ISOLATED }

/** Categoria ampla usada para escolher o `HealthConnectExerciseSegmentType`/`HKWorkoutActivityType`
 * — não é o grupo muscular (que mora no cadastro do exercício, fora deste módulo). */
enum class ExerciseCategory {
    STRENGTH_TRAINING,
    CARDIO,
    MOBILITY,
    CIRCUIT,
    HIIT,
}

/** Mapa estável: nosso `exerciseRefId` -> categoria -> segmento/tipo em cada plataforma. A tabela
 * em si (nome -> categoria) é dado de catálogo e fica fora da lib (Onda 1, `RF-EXE-01`); aqui só a
 * FUNÇÃO de categoria -> tipo nativo, que é regra estável e testável. */
object HealthPlatformMapping {

    /** Nome da constante `HealthConnectExerciseSegmentType.*` esperada (Android, `kmplib-health`).
     * Devolvido como String para este módulo não depender do SDK do Health Connect (que não compila
     * em watchOS). `kmplib-health` resolve o nome para a constante real. */
    fun healthConnectSegmentTypeName(category: ExerciseCategory): String = when (category) {
        ExerciseCategory.STRENGTH_TRAINING -> "EXERCISE_SEGMENT_TYPE_STRENGTH_TRAINING"
        ExerciseCategory.CARDIO -> "EXERCISE_SEGMENT_TYPE_AEROBIC"
        ExerciseCategory.MOBILITY -> "EXERCISE_SEGMENT_TYPE_STRETCHING"
        ExerciseCategory.CIRCUIT -> "EXERCISE_SEGMENT_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING"
        ExerciseCategory.HIIT -> "EXERCISE_SEGMENT_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING"
    }

    /** Nome da constante `HKWorkoutActivityType.*` esperada (iOS, `kmplib-health`). */
    fun healthKitActivityTypeName(category: ExerciseCategory): String = when (category) {
        ExerciseCategory.STRENGTH_TRAINING -> "traditionalStrengthTraining"
        ExerciseCategory.CARDIO -> "cardioDance" // placeholder amplo; tela usa tipo específico quando souber
        ExerciseCategory.MOBILITY -> "flexibility"
        ExerciseCategory.CIRCUIT -> "highIntensityIntervalTraining"
        ExerciseCategory.HIIT -> "highIntensityIntervalTraining"
    }

    /** Exercício sem correspondente no catálogo Garmin (1.600+ chaves) fica SINALIZADO, nunca vazio
     * — é a regra da Onda 3 (`docs/06` §4 / roadmap 3.2): o chamador decide o que fazer com `null`
     * (bloquear o envio daquele exercício, avisar o personal etc.), esta função só não inventa uma
     * chave que não existe. */
    fun garminCatalogKey(ourExerciseRefId: String, catalog: Map<String, String>): String? = catalog[ourExerciseRefId]
}
