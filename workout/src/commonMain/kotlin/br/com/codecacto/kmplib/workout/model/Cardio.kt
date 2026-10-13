package br.com.codecacto.kmplib.workout.model

import kotlinx.serialization.Serializable

/**
 * Como um bloco de cardio é conduzido (2.297.0):
 * - [INTERVAL]: [CardioBlock.rounds] voltas de TRABALHO seguido de DESCANSO;
 * - [CONTINUOUS]: uma fase só de trabalho — gravado como **1 volta sem descanso** (decisão K-13 do App
 *   do Personal: o contínuo usa o MESMO contrato do intervalado, e o backend não tem caso à parte).
 */
@Serializable
enum class CardioMode { INTERVAL, CONTINUOUS }

/**
 * Fase de um bloco de cardio. É a fase que a tela escreve **em texto** ("TRABALHO"/"DESCANSO" no
 * idioma do app), nunca deduzida pela cor — a lib não tem texto; o nome do enum é o que cruza a ponte
 * celular↔relógio (`StateSnapshot.cardioPhase`) e o JSON da sessão (`SetResult.cardioPhase`).
 */
@Serializable
enum class CardioPhase { WORK, REST }

/**
 * Zona de frequência cardíaca do modelo de 5 zonas por percentual da FC máxima (o das diretrizes do
 * ACSM e dos relógios Polar/Garmin): Z1 50–60 %, Z2 60–70 %, Z3 70–80 %, Z4 80–90 %, Z5 90–100 %.
 * A conta em bpm está em `workout.cardio` (`bpmRange`, `heartRateZoneOf`, `positionOf`).
 *
 * @property lowerPercent limite inferior, inclusivo, em % da FC máxima.
 * @property upperPercent limite superior, exclusivo (o da Z5, 100, é inclusivo), em % da FC máxima.
 */
@Serializable
enum class HeartRateZone(val lowerPercent: Int, val upperPercent: Int) {
    Z1(50, 60),
    Z2(60, 70),
    Z3(70, 80),
    Z4(80, 90),
    Z5(90, 100),
}

/**
 * Bloco de cardio estruturado, preso a um item do treino (`ExerciseStep.cardio`) — o
 * `CardioBlockDto` do App do Personal, campo a campo.
 *
 * Cada volta é **TRABALHO ([workSeconds]) e, se [restSeconds] > 0, DESCANSO ([restSeconds])** —
 * inclusive na última volta, como o "Total = voltas × (trabalho + descanso)" que o personal vê ao
 * montar o bloco (8 × (0:40 + 0:20) = 8:00, o Tabata de 4 min inclusive). O descanso ENTRE ITENS
 * continua sendo o `ExerciseStep.restSeconds`, depois da última fase.
 *
 * Construa por [interval]/[continuous]; o construtor fica aberto para a desserialização. A faixa
 * aceita pelo backend está em [invalidFields] (o motor não recusa nada: fase de 0 s ou menos
 * simplesmente não existe na execução, e `rounds` ≤ 0 não gera passo nenhum).
 *
 * @property targetZone zona-alvo do TRABALHO; `null` = sem zona.
 */
@Serializable
data class CardioBlock(
    val mode: CardioMode,
    val workSeconds: Int,
    val restSeconds: Int,
    val rounds: Int,
    val targetZone: HeartRateZone? = null,
) {
    /** Duração planejada do bloco, em segundos: `rounds × (workSeconds + restSeconds)`. */
    val totalSeconds: Int get() = rounds.coerceAtLeast(0) * (workSeconds.coerceAtLeast(0) + restSeconds.coerceAtLeast(0))

    /**
     * Os campos fora da faixa do contrato, pelo NOME do campo (`"workSeconds"`, `"restSeconds"`,
     * `"rounds"`) — o que o backend devolve no 400 (`cardio.<campo>`). Lista vazia = válido.
     * - `workSeconds`: 10..3600 no intervalado; 10..7200 no contínuo;
     * - `restSeconds`: 0..900; no contínuo, exatamente 0;
     * - `rounds`: 1..50; no contínuo, exatamente 1.
     */
    fun invalidFields(): List<String> = buildList {
        val workRange = if (mode == CardioMode.CONTINUOUS) CONTINUOUS_WORK_SECONDS_RANGE else INTERVAL_WORK_SECONDS_RANGE
        if (workSeconds !in workRange) add("workSeconds")
        if (if (mode == CardioMode.CONTINUOUS) restSeconds != 0 else restSeconds !in REST_SECONDS_RANGE) add("restSeconds")
        if (if (mode == CardioMode.CONTINUOUS) rounds != 1 else rounds !in ROUNDS_RANGE) add("rounds")
    }

    /** `true` quando [invalidFields] está vazia. */
    val isValid: Boolean get() = invalidFields().isEmpty()

    companion object {
        val INTERVAL_WORK_SECONDS_RANGE: IntRange = 10..3600
        val CONTINUOUS_WORK_SECONDS_RANGE: IntRange = 10..7200
        val REST_SECONDS_RANGE: IntRange = 0..900
        val ROUNDS_RANGE: IntRange = 1..50

        /** Intervalado: [rounds] × ([workSeconds] de trabalho + [restSeconds] de descanso). */
        fun interval(workSeconds: Int, restSeconds: Int, rounds: Int, targetZone: HeartRateZone? = null): CardioBlock =
            CardioBlock(CardioMode.INTERVAL, workSeconds, restSeconds, rounds, targetZone)

        /** Contínuo: uma fase de [durationSeconds] — 1 volta sem descanso (K-13). */
        fun continuous(durationSeconds: Int, targetZone: HeartRateZone? = null): CardioBlock =
            CardioBlock(CardioMode.CONTINUOUS, durationSeconds, 0, 1, targetZone)
    }
}

/** Segundos planejados de uma [phase] deste bloco. */
fun CardioBlock.secondsOf(phase: CardioPhase): Int = when (phase) {
    CardioPhase.WORK -> workSeconds
    CardioPhase.REST -> restSeconds
}

/**
 * As fases de UMA volta, na ordem: trabalho e, se houver, descanso. Fase com 0 s ou menos não existe
 * (contínuo = só [CardioPhase.WORK]).
 */
fun CardioBlock.phasesPerRound(): List<CardioPhase> = buildList {
    if (workSeconds > 0) add(CardioPhase.WORK)
    if (restSeconds > 0) add(CardioPhase.REST)
}

/**
 * Faixa aceita para `SetResult.durationSeconds` numa fase de cardio: de 0 (fase pulada no primeiro
 * instante) até a duração planejada da fase — o motor nunca registra mais do que o planejado, porque a
 * fase termina sozinha no instante final absoluto.
 */
fun CardioBlock.phaseDurationRange(phase: CardioPhase): IntRange = 0..secondsOf(phase).coerceAtLeast(0)
