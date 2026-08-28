import org.gradle.accessors.dm.LibrariesForLibs

/**
 * Camada opcional sobre [kmplib.module] para os módulos que desenham tela.
 *
 * Fica separado porque o compilador do Compose custa tempo de build em todo módulo onde é
 * aplicado, e parte da lib (`core`, `validation`, `astro`, `pdf`) não tem um `@Composable` sequer.
 *
 * As dependências do Compose vêm daqui, e não do build de cada módulo, porque `implementation` não
 * é transitivo: enquanto os ícones eram declarados só no `kmplib-ui`, as telas de contato, de
 * desenvolvedor e o botão de voz — que moram em outros módulos — deixavam de enxergar `Icons`.
 */
plugins {
    id("kmplib.module")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = the<LibrariesForLibs>()

kotlin {
    sourceSets {
        commonMain.dependencies {
            // A kmplib É uma biblioteca de UI: todo composable público nomeia tipos do Compose,
            // então api() — mesmo padrão do androidx.compose.material3, que declara `api` para ui
            // e foundation.
            api(libs.compose.ui)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            // As 28 funções @Preview da lib são todas private: uso interno.
            implementation(compose.components.uiToolingPreview)
            // Os módulos usam VALORES (Icons.Outlined.*) como default de parâmetro, nunca um TIPO
            // deste artefato — o tipo é ImageVector, que vem do compose.ui acima.
            @Suppress("DEPRECATION")
            implementation(compose.materialIconsExtended)
        }
    }
}
