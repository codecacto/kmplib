plugins {
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.vanniktech.mavenPublish) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kover) apply false
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
