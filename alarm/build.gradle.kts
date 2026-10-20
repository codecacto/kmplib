plugins {
    // Sem Compose: agendamento de alarme, sem tela.
    id("kmplib.module")
}

/**
 * `kmplib-alarm` (2.308.0) — alarme de horário que ACORDA o aparelho (início de ronda, lembrete):
 * `AlarmManager.setAlarmClock`/`setExactAndAllowWhileIdle` no Android; AlarmKit (iOS 26+, pela ponte
 * Swift) ou notificação Time Sensitive no iOS.
 *
 * OPT-IN, fora do umbrella `:kmplib`: o manifesto declara `SCHEDULE_EXACT_ALARM`, permissão de uso
 * RESTRITO na Play (o app precisa ter alarme/agenda como função) — mesma razão do
 * `kmplib-video-download` e do `kmplib-location-session`. Só o app que agenda alarme declara.
 */
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
        }

        androidMain.dependencies {
            // NotificationCompat / NotificationManagerCompat / AlarmManagerCompat.
            implementation(libs.androidx.core)
        }
    }
}
