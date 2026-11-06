plugins {
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.vanniktech.mavenPublish) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kover)
}

// =============================================================================
// Kover — cobertura medida NA RAIZ, somando todos os módulos
// =============================================================================
//
// A lib virou N módulos e o `:kmplib` (`library/`) ficou só como fachada: 2 fontes e nenhum teste.
// Medir nele dava 0% contra um mínimo de 40 e o job reprovava a cada push, enquanto os testes dos
// módulos reais nem sequer rodavam no CI. `merge` aplica o Kover em cada subprojeto e soma tudo na
// variante `unit`:
//
//   ./gradlew koverVerifyUnit        roda os testes de unidade dos módulos e confere o mínimo
//   ./gradlew koverHtmlReportUnit    relatório em build/reports/kover/htmlUnit/
//
// `debug` (e não `total`) para não rodar a mesma suíte duas vezes, em debug e em release. Não muda
// nada do que a lib publica: o Kover instrumenta só as tasks de teste.
// `kmplib-workout-fixtures` fica de fora: JAR sem código, só com dados de teste.
kover {
    merge {
        subprojects { it.name != "kmplib-workout-fixtures" }
        createVariant("unit") {
            add("debug", optional = true)
        }
    }

    reports {
        filters {
            excludes {
                // Classes geradas pelo Compose
                classes("*generated.resources*")
                classes("*ComposableSingletons*")
                // Holders são providers para Android lifecycle — não testáveis em unit test
                classes("*Holder")
            }
        }

        verify {
            rule {
                minBound(40) // Aumentar conforme a cobertura crescer.
            }
        }
    }
}

// O `kmplib-gradle-plugin` (build incluído `gradle-plugin/`) sai junto com a lib, na mesma versão:
// quem roda `./gradlew publishToMavenLocal` (ou o release do Central) publica os dois. Sem isto, um
// clone isolado que cai no mavenLocal acharia a lib 2.233.0 e não o plugin que o app aplica.
listOf("publishToMavenLocal", "publishToMavenCentral", "publishAndReleaseToMavenCentral").forEach { publishTask ->
    tasks.register(publishTask) {
        group = "publishing"
        description = "Publica o kmplib-gradle-plugin ($publishTask)."
        dependsOn(gradle.includedBuild("gradle-plugin").task(":$publishTask"))
    }
}
