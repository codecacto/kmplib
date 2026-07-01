# Integração iOS - KmpLib

Este documento descreve como integrar a kmplib em projetos iOS KMP (Kotlin Multiplatform) da CodeCacto.

## Índice

1. [Configuração do Gradle](#configuração-do-gradle)
2. [Dependências iOS (SPM)](#dependências-ios-spm)
3. [Estrutura do Projeto iOS](#estrutura-do-projeto-ios)
4. [Snippets Swift Padrão](#snippets-swift-padrão)
5. [Features Disponíveis](#features-disponíveis)
6. [Troubleshooting](#troubleshooting)

---

## Configuração do Gradle

### build.gradle.kts (composeApp)

Para que os tipos da kmplib sejam visíveis no Swift, é **obrigatório** usar `api()` e `export()`:

```kotlin
kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            // OBRIGATÓRIO: Exporta kmplib para Swift acessar tipos iOS
            export(libs.kmplib)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // OBRIGATÓRIO: usar api() ao invés de implementation()
            api(libs.kmplib)
            // ... outras dependências
        }
    }
}
```

> **Importante**: Sem `api()` + `export()`, tipos como `IosNativeMap`, `IosMapMarkerData`, e bridges não estarão disponíveis no Swift.

---

## Dependências iOS (SPM)

### Padrão: Swift Package Manager (SPM)

Todos os projetos devem usar **SPM** (Swift Package Manager) para dependências iOS. Evite CocoaPods.

#### Packages obrigatórios (via Xcode > Add Package Dependency):

| Package | URL | Uso |
|---------|-----|-----|
| Firebase iOS SDK | `https://github.com/firebase/firebase-ios-sdk` | Auth, Crashlytics, Analytics |
| RevenueCat | `https://github.com/RevenueCat/purchases-ios-spm` | Compras in-app (se usar monetização) |
| Google Sign-In | `https://github.com/google/GoogleSignIn-iOS` | Login Google (se usar) |

#### Como adicionar no Xcode:

1. Abra `iosApp.xcodeproj` no Xcode
2. File > Add Package Dependencies...
3. Cole a URL do repositório
4. Selecione a versão (geralmente "Up to Next Major")
5. Adicione ao target `iosApp`

#### Produtos Firebase recomendados:
- `FirebaseAuth`
- `FirebaseAnalytics`
- `FirebaseCrashlytics`
- `FirebaseRemoteConfig` (se usar)
- `FirebaseMessaging` (se usar push notifications)

---

## Estrutura do Projeto iOS

### Arquivos obrigatórios em `iosApp/iosApp/`:

```
iosApp/
├── iosApp.xcodeproj/
├── iosApp/
│   ├── Assets.xcassets/
│   ├── Preview Content/
│   ├── GoogleService-Info.plist    # Firebase config
│   ├── Info.plist                  # App config
│   ├── iOSApp.swift               # Entry point
│   └── ContentView.swift          # ComposeView bridge
└── Configuration/
    └── Config.xcconfig            # Build settings (bundle ID, etc)
```

### Config.xcconfig (exemplo):

```
TEAM_ID=
PRODUCT_NAME=MeuApp
PRODUCT_BUNDLE_IDENTIFIER=br.com.codecacto.meuapp$(TEAM_ID)
CURRENT_PROJECT_VERSION=1
MARKETING_VERSION=1.0
```

---

## Snippets Swift Padrão

### iOSApp.swift (mínimo)

```swift
import SwiftUI
import FirebaseCore

@main
struct iOSApp: App {
    init() {
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

### iOSApp.swift (com Google Sign-In)

```swift
import SwiftUI
import FirebaseCore
import GoogleSignIn

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
        FirebaseApp.configure()
        return true
    }

    func application(_ app: UIApplication,
                     open url: URL,
                     options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        return GIDSignIn.sharedInstance.handle(url)
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
```

### ContentView.swift (mínimo)

```swift
import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
```

### ContentView.swift (com Google Sign-In handler)

```swift
import UIKit
import SwiftUI
import ComposeApp
import GoogleSignIn

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // Configura Google Sign-In handler ANTES de criar o ViewController
        MainViewControllerKt.googleSignInHandler = { onSuccess, onError in
            guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                  let rootViewController = windowScene.windows.first?.rootViewController else {
                _ = onError("Nao foi possivel encontrar a tela principal")
                return
            }

            GIDSignIn.sharedInstance.signIn(withPresenting: rootViewController) { result, error in
                if let error = error {
                    let errorMessage: String
                    if (error as NSError).code == GIDSignInError.canceled.rawValue {
                        errorMessage = "Login cancelado"
                    } else {
                        errorMessage = error.localizedDescription
                    }
                    _ = onError(errorMessage)
                    return
                }

                guard let user = result?.user,
                      let idToken = user.idToken?.tokenString else {
                    _ = onError("Falha ao obter token do Google")
                    return
                }

                _ = onSuccess(idToken, user.accessToken.tokenString)
            }
        }

        return MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard)
    }
}
```

### Apple Sign-In Coordinator (exemplo completo)

```swift
import AuthenticationServices
import CryptoKit

final class AppleSignInCoordinator: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private var onSuccess: ((String, String, String?) -> Void)?
    private var onError: ((String) -> Void)?
    private var currentNonce: String?

    func start(onSuccess: @escaping (String, String, String?) -> Void,
               onError: @escaping (String) -> Void) {
        self.onSuccess = onSuccess
        self.onError = onError
        let nonce = randomNonceString()
        self.currentNonce = nonce

        let provider = ASAuthorizationAppleIDProvider()
        let request = provider.createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = sha256(nonce)

        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
    }

    func authorizationController(controller: ASAuthorizationController,
                                 didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let nonce = currentNonce,
              let tokenData = credential.identityToken,
              let idToken = String(data: tokenData, encoding: .utf8) else {
            onError?("Falha ao obter idToken da Apple")
            return
        }
        let fullName = [credential.fullName?.givenName, credential.fullName?.familyName]
            .compactMap { $0 }
            .joined(separator: " ")
            .trimmingCharacters(in: .whitespaces)
        onSuccess?(idToken, nonce, fullName.isEmpty ? nil : fullName)
    }

    func authorizationController(controller: ASAuthorizationController,
                                 didCompleteWithError error: Error) {
        let nsError = error as NSError
        if nsError.code == ASAuthorizationError.canceled.rawValue {
            onError?("Login cancelado")
        } else {
            onError?(error.localizedDescription)
        }
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        return UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.windows.first }
            .first ?? ASPresentationAnchor()
    }

    private func randomNonceString(length: Int = 32) -> String {
        let charset: [Character] = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remaining = length
        while remaining > 0 {
            var random: UInt8 = 0
            let status = SecRandomCopyBytes(kSecRandomDefault, 1, &random)
            guard status == errSecSuccess else { continue }
            if random < charset.count {
                result.append(charset[Int(random)])
                remaining -= 1
            }
        }
        return result
    }

    private func sha256(_ input: String) -> String {
        let inputData = Data(input.utf8)
        let hashed = SHA256.hash(data: inputData)
        return hashed.compactMap { String(format: "%02x", $0) }.joined()
    }
}
```

---

## Features Disponíveis

### Tipos exportados para Swift

| Tipo | Descrição | Import |
|------|-----------|--------|
| `IosNativeMap` | Protocolo para Google Maps nativo | `ComposeApp` |
| `IosMapMarkerData` | Dados de marcador no mapa | `ComposeApp` |
| `IosMapBridge` | Bridge para injetar factory do mapa | `ComposeApp` |
| `ImagePickerServiceBridge` | Bridge para photo picker nativo | `ComposeApp` |

### Callbacks Kotlin → Swift

> **Atenção**: Callbacks Kotlin retornam `KotlinUnit`. No Swift, use `_ = callback(...)` para ignorar o retorno.

```swift
// CORRETO
_ = onSuccess(idToken, accessToken)
_ = onError("Mensagem de erro")

// INCORRETO (erro de compilação)
onSuccess(idToken, accessToken)  // Error: cannot convert KotlinUnit to Void
```

### Tipos Kotlin no Swift

| Kotlin | Swift |
|--------|-------|
| `Double` | `KotlinDouble` |
| `Int` | `KotlinInt` |
| `Long` | `KotlinLong` |
| `Unit` | `KotlinUnit` |

---

## Troubleshooting

### Erro: "cannot find type 'IosNativeMap' in scope"

**Causa**: kmplib não está sendo exportada no framework.

**Solução**: Verificar `build.gradle.kts`:
```kotlin
// Em binaries.framework:
export(libs.kmplib)

// Em commonMain.dependencies:
api(libs.kmplib)  // NÃO implementation()
```

### Erro: "Undefined symbols for architecture arm64"

**Causa**: Dependências nativas faltando (Firebase, RevenueCat, etc).

**Solução**: Adicionar os packages SPM necessários no Xcode:
- `FirebaseAuth`, `FirebaseCrashlytics`, `FirebaseRemoteConfig`
- `RevenueCat` (se usar monetização)
- `PurchasesHybridCommon` (se usar RevenueCat via KMP)

### Erro: "cannot convert KotlinUnit to Void"

**Causa**: Callback Kotlin retorna `Unit` que vira `KotlinUnit` no Swift.

**Solução**: Usar `_ =` para ignorar o retorno:
```swift
_ = onSuccess(value)
```

### Erro: "AppBuildConfigKt not found"

**Causa**: Nome do arquivo gerado tem underscore.

**Solução**: Se o arquivo Kotlin é `AppBuildConfig.ios.kt`, o nome no Swift será `AppBuildConfig_iosKt`:
```swift
// CORRETO
AppBuildConfig_iosKt.googleMapsApiKey

// INCORRETO
AppBuildConfigKt.googleMapsApiKey
```

---

## Checklist de Integração

- [ ] `build.gradle.kts` usa `api(libs.kmplib)` em `commonMain.dependencies`
- [ ] `build.gradle.kts` usa `export(libs.kmplib)` em `binaries.framework`
- [ ] Dependências SPM adicionadas no Xcode (Firebase, RevenueCat se necessário)
- [ ] `GoogleService-Info.plist` adicionado ao target
- [ ] `iOSApp.swift` chama `FirebaseApp.configure()` no init
- [ ] `ContentView.swift` configura handlers ANTES de criar `MainViewController()`
- [ ] Callbacks usam `_ = callback(...)` para ignorar `KotlinUnit`

---

*Documento gerado em 2026-07-01. Atualizar conforme necessário.*
