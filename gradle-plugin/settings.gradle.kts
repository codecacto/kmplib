// `br.com.codecacto:kmplib-gradle-plugin` — o plugin Gradle que o APP aplica (2.233.0).
//
// Build PRÓPRIO, e não um módulo do build da kmplib, por dois motivos:
//  · o app o inclui em `pluginManagement { includeBuild(...) }`, e resolver plugin não pode exigir
//    configurar os 23 módulos KMP da lib (nem o host Mac que os alvos Apple pedem);
//  · ele é JVM puro — sai igual de qualquer host, sem a guarda de macOS dos artefatos KMP.
//
// A VERSÃO é a do conjunto (`../gradle.properties`, `kmplib.version`), lida no build.gradle.kts; o
// catálogo é o MESMO da lib, como no `build-logic`.
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

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

rootProject.name = "kmplib-gradle-plugin"
