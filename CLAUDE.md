# CLAUDE.md - KmpLib

Este arquivo fornece orientações para Claude Code (claude.ai/code) ao trabalhar com o código neste repositório.

> ⚠️ **REGRA ZERO desta biblioteca — [`NUNCA_DESLIGUE_FUNCIONALIDADE.md`](NUNCA_DESLIGUE_FUNCIONALIDADE.md).**
> Nenhuma funcionalidade se desliga, comenta ou vira no-op para fazer compilar. A lib é a fundação de
> dezenas de apps: a decisão de remover algo daqui **não é local**, mesmo quando o arquivo parece
> isolado. Travou de verdade (falta de Mac, API não exposta, limitação do K/N)? Pare, reporte e
> proponha a solução correta — não entregue o recorte em silêncio.

## Visão Geral

KmpLib é a biblioteca compartilhada Kotlin Multiplatform (KMP) da CodeCacto, usada por todos os projetos mobile (Android + iOS):

- **influencer**
- **locadora**
- **meu-advogado**
- **super8**

## Estrutura do Projeto

```
kmplib/
├── build-logic/convention/   # convention plugins: kmplib.module e kmplib.module.compose
├── core/  ui/  platform/  auth/  sync/  monetization/  central/  firebase/
├── mask/  brdata/  qr/  map/  location/  push/  observability/  camera/
├── pdf/  media/  ads/  astro/           # 21 módulos, cada um um artefato Maven
│   └── src/{commonMain,androidMain,iosMain,commonTest}/
├── library/                  # o UMBRELLA `br.com.codecacto:kmplib` — só KmpLib.kt e KmpLibInit.kt
├── library-testing/          # `kmplib-testing`, dublês de loja para build de QA
├── docs/MODULARIZACAO.md     # por que a divisão, e o que ela corrigiu
├── IOS_INTEGRATION.md
└── CLAUDE.md
```

Um módulo novo é `plugins { id("kmplib.module") }` (ou `kmplib.module.compose`, se desenha tela)
mais as suas dependências — alvos, namespace, guarda de host e publicação vêm do convention plugin.
Registre-o no `settings.gradle.kts` **com o nome do artefato** (`:kmplib-x`, nunca `:x`: o KMP deriva
o artifactId dos artefatos por-target do nome do projeto Gradle) e acrescente um `api()` no umbrella.

## Comandos de Build

```bash
# Testes (roda em qualquer host — é o gate obrigatório antes de commitar)
./gradlew :kmplib:testDebugUnitTest

# Publicação local (funciona em Linux desde a 2.69.0)
./gradlew publishToMavenLocal

# Build apenas iOS (requer macOS)
./gradlew :kmplib:linkDebugFrameworkIosSimulatorArm64
```

### Guarda de host (2.69.0) — alvos Apple só em macOS

`library/build.gradle.kts` declara `iosX64/iosArm64/iosSimulatorArm64` **apenas quando
`HostManager.hostIsMac`**. iOS não está desativado: é **condicional ao host**.

| Host | O que sai do `publishToMavenLocal` |
|------|-----------------------------------|
| Linux/Windows | `commonMain` + **Android** (AAR). Módulo Gradle coerente. |
| macOS | Tudo, incluindo `kmplib-iosarm64`/`-iosx64`/`-iossimulatorarm64` e o klib de metadata completo. |

Antes disso, os alvos iOS eram sempre declarados e o KGP os desabilitava fora do Mac
(`kotlin.native.ignoreDisabledTargets=true`) — mas o `kmplib-<v>.module` publicado **ainda anunciava**
variantes `iosArm64ApiElements-published` etc. apontando (`available-at`) para artefatos que nunca
eram publicados. Isso quebrava o fallback `mavenLocal` de um clone isolado.

Consequências a respeitar:
- Com **um único alvo**, o KGP não gera klib de metadata → o jar raiz publicado no Linux vem vazio.
  Não afeta o dia a dia (num host Linux o app consumidor também só tem Android habilitado), mas
- **Release para Maven Central exige macOS.** Qualquer tarefa `*MavenCentral*` **falha de propósito**
  em host não-macOS. Para forçar a tentativa de declarar iOS fora do Mac (diagnóstico):
  `./gradlew publishToMavenLocal -Pkmplib.forceAppleTargets=true`.

## Padrões Importantes

### Exportação para iOS

> ⚠️ **Mudou na 2.163.0.** O que está escrito abaixo substitui o `export(libs.kmplib)` +
> `api(libs.kmplib)` que valeu até a 2.162.0. Aquele par **é a causa do OOM no link Release**:
> `export()` declara cada símbolo público da dependência no header Obj-C e o torna **raiz do dead
> code elimination** — nada abaixo de uma raiz pode ser eliminado, e toda raiz entra no CallGraph
> do `DevirtualizationAnalysis`. Exportando a lib inteira, são ~1.436 raízes num app que costuma
> falar com ela por um ou dois objetos.

