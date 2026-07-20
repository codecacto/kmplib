# Analise de Reuso da kmplib

**Data:** 2026-05-15
**Metodo:** leitura direta de codigo fonte. Documentacoes existentes da `kmplib` e dos apps nao foram usadas como fonte desta analise.

## 1. Fontes Lidas

Projetos analisados:

- `E:\CodeCacto\Lib\kmplib`
- `E:\CodeCacto\locadora`
- `E:\CodeCacto\Meu Advogado`
- `E:\CodeCacto\Meu Fisio`
- `E:\CodeCacto\Prospecta`
- `E:\CodeCacto\Foco`
- `E:\CodeCacto\Influencer\mobile`
- `E:\CodeCacto\Residencial`
- `E:\CodeCacto\StatusHub\StatusHub`
- `E:\CodeCacto\StatusHub\Colaborador`
- `E:\Freelancers\TechPromos`, somente para push notification

Fora do escopo:

- `E:\CodeCacto\Calculadora BTU`
- `E:\CodeCacto\Repelente Anti-Mosquito`

## 2. Estado Real da kmplib

A `kmplib` ja cobre mais do que o plano inicial sugeria:

- MVI: `ui/mvi/BaseViewModel.kt` e `MviContracts.kt`.
- UI: `EmptyState`, `StatusBadge`, `AppTextField`, `AppTextArea`, `AppButton`, `AppOutlinedButton`, `Toast`, `AppReviewDialog`.
- Mascaras: CPF, CNPJ, phone, CEP, currency e date. `DateVisualTransformation` ja existe.
- Currency: `String.currencyToDouble()` e `Double.formatAsCurrency()` ja existem em `mask/CurrencyMask.kt`.
- Arquivo: `platform/FilePicker.kt` ja tem `FileData`, mas nao tem `expect fun rememberFilePicker` nem `actual` ativos. As implementacoes Android/iOS aparecem como `.bak`.
- Rede: existe `NetworkChecker` simples com `fun isAvailable(): Boolean`, mas nao existe `ConnectivityObserver` reativo.
- Storage: `StorageService` tem download URL, delete, delete em lote, exists, reference e mime type, mas nao tem upload generico de bytes.
- Push: nao existe modulo de push notification.
- `ApiResult`/`handleApiCall`: nao existem na lib.

Conclusao: o trabalho mais valioso nao e "criar tudo", mas estabilizar APIs que ja estao pela metade e alinhar nomes/contratos para reduzir forks locais.

## 3. Tabela por Projeto

| Projeto | Usa `kmplib` | Evidencia principal | Duplicacao real encontrada | O que vale mover ou ajustar | Prioridade |
|---|---:|---|---|---|---|
| `locadora` | Sim | imports de feedback, review, image picker e notification badge | `BaseViewModel` proprio com `dispatch`, `UiState/UiAction/UiEffect`, mascaras/validadores locais, formatadores em telas/PDF, preferencias locais, notificacoes locais de dominio | Alias/compatibilidade de MVI, reutilizar mascaras existentes, mover formatador BRL comum, manter notificacoes de cobranca no app | Alta |
| `Meu Advogado` | Sim | `implementation(libs.kmplib)` e uso de auth/firestore/feedback/review | `SelectedFile`/`rememberFilePicker`, `AppPreferences` para onboarding, `StorageService` local com upload de documento, `ByteArray.toFirebaseData` | Ativar `FilePicker` da lib, adicionar upload generico de bytes, talvez preferencias chave/valor simples | Alta |
| `Meu Fisio` | Sim | uso intenso de `UiState/UiAction/UiEffect`, Firestore, feedback, monetizacao | `core/base/BaseViewModel` local quase equivalente, platform `ImagePicker`, DataStore local | Migrar BaseViewModel se a lib oferecer `setState/sendEffect` ou aliases; manter pickers de video/imagem especificos ate a API da lib cobrir o caso | Media |
| `Prospecta` | Sim | usa `NetworkChecker`, review, auth e monetizacao da lib | `BaseViewModel` local, marcadores MVI locais, `PhoneVisualTransformation` local, empty states locais por tela | Migrar MVI, usar mascara phone da lib, criar `ConnectivityObserver` reativo | Media |
| `Foco` | Sim | usa Firestore/feedback/login/review da lib | modulo local de push quase igual a TechPromos; sync de token com Firestore no listener | Extrair contrato e implementacao KMPNotifier para `kmplib.push`; deixar sync Firestore como callback do app | Alta |
| `Influencer/mobile` | Sim | `KmpLib.init`, feedback, auth, review | push copiado de TechPromos, preferencias de token FCM, callbacks de notificacao, pending token | Extrair push base e fake de teste para lib; manter `UpdateFcmTokenUseCase` e backend no app | Alta |
| `Residencial` | Sim | usa `BaseViewModel`, UI components, theme, Firestore e Storage da lib | `formatCurrencyBRL`, `formatMonthYear`, `initialsOf`, `SelectedFile`/picker, `AttachmentUploader` com upload bytes | Mover formatadores comuns, ativar file picker, adicionar upload bytes no StorageService | Alta |
| `StatusHub/StatusHub` | Nao | nenhum uso de `br.com.codecacto.kmplib` encontrado | `BaseViewModel`, `ApiResult`, `handleApiCall`, `ConnectivityObserver`, `PickedFile`, `EmptyStateView`, `StatusBadge`, CNPJ local | Introduzir lib por contratos tecnicos: `ApiResult`, conectividade, MVI e file picker antes de UI | Alta |
| `StatusHub/Colaborador` | Nao | nenhum uso de `br.com.codecacto.kmplib` encontrado | `BaseViewModel` identico ao StatusHub, `ApiResult`, `ConnectivityObserver`, file picker, push nativo via bridge | Mesma estrategia do StatusHub; push aqui e candidato depois de estabilizar o modulo | Alta |

