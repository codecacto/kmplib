package br.com.codecacto.kmplib.location.session

/**
 * Modo de precisão da sessão (2.308.0) — **adaptativo**: a bateria do vigia dura o turno inteiro só
 * se o GPS de alta precisão ficar ligado apenas perto do ponto de ronda.
 *
 * | Modo | Android (Fused) | iOS (Core Location) | Intervalo |
 * |---|---|---|---|
 * | [BALANCED] | `PRIORITY_BALANCED_POWER_ACCURACY` | `kCLLocationAccuracyNearestTenMeters`, filtro 10 m | 10 s |
 * | [HIGH] | `PRIORITY_HIGH_ACCURACY` | `kCLLocationAccuracyBest`, filtro 5 m | 5 s |
 *
 * O iOS não tem intervalo por tempo (entrega por distância); a lib aplica o espaçamento mínimo nos
 * dois lados ([shouldEmitFix]) para o app receber a mesma cadência.
 */
enum class PatrolAccuracy(val intervalMillis: Long) {
    BALANCED(10_000L),
    HIGH(5_000L),
    ;

    /** Espaçamento mínimo entre duas leituras entregues — metade do intervalo (o `minUpdateInterval` do Fused). */
    val minSpacingMillis: Long get() = intervalMillis / 2

    companion object {
        /** Distância ao ponto de ronda a partir da qual a precisão sobe — 200 m. */
        const val HIGH_ACCURACY_RADIUS_METERS: Double = 200.0

        /**
         * O modo para a distância até o ponto de ronda mais próximo: [HIGH] a até
         * [HIGH_ACCURACY_RADIUS_METERS], [BALANCED] além disso ou sem ponto conhecido (`null`).
         */
        fun forDistanceToCheckpoint(meters: Double?): PatrolAccuracy =
            if (meters != null && meters >= 0.0 && meters <= HIGH_ACCURACY_RADIUS_METERS) HIGH else BALANCED
    }
}

/**
 * A leitura [next] deve ser entregue, dado o último entregue [previous]? Pura, para teste.
 *
 * - primeira leitura: sim;
 * - leitura mais antiga ou igual à anterior (reentrega do sistema, relógio voltando): não;
 * - leitura antes do espaçamento mínimo do modo: só se for **mais precisa** que a anterior (não se
 *   descarta um fix bom por chegar cedo — é o que vale perto do ponto).
 */
fun shouldEmitFix(previous: LocationFix?, next: LocationFix, accuracy: PatrolAccuracy): Boolean {
    if (previous == null) return true
    val gap = next.timestampMillis - previous.timestampMillis
    if (gap <= 0) return false
    if (gap >= accuracy.minSpacingMillis) return true
    return next.accuracy < previous.accuracy
}
