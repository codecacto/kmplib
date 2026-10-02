plugins {
    id("kmplib.module")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api`: `NavType`, `SavedState` e `KType` aparecem na assinatura pública (`enumNavType`,
            // `enumTypeMap`). Só o `navigation-common` — o app já traz o `navigation-compose`.
            api(libs.navigation.common)
        }
    }
}
