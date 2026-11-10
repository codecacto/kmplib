package br.com.codecacto.kmplib.core.time

/**
 * Desktop: `System.nanoTime()` — o relógio monotônico da JVM (QueryPerformanceCounter no Windows,
 * `CLOCK_MONOTONIC` no Linux/macOS). Imune a acerto de hora; a origem é arbitrária, por isso só
 * serve para intervalo dentro do mesmo processo (o mesmo contrato do `MonotonicClock`).
 */
internal actual fun platformElapsedRealtimeNanos(): Long = System.nanoTime()
