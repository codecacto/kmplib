plugins {
    id("kmplib.module")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            // Os ~100 mil CEPs e o parser de BrCode do Pix são modelos @Serializable públicos.
            api(libs.kotlinx.serialization.json)
        }
    }
}
