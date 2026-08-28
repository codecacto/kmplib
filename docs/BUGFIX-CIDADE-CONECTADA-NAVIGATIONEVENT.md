# Bug: navigationevent-compose quebrando build iOS do cidade-conectada

**Data:** 2026-08-28
**Projeto afetado:** cidade-conectada
**Erro:** Build iOS falha no Xcode

> ## ✅ RESOLVIDO em 28/ago/2026 — e não só no cidade-conectada
>
> Aplicada a **Opção 1**, com uma correção ao diagnóstico do documento: o
> `androidx.compose.ui.backhandler.BackHandler` **está depreciado** (foi por isso que o
> `LoginRoute` tinha escolhido o `NavigationBackHandler`). A depreciação continua, e é aceita de
> propósito — o substituto, `NavigationEventHandler`, modela *predictive back* com progresso, que
> estas telas não têm, e vem no artefato Android-only que causou o bug. O `@Suppress("DEPRECATION")`
> fica isolado num wrapper privado, que é o padrão que o `CheckoutContent` deste mesmo app já usava.
>
> **O mesmo bug estava no `Minha Lanterna`** (`LockOverlay`, mesmo artefato, mesmo `commonMain`),
> esperando para custar o mesmo ciclo. Corrigido junto.
>
> **E não precisa de Mac para verificar.** A resolução de dependência é do Gradle, não do compilador
> Kotlin/Native:
> ```bash
> ./gradlew :composeApp:dependencies --configuration iosArm64CompileKlibraries \
>   -Papp.forceAppleTargets=true -Pkmplib.forceAppleTargets=true
> ```
> roda em Linux, em segundos, e devolve o mesmo `No matching variant`. Depois da correção, os dois
> alvos iOS resolvem limpos nos dois apps. O portfólio inteiro foi varrido com esse comando: nenhum
> outro app tem artefato Android-only em `commonMain`.
>
> Fica valendo para todo app da fábrica: a guarda de host precisa aceitar
> `-Papp.forceAppleTargets=true`, senão a configuração `iosArm64CompileKlibraries` nem existe e o
> Gradle responde "configuration not found" — que não é problema de dependência.

## Erro

```
No matching variant of androidx.navigationevent:navigationevent-compose:1.0.2 was found.
The consumer was configured to find a library for use during 'kotlin-api', preferably optimized for non-jvm,
as well as attribute 'org.jetbrains.kotlin.klib.packaging' with value 'non-packed',
attribute 'org.jetbrains.kotlin.native.target' with value 'ios_simulator_arm64',
attribute 'org.jetbrains.kotlin.platform.type' with value 'native'
```

## Causa

A biblioteca `androidx.navigationevent:navigationevent-compose:1.0.2` é **Android-only** (não tem variante KMP/iOS), mas está declarada em `commonMain.dependencies`.

### Arquivos envolvidos

1. **cidade-conectada/app/gradle/libs.versions.toml** (linhas 14 e 49):
   ```toml
   navigationEvent = "1.0.2"
   navigationevent-compose = { module = "androidx.navigationevent:navigationevent-compose", version.ref = "navigationEvent" }
   ```

2. **cidade-conectada/app/composeApp/build.gradle.kts** (linha 302):
   ```kotlin
   commonMain.dependencies {
       // ...
       implementation(libs.navigationevent.compose)  // <-- PROBLEMA: está em commonMain
   }
   ```

3. **cidade-conectada/app/composeApp/src/commonMain/kotlin/.../LoginRoute.kt** (linhas 12-14, 162):
   ```kotlin
   import androidx.navigationevent.NavigationEventInfo
   import androidx.navigationevent.compose.NavigationBackHandler
   import androidx.navigationevent.compose.rememberNavigationEventState
   // ...
   NavigationBackHandler(...)
   ```

## Solucao

### Opcao 1: Usar BackHandler do Compose (RECOMENDADA)

O `BackHandler` de `org.jetbrains.compose.ui:ui-backhandler` (que já está no projeto como `libs.compose.uiBackhandler`) funciona em KMP e faz a mesma coisa.

**Passos:**

1. Em `LoginRoute.kt`, substituir:
   ```kotlin
   // REMOVER:
   import androidx.navigationevent.NavigationEventInfo
   import androidx.navigationevent.compose.NavigationBackHandler
   import androidx.navigationevent.compose.rememberNavigationEventState

   // USAR:
   import androidx.compose.ui.backhandler.BackHandler
   ```

2. Substituir o uso de `NavigationBackHandler` por `BackHandler`:
   ```kotlin
   // ANTES:
   NavigationBackHandler(
       eventState = eventState,
       enabled = true
   ) { ... }

   // DEPOIS:
   BackHandler(enabled = true) { ... }
   ```

3. No `build.gradle.kts`, remover a linha 302:
   ```kotlin
   // REMOVER de commonMain.dependencies:
   implementation(libs.navigationevent.compose)
   ```

4. No `libs.versions.toml`, remover as linhas 14 e 49 (navigationEvent e navigationevent-compose).

### Opcao 2: Mover para androidMain (se precisar manter o NavigationBackHandler)

Se por algum motivo o `NavigationBackHandler` for necessario (ele tem info de navegacao que o `BackHandler` nao tem), criar expect/actual:

1. Mover a dependencia para `androidMain`:
   ```kotlin
   androidMain.dependencies {
       implementation(libs.navigationevent.compose)
   }
   ```

2. Criar expect em `commonMain`:
   ```kotlin
   // commonMain/kotlin/.../NavigationBackHandlerWrapper.kt
   @Composable
   expect fun NavigationBackHandlerWrapper(enabled: Boolean, onBack: () -> Unit)
   ```

3. Criar actual em `androidMain`:
   ```kotlin
   // androidMain/kotlin/.../NavigationBackHandlerWrapper.android.kt
   @Composable
   actual fun NavigationBackHandlerWrapper(enabled: Boolean, onBack: () -> Unit) {
       NavigationBackHandler(enabled = enabled, onBack = onBack)
   }
   ```

4. Criar actual em `iosMain`:
   ```kotlin
   // iosMain/kotlin/.../NavigationBackHandlerWrapper.ios.kt
   @Composable
   actual fun NavigationBackHandlerWrapper(enabled: Boolean, onBack: () -> Unit) {
       BackHandler(enabled = enabled, onBack = onBack)
   }
   ```

## Verificacao

Apos a correcao, rodar no Mac:

```bash
cd cidade-conectada/app
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

Se passar, o Xcode vai buildar.
