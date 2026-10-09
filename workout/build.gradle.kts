plugins {
    id("kmplib.module.pure")
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kover)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Domínio puro do treino guiado: modelo, máquina de estados, protocolo celular<->relógio,
            // estimativa de calorias e agregados. Compila também para watchOS (ver kmplib.module.pure) —
            // por isso NENHUMA dependência de Compose, Koin, Ktor ou persistência aqui.
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
        }
    }
}

// Tabela evento -> estado exportada para `fixtures/engine-transitions.json` (caso de teste da máquina
// para outra linguagem, ex.: Monkey C). `-Pkmplib.workout.updateFixtures=true` regrava o arquivo.
tasks.withType<Test>().configureEach {
    systemProperty("kmplib.workout.fixturesDir", layout.projectDirectory.dir("fixtures").asFile.absolutePath)
    providers.gradleProperty("kmplib.workout.updateFixtures").orNull?.let {
        systemProperty("kmplib.workout.updateFixtures", it)
    }
}

// Fundação: cobertura total (skill `test-strategy`). O relatório `total` do Kover mede o alvo jvm,
// que roda o MESMO commonTest dos demais alvos: `./gradlew :kmplib-workout:koverVerify`.
kover {
    reports {
        verify {
            rule {
                minBound(95)
            }
        }
    }
}
