plugins {
    id("kmplib.module")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            api(libs.kotlinx.serialization.json)

            // GitLive Firebase — Auth, Storage e Remote Config. SEM Firestore, por decisão de
            // arquitetura: dado vem do apps-api, Firebase fica em login e push.
            // `implementation` está auditado e correto: FirebaseAuth/FirebaseUser/FirebaseStorage
            // são internal aqui, e a API pública é toda em tipos próprios (IAuthRepository, User,
            // StorageService). É isso que deixa um projeto own-auth consumir a lib sem falar
            // Firebase.
            implementation(libs.firebase.auth)
            implementation(libs.firebase.storage)
            implementation(libs.firebase.config)
        }

        androidMain.dependencies {
            // api() no Android: o SDK do Firebase precisa chegar ao app (Gradle não resolve o
            // google-services sem ele no classpath do consumidor).
            api(libs.firebase.auth.android)
            api(libs.firebase.storage.android)
            api(libs.firebase.common.android)
            api(libs.firebase.analytics.android)
            api(libs.firebase.config.android)
        }
    }
}
