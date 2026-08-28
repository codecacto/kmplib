plugins {
    id("kmplib.module.compose")
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
