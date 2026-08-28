# Modularização da kmplib

**Status: FEITO em 28/ago/2026, na versão 2.163.0.** O texto abaixo da linha é a proposta original,
mantida como registro. Esta abertura conta o que foi executado e onde ela errou — o que importa,
porque as duas coisas que mais pesaram no resultado não estavam nela.

## O que foi feito

21 módulos publicáveis, `br.com.codecacto:kmplib` preservado como **umbrella** (`api()` de todos),
nenhum pacote renomeado, nenhuma API removida, nenhum dos ~25 apps precisando ser tocado. 2.316
testes, zero falhas. Piloto: Cidade Conectada.

## As três correções à proposta

**1. A causa raiz não era só o tamanho — era o `export()`.** A proposta atribui o OOM ao volume da
lib. O multiplicador real é outro: exportar uma dependência declara cada símbolo público dela no
header Obj-C e **a torna raiz do dead code elimination**. Nada abaixo de uma raiz pode ser
eliminado, e toda raiz entra no CallGraph do `DevirtualizationAnalysis`. Com `export(libs.kmplib)`,
eram ~1.436 declarações de nível superior servindo de raiz — num app cujo Swift usa **dois** objetos
(`GoogleSignInBridge`, `ApplePushBridge`). Trocando por `export(kmplib-auth)` + `export(kmplib-push)`:
~96 raízes, **−93%**. Modularizar sem corrigir o `export` daria uma fração disso.

**2. Faltava uma linha de configuração, e ela não é da lib.** `org.gradle.jvmargs` **não vale para o
Kotlin/Native**: o link do framework roda em processo próprio, com heap default. Os 8GB do daemon
nunca chegaram ao compilador que estourava. `kotlin.native.jvmArgs=-Xmx6g` no `gradle.properties` do
app.

**3. A árvore de dependências da proposta está invertida.** Ela põe `auth` e `monetization`
dependendo de `ui` ("usa telas de login", "usa telas de paywall"). O real era o contrário: era o
`ui` que importava monetization, sync, auth, brdata, feedback, developer, qr e contact. É essa
inversão que produz a estimativa de 8-15 dias — com a seta virada, o único caminho visível é picar o
`ui` em pedaços.

A seta certa é **feature → design system**, e aí o conserto é pequeno: **34 arquivos, de 150**,
causavam todo o acoplamento do `ui`, e nenhum deles era design system — eram telas de login, de
cadastro e de paywall, banners de sincronização, o medidor de uso. Cada um foi para o módulo dono, e
o `ui` ficou com o que o nome promete.

## Números medidos (não estimados)

| | proposta estimava | medido |
|---|---|---|
| arquivos em commonMain | 153 | **422** |
| ciclos no grafo interno | não menciona | **5** |
| raízes do DCE, depois | — | **−93%** (1.436 → 96) |
| código fora do binário do piloto | −74% | **−19%** (112 arquivos, 16.187 linhas) |

O −19% é honesto e menor que o −74% da proposta porque aquele número supunha um app que usa "só
core + ui + auth". O Cidade Conectada usa 15 dos 21 módulos. **O que resolve o OOM dele é o −93% das
raízes, não o −19% do código.**

## O que a proposta acertou

O diagnóstico do sintoma, a leitura de que `DevirtualizationAnalysis`/`DCE` constroem um CallGraph
da aplicação inteira, a escolha de modularizar em vez de XCFramework pré-compilado ou máquina maior,
e a ordem de extração (do mais independente para o mais acoplado). O plano de fases também se
sustentou — o que mudou foi que a Fase 4 (deprecar o monolítico) **não existe**: o umbrella fica.

Detalhe por versão: `CHANGELOG.md` (2.163.0).

---

# (registro) Proposta original — 28/ago/2026


## 1. O Problema

### 1.1 Sintoma

O projeto **cidade-conectada** (e potencialmente outros) **nao consegue compilar iOS Release** em um Mac com 16GB de RAM:

```
e: java.lang.OutOfMemoryError: Java heap space
    at org.jetbrains.kotlin.backend.konan.optimizations.DevirtualizationAnalysis...
```

O erro ocorre na task `linkReleaseFrameworkIosArm64`, especificamente nas fases:
- `DevirtualizationAnalysis` (analise de tipos virtuais)
- `DCEPhase` (Dead Code Elimination)
- `RemoveRedundantCallsToStaticInitializersPhase`

Todas essas fases constroem um **CallGraph** de toda a aplicacao, incluindo tudo que esta exportado.

### 1.2 Causa Raiz

A **kmplib e uma biblioteca monolitica gigante** que e exportada inteira para o iOS framework:

```kotlin
// Em cada app que usa a lib
iosTarget.binaries.framework {
    export(libs.kmplib)  // <-- EXPORTA TUDO
}
```

**Numeros da kmplib v2.160.0:**
- **153 arquivos Kotlin** em commonMain
- **73 arquivos** iOS-specific
- **80 arquivos** Android-specific
- **7.7MB** de codigo fonte
- **31 modulos/pacotes** diferentes
- **13+ frameworks externos** (Firebase, RevenueCat, Ktor, Maps, Camera, etc.)

Quando o Kotlin Native compila o Release, ele precisa:
1. Analisar **TODOS** os tipos e metodos da kmplib
2. Construir um grafo de chamadas de **TUDO**
3. Fazer otimizacoes em **TUDO**

**Mesmo que o app use 10% da lib, o compilador processa 100%.**

### 1.3 Por que os outros apps "funcionaram"

Verificacao feita em 28/ago/2026:
- **Nenhum projeto** em `/Users/weback/CodeCacto/` tem build iOS Release compilado localmente
- Os apps que foram para a App Store provavelmente foram compilados em **outra maquina** ou **antes da kmplib crescer tanto**
- O problema vai afetar **todos os apps** que tentarem fazer Archive para App Store nesta maquina

---

## 2. Por que Modularizar

### 2.1 Beneficios Imediatos

| Beneficio | Descricao |
|-----------|-----------|
| **Compila em 16GB** | Apps importam so o que usam, reduzindo o grafo de tipos |
| **Binario menor** | iOS framework fica menor (menos codigo morto) |
| **Build mais rapido** | Menos codigo para analisar/otimizar |
| **APK menor** | Android tambem se beneficia (menos DEX) |

### 2.2 Beneficios de Longo Prazo

| Beneficio | Descricao |
|-----------|-----------|
| **Independencia de times** | Modulos podem evoluir separadamente |
| **Testes focados** | Cada modulo tem sua suite de testes |
| **Versionamento granular** | Bump so no que mudou |
| **Adocao gradual** | Apps novos podem comecar so com `kmplib-core` |

### 2.3 Como Outras Empresas Fazem

**Ktor (JetBrains):**
```
ktor-client-core
ktor-client-okhttp
ktor-client-darwin
ktor-client-content-negotiation
ktor-serialization-kotlinx-json
```

**kotlinx.serialization (JetBrains):**
```
kotlinx-serialization-core
kotlinx-serialization-json
kotlinx-serialization-protobuf
kotlinx-serialization-cbor
```

**SQLDelight (Square/CashApp):**
```
sqldelight-runtime
sqldelight-coroutines-extensions
sqldelight-android-driver
sqldelight-native-driver
```

**Padrao:** Cada recurso e um artefato Maven separado. Apps importam so o que precisam.

---

## 3. Estrutura Atual da KmpLib

### 3.1 Modulos Existentes (por tamanho)