```kotlin
// build.gradle.kts do projeto consumidor

// Em binaries.framework: SÓ os módulos cujos tipos o SWIFT NOMEIA.
// Confira abrindo os .swift do projeto: normalmente é um ou dois.
export(libs.kmplib.auth)   // GoogleSignInBridge
export(libs.kmplib.push)   // ApplePushBridge

// Em commonMain.dependencies: os módulos que o app usa. `api()`, não `implementation()`.
api(libs.kmplib.core)
api(libs.kmplib.ui)
api(libs.kmplib.auth)
// … só os que as telas abrem
```

O resto da lib continua no binário e continua funcionando: `api()` leva o código, `export()` só
decide o que aparece para o Swift.

**No `gradle.properties` do app**, uma linha que não é da lib e que faltava em todos:

```properties
# org.gradle.jvmargs NÃO vale para o Kotlin/Native — o link do framework roda em processo próprio.
kotlin.native.jvmArgs=-Xmx6g
```

**Inicialização sem o umbrella:** `KmpLib.init(context)` só existe no artefato `kmplib`. Quem
declara módulos chama os inits deles — `initKmpLibCore`, `initKmpLibPlatform`, `initKmpLibAuth`,
`initKmpLibSync`, `initKmpLibMedia` — e `kmpLibPlatformOnResume`/`OnPause` +
`kmpLibAuthOnResume`/`OnPause` no lugar de `setActivity`/`clearActivity`.

**Quem já usa `api(libs.kmplib)` não precisa mudar nada** — o umbrella continua trazendo tudo. Mas
não terá o ganho: um app que exporta o umbrella exporta a lib inteira.

### Tipos iOS Exportados

| Tipo | Arquivo | Uso |
|------|---------|-----|
| `IosNativeMap` | `map/IosMapBridge.kt` | Protocolo para Google Maps nativo |
| `IosMapMarkerData` | `map/IosMapBridge.kt` | Dados de marcador |
| `IosMapBridge` | `map/IosMapBridge.kt` | Factory do mapa |
| `ImagePickerServiceBridge` | Varia por projeto | Photo picker |

### Anotações ObjC

Para exportar tipos ao Swift com nomes específicos, usar:

```kotlin
@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

import kotlin.native.ObjCName

@ObjCName("IosNativeMap")
interface IosNativeMap { ... }
```

### Callbacks Swift

Kotlin `Unit` vira `KotlinUnit` no Swift. Documentar para consumidores:

```swift
// Swift: usar _ = para ignorar retorno
_ = onSuccess(value)
```

## Geradores de PDF (iOS)

