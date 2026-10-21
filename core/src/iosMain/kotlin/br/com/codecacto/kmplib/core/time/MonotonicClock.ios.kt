package br.com.codecacto.kmplib.core.time

import platform.posix.CLOCK_MONOTONIC_RAW
import platform.posix.clock_gettime_nsec_np

/**
 * `clock_gettime_nsec_np(CLOCK_MONOTONIC_RAW)`: nanossegundos desde o boot, contando o sono, sem
 * ajuste de frequência (o equivalente de `mach_continuous_time`, sem a conversão de timebase).
 */
internal actual fun platformElapsedRealtimeNanos(): Long = clock_gettime_nsec_np(CLOCK_MONOTONIC_RAW.toUInt()).toLong()
