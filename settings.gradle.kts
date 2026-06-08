pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kmplib"

// O módulo se mantém na pasta `library/` no disco, mas é exposto ao Gradle como
// `:kmplib`. Isso é necessário porque o Kotlin Multiplatform deriva o artifactId
// dos artefatos por-target (iosArm64/iosSimulatorArm64/iosX64) do NOME DO PROJETO
// Gradle. Com o módulo chamado `:library`, os artefatos iOS saíam como
// `library-iosarm64` etc., quebrando a resolução iOS dos consumidores (que esperam
// `kmplib-iosarm64`). Mapear `:kmplib` -> pasta `library/` restaura o naming correto
// (idêntico à 1.0.0) sem precisar mover arquivos no disco.
include(":kmplib")
project(":kmplib").projectDir = file("library")
