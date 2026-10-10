plugins {
    id("kmplib.module.compose")
}

kotlin {
    sourceSets {
        // A câmera guiada (2.278.0) codifica a foto pelo MESMO encoder do seletor de imagem do
        // `kmplib-ui` (JPEG em pé, sem EXIF, com teto) — API interna entre módulos da lib.
        all { languageSettings.optIn("br.com.codecacto.kmplib.ui.KmpLibUiInternalApi") }

        commonMain.dependencies {
            api(project(":kmplib-core"))
            api(project(":kmplib-ui"))
            api(project(":kmplib-mask"))
            // Câmera exige permissão em runtime.
            api(project(":kmplib-platform"))
            // LifecycleResumeEffect da câmera guiada: reconsulta a permissão ao voltar das
            // Configurações.
            implementation(libs.androidx.lifecycle.runtime.compose)
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
