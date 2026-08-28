// Build separado (`includeBuild` no settings raiz) que hospeda os convention plugins da kmplib.
// Precisa da própria declaração de repositórios e do MESMO version catalog do build principal —
// é assim que `kmplib.library.gradle.kts` consegue ler `libs.versions.toml` sem duplicar versão.
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
