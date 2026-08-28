plugins {
    id("kmplib.module.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            api(project(":kmplib-ui"))
            api(project(":kmplib-mask"))
            // Câmera exige permissão em runtime.
            api(project(":kmplib-platform"))
        }

        androidMain.dependencies {
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            // OCR de placa e leitura de código de barras — modelo embarcado, funciona offline.
            implementation(libs.mlkit.text.recognition)
            implementation(libs.mlkit.barcode.scanning)
        }
    }
}
