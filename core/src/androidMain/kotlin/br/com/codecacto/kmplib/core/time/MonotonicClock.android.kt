package br.com.codecacto.kmplib.core.time

import android.os.SystemClock

/** `elapsedRealtimeNanos`: desde o boot, incluindo o deep sleep (o recomendado para intervalo). */
internal actual fun platformElapsedRealtimeNanos(): Long = SystemClock.elapsedRealtimeNanos()
