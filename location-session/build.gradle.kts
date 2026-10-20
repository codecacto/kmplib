plugins {
    // Sem Compose: o módulo não desenha tela — serviço de localização + estado em Flow.
    id("kmplib.module")
}

/**
 * `kmplib-location-session` (2.308.0) — localização CONTÍNUA durante uma sessão (ronda de vigia):
 * Foreground Service `location` + Fused Location Provider no Android, `CLLocationManager` +
 * `CLBackgroundActivitySession`/`CLServiceSession` no iOS.
 *
 * OPT-IN, fora do umbrella `:kmplib`: o manifesto deste módulo declara `ACCESS_BACKGROUND_LOCATION`,
 * `FOREGROUND_SERVICE_LOCATION` e um serviço `foregroundServiceType="location"` — na Play isso exige a
 * declaração de localização em segundo plano. Herdar isso sem usar barraria a publicação de todo app
 * do portfólio (mesmo motivo do `kmplib-video-download`). Só o app que faz ronda declara.
 */
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
        }

        androidMain.dependencies {
            // Fused Location Provider — o caminho oficial de localização contínua no Android.
            implementation(libs.play.services.location)
            // NotificationCompat / ServiceCompat.startForeground(…, FOREGROUND_SERVICE_TYPE_LOCATION).
            implementation(libs.androidx.core)
        }
    }
}
