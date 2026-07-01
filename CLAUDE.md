# CLAUDE.md - KmpLib

Este arquivo fornece orientações para Claude Code (claude.ai/code) ao trabalhar com o código neste repositório.

## Visão Geral

KmpLib é a biblioteca compartilhada Kotlin Multiplatform (KMP) da CodeCacto, usada por todos os projetos mobile (Android + iOS):

- **influencer**
- **locadora**
- **meu-advogado**
- **super8**

## Estrutura do Projeto

```
kmplib/
├── library/
│   └── src/
│       ├── commonMain/       # Código compartilhado Android + iOS
│       ├── androidMain/      # Implementações Android
│       └── iosMain/          # Implementações iOS
├── IOS_INTEGRATION.md        # Guia de integração iOS
└── CLAUDE.md                 # Este arquivo
```

## Comandos de Build

```bash
# Build completo
./gradlew build

# Build apenas Android
./gradlew :library:compileKotlinAndroid

# Build apenas iOS (requer macOS)
./gradlew :library:linkDebugFrameworkIosSimulatorArm64

# Testes
./gradlew :library:allTests
```

## Padrões Importantes

### Exportação para iOS

Projetos que usam kmplib **devem** configurar:

```kotlin
// build.gradle.kts do projeto consumidor

// Em binaries.framework:
export(libs.kmplib)

// Em commonMain.dependencies:
api(libs.kmplib)  // NÃO implementation()
```

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

Os geradores de PDF em `iosMain/kotlin/.../pdf/` estão como **stubs** que lançam exceção:

- `ReciboPdf.ios.kt`
- `DocumentPdfGenerator.ios.kt`
- `FinanceReportPdfGenerator.ios.kt`
- `HoursReportPdfGenerator.ios.kt`
- `OsPdfGenerator.ios.kt`
- `TableReportPdfGenerator.ios.kt`
- `VaccinationCardPdfGenerator.ios.kt`
- `WorkReportPdfGenerator.ios.kt`

**Motivo**: APIs de desenho de texto UIKit (`NSString.sizeWithAttributes`, `drawAtPoint`) não estão disponíveis no Kotlin/Native 2.x.

**TODO**: Implementar quando APIs estiverem disponíveis ou usar biblioteca de terceiros.

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

*Atualizado em 2026-07-01*