| Modulo | Tamanho | Arquivos | O que faz |
|--------|---------|----------|-----------|
| **ui** | 1.3M | 135 | Compose UI, telas, layouts |
| **sync** | 284K | 20 | Offline-first com SQLDelight |
| **brdata** | 284K | 4 | CEPs brasileiros (~100k) |
| **platform** | 264K | 37 | Notificacoes, biometria, permissoes, audio, TTS |
| **monetization** | 200K | 24 | RevenueCat, paywall, compras |
| **core** | 160K | 26 | HTTP client, REST, storage, formatters |
| **auth** | 132K | 20 | Firebase Auth, OwnAuth, login social |
| **pdf** | 128K | 20 | Geracao de PDF (9 tipos) |
| **ads** | 104K | 20 | Sistema de ads interno |
| **camera** | 84K | 13 | Camera, barcode, OCR |
| **pix** | 84K | 6 | Parser PIX/BrCode |
| **qr** | 72K | 8 | Encoder QR Code |
| **firebase** | 72K | 11 | Wrapper Firebase (GitLive) |
| **map** | 52K | 8 | Google Maps / MapKit |
| **astro** | 48K | 6 | Fases da lua |
| **validation** | 44K | 7 | Validadores (CPF, CNPJ, etc) |
| **voice** | 44K | 4 | Reconhecimento de voz, TTS |
| **mask** | 36K | 8 | Mascaras de input |
| **media** | 36K | 7 | Audio/video player |
| **torch** | 36K | 6 | Lanterna |
| **appupdate** | 36K | 6 | Checagem de versao |
| **push** | 28K | 7 | Push notifications |
| **observability** | 24K | 4 | Crash reporting (Sentry) |
| **permissions** | 20K | 2 | Gerenciamento de permissoes |
| **contact** | 16K | 3 | Picker de contatos |
| **developer** | 16K | 3 | Menu de desenvolvedor |
| **feedback** | 16K | 3 | Coleta de feedback |
| **location** | 4K | 1 | GPS/geolocalizacao |
| **account** | 8K | 1 | Gerenciamento de conta |
| **signature** | 8K | 1 | Assinatura digital |

### 3.2 Dependencias Externas Principais

| Dependencia | Versao | Usado por |
|-------------|--------|-----------|
| Ktor | 3.1.1 | core, sync |
| Firebase (GitLive) | 2.1.0 | auth, firebase |
| RevenueCat | 2.2.13 | monetization |
| SQLDelight | 2.0.2 | sync |
| Compose Multiplatform | 1.10.0 | ui |
| Sentry | 0.13.0 | observability |
| KMPNotifier | 1.6.0 | push |
| Koin | 4.1.1 | todos |
| Google Maps SDK | 19.2.0 | map (Android) |
| CameraX | 1.4.2 | camera (Android) |
| ML Kit | 17.3.0 | camera (Android) |

---

## 4. Proposta de Modularizacao

### 4.1 Opcao A: Por Funcionalidade (Recomendada)

```
kmplib/
├── kmplib-core/           # Fundacao (obrigatorio)
│   ├── util/              # Formatters, extensions, validators
│   ├── network/           # HttpClient factory, REST base
│   ├── storage/           # Preferences, KeyValue store
│   └── di/                # Koin modules base
│
├── kmplib-ui/             # Compose UI (opcional)
│   ├── components/        # Botoes, inputs, cards
│   ├── screens/           # Telas prontas (Login, Feedback, etc)
│   ├── theme/             # Cores, tipografia
│   └── mvi/               # BaseViewModel, contracts
│
├── kmplib-auth/           # Autenticacao (opcional)
│   ├── firebase/          # Firebase Auth
│   ├── ownauth/           # Auth proprio (backend)
│   └── social/            # Google/Apple Sign-In
│
├── kmplib-sync/           # Offline-first (opcional)
│   ├── engine/            # SyncEngine
│   ├── store/             # SQLDelight storage
│   └── rest/              # REST adapter
│
├── kmplib-monetization/   # Compras (opcional)
│   ├── revenuecat/        # RevenueCat SDK
│   ├── paywall/           # Telas de paywall
│   └── quota/             # Sistema de cotas
│
├── kmplib-platform/       # Servicos de plataforma (opcional)
│   ├── permissions/       # Gerenciador de permissoes
│   ├── biometric/         # Autenticacao biometrica
│   ├── notifications/     # Notificacoes locais
│   └── audio/             # Audio capture, TTS
│
├── kmplib-camera/         # Camera/OCR (opcional)
│   ├── preview/           # Preview de camera
│   ├── capture/           # Captura de foto
│   ├── barcode/           # Leitor de barcode
│   └── ocr/               # Reconhecimento de placa
│
├── kmplib-map/            # Mapas (opcional)
│   ├── native/            # Google Maps / MapKit
│   └── markers/           # Sistema de markers
│
├── kmplib-pdf/            # Geracao de PDF (opcional)
│   └── generators/        # 9 tipos de documento
│
├── kmplib-push/           # Push notifications (opcional)
│   ├── fcm/               # Firebase Cloud Messaging
│   └── apns/              # Apple Push Notification
│
├── kmplib-ads/            # Ads internos (opcional)
│
├── kmplib-observability/  # Crash reporting (opcional)
│
├── kmplib-brdata/         # Dados BR (opcional)
│   └── cep/               # Banco de CEPs
│
└── kmplib-bom/            # Bill of Materials
```

