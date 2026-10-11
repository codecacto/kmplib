package br.com.codecacto.kmplib.core.locale

import kotlin.concurrent.Volatile

/**
 * # Os idiomas que o APP traduz — e o que a lib faz com isso (2.288.0, GAP-PT-M31)
 *
 * O `compose-resources` escolhe a pasta `values-*` **por biblioteca**: cada artefato com recursos
 * resolve o idioma do aparelho contra as pastas QUE ELE tem. A kmplib traz os 4 idiomas da fábrica
 * (`values` = pt-BR, `values-en`, `values-es`, `values-pt-rPT`); um app só pt-BR (`store_reach = BR`,
 * só `values/`) num aparelho em inglês mostrava o app em pt-BR e as telas da lib em inglês — o
 * paywall do App do Personal saiu com "No plans available", "Restore purchases", "Terms of Use" no
 * meio de uma tela em português.
 *
 * A correção: o app **declara** os idiomas que traduz, uma vez, na inicialização —
 * ```kotlin
 * KmpLibLocales.configure(supported = listOf(FactoryLocales.PT_BR))   // app BR
 * ```
 * — e todo texto da lib (composable ou não: `stringResource` das telas, `remember…Texts()`,
 * `load…Texts()` dos ViewModels, mensagens de erro de compra, nomes de plano) passa a sair no idioma
 * que **o app** mostra: quando o aparelho está num idioma que a lib traduz e o app não, a lib cai na
 * base pt-BR, igual ao app. O idioma continua sendo o do APARELHO — sem seletor, sem troca em tempo
 * de execução, sem forçar locale (constituição, "i18n mobile").
 *
 * **Default = [FactoryLocales.ALL]**: app GLOBAL não chama nada e nada muda.
 *
 * Quem lê isto:
 * - [appLanguageTag] (default de `supported` = [supported]) → `Accept-Language` do
 *   `DeviceLocaleHeaders`, `locale` do cadastro, nome de país do [Countries], textos do `SignaturePad`;
 * - o resolvedor de recursos do `kmplib-ui` (`kmpStringResource`/`kmpGetString`), por
 *   [shouldUseBaseTexts].
 */
object KmpLibLocales {

    @Volatile
    private var current: List<String> = FactoryLocales.ALL

    /** Os idiomas declarados pelo app ([configure]); [FactoryLocales.ALL] até alguém declarar. */
    val supported: List<String> get() = current

    /**
     * Declara os idiomas que o app traduz. Chamar **uma vez, antes da primeira tela** (no
     * `Application.onCreate` / no `MainViewController`, junto dos outros `configure` da lib).
     *
     * A base pt-BR ([FactoryLocales.PT_BR], a pasta `values` de todo app da fábrica) entra sempre,
     * mesmo que a lista não a traga — é para ela que o `compose-resources` cai quando o aparelho
     * está num idioma sem tradução, e é para ela que a lib cai também.
     *
     * @param supported tags BCP 47 — use as constantes de [FactoryLocales] (`PT_BR`, `EN`, `ES`,
     *   `PT_PT`). Tag sem região (`en`) serve qualquer região, como a pasta `values-en`.
     * @throws IllegalArgumentException tag que não é BCP 47 (`""`, `"1234"`).
     */
    fun configure(supported: List<String>) {
        current = normalize(supported)
    }

    /** Volta ao default ([FactoryLocales.ALL]) — para teste. */
    fun reset() {
        current = FactoryLocales.ALL
    }

    /**
     * A lista que [configure] grava: tags validadas e sem espaço, pt-BR primeiro (se não veio),
     * sem repetição. Pura e pública para quem quiser conferir antes.
     */
    fun normalize(supported: List<String>): List<String> {
        val tags = supported.map { tag ->
            val limpa = tag.trim()
            require(splitLanguageTag(limpa) != null) { "Idioma inválido em KmpLibLocales.configure: '$tag'" }
            limpa.replace('_', '-')
        }
        val comBase = if (tags.any { splitLanguageTag(it) == ("pt" to "BR") }) {
            tags
        } else {
            listOf(FactoryLocales.PT_BR) + tags
        }
        return comBase.distinct()
    }

    /**
     * O idioma que as pastas da LIB escolhem para [languageTag] — os 4 da fábrica, sempre (é o que o
     * `compose-resources` faz com os recursos dela, independentemente do app).
     */
    fun libraryLanguageTag(languageTag: String? = deviceLanguageTag()): String =
        FactoryLocales.match(languageTag, FactoryLocales.ALL)

    /**
     * A lib deve ignorar a pasta que o `compose-resources` escolheu e usar a base pt-BR?
     *
     * `true` só quando as duas respostas divergem: a pasta da lib cairia num idioma diferente de
     * pt-BR **e** o app, com os idiomas que declarou, mostra outro (a base pt-BR). Ex.: app só pt-BR
     * num aparelho em inglês → `true`; app com pt-BR + en no mesmo aparelho → `false` (os dois em
     * inglês); aparelho em francês → `false` (a lib já está em pt-BR, igual ao app).
     *
     * @param languageTag idioma do aparelho (default: [deviceLanguageTag]).
     * @param supported idiomas do app (default: os declarados em [configure]).
     */
    fun shouldUseBaseTexts(
        languageTag: String? = deviceLanguageTag(),
        supported: List<String> = this.supported,
    ): Boolean {
        val daLib = libraryLanguageTag(languageTag)
        if (daLib == FactoryLocales.PT_BR) return false
        return FactoryLocales.match(languageTag, supported) != daLib
    }
}
