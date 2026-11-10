plugins {
    id("kmplib.module.compose")
    // Alvo desktop (2.353.0, opt-in): own-auth + login no Compose Desktop.
    id("kmplib.module.jvm")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            // As máscaras SÃO `VisualTransformation` do Compose — o tipo está em toda assinatura
            // pública do módulo, então api(). É por causa disto que `mask` não mora em
            // `kmplib-core`: a base não arrasta o compilador do Compose.
        }
    }
}
