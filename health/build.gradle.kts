plugins {
    id("kmplib.module")
}

// `connect-client` 1.1.0 (AndroidX Health Connect) exige minSdk 26 no APP consumidor — maior que o
// 24 padrão da kmplib. Como este artefato é SEPARADO, só quem declara `kmplib-health` sobe o
// próprio minSdk; os demais módulos e apps continuam em 24. Ver `kmplib-catalog/references/health.md`.
kotlin {
    sourceSets {
        commonMain.dependencies {
            // `EnergySource`/`ExerciseCategory` — a mesma régua de fonte de calorias e de mapeamento
            // de exercício vale no celular e no `kmplib-workout` do backend.
            api(project(":kmplib-workout"))
            implementation(libs.kotlinx.coroutines.core)
        }

        androidMain.dependencies {
            implementation("androidx.health.connect:connect-client:1.1.0")
            implementation(libs.kotlinx.coroutines.android)
            // Só `activity-ktx` (ComponentActivity + `activityResultRegistry`), NUNCA
            // `activity-compose`: este módulo não carrega o runtime do Compose.
            implementation("androidx.activity:activity-ktx:1.10.1")
        }
    }
}
