plugins {
    id("kmplib.module.compose")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            // `AddressFields` — o formulário de endereço que preenche sozinho a partir do CEP.
            // É o único Composable do módulo, e é por causa dele que o Compose entra aqui.
            api(project(":kmplib-ui"))
            api(project(":kmplib-mask"))
            // Os ~100 mil CEPs e o parser de BrCode do Pix são modelos @Serializable públicos.
            api(libs.kotlinx.serialization.json)
        }
    }
}