**2.78.0 — os 9 geradores iOS estão IMPLEMENTADOS em código** (ADR-0003). O helper multi-página
`renderIosPdfPaged` + `IosPageFlow` (marca d'água por página) estendeu o `renderIosPdf` de página única
(recibo, 2.77.0) para N páginas, e o `IosPdfCanvas` ganhou `strokeRect`/`strokeRoundRect`/`fillCircle`/
`imageCrop`/`measureWrappedHeight`. Os 7 que eram stub agora renderizam de verdade, espelhando o par
Android (mesmo layout/coordenadas/cores):

- `DocumentPdfGenerator.ios.kt`
- `FinanceReportPdfGenerator.ios.kt`
- `HoursReportPdfGenerator.ios.kt`
- `InspectionPdfGenerator.ios.kt`
- `TableReportPdfGenerator.ios.kt`
- `VaccinationCardPdfGenerator.ios.kt`
- `WorkReportPdfGenerator.ios.kt`

Somados a `OsPdfGenerator.ios`/`ReciboPdf.ios` (2.77.0) e `PdfRasterizer.ios` (`renderPdfPagesToImages`),
os 9 geradores são reais. Técnica: **CoreText** (`CTLine`) dentro de `UIGraphicsPDFRenderer`/`CGContext`
(as categorias de desenho de texto do UIKit `NSString.drawAtPoint` não são exportadas no K/N 2.x).

**PENDÊNCIA — validação em host macOS.** O build Kotlin/Native iOS **não roda em Linux**; o código é
fiel ao Android mas **não foi compilado/validado em macOS**. Por isso
`platform/PlatformCapabilities.pdfGeneration` (e `cameraCapture`) **continuam `false`** — o flip para
`true` é o **passo final em macOS** (compilar os alvos iOS + validação visual dos PDFs; para câmera,
testar num device). Enquanto `false`, o app **não vende/exibe** a feature no iOS:
`"Exportar PDF" requiring PlatformCapability.PdfGeneration` + `List<CapabilityFeature<T>>.availableValues()`
ao montar `PaywallPlan.highlights`/menus, e `CapabilityGate(PlatformCapability.PdfGeneration) { ... }`
para UI pontual. Nenhum app precisa mudar quando o flag virar `true`.

## Câmera / OCR (iOS)

**2.78.0 — `CameraView.ios` e `PlateOcrAnalyzer.ios` implementados** (não mais placeholder).
`PlateOcrAnalyzer.ios` usa **Apple Vision** (`VNRecognizeTextRequest`) para OCR on-device; `CameraView.ios`
usa **AVFoundation** (`AVCaptureSession` + `AVCaptureVideoDataOutput` + `AVCaptureVideoPreviewLayer` via
`UIKitView`) + Vision no `CVPixelBuffer` dos frames (com throttle) e codifica o frame reconhecido em
**JPEG** na variante `onCapture`. Padrão-ouro (APIs oficiais Apple, sem WebView). Info.plist exige
`NSCameraUsageDescription`. **Pendente de validação em host macOS** (não compila em Linux).

## Push own-stack (2.76.0 — sem cerimônia Firebase por app)

O módulo `push/` ganhou o caminho **own-stack** (piloto Meu Barbeiro), ADITIVO e reversível — os apps
legados (`influencer`/`locadora`/`meu-advogado`/`super8`) seguem no fluxo antigo sem mudança. Objetivo:
eliminar `google-services.json`/`GoogleService-Info.plist`/plugin google-services/`processDebugGoogleServices`,
**mantendo o projeto central `code-cacto`**.

- **Android = FCM com init MANUAL do FirebaseApp.** `initFirebaseForPush(context, AndroidFcmAppId)`
  (`FirebaseApp.initializeApp` + `FirebaseOptions.Builder`, idempotente) **antes** de
  `NotifierManager.initialize(...)`. `AndroidFcmAppId.PerApp` (DEFAULT, App ID próprio no console
  `code-cacto`, sem json) ou `.Shared` (atalho opt-in com App ID compartilhado). KMPNotifier-android já
  traz `firebase-messaging` transitivo — **sem plugin google-services**.
- **iOS = APNs-direto** (sem FCM/plist): `@ObjCName("ApplePushBridge") object ApplePushBridge` alimentado
  pelo `AppDelegate` Swift (`onApnsToken`/`onApnsRegistrationFailed`/`onRemoteNotification(userInfo,
  wasTapped)`/`currentToken`) → mesmo `PushNotificationListener`. Passo a passo Swift no KDoc de
  `ApplePushBridge`. Bridge + `UNUserNotificationCenter` **validados por inspeção** (link real no Mac).
- **Comum:** `createPushNotificationService(listener)` (Android⇒KMPNotifier, iOS⇒bridge) e
  `createLocalPushNotifier()` (foreground local: Android⇒KMPNotifier, iOS⇒`UNUserNotificationCenter`).
  Roteamento puro `PushEventRouter`/`PushPayload` (commonMain, testado). Detalhe na skill `kmplib-catalog`.

## Compatibilidade

### Kotlin/Native 2.x

Mudanças importantes:
- `@Volatile` agora requer `import kotlin.concurrent.Volatile`
- `allocArray` em `memScoped` substituído por `usePinned` + `addressOf`
- `CGPDFBox.kCGPDFMediaBox` não acessível, usar valor numérico `0`

### Koin 4.x

- `GlobalContext.get()` → `KoinPlatformTools.defaultContext().get()`

## Dependências Nativas iOS

Projetos consumidores precisam adicionar via SPM:

| Package | URL |
|---------|-----|
| Firebase iOS SDK | `https://github.com/firebase/firebase-ios-sdk` |
| RevenueCat | `https://github.com/RevenueCat/purchases-ios-spm` |
| Google Sign-In | `https://github.com/google/GoogleSignIn-iOS` |

## Documentação Adicional

- **IOS_INTEGRATION.md**: Guia completo de integração iOS
- **library/README.md**: Documentação da biblioteca (se existir)

## Troubleshooting

### "cannot find type 'IosNativeMap' in scope"
Projeto consumidor não está usando `api()` + `export()`.

### "Undefined symbols for architecture arm64"
Faltam dependências SPM no Xcode (Firebase, RevenueCat).

### "cannot convert KotlinUnit to Void"
Usar `_ = callback(...)` no Swift.

---

*Atualizado em 2026-08-28 (2.163.0 — a lib em 21 módulos).*