## 4. Achados por Area

### 4.1 MVI e BaseViewModel

Ha tres variantes recorrentes:

- `kmplib`: `BaseViewModel<State, Action, Effect>` com `state`, `effect`, `updateState`, `emitEffect`, `launch` e `onAction`.
- `Meu Fisio`/`Prospecta`: ordem generica `STATE, EFFECT, ACTION`, helpers `setState` e `sendEffect`.
- `locadora`: nao recebe estado inicial; a subclasse declara `state`; entrada publica e `dispatch(action)`; tem `ErrorHandler`.
- `StatusHub` e `Colaborador`: usam `uiState`, `updateState`, `sendEffect` e genericos `S, A, E`.

Recomendacao:

- nao quebrar o `BaseViewModel` atual da `kmplib`;
- adicionar helpers compativeis: `setState`, `sendEffect` e `dispatch`;
- avaliar uma classe alternativa para casos que querem expor `uiState`, por exemplo `SimpleMviViewModel`;
- nao colocar `ErrorHandler` de app dentro da classe base principal. Se necessario, criar um contrato opcional de error mapping separado.

Ganho esperado: `locadora`, `Meu Fisio`, `Prospecta`, `StatusHub` e `Colaborador` conseguem migrar sem reescrever todos os ViewModels de uma vez.

### 4.2 Arquivos e Upload

Situacao real:

- `kmplib` tem `FileData`, mas nao tem picker composable ativo.
- `Meu Advogado` e `Residencial` tem `SelectedFile(name, data, mimeType)` com `expect fun rememberFilePicker`.
- `StatusHub` usa `PickedFile(bytes, nome)`.
- `Residencial` e `Meu Advogado` precisam converter `ByteArray` para `dev.gitlive.firebase.storage.Data`.
- `StorageService` da `kmplib` ainda nao faz upload.

Recomendacao:

- completar `platform.FilePicker` com `expect/actual rememberFilePicker`.
- aceitar filtros opcionais: mime types, max size e multiplos arquivos depois.
- padronizar retorno em `FileData(name, mimeType, data, size, extension)`.
- adicionar `StorageService.uploadBytes(path, bytes, mimeType?)`.
- manter criacao de `Document`, `Attachment` e paths de storage no app, porque isso e dominio.

Ganho esperado: remove duplicacao direta de `Meu Advogado`, `Residencial`, `StatusHub` e `Colaborador`.

### 4.3 ApiResult, Ktor e Conectividade

`StatusHub/StatusHub` e `StatusHub/Colaborador` tem `ApiResult` proprio. O app principal tambem tem `handleApiCall` que converte `ResponseException`, timeout, erro de serializacao e mensagens comuns para `ApiResult.Error`.

`kmplib` so tem `NetworkChecker.isAvailable()`, que e pontual. StatusHub precisa de `ConnectivityObserver` com `StateFlow<Boolean>`, `start()` e `stop()`.

Recomendacao:

- criar `core.network.ApiResult<T>` com `Success`, `Error(code, message)` e `Loading`;
- mover `map`, `onSuccess`, `onError`, `getOrNull`, `errorOrNull`;
- criar `handleApiCall` em artefato comum para Ktor, sem dependencia de DTO especifico do StatusHub;
- criar `ConnectivityObserver` reativo separado do `NetworkChecker` atual.

