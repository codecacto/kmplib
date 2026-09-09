package br.com.codecacto.kmplib.video

import android.content.Context

/**
 * Registra o `Context` do player de vídeo.
 *
 * Chame no `Application.onCreate()`. Quem consome o artefato umbrella (`br.com.codecacto:kmplib`)
 * já recebe isto de `KmpLib.init(context)`; quem declara `kmplib-video` direto chama aqui.
 */
fun initKmpLibVideo(context: Context) {
    VideoPlayerHolder.init(context)
}
