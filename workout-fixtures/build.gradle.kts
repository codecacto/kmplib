import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import java.util.zip.ZipFile

// =============================================================================
// br.com.codecacto:kmplib-workout-fixtures — as tabelas de casos do `kmplib-workout`, SÓ DE TESTE
// =============================================================================
//
// Um JAR sem código, com os arquivos de `workout/fixtures/` em `kmplib/workout/fixtures/` no
// classpath:
//   - `engine-transitions.json` — evento -> estado da máquina do treino guiado (outra implementação
//     da máquina, ex.: o app Connect IQ, roda os mesmos casos);
//   - `session-validation.json` — sessão -> veredito do `validateRun` (o teste do backend roda com o
//     JAR JVM `kmplib-workout-jvm` e garante que o servidor aceita a sessão que o celular produz).
//
// Artefato SEPARADO de propósito: o consumidor declara em `testImplementation`, e nenhum dado de
// teste entra no JAR de produção do backend nem no app. Os arquivos são gerados e conferidos pelo
// `TransitionFixtureExportTest` do `kmplib-workout` (`jvmTest`) — o teste falha se ficarem
// desatualizados. Uso: `kmplib-catalog/references/workout.md` §"Tabelas de casos".
plugins {
    `java-library`
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "br.com.codecacto"
version = providers.gradleProperty("kmplib.version").get()

// Mesma JVM-alvo do resto da lib (`jvmToolchain(17)`): sem isto o JAR herda a do Gradle (21) e o
// Gradle de um backend em 17 recusa resolver ("only compatible with JVM runtime version 21").
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

val fixturesDir = rootProject.layout.projectDirectory.dir("workout/fixtures")

tasks.named<ProcessResources>("processResources") {
    from(fixturesDir) {
        include("*.json")
        into("kmplib/workout/fixtures")
    }
}

// O JAR só existe se trouxer os dois arquivos — artefato vazio publicado em silêncio seria o pior caso
// (o teste do backend leria "nenhum caso" e passaria).
tasks.named<Jar>("jar") {
    val archive = archiveFile
    doLast {
        val names = ZipFile(archive.get().asFile).use { zip ->
            zip.entries().asSequence().map { it.name }.toSet()
        }
        listOf("engine-transitions.json", "session-validation.json").forEach { required ->
            val entry = "kmplib/workout/fixtures/$required"
            if (entry !in names) throw GradleException("kmplib-workout-fixtures: $entry ausente no JAR")
        }
    }
}

mavenPublishing {
    configure(JavaLibrary(javadocJar = JavadocJar.Empty(), sourcesJar = true))
    publishToMavenCentral()
    if (project.hasProperty("signing.keyId")) {
        signAllPublications()
    }
    coordinates(group.toString(), project.name, version.toString())

    pom {
        name = project.name
        description = "CodeCacto KMP — tabelas de casos (fixtures JSON) do kmplib-workout, só para teste"
        inceptionYear = "2025"
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