Ganho esperado: alta para StatusHub; medio para apps que usam Firebase hoje mas podem consumir APIs Ktor no futuro.

### 4.4 Formatadores e Dados Simples

Duplicacoes reais:

- `Residencial`: `formatCurrencyBRL`, `formatMonthYear`, `initialsOf`.
- `locadora`: multiplos `formatCurrency`, `formatCpf`, `formatCnpj` em telas e geradores PDF.
- `StatusHub`: `Avatar` calcula iniciais; `EstabelecimentosScreen` tem `formatCnpj` e `isValidCnpj`.
- `Prospecta`: iniciais simples com `name.take(2).uppercase()`.

O que ja existe:

- `kmplib.mask.CurrencyMask.kt` tem `currencyToDouble` e `Double.formatAsCurrency`.
- validadores CPF/CNPJ e mascaras ja existem.

Recomendacao:

- criar pacote de utilitarios sem Compose, por exemplo `core.format`.
- adicionar funcoes nomeadas para uso direto: `formatCurrencyBRL(Double)`, `formatCpf(String)`, `formatCnpj(String)`, `initialsOf(String)`, `formatMonthYear(month, year)`.
- manter `Double.formatAsCurrency` para compatibilidade.
- cobrir entrada vazia, valor negativo, acentos em nomes e meses invalidos com testes.

### 4.5 UI Generica

`EmptyState` da `kmplib` ja e razoavelmente flexivel. O `EmptyStateView` do StatusHub e mais ajustado ao tema do produto, mas a estrutura e a mesma: icone, titulo, subtitulo e acao.

`StatusBadge` da `kmplib` recebe texto, cor de texto e cor de fundo. StatusHub tem wrapper generico parecido, mas tambem tem wrappers de dominio (`UsuarioStatusBadge`, `OcorrenciaTipoBadge`, `OcorrenciaStatusBadge`) que nao devem ir para a lib porque conhecem enums do produto.

Recomendacao:

- manter `EmptyState` e adicionar pequenos ajustes, como `subtitle` alias para `description` se ajudar migracao.
- manter `StatusBadge` generico e talvez adicionar overload com `backgroundColor` default e `textColor` default.
- nao mover wrappers por enum de dominio.
- `LoadingOverlay`, `OfflineBanner` e `SectionHeader` valem como componentes genericos se forem criados sem cor fixa de app.

### 4.6 Push Notification

Codigo lido:

- `TechPromos`: `core/push/PushNotificationService.kt`, `PushNotificationListener.kt`, `DefaultPushNotificationListener.kt`, fake em `commonTest`.
- `Influencer/mobile`: `push/KmpPushNotificationService.kt`, `PushNotificationListener.kt`, `DefaultPushNotificationListener.kt`, `UpdateFcmTokenUseCase.kt`.
- `Foco`: `core/push/PushNotificationService.kt`, `PushNotificationListener.kt`, `DefaultPushNotificationListener.kt`, bridge iOS.
- `StatusHub/Colaborador`: push nativo proprio com `NotificationBridge`, `FcmToken`, permissao e Firebase Messaging.

Padrao comum:

- uso de `com.mmk.kmpnotifier.notification.NotifierManager`;
- callbacks para token novo, notificacao recebida, payload e clique;
- `getToken`, `deleteToken`, `subscribeToTopic`, `unsubscribeFromTopic`;
- foreground notification usando `NotifierManager.getLocalNotifier().notify`;
- inicializacao forca criacao do singleton para registrar listener cedo.

O que nao deve ir para a lib:

- `UpdateFcmTokenUseCase`;
- persistencia de token pendente em preferencias;
- PATCH para backend;
- escrita em Firestore;
- deep links especificos;
- topicos derivados de flavor, papel, empresa ou usuario.

Recomendacao de API:

```kotlin
interface PushNotificationService {
    suspend fun getToken(): String?
    suspend fun deleteToken(): Result<Unit>
    suspend fun subscribeToTopic(topic: String): Result<Unit>
    suspend fun unsubscribeFromTopic(topic: String): Result<Unit>
}

interface PushNotificationListener {
    fun onNewToken(token: String)
    fun onPushNotification(title: String?, body: String?)
    fun onPayloadData(data: Map<String, String>)
    fun onNotificationClicked(data: Map<String, String>)
}
```