### 4.2 Arvore de Dependencias

```
kmplib-bom (versoes)
    │
    └── Todos os modulos

kmplib-core (obrigatorio)
    │
    ├── kmplib-ui
    │   └── kmplib-auth (usa telas de login)
    │
    ├── kmplib-sync
    │
    ├── kmplib-monetization
    │   └── kmplib-ui (usa telas de paywall)
    │
    ├── kmplib-platform
    │
    ├── kmplib-camera
    │   └── kmplib-platform (permissoes)
    │
    ├── kmplib-map
    │   └── kmplib-platform (permissoes)
    │
    ├── kmplib-pdf
    │
    ├── kmplib-push
    │
    ├── kmplib-ads
    │
    ├── kmplib-observability
    │
    └── kmplib-brdata
```

### 4.3 Exemplo de Uso por App

**App simples (catalogo sem compras):**
```kotlin
dependencies {
    implementation("br.com.codecacto:kmplib-core:2.x.x")
    implementation("br.com.codecacto:kmplib-ui:2.x.x")
    implementation("br.com.codecacto:kmplib-auth:2.x.x")
}
```

**App com compras (atual cidade-conectada):**
```kotlin
dependencies {
    implementation("br.com.codecacto:kmplib-core:2.x.x")
    implementation("br.com.codecacto:kmplib-ui:2.x.x")
    implementation("br.com.codecacto:kmplib-auth:2.x.x")
    implementation("br.com.codecacto:kmplib-map:2.x.x")
    // NAO precisa de: camera, pdf, sync, monetization
}
```

**App completo (super8 com tudo):**
```kotlin
dependencies {
    implementation(platform("br.com.codecacto:kmplib-bom:2.x.x"))
    implementation("br.com.codecacto:kmplib-core")
    implementation("br.com.codecacto:kmplib-ui")
    implementation("br.com.codecacto:kmplib-auth")
    implementation("br.com.codecacto:kmplib-monetization")
    implementation("br.com.codecacto:kmplib-push")
}
```

---

## 5. Impacto no Android e iOS

### 5.1 Android

| Aspecto | Antes | Depois |
|---------|-------|--------|
| **APK size** | ~15-20MB (estimado) | Varia por app, -30% a -50% |
| **Build time** | Compila tudo | Compila so o necessario |
| **DEX count** | Alto (muitas classes) | Reduzido |
| **ProGuard** | Processa tudo | Processa menos |

### 5.2 iOS

| Aspecto | Antes | Depois |
|---------|-------|--------|
| **Framework size** | Grande (~50MB+) | Varia por app |
| **Compilacao Release** | >12GB RAM | <8GB RAM (estimado) |
| **Tempo de link** | 5-10 min | 1-3 min (estimado) |
| **DCE efetivo** | Limitado | Muito melhor |

### 5.3 iOS Framework Export

Cada app exporta **so os modulos que usa**:

