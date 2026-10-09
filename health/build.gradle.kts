plugins {
    id("kmplib.module")
    alias(libs.plugins.kover)
}

// `connect-client` 1.1.0 (AndroidX Health Connect) declara minSdk 26 — maior que o 24 padrão da
// kmplib. Como este artefato é SEPARADO, só quem declara `kmplib-health` sobe o próprio minSdk para
// 26; os demais módulos e apps continuam em 24. Ver `kmplib-catalog/references/health.md`.
android {
    defaultConfig {
        minSdk = 26
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `EnergySource`/`ExerciseCategory`/`WorkoutRun` — a mesma régua de fonte de calorias e de
            // mapeamento de exercício vale no celular e no `kmplib-workout` do backend.
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

// Fundação: a regra do módulo mora em commonMain e é medida pelos testes de unidade do Android
// (o mesmo commonTest). Os gateways de plataforma (Health Connect, HealthKit, GATT, CoreBluetooth)
// só se provam em aparelho (spike 0.7) e ficam fora da conta.
kover {
    currentProject {
        createVariant("fundacao") {
            add("debug")
        }
    }
    reports {
        filters {
            excludes {
                classes(
                    "br.com.codecacto.kmplib.health.HealthConnectGateway*",
                    "br.com.codecacto.kmplib.health.HealthConnectGateway_androidKt*",
                    "br.com.codecacto.kmplib.health.HealthActivityHolder*",
                    "br.com.codecacto.kmplib.health.HealthContextHolder*",
                    "br.com.codecacto.kmplib.health.HealthActivityHolder_androidKt*",
                    "br.com.codecacto.kmplib.health.heartrate.AndroidBleHeartRateTransport*",
                    "br.com.codecacto.kmplib.health.heartrate.AndroidBleHeartRateTransport_androidKt*",
                    "br.com.codecacto.kmplib.health.heartrate.BleLinkLostException",
                )
            }
        }
        variant("fundacao") {
            verify {
                rule {
                    minBound(95)
                }
            }
        }
    }
}
