plugins {
    id("kmplib.module.compose")
}

// `kmplib-bible` (2.354.0, OPT-IN) — motor de texto bíblico embarcado: os 66 livros (USFM) com nomes e
// abreviações em pt-BR/pt-PT/en/es, a chave canônica de referência (`JHN.3.16`), o parser do que a pessoa
// digita, a leitura do banco SQLite somente leitura gerado pelo `Ferramentas/conteudo-biblico/` (busca por
// partes sem acento com `instr()`), o seletor Livro → Capítulo → Versículo (sem cor própria) e o cálculo
// da Páscoa/tempos litúrgicos. Genérico: nada de "família" aqui.
//
// NÃO está no umbrella: só app que mostra Bíblia declara (o texto bíblico não vai na lib — cada app embarca
// o recorte que usa em `composeResources/files/`).
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            api(libs.kotlinx.datetime)
        }
        iosMain.dependencies {
            // SQLite do sistema pelo SQLiter (o motor do driver nativo do SQLDelight, já no grafo do kmplib-sync).
            implementation(libs.sqliter)
        }
        androidUnitTest.dependencies {
            // O leitor é testado contra SQLite de verdade (JDBC) — o android.database do teste de unidade é stub.
            implementation(libs.sqldelight.driver.sqlite)
        }
    }
}
