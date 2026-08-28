plugins {
    id("kmplib.module")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            // KMPNotifier aparece na API pública (o listener que o app registra).
            api(libs.kmpnotifier)
        }

        androidMain.dependencies {
            // FCM: o serviço do KMPNotifier no Android precisa do Firebase Messaging.
            api(libs.firebase.common.android)
        }
    }
}
