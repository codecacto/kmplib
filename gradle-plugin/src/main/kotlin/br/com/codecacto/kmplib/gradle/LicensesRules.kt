package br.com.codecacto.kmplib.gradle

/** Regras puras do plugin `br.com.codecacto.kmplib.licenses` (testadas sem Gradle). */
object LicensesRules {
    /** O plugin oficial do AboutLibraries (mesma versão do runtime `aboutlibraries-core` da kmplib). */
    const val ABOUT_LIBRARIES_PLUGIN_ID: String = "com.mikepenz.aboutlibraries.plugin"

    /** A tarefa do AboutLibraries que grava o JSON de todas as variantes. */
    const val EXPORT_TASK: String = "exportLibraryDefinitions"

    /** Onde o JSON fica no app — o `composeResources` do `commonMain`, lido por `Res.readBytes("files/aboutlibraries.json")`. */
    const val OUTPUT_FILE: String = "src/commonMain/composeResources/files/aboutlibraries.json"

    /** `-Pkmplib.licenses.autoExport=false` (ou no `gradle.properties`) desliga a geração automática no build. */
    const val AUTO_EXPORT_PROPERTY: String = "kmplib.licenses.autoExport"

    /**
     * As tarefas do Compose Multiplatform que LEEM o `composeResources` do `commonMain` — elas precisam
     * rodar depois da exportação, senão o build empacota o JSON da rodada anterior e o Gradle reprova a
     * dependência implícita (a exportação escreve na pasta que elas leem).
     */
    fun readsCommonComposeResources(taskName: String): Boolean =
        taskName.endsWith("ForCommonMain") && taskName.contains("Resource")

    /** Ausente = ligado; só `false` (sem diferenciar caixa) desliga. */
    fun autoExportEnabled(propertyValue: String?): Boolean =
        !propertyValue.orEmpty().trim().equals("false", ignoreCase = true)
}
