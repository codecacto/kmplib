package br.com.codecacto.kmplib.core

import android.content.Context
import br.com.codecacto.kmplib.core.context.AndroidAppContext
import br.com.codecacto.kmplib.core.storage.BlobStoreHolder

/**
 * Registra o `Context` do Android nos pontos do `kmplib-core` que precisam dele: o
 * [AndroidAppContext] (preferências, checagem de rede, `BuildInfo`) e o `BlobStore` (os binários
 * da fila de upload).
 *
 * Chame no `Application.onCreate()`. Um app que use o artefato umbrella `br.com.codecacto:kmplib`
 * não precisa chamar nada disto — o `KmpLib.init(context)` de lá chama o init de cada módulo.
 *
 * Cada módulo tem o seu, e é o que torna a lib modular utilizável: enquanto a inicialização era
 * uma função só, ela tocava holders de auth, sync, media e platform de uma vez — e um app que
 * quisesse apenas `kmplib-core` + `kmplib-ui` era obrigado a trazer todos eles para conseguir
 * compilar a própria `Application`.
 */
fun initKmpLibCore(context: Context) {
    AndroidAppContext.init(context)
    BlobStoreHolder.init(context)
}
