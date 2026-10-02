import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    `java-gradle-plugin`
    // O MESMO Kotlin da lib (e do app), não o `kotlin-dsl`: o `kotlin-dsl` traz o Kotlin embutido do
    // Gradle, e dois KGPs no mesmo composite (este build é incluído pela lib e pelo app) quebram o
    // configuration cache (`checkKotlinGradlePluginConfigurationErrors` não desserializa).
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.vanniktech.mavenPublish)
}

// Versão do conjunto da kmplib — um número só para lib e plugin (o app pede os dois pela mesma
// `version.ref`). Lida do arquivo porque este é outro build: `providers.gradleProperty` não o vê.
val kmplibVersion: String = Properties()
    .apply { rootDir.parentFile.resolve("gradle.properties").reader().use(::load) }
    .getProperty("kmplib.version")
    ?: error("kmplib.version ausente em ../gradle.properties")

group = "br.com.codecacto"
version = kmplibVersion

kotlin {
    jvmToolchain(17)
    // Recomendação do Gradle para plugin em Kotlin: a linguagem/API no nível do Kotlin EMBUTIDO no
    // Gradle que vai carregar o plugin (2.0 no Gradle 8.x) — é o stdlib dele que roda o plugin.
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
    }
}

val functionalTestPlugins: Configuration by configurations.creating

dependencies {
    // `compileOnly`: o plugin REAGE ao AGP e ao Kotlin que o app já aplicou (`withPlugin`) e usa as
    // classes DELE em runtime. Como `implementation`, arrastaria uma segunda versão de cada um para o
    // classpath de plugins do app.
    compileOnly(libs.plugin.android.gradle.api)
    compileOnly(libs.plugin.kotlin.gradle)
    compileOnly(kotlin("stdlib"))

    testImplementation(libs.kotlin.test)
    testImplementation(gradleTestKit())

    // O teste funcional (TestKit) aplica o AGP e o Kotlin de verdade num projeto temporário: eles
    // entram no classpath do "plugin sob teste" para o plugin enxergá-los como no app.
    functionalTestPlugins(libs.plugin.android.gradle)
    functionalTestPlugins(libs.plugin.kotlin.gradle)
}

tasks.pluginUnderTestMetadata {
    pluginClasspath.from(functionalTestPlugins)
}

gradlePlugin {
    plugins {
        register("storeDouble") {
            id = "br.com.codecacto.kmplib.store-double"
            implementationClass = "br.com.codecacto.kmplib.gradle.StoreDoublePlugin"
            displayName = "kmplib — dublê da loja só em build de QA"
            description = "Liga o dublê da loja (kmplib-testing) por -Pqa.paywallDemo / QA_PAYWALL_DEMO e " +
                "reprova qualquer build publicável que o carregue (variante Android não-debuggable, " +
                "framework iOS release, Archive do Xcode)."
        }
    }
}

tasks.test {
    useJUnit()
    // O projeto temporário do teste funcional precisa do Android SDK (o mesmo da lib).
    val sdk = rootDir.parentFile.resolve("local.properties").takeIf { it.exists() }
        ?.let { f -> Properties().apply { f.reader().use(::load) }.getProperty("sdk.dir") }
        ?: System.getenv("ANDROID_HOME")
    if (sdk != null) systemProperty("kmplib.androidSdk", sdk)
}

mavenPublishing {
    configure(GradlePlugin(javadocJar = JavadocJar.Empty(), sourcesJar = true))
    publishToMavenCentral()
    if (project.hasProperty("signing.keyId")) {
        signAllPublications()
    }
    coordinates(group.toString(), "kmplib-gradle-plugin", version.toString())

    pom {
        name = "kmplib-gradle-plugin"
        description = "CodeCacto KMP — plugins Gradle que o app aplica (dublê da loja)"
        inceptionYear = "2026"
        url = "https://github.com/codecacto/kmplib"
        licenses {
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }
        developers {
            developer {
                id = "codecacto"
                name = "CodeCacto"
                url = "https://codecacto.com.br"
            }
        }
        scm {
            url = "https://github.com/codecacto/kmplib"
            connection = "scm:git:git://github.com/codecacto/kmplib.git"
            developerConnection = "scm:git:ssh://github.com/codecacto/kmplib.git"
        }
    }
}
