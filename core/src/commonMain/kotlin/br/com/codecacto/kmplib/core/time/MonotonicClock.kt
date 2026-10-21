package br.com.codecacto.kmplib.core.time

/**
 * **Relógio monotônico do aparelho** (2.312.0): tempo decorrido desde um ponto fixo (o boot),
 * que **só anda para a frente** e **não muda** quando a pessoa (ou a rede) acerta a hora do aparelho.
 *
 * Serve para medir intervalo e para ancorar uma hora vinda do servidor (RondAki §11.0: guarde
 * `servidorEm` junto com [MonotonicClock.elapsedMillis] no recebimento; depois, hora do servidor
 * estimada = `servidorEm + (agora − âncora)`). **Nunca** use a hora de parede (`currentTimeMillis`)
 * para isso: ela volta, pula e é editável nas Configurações.
 *
 * | Plataforma | Fonte | Conta o tempo dormindo? |
 * |---|---|---|
 * | Android | `SystemClock.elapsedRealtimeNanos()` | sim (deep sleep incluído) |
 * | iOS | `clock_gettime_nsec_np(CLOCK_MONOTONIC_RAW)` | sim (= `mach_continuous_time`) |
 *
 * As duas contam o tempo com a tela apagada e zeram **no reinício do aparelho** — valor menor que o
 * guardado na âncora = o aparelho reiniciou, a âncora não vale mais. Não é comparável entre aparelhos.
 *
 * ⚠️ Não é o `kotlin.time.TimeSource.Monotonic`: no Android ele usa `System.nanoTime()`, que **para**
 * durante o deep sleep — uma ronda de 40 minutos com a tela apagada mediria bem menos.
 *
 * iOS: `CLOCK_MONOTONIC_RAW` não está entre as "APIs de horário de boot" que pedem justificativa no
 * Privacy Manifest (`systemUptime`, `mach_absolute_time()`).
 */
object MonotonicClock : MonotonicTimeSource {
    /** Nanossegundos desde o boot, contando o tempo dormindo. */
    override fun elapsedNanos(): Long = platformElapsedRealtimeNanos()
}

/**
 * Fonte de tempo monotônico — [MonotonicClock] no app, um dublê no teste (injete esta interface
 * onde a regra depende do tempo decorrido).
 */
fun interface MonotonicTimeSource {
    /** Nanossegundos desde um ponto fixo; nunca diminui enquanto o aparelho não reinicia. */
    fun elapsedNanos(): Long

    /** [elapsedNanos] em milissegundos (truncado). */
    fun elapsedMillis(): Long = elapsedNanos() / NANOS_PER_MILLI
}

/** Milissegundos decorridos de [startMillis] (lido da mesma fonte) até agora; `null` se a fonte voltou (reinício). */
fun MonotonicTimeSource.millisSince(startMillis: Long): Long? {
    val agora = elapsedMillis()
    return if (agora < startMillis) null else agora - startMillis
}

private const val NANOS_PER_MILLI = 1_000_000L

internal expect fun platformElapsedRealtimeNanos(): Long