```kotlin
// App simples
iosTarget.binaries.framework {
    export("br.com.codecacto:kmplib-core")
    export("br.com.codecacto:kmplib-ui")
    export("br.com.codecacto:kmplib-auth")
}

// Em vez de
iosTarget.binaries.framework {
    export(libs.kmplib)  // Tudo
}
```

---

## 6. Estrutura Gradle Proposta

### 6.1 Projeto Raiz

```
kmplib/
├── build-logic/
│   └── convention/
│       ├── build.gradle.kts
│       └── src/main/kotlin/
│           ├── KmpLibraryConvention.kt
│           └── KmpPublishConvention.kt
│
├── core/
│   └── build.gradle.kts
│
├── ui/
│   └── build.gradle.kts
│
├── auth/
│   └── build.gradle.kts
│
├── ... (outros modulos)
│
├── bom/
│   └── build.gradle.kts
│
├── gradle/
│   └── libs.versions.toml
│
├── settings.gradle.kts
└── build.gradle.kts
```

### 6.2 settings.gradle.kts

```kotlin
rootProject.name = "kmplib"

// Convention plugins
includeBuild("build-logic")

// Modulos da lib
include(":core")
include(":ui")
include(":auth")
include(":sync")
include(":monetization")
include(":platform")
include(":camera")
include(":map")
include(":pdf")
include(":push")
include(":ads")
include(":observability")
include(":brdata")
include(":bom")

// Renomeia para Maven Central
project(":core").name = "kmplib-core"
project(":ui").name = "kmplib-ui"
// ... etc
```

### 6.3 Exemplo de build.gradle.kts (modulo)

```kotlin
// core/build.gradle.kts
plugins {
    id("kmplib.library")
    id("kmplib.publish")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.ktor.client.core)
            api(libs.koin.core)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}
```

### 6.4 Bill of Materials (BoM)

```kotlin
// bom/build.gradle.kts
plugins {
    `java-platform`
    id("kmplib.publish")
}

dependencies {
    constraints {
        api(project(":kmplib-core"))
        api(project(":kmplib-ui"))
        api(project(":kmplib-auth"))
        api(project(":kmplib-sync"))
        api(project(":kmplib-monetization"))
        api(project(":kmplib-platform"))
        api(project(":kmplib-camera"))
        api(project(":kmplib-map"))
        api(project(":kmplib-pdf"))
        api(project(":kmplib-push"))
        api(project(":kmplib-ads"))
        api(project(":kmplib-observability"))
        api(project(":kmplib-brdata"))
    }
}
```

---

## 7. Plano de Migracao

### 7.1 Fase 1: Preparacao (sem quebrar nada)

1. **Criar estrutura de pastas** para os novos modulos
2. **Criar convention plugins** em `build-logic/`
3. **Manter `library/` funcionando** como fallback
4. **Criar modulo `kmplib-core`** extraindo:
   - `core/`
   - `util/`
   - `validation/`
   - `mask/`

### 7.2 Fase 2: Extracoes Incrementais

**Ordem sugerida (do mais independente para o mais acoplado):**

1. `kmplib-brdata` (sem dependencias)
2. `kmplib-observability` (so Sentry)
3. `kmplib-platform` (permissoes, biometric)
4. `kmplib-push` (depende de platform)
5. `kmplib-camera` (depende de platform)
6. `kmplib-map` (depende de platform)
7. `kmplib-pdf` (depende de core)
8. `kmplib-ads` (depende de core)
9. `kmplib-sync` (depende de core)
10. `kmplib-auth` (depende de core, firebase)
11. `kmplib-monetization` (depende de ui)
12. `kmplib-ui` (depende de core) - **maior modulo**

### 7.3 Fase 3: Publicacao e Migracao de Apps

1. **Publicar todos os modulos** no Maven Central
2. **Criar `kmplib-bom`** para facilitar adocao
3. **Migrar um app piloto** (sugestao: app novo ou simples)
4. **Documentar processo** de migracao
5. **Migrar demais apps** gradualmente

### 7.4 Fase 4: Deprecar Modulo Monolitico

1. **Marcar `br.com.codecacto:kmplib` como deprecated**
2. **Manter funcionando** por 2-3 versoes
3. **Remover** quando todos os apps migrarem

