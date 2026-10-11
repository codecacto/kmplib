package br.com.codecacto.kmplib.core.locale

/**
 * # Idioma do app — o que a fábrica suporta e como o aparelho escolhe
 *
 * A regra da casa (constituição, "i18n mobile"): **o app segue o idioma do DISPOSITIVO**, pelos
 * Compose Multiplatform Resources — sem seletor no app e sem trocar o idioma em tempo de execução.
 * Quem escolhe a pasta `values-*` é o próprio `compose-resources`, e ele cai em `values` (pt-BR)
 * quando o aparelho está num idioma que o app não traduz.
 *
 * Este arquivo responde à pergunta que o servidor faz a partir daí — **"em que idioma esta pessoa está
 * lendo a tela?"** — com a MESMA resposta do `compose-resources`, sem depender de Compose (o `core`
 * não o carrega). Serve ao `Accept-Language` do [DeviceLocaleHeaders][br.com.codecacto.kmplib.core.network.DeviceLocaleHeaders],
 * ao `locale` do cadastro e a qualquer texto que o servidor gere (e-mail, PDF, push).
 *
 * Quem tem Compose à mão e quer a resposta **exata** da própria pasta escolhida usa `uiLanguageTag()`
 * do `kmplib-ui`, que lê um recurso por pasta.
 */
object FactoryLocales {

    /** Português do Brasil — a pasta `values` (o default de toda tradução da fábrica). */
    const val PT_BR: String = "pt-BR"

    /** Inglês — `values-en` (qualquer região: en-US, en-GB, en-IN…). */
    const val EN: String = "en"

    /** Espanhol — `values-es` (qualquer região: es-ES, es-MX, es-419…). */
    const val ES: String = "es"

    /** Português de Portugal — `values-pt-rPT`. Só a região PT cai aqui; pt-AO/pt-MZ caem no pt-BR. */
    const val PT_PT: String = "pt-PT"

    /** Os quatro idiomas da fábrica, na ordem em que aparecem nos catálogos. */
    val ALL: List<String> = listOf(PT_BR, EN, ES, PT_PT)

    /**
     * O idioma, dentre [supported], que o `compose-resources` escolheria para [languageTag].
     *
     * Reproduz a resolução por qualificador dele: primeiro **idioma + região** exatos (`pt-PT`),
     * depois **só o idioma** (`en` serve `en-GB`), e por fim o [fallback] — a pasta `values`. Um
     * aparelho em francês recebe pt-BR aqui porque é pt-BR que ele vê na tela.
     *
     * @param languageTag tag BCP 47 do aparelho (`pt-BR`, `en-US`, `es-419`, `pt_PT` também serve).
     * @param supported as tags que o app traduz. Tag sem região (`en`) casa com qualquer região.
     * @param fallback o que a pasta `values` contém — pt-BR na fábrica.
     */
    fun match(
        languageTag: String?,
        supported: List<String> = ALL,
        fallback: String = PT_BR,
    ): String {
        val (idioma, regiao) = splitLanguageTag(languageTag) ?: return fallback
        // 1. idioma + região exatos (pt-PT).
        supported.firstOrNull { tag ->
            val (i, r) = splitLanguageTag(tag) ?: return@firstOrNull false
            i == idioma && r != null && r == regiao
        }?.let { return it }
        // 2. só o idioma, numa tag SEM região (en serve en-GB). Uma tag COM região (pt-PT) não serve
        //    a outra região — é assim que o compose-resources faz, e é por isso que pt-AO vê pt-BR.
        supported.firstOrNull { tag ->
            val (i, r) = splitLanguageTag(tag) ?: return@firstOrNull false
            i == idioma && r == null
        }?.let { return it }
        // 3. a pasta `values`: pt-AO, fr-FR, de-DE… veem o fallback na tela, e é ele que vale.
        return fallback
    }
}

/**
 * Separa uma tag BCP 47 em (idioma minúsculo, região maiúscula ou `null`). Aceita `_` (o
 * `NSLocale` do iOS escreve `pt_BR`) e ignora script/variante (`zh-Hant-TW` → `zh`, `TW`).
 * Devolve `null` para tag vazia ou sem idioma.
 */
fun splitLanguageTag(tag: String?): Pair<String, String?>? {
    val partes = tag?.trim()?.replace('_', '-')?.split('-')?.filter { it.isNotEmpty() } ?: return null
    val idioma = partes.firstOrNull()?.lowercase()?.takeIf { it.length in 2..3 && it.all(Char::isLetter) }
        ?: return null
    // Região = subtag de 2 letras ou 3 dígitos (419); script tem 4 letras e é pulado.
    val regiao = partes.drop(1).firstOrNull { p ->
        (p.length == 2 && p.all(Char::isLetter)) || (p.length == 3 && p.all(Char::isDigit))
    }?.uppercase()
    return idioma to regiao
}

/**
 * Tag BCP 47 do **idioma preferido do aparelho** (`pt-BR`, `en-US`, `es-419`).
 *
 * - **Android:** `Locale.getDefault()` — que, desde o Android 13, já é o idioma **por app** quando a
 *   pessoa escolheu um em Configurações → Idioma do app (o sistema aplica a configuração ao
 *   processo antes de qualquer código rodar).
 * - **iOS:** o primeiro de `NSLocale.preferredLanguages` — a mesma fonte que o `compose-resources`
 *   usa para escolher a pasta.
 *
 * É o idioma **do aparelho**, não o da tela: num aparelho em francês isto devolve `fr-FR`, e a tela
 * mostra pt-BR. Para o idioma da tela use [appLanguageTag].
 */
expect fun deviceLanguageTag(): String

/** Só o idioma do aparelho, minúsculo (`pt`, `en`, `es`). */
fun deviceLanguage(): String = splitLanguageTag(deviceLanguageTag())?.first ?: "pt"

/**
 * O idioma **em que a tela está sendo mostrada** — o [FactoryLocales.match] do idioma do aparelho.
 *
 * É o valor a mandar ao servidor (`Accept-Language`, `locale` do cadastro) para que o que ele gera —
 * e-mail, PDF, push, mensagem de erro — saia no idioma que a pessoa está lendo no app.
 *
 * @param supported os idiomas que o app traduz; o default são os declarados em
 *   [KmpLibLocales.configure] (2.288.0) — os 4 da fábrica enquanto o app não declarar. Até a
 *   2.287.0 o default eram sempre os 4, e um app só pt-BR num aparelho em inglês mandava `en` ao
 *   servidor com a tela em português.
 */
fun appLanguageTag(supported: List<String> = KmpLibLocales.supported): String =
    FactoryLocales.match(deviceLanguageTag(), supported)