Evitar expor `PayloadData` diretamente na API publica se isso amarrar os apps ao KMPNotifier. Internamente a lib pode adaptar `PayloadData` para `Map<String, String>`.

Adicionar fake de teste na lib ou pelo menos uma implementacao simples em `commonTest`, inspirada no fake de TechPromos.

## 5. Plano Incremental Recomendado

1. **Formatadores e validadores de superficie**
   - Adicionar `formatCurrencyBRL`, `formatCpf`, `formatCnpj`, `initialsOf`, `formatMonthYear`.
   - Migrar primeiro `Residencial` e depois pontos pequenos da `locadora`.

2. **Storage e arquivos**
   - Completar `FilePicker` da `kmplib`.
   - Adicionar `uploadBytes` no `StorageService`.
   - Migrar `Meu Advogado` ou `Residencial`, uma tela de upload por vez.

3. **MVI compatibilidade**
   - Adicionar `dispatch`, `setState` e `sendEffect` sem remover `updateState` e `emitEffect`.
   - Migrar `Meu Fisio` ou `Prospecta` primeiro.
   - Depois avaliar `locadora`, que tem `ErrorHandler` proprio.

4. **ApiResult e conectividade**
   - Criar `ApiResult`, `handleApiCall` e `ConnectivityObserver`.
   - Usar `StatusHub/Colaborador` como piloto menor antes do `StatusHub` principal.

5. **Push notification**
   - Extrair contratos e implementacao KMPNotifier.
   - Validar com `Influencer/mobile`, porque ja segue o padrao TechPromos e tem fake/fluxo de pending token claro.
   - Depois validar `Foco`.
   - Por ultimo avaliar `StatusHub/Colaborador`, porque ele usa ponte nativa propria e pode exigir adaptadores.

### 5.1 Checklist operacional

Este bloco deve ser atualizado a cada rodada de implementacao para manter o contexto recuperavel.

#### Etapa 1 - Formatadores e validadores de superficie

Status: `[BLOQUEADO]` somente na validacao Gradle por erro de lock em `C:\Users\rapha\.gradle\wrapper\dists\gradle-8.14.3-bin\...\gradle-8.14.3-bin.zip.lck`.

- [x] Criar `core/format/BrazilianFormatters.kt` na `kmplib`.
- [x] Cobrir moeda BRL, CPF, CNPJ, iniciais e mes/ano com testes.
- [x] Migrar `Residencial` para os formatadores da `kmplib`.
- [x] Migrar pontos pequenos da `locadora` para validadores/formatadores da `kmplib`.
- [ ] Rodar build/testes Gradle sem bloqueio de lock local.

#### Etapa 2 - Storage e arquivos

Status: `[BLOQUEADO]` somente na validacao Gradle pelo mesmo lock local do wrapper.

- [x] Ativar `rememberFilePicker` expect/actual Android e iOS na `kmplib`.
- [x] Adicionar `FileData` e compatibilidade com MIME types.
- [x] Adicionar `StorageService.uploadBytes`.
- [x] Adicionar conversao interna `ByteArray.toFirebaseData()`.
- [x] Migrar picker/upload do `Residencial`.
- [ ] Rodar build/testes Gradle sem bloqueio de lock local.

#### Etapa 3 - MVI compatibilidade

Status: `[BLOQUEADO]` somente na validacao Gradle pelo mesmo lock local do wrapper.

- [x] Adicionar `setState`, `sendEffect` e `dispatch` sem quebrar a API existente.
- [x] Adicionar `SimpleMviViewModel`.
- [x] Cobrir compatibilidade com teste comum.
- [x] Migrar piloto `Meu Fisio` em `RestViewModel`.
- [ ] Avaliar migracao futura de `Prospecta` e `locadora`.
- [ ] Rodar build/testes Gradle sem bloqueio de lock local.

#### Etapa 4 - ApiResult e conectividade

Status: `[BLOQUEADO]` somente na validacao Gradle pelo mesmo lock local do wrapper.

- [x] Criar `core/network/ApiResult.kt`.
- [x] Criar `handleApiCall` e helpers de mensagem HTTP/rede.
- [x] Criar `ConnectivityObserver` expect/actual Android e iOS.
- [x] Adicionar dependencia Ktor core na `kmplib`.
- [x] Migrar piloto `StatusHub/Colaborador` para usar `ApiResult` da `kmplib` via typealias.
- [ ] Rodar build/testes Gradle sem bloqueio de lock local.

#### Etapa 5 - Push notification

Status: `[BLOQUEADO]` somente na validacao Gradle pelo mesmo lock local do wrapper.

