package br.com.codecacto.kmplib.navigation

import androidx.navigation.NavType
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.write
import kotlin.enums.enumEntries
import kotlin.reflect.KClass
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
 * **Igualdade estável (2.290.0).** Dois `enumNavType<Aba>()` são IGUAIS ([EnumNavType] compara pela
 * classe do enum), então escrever o `typeMap` dentro do construtor do `NavHost` é seguro: o grafo
 * refeito numa recomposição é o mesmo grafo, e o `setGraph` não esvazia a pilha. Até a 2.289.0 não era
 * — cada chamada criava um tipo diferente e a pilha voltava ao `startDestination` (Vitalis, laço
 * Entrada → Login). Mesmo assim, **crie o mapa uma vez** quando ele se repete em vários destinos
 * (`private val abaTypeMap = enumTypeMap<Aba>()` no topo do arquivo do grafo): é o mesmo mapa no
 * `composable<…>` e no `toRoute<…>(typeMap)`, e não se aloca nada a cada recomposição.
 *
 * O `typeMap` vai em **todo** destino cuja rota leva o enum — inclusive em `navigation<Grafo>(…)` de
 * grafo aninhado e em `dialog<Rota>(…)` —, e no `savedStateHandle.toRoute<Rota>(typeMap)` do ViewModel
 * que lê a rota. O valor trafega pelo `name` da constante, igual nas duas
 * plataformas: renomear a constante muda o valor, e um deep link com o nome antigo deixa de abrir.
 */
inline fun <reified T : Enum<T>> enumNavType(): NavType<T> = EnumNavType(T::class, enumEntries<T>())

/**
 * O mesmo para argumento `enum` **anulável** (`val metodo: Metodo? = null`).
 *
 * A chave do `typeMap` é o tipo anulável (`typeOf<T?>()`), e o nulo trafega como o literal `"null"` —
 * a mesma convenção dos tipos anuláveis da própria biblioteca de navegação.
 */
inline fun <reified T : Enum<T>> enumNullableNavType(): NavType<T?> = NullableEnumNavType(T::class, enumEntries<T>())

/**
 * `NavType` de um `enum` **não anulável**, com **igualdade pela classe do enum** (2.290.0,
 * GAP-NAVTYPE-EQUALS-01) — o par do `NavType.EnumType` do androidx, que existe só no Android.
 *
 * ## Por que a igualdade importa
 *
 * O `NavHost(startDestination) { … }` refaz o grafo quando o lambda construtor muda de identidade, e o
 * `NavController.setGraph` compara o grafo novo com o atual (destino → argumento → `NavType`). Até a
 * 2.289.0 cada `enumNavType<T>()` era um `object : NavType<T>` anônimo, igual só a si mesmo: com o
 * `typeMap = enumTypeMap<T>()` escrito DENTRO do construtor do grafo — a forma que este KDoc ensinava —,
 * todo grafo refeito saía "diferente", e o `setGraph` esvaziava a pilha até o `startDestination`. No
 * Vitalis isso virou um laço Entrada → Login → Entrada, com o login impossível nas duas plataformas
 * (docs/42, 10/out/2026). Com `equals`/`hashCode` pela classe do enum, dois `enumNavType<Aba>()` são
 * iguais, e o grafo refeito é o mesmo grafo.
 *
 * Crie pelo [enumNavType] / [enumTypeMap] — o construtor recebe as constantes porque `commonMain` não
 * tem reflexão para listá-las a partir da classe.
 *
 * @property enumClass a classe do enum — é ela (com a nulabilidade) que define a igualdade.
 */
class EnumNavType<T : Enum<T>>(
    val enumClass: KClass<T>,
    private val entries: List<T>,
) : NavType<T>(isNullableAllowed = false) {

    override val name: String get() = enumClass.simpleName ?: "enum"

    override fun put(bundle: SavedState, key: String, value: T) {
        bundle.write { putString(key, value.name) }
    }

    override fun get(bundle: SavedState, key: String): T? =
        bundle.read { getStringOrNull(key) }?.let { parseEnumConstant(entries, enumClass, it) }

    override fun parseValue(value: String): T = parseEnumConstant(entries, enumClass, value)

    override fun serializeAsValue(value: T): String = value.name

    override fun equals(other: Any?): Boolean = other is EnumNavType<*> && other.enumClass == enumClass

    override fun hashCode(): Int = enumClass.hashCode()

    override fun toString(): String = "EnumNavType(${enumClass.simpleName})"
}

/**
 * `NavType` de um `enum` **anulável**, com igualdade pela classe do enum (2.290.0) — ver [EnumNavType].
 * O nulo trafega como o literal `"null"`. Nunca é igual ao [EnumNavType] do mesmo enum: a nulabilidade
 * faz parte do tipo do argumento.
 */
class NullableEnumNavType<T : Enum<T>>(
    val enumClass: KClass<T>,
    private val entries: List<T>,
) : NavType<T?>(isNullableAllowed = true) {

    override val name: String get() = (enumClass.simpleName ?: "enum") + "?"

    override fun put(bundle: SavedState, key: String, value: T?) {
        bundle.write { if (value == null) putNull(key) else putString(key, value.name) }
    }

    override fun get(bundle: SavedState, key: String): T? =
        bundle.read { getStringOrNull(key) }?.let { parseEnumConstant(entries, enumClass, it) }

    override fun parseValue(value: String): T? =
        if (value == NULL_VALUE) null else parseEnumConstant(entries, enumClass, value)

    override fun serializeAsValue(value: T?): String = value?.name ?: NULL_VALUE

    override fun equals(other: Any?): Boolean = other is NullableEnumNavType<*> && other.enumClass == enumClass

    override fun hashCode(): Int = 31 * enumClass.hashCode() + 1

    override fun toString(): String = "NullableEnumNavType(${enumClass.simpleName})"
}

/** O `enumValueOf` sem `reified`: a constante pelo nome, ou `IllegalArgumentException` — nunca outra constante. */
private fun <T : Enum<T>> parseEnumConstant(entries: List<T>, enumClass: KClass<T>, value: String): T =
    entries.firstOrNull { it.name == value }
        ?: throw IllegalArgumentException("No enum constant ${enumClass.simpleName}.$value")

/** `typeMap` de um destino cuja rota tem um tipo de `enum` como argumento. Vários: some os mapas. */
inline fun <reified T : Enum<T>> enumTypeMap(): Map<KType, NavType<*>> = mapOf(typeOf<T>() to enumNavType<T>())

/** `typeMap` de um destino cuja rota tem um tipo de `enum` **anulável** como argumento. */
inline fun <reified T : Enum<T>> enumNullableTypeMap(): Map<KType, NavType<*>> =
    mapOf(typeOf<T?>() to enumNullableNavType<T>())

/** Como o nulo trafega na rota — o mesmo literal que os tipos anuláveis da navegação usam. */
@PublishedApi
internal const val NULL_VALUE: String = "null"
