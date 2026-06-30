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

// O nome do BUILD precisa ser DIFERENTE do nome do subprojeto `:kmplib` abaixo.
// Se ambos se chamarem "kmplib", um consumidor que use este projeto via `includeBuild`
// COM acessores tipados (`enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")`) quebra na
// geração do `RootProjectAccessor` com "method getKmplib() is already defined" — o build
// e o subprojeto colidem no mesmo acessor. Manter o build como "kmplib-build" elimina a
// colisão sem afetar artefatos (que derivam do NOME DO SUBPROJETO `:kmplib`, não do build).
rootProject.name = "kmplib-build"

// O módulo se mantém na pasta `library/` no disco, mas é exposto ao Gradle como
// `:kmplib`. Isso é necessário porque o Kotlin Multiplatform deriva o artifactId
// dos artefatos por-target (iosArm64/iosSimulatorArm64/iosX64) do NOME DO PROJETO
// Gradle. Com o módulo chamado `:library`, os artefatos iOS saíam como
// `library-iosarm64` etc., quebrando a resolução iOS dos consumidores (que esperam
// `kmplib-iosarm64`). Mapear `:kmplib` -> pasta `library/` restaura o naming correto
// (idêntico à 1.0.0) sem precisar mover arquivos no disco.
include(":kmplib")
project(":kmplib").projectDir = file("library")