- [x] Confirmar duplicacao em `TechPromos`, `Influencer/mobile` e `Foco`.
- [x] Adicionar dependencia `io.github.mirzemehdi:kmpnotifier:1.6.0` na `kmplib`.
- [x] Criar `br.com.codecacto.kmplib.push.PushNotificationService`.
- [x] Criar `br.com.codecacto.kmplib.push.PushNotificationListener` sem expor `PayloadData`.
- [x] Criar `KmpPushNotificationService` adaptando `PayloadData` para `Map<String, String>`.
- [x] Criar fake/teste comum de push sem Firebase real.
- [x] Migrar piloto `Influencer/mobile` para usar aliases da `kmplib`.
- [x] Manter no app `Influencer` as regras de token pendente, backend e deep link.
- [ ] Validar build/testes Gradle sem bloqueio de lock local.
- [ ] Migrar `Foco` depois que o build local estiver validavel.
- [ ] Avaliar `StatusHub/Colaborador` depois porque ele tem ponte nativa propria.

Log de execucao da Etapa 5:

- `kmplib`: adicionados `push/PushNotificationService.kt`, `push/PushNotificationListener.kt`, `push/KmpPushNotificationService.kt`, fake e teste comum.
- `Influencer/mobile`: `PushNotificationService` e `KmpPushNotificationService` viraram aliases para a `kmplib`; `DefaultPushNotificationListener` e `PushDeepLinkHandler` passaram a receber payload como `Map<String, String>`.
- Decisao mantida: inicializacao do `NotifierManager`, sincronizacao de token com backend/Firestore, topicos e navegacao por payload continuam nos apps.
- Validacao estatica: `git diff --check` passou na `kmplib` e no `Influencer/mobile` apenas com avisos de CRLF.
- Validacao Gradle: `./gradlew.bat :library:compileKotlinMetadata --no-daemon` e `./gradlew.bat :composeApp:compileKotlinMetadata --no-daemon` falharam antes da compilacao por `FileNotFoundException`/`Acesso negado` no lock `C:\Users\rapha\.gradle\wrapper\dists\gradle-8.14.3-bin\cv11ve7ro1n3o1j4so8xd9n66\gradle-8.14.3-bin.zip.lck`.

## 6. Riscos por Target

Android:

- push exige permissao `POST_NOTIFICATIONS` em Android 13+;
- content URI em picker nao pode ser tratado como path simples;
- uploads grandes por `ByteArray` podem estourar memoria;
- `EncryptedSharedPreferences` em StatusHub nao deve virar dependencia obrigatoria da lib comum.

iOS:

- push depende de ponte Swift/AppDelegate para APNs/FCM;
- picker precisa lidar com security-scoped resources;
- `ByteArray.toFirebaseData()` tem implementacao native especifica;
- callbacks de push devem funcionar com app frio, background e foreground.

JVM/WASM:

- `StatusHub` usa JVM e WASM; APIs mobile devem ter no-op ou erro controlado nesses targets.
- `ConnectivityObserver` precisa `actual` para JVM/WASM se for usado pelo StatusHub.
- push nao deve forcar dependencia Firebase/KMPNotifier em targets que nao suportam notificacao mobile.

## 7. O Que Nao Vale Mover

Nao mover para a `kmplib`:

- models de dominio (`Tenant`, `Request`, `PushToken`, `Notificacao`, `Usuario`, `Ocorrencia`, etc.);
- repositories com paths de Firestore ou endpoints de backend especificos;
- regras de permissao, perfil, empresa, tenant, flavor ou assinatura;
- telas completas de produto;
- wrappers de badge que recebem enums de dominio;
- notificacoes locais de cobranca/vencimento da `locadora`;
- sincronizacao de token FCM com backend ou Firestore;
- deep links e navegacao por payload.

## 8. Criterios de Aceite

Na `kmplib`:

- testes de formatacao: moeda, CPF, CNPJ, iniciais e mes/ano;
- testes de `ApiResult` e `handleApiCall`;
- testes de `FileData`;
- testes de push com fake listener/service sem Firebase real;
- `./gradlew jvmTest`;
- `./gradlew :library:build` antes de publicar.

Nos apps:

- migrar sempre em PRs pequenos;
- compilar o app migrado apos cada troca;
- validar Android e iOS para picker, storage e push;
- em `StatusHub`, validar tambem JVM/WASM quando a API tocada for compartilhada.

Sucesso significa reduzir duplicacao tecnica sem transformar a `kmplib` em um deposito de regra de negocio dos apps.
