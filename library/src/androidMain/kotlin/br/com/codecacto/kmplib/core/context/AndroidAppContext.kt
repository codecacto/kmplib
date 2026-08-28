package br.com.codecacto.kmplib.core.context

import android.content.Context
import java.lang.ref.WeakReference

/**
 * Guarda o `Context` da APLICAÇÃO Android para o código da lib que precisa dele fora de uma tela
 * (preferências, checagem de rede, `BuildInfo`, clipboard, storage de token, localização).
 *
 * Mora em `core` de propósito: é a camada mais baixa da lib, e todo mundo que precisa de contexto
 * está ACIMA dela. Antes este holder morava em `platform` sob o nome `UrlLauncherHolder`, o que
 * obrigava `core` a importar `platform` — um ciclo que impedia a lib de ser modularizada (o
 * `kmplib-core` não pode depender do `kmplib-platform`, que depende dele).
 *
 * Guarda uma [WeakReference] do `applicationContext`: o holder é um `object` que vive enquanto o
 * processo vive, e segurar uma `Activity` aqui vazaria a tela inteira.
 *
 * É inicializado uma vez, por `initKmpLib(context)` (`KmpLibInit.android.kt`). Quem chamava
 * `UrlLauncherHolder.init(context)` continua funcionando — aquele `init` agora delega para cá.
 */
object AndroidAppContext {
    private var contextRef: WeakReference<Context>? = null

    /** Registra o `applicationContext`. Chamado por `initKmpLib`; idempotente. */
    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    /** O contexto, ou `null` se a lib ainda não foi inicializada. */
    fun get(): Context? = contextRef?.get()

    /**
     * O contexto, ou erro explicando o que faltou. Use onde a ausência é um bug de integração
     * (e não um caminho degradado que o chamador saiba tratar).
     */
    fun require(): Context = get() ?: error(
        "kmplib não foi inicializada. Chame initKmpLib(context) no Application.onCreate()."
    )
}
