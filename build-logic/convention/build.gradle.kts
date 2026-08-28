plugins {
    `kotlin-dsl`
}

// Os plugins que os convention plugins APLICAM entram aqui como dependência de implementação,
// pela coordenada do artefato do plugin (não pelo id). Sem isso, o
// `plugins { id("org.jetbrains.kotlin.multiplatform") }` de um script pré-compilado não resolve.
dependencies {
    implementation(libs.plugin.kotlin.gradle)
    implementation(libs.plugin.android.gradle)
    implementation(libs.plugin.compose.multiplatform)
    implementation(libs.plugin.compose.compiler)
    implementation(libs.plugin.maven.publish)

    // Dá ao script pré-compilado acesso ao `LibrariesForLibs` — a classe que o Gradle GERA a
    // partir do libs.versions.toml e que só existe no classpath do build que a gerou. Sem esta
    // linha, `the<LibrariesForLibs>()` não resolve e o convention plugin teria de repetir as
    // versões à mão. É o mesmo recurso usado pelo projeto Now in Android, do Google.
    compileOnly(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

kotlin {
    jvmToolchain(17)
}
