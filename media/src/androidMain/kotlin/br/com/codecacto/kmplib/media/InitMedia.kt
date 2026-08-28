package br.com.codecacto.kmplib.media

import android.content.Context
import br.com.codecacto.kmplib.voice.SpeechRecognizerHolder

/**
 * Registra o `Context` no player de efeitos sonoros e no reconhecimento de voz.
 *
 * Chame no `Application.onCreate()`. Ver [br.com.codecacto.kmplib.core.initKmpLibCore] para o
 * porquê de cada módulo ter o seu.
 */
fun initKmpLibMedia(context: Context) {
    SoundEffectPlayerHolder.init(context)
    SpeechRecognizerHolder.init(context)
}
