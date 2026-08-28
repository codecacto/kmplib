plugins {
    id("kmplib.module")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Fases da lua: cálculo puro. Não depende nem do kmplib-core — só de datas, que
            // aparecem na API pública (MoonPhaseEvent.instant, dateIn(TimeZone)).
            api(libs.kotlinx.datetime)
        }
    }
}
