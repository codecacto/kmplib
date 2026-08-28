plugins {
    id("kmplib.module.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            // Permissão de localização passa pelo host de permissões do platform.
            api(project(":kmplib-platform"))
        }

        androidMain.dependencies {
            // Fused Location Provider — o caminho oficial de GPS no Android.
            implementation(libs.play.services.location)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core)
        }
    }
}
