plugins {
    id("kmplib.module.pure")
    alias(libs.plugins.kotlinSerialization)
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
