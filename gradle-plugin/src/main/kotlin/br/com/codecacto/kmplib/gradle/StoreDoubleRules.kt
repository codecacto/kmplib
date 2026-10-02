package br.com.codecacto.kmplib.gradle

/**
 * As regras do dublê da loja, PURAS (testadas sem Gradle). O plugin só as liga às tarefas.
 *
 * O que elas respondem é uma pergunta só: **este build pode virar binário publicável?** Até a
 * 2.232.0 cada app respondia pelo NOME da tarefa digitada (`aR` — abreviação do `assembleRelease` —
 * passava; `assemble` sem variante também) e pelo `CONFIGURATION == "Release"` do Xcode (que com
 * flavor vale `Release-<flavor>`). Aqui a resposta vem de onde o próprio build decide: a VARIANTE
 * Android (o build type é debuggable?) e o TIPO do framework Kotlin/Native que vai ser linkado.
 */
internal object StoreDoubleRules {

    /** Propriedade Gradle que liga o dublê (`-Pqa.paywallDemo=true`). */
    const val PROPERTY = "qa.paywallDemo"

    /**
     * Variável de ambiente equivalente (`QA_PAYWALL_DEMO=1`) — no iOS quem chama o Gradle é o script
     * do Xcode, e `-P` não atravessa esse caminho (build setting do `xcodebuild` vira variável).
     */
    const val ENV = "QA_PAYWALL_DEMO"

    /** O build pediu o dublê? */
    fun isRequested(property: String?, env: String?): Boolean =
        property?.trim().equals("true", ignoreCase = true) || env?.trim() == "1"

    /**
     * Uma variante Android é publicável quando o build type **não** é debuggable. Build type que não
     * se consegue ler (`null`) conta como publicável: na dúvida, a trava fecha.
     */
    fun isPublishableAndroidBuildType(debuggable: Boolean?): Boolean = debuggable != true

    /**
     * O Xcode está montando um binário publicável?
     *
     * - `KOTLIN_FRAMEWORK_BUILD_TYPE=release` — o que o KGP usa primeiro para escolher o framework
     *   (é como app com flavor diz "release" numa configuração `Release-<flavor>`);
     * - `CONFIGURATION` começando com `Release` (`Release`, `Release-diariaCerta`…);
     * - `ACTION=install` — é o que o `xcodebuild archive` exporta.
     */
    fun isXcodeRelease(configuration: String?, frameworkBuildType: String?, action: String?): Boolean =
        frameworkBuildType?.trim().equals("release", ignoreCase = true) ||
            configuration?.trim()?.startsWith("release", ignoreCase = true) == true ||
            action?.trim().equals("install", ignoreCase = true)

    /** A âncora que TODA tarefa de uma variante Android espera (`preReleaseBuild`, `preFreeReleaseBuild`). */
    fun preBuildTaskName(variantName: String): String =
        "pre" + variantName.replaceFirstChar { it.uppercaseChar() } + "Build"

    /** As tarefas que o script de build do Xcode chama (`embedAndSignAppleFrameworkForXcode` e variantes). */
    fun isXcodeEmbedTask(taskName: String): Boolean =
        taskName.startsWith("embedAndSign") && taskName.endsWith("AppleFrameworkForXcode")

    fun failureMessage(where: String): String =
        "kmplib: o DUBLÊ DA LOJA ($PROPERTY / $ENV) está ligado num build PUBLICÁVEL ($where). " +
            "Ele responde \"é premium\" sem cobrar e não pode chegar à loja. Tire a flag — ela é só para " +
            "o build de QA (debug) do print de review e do teste no emulador."
}
