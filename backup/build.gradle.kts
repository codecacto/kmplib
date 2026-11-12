plugins {
    id("kmplib.module.compose")
    alias(libs.plugins.kotlinSerialization)
}

// `kmplib-backup` (2.355.0, OPT-IN) — cópia de segurança EM ARQUIVO para app sem conta (os offline):
// `<slug>-DD-MM-AAAA.copia` = ZIP com `manifesto.json`, `dados.json` e `anexos/`. O app fornece o
// export/import das tabelas (e a migração de esquema); a lib monta/lê o arquivo, confere o app e o
// esquema, e liga o salvar (seletor do sistema), o enviar (folha do sistema) e o escolher arquivo.
// NÃO está no umbrella.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            api(project(":kmplib-platform"))
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
        }
    }
}