---

## 8. Estimativas

### 8.1 Esforco de Desenvolvimento

| Fase | Estimativa |
|------|------------|
| Fase 1 (preparacao) | 2-3 dias |
| Fase 2 (extracoes) | 5-10 dias |
| Fase 3 (publicacao) | 1-2 dias |
| Fase 4 (deprecacao) | Continuo |
| **Total** | **8-15 dias** |

### 8.2 Reducao de Tamanho Esperada

Para um app que usa **so auth + ui + core**:

| Metrica | Antes | Depois | Reducao |
|---------|-------|--------|---------|
| Codigo fonte processado | 7.7MB | ~2MB | -74% |
| Tipos no framework iOS | ~3000 | ~800 | -73% |
| RAM para compilar | >12GB | ~4GB | -67% |
| Tempo de link iOS | ~8min | ~2min | -75% |

**Nota:** Estimativas baseadas na proporcao de codigo. Valores reais podem variar.

---

## 9. Riscos e Mitigacoes

| Risco | Mitigacao |
|-------|-----------|
| Quebrar apps existentes | Manter `library/` como fallback |
| Dependencias circulares | Definir DAG claro de dependencias |
| Versoes incompativeis | Usar BoM para sincronizar versoes |
| Complexidade de publicacao | Convention plugins automatizam |
| Curva de aprendizado | Documentacao clara + exemplos |

---

## 10. Alternativas Consideradas

### 10.1 Nao Modularizar (Status Quo)

**Pros:**
- Zero esforco agora

**Contras:**
- Apps nao compilam em Macs com 16GB
- Binarios grandes
- Build lento
- Problema so piora com o tempo

**Decisao:** Rejeitado.

### 10.2 XCFramework Pre-compilado

**Pros:**
- Apps linkam binario pronto
- Zero compilacao local

**Contras:**
- Complexidade de CI/CD
- Nao reduz tamanho do binario
- Depuracao mais dificil

**Decisao:** Pode ser complementar, mas nao substitui modularizacao.

### 10.3 CI/CD com Maquinas Potentes

**Pros:**
- Resolve compilacao local

**Contras:**
- Custo mensal
- Nao reduz tamanho
- Desenvolvedores nao podem testar Release localmente

**Decisao:** Paliativo, nao solucao.

---

## 11. Proximos Passos

1. **Revisar esta proposta** com a equipe
2. **Decidir ordem de extracoes** (qual modulo primeiro)
3. **Criar branch `feature/modularization`**
4. **Iniciar Fase 1** (preparacao)
5. **Testar com cidade-conectada** como piloto

---

## 12. Referencias

### Documentacao Oficial
- [Kotlin Multiplatform Project Structure](https://kotlinlang.org/docs/multiplatform/multiplatform-project-recommended-structure.html)
- [Build Native Binaries](https://kotlinlang.org/docs/multiplatform/multiplatform-build-native-binaries.html)
- [Publishing Setup](https://kotlinlang.org/docs/multiplatform/multiplatform-publish-lib-setup.html)

### Exemplos de Bibliotecas Modularizadas
- [Ktor](https://github.com/ktorio/ktor) - JetBrains
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) - JetBrains
- [SQLDelight](https://github.com/cashapp/sqldelight) - Square/CashApp

### Artigos Tecnicos
- [KMP Modularization Ultimate Guide](https://medium.com/@himanshugaur684/kmp-modularization-ultimate-guide-with-real-world-app-cdb5be94d096)
- [Three Framework Problem with KMP](https://medium.com/xorum-io/three-framework-problem-with-kotlin-multiplatform-mobile-16267c5afa53)
- [KMM Architecture: Umbrella](https://medium.com/@maruchin/kmm-architecture-4-umbrella-a26a370071d5)

---

**Documento gerado em:** 28/ago/2026
**Versao da kmplib analisada:** 2.160.0
**Projeto que motivou a analise:** cidade-conectada (iOS Release OOM)
