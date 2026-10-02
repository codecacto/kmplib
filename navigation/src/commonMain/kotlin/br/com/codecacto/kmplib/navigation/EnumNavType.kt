package br.com.codecacto.kmplib.navigation

import androidx.navigation.NavType
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.write
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * `NavType` de um `enum` usado como argumento de rota **type-safe**.
 *
 * ## Por que existe (02/out/2026)
 *
 * A navegação (2.9.x) só resolve `enum` sozinha no **Android**, por reflexão. Nas outras plataformas o
 * tipo é "desconhecido", e montar o grafo com uma rota que tem argumento `enum` lança
 * `IllegalArgumentException: Route … could not find any NavType for argument … - typeMap received was {}`
 * dentro da composição do `NavHost` — o app **fecha ao abrir no iOS**, e só no iOS (o Android passa em
 * tudo). O caminho oficial é declarar o tipo no `typeMap` do destino. Caso de origem: Esquecido; o
 * mesmo defeito estava em Barista de Casa, MinhaObra e ReciboFacil, cada um com uma cópia deste
 * arquivo (docs/42 §App). O auditor `Nexus/fabrica/rota_sem_navtype.py` cobra.
 *
 * ## Uso
 *
 * ```kotlin
 * @Serializable enum class Aba { RESUMO, HISTORICO }   // @Serializable NA DECLARAÇÃO — ver abaixo
 * @Serializable data class Detalhe(val id: String, val aba: Aba)
 * @Serializable data class Filtro(val metodo: Metodo? = null)              // enum anulável
 *
 * composable<Detalhe>(typeMap = enumTypeMap<Aba>()) { … }
 * composable<Filtro>(typeMap = enumNullableTypeMap<Metodo>()) { … }
 * // Dois enums na mesma rota: some os mapas.
 * composable<Relatorio>(typeMap = enumTypeMap<Periodo>() + enumTypeMap<Formato>()) { … }
 * ```
 *
 * **O `enum` também precisa de `@Serializable` na declaração**, mesmo com o `typeMap`: para casar a
 * chave do mapa com o campo, a navegação chama `serializerOrNull(kType)`, que acha serializer de enum
 * não anotado só no Android (reflexão). No iOS o app fecha ao abrir com
 * `IllegalStateException: Cannot find KSerializer for […] (computeNavType)` (Recibo Fácil, 02/out/2026).
 *
 * O `typeMap` vai em **todo** destino cuja rota leva o enum — inclusive em `navigation<Grafo>(…)` de
 * grafo aninhado e em `dialog<Rota>(…)` —, e no `savedStateHandle.toRoute<Rota>(typeMap)` do ViewModel
 * que lê a rota. O valor trafega pelo `name` da constante, igual nas duas
 * plataformas: renomear a constante muda o valor, e um deep link com o nome antigo deixa de abrir.
 */
inline fun <reified T : Enum<T>> enumNavType(): NavType<T> =
    object : NavType<T>(isNullableAllowed = false) {
        override fun put(bundle: SavedState, key: String, value: T) {
            bundle.write { putString(key, value.name) }
        }

        override fun get(bundle: SavedState, key: String): T? =
            bundle.read { getStringOrNull(key) }?.let { enumValueOf<T>(it) }

        override fun parseValue(value: String): T = enumValueOf(value)

        override fun serializeAsValue(value: T): String = value.name
    }

/**
 * O mesmo para argumento `enum` **anulável** (`val metodo: Metodo? = null`).
 *
 * A chave do `typeMap` é o tipo anulável (`typeOf<T?>()`), e o nulo trafega como o literal `"null"` —
 * a mesma convenção dos tipos anuláveis da própria biblioteca de navegação.
 */
inline fun <reified T : Enum<T>> enumNullableNavType(): NavType<T?> =
    object : NavType<T?>(isNullableAllowed = true) {
        override fun put(bundle: SavedState, key: String, value: T?) {
            bundle.write { if (value == null) putNull(key) else putString(key, value.name) }
        }

        override fun get(bundle: SavedState, key: String): T? =
            bundle.read { getStringOrNull(key) }?.let { enumValueOf<T>(it) }

        override fun parseValue(value: String): T? = if (value == NULL_VALUE) null else enumValueOf<T>(value)

        override fun serializeAsValue(value: T?): String = value?.name ?: NULL_VALUE
    }

/** `typeMap` de um destino cuja rota tem um tipo de `enum` como argumento. Vários: some os mapas. */
inline fun <reified T : Enum<T>> enumTypeMap(): Map<KType, NavType<*>> = mapOf(typeOf<T>() to enumNavType<T>())

/** `typeMap` de um destino cuja rota tem um tipo de `enum` **anulável** como argumento. */
inline fun <reified T : Enum<T>> enumNullableTypeMap(): Map<KType, NavType<*>> =
    mapOf(typeOf<T?>() to enumNullableNavType<T>())

/** Como o nulo trafega na rota — o mesmo literal que os tipos anuláveis da navegação usam. */
@PublishedApi
internal const val NULL_VALUE: String = "null"
