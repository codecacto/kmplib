package br.com.codecacto.kmplib.platform

import android.content.Context
import androidx.core.os.ConfigurationCompat
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * Contexto de aplicação para a leitura da região, no padrão de holder da casa
 * (inicializado por `KmpLib.init`).
 */
object DeviceLocaleHolder {
    private var contextRef: WeakReference<Context>? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    internal fun getContext(): Context? = contextRef?.get()
}

/**
 * Região do aparelho no Android.
 *
 * Lê a configuração **da aplicação** antes do `Locale.getDefault()`: desde o Android 13 o usuário
 * pode escolher um idioma **só para este app**, e é a configuração que reflete essa escolha — o
 * default do processo pode continuar sendo o do sistema. Sem contexto registrado, cai no default,
 * que é o comportamento correto para quem chama antes do `KmpLib.init`.
 */
actual fun platformRegionCode(): String? {
    val doApp = DeviceLocaleHolder.getContext()?.let { contexto ->
        ConfigurationCompat.getLocales(contexto.resources.configuration).get(0)
    }
    // `country` é o campo de região do `Locale`, e vem "" quando não há região definida —
    // o `normalizeRegion` do commonMain transforma isso em `null`.
    return (doApp ?: Locale.getDefault()).country
}
