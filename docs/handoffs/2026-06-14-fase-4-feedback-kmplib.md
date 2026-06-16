## Handoff — lib-mobile (kmplib) → cto / dev-mobile (migração de apps)
**Demanda:** Fase 4 (Feedback) parte kmplib — repontar `FeedbackService` do Firestore `code-cacto` para o endpoint central `POST {apps-api}/feedback/v1`. Última peça de "Firestore-como-banco" do feedback.
**Status:** concluído (compila commonMain metadata + Android; testes verdes no Android unit test; iOS validar em CI macOS) e publicado em mavenLocal.

### O que foi feito
- `FeedbackService` reescrito: agora faz `POST {appsApiBaseUrl}/feedback/v1` no **apps-api** via Ktor `HttpClient`, em vez do Firestore REST (envelope Firestore removido).
- Reusa `core/network` (`handleApiCall`/`ApiResult`) — Ktor core puro, **não exige ContentNegotiation** no client do app. Serialização própria com `kotlinx.serialization` (`encodeDefaults = false` → opcionais nulos omitidos).
- Removido o `expect/actual suspend fun httpPost(...)` (android/ios). Sobrou só `expect/actual val currentPlatform` (`android`/`ios`) para preencher `platform`.
- Auth simplificada: endpoint é público (sem Firebase ID token) — não há mais necessidade de auth anônimo só para o feedback. Nenhuma dependência de Firebase Auth no `FeedbackService`.
- Bump kmplib **2.23.0 → 2.24.0** (minor) + `publishToMavenLocal` (metadata + android aar).
- Catálogo (`.claude/skills/kmplib-catalog/SKILL.md`) e `Lib/kmplib/docs/backlog.md` atualizados.

### Como ficou o FeedbackService (assinatura/config)
- `FeedbackConfig(projectSlug: String, httpClient: HttpClient, appsApiBaseUrl: String = "https://apps-api.codecacto.com.br", appVersion: String = "", userId: String = "", userEmail: String = "")`.
- `FeedbackService.initialize(config)`, `updateUser(userId, userEmail)`.
- `suspend sendFeedback(source, motivo = "", mensagem, email = "", whatsapp = "", rating: Int? = null): Result<Unit>`.
- `suspend send(data: FeedbackData, rating: Int? = null): Result<Unit>`.
- DTO de fio `FeedbackRequest` (camelCase: `projectSlug`,`message`,`rating?`,`uid?`,`appVersion?`,`platform?`) + `FeedbackResponse` (201: `id`,`projectSlug`,`createdAt`).

### O que mudou no envio
- URL: `https://firestore.googleapis.com/.../documents/feedbacks?key=...` → `{appsApiBaseUrl}/feedback/v1`.
- Corpo: envelope Firestore (`fields.*.stringValue`) → JSON camelCase plano casando o contrato do apps-api.
- O endpoint central só tem `message`/`rating` + opcionais; por isso `motivo`/`source`/`email`/`whatsapp`/`conta` são **compostos em `message`** (ex.: `"[bug] texto...\n\n--\nemail: x | whatsapp: 11..."`) para preservar a triagem no painel.
- `uid` = `usuarioId` (capturado pelo servidor como NÃO confiável); `platform` via `currentPlatform`.

### Tratamento de erro / robustez
- Best-effort: nunca lança nem derruba a UI. `handleApiCall` normaliza tudo; `ApiResult.Error` (400 validação / 429 rate-limit / falha de rede) vira `Result.failure(FeedbackSendException(code, message))` com log leve `AppLogger.w`. `ApiResult.Success` (201) → `Result.success(Unit)`.
- `FeedbackService` não inicializado → `Result.failure(IllegalStateException)` + log (não lança).
- `FeedbackScreen` (ui/screens) consome inalterada (já trata `onSuccess`/`onFailure`).

### Contratos/APIs (BREAKING)
- `FeedbackConfig` mudou: **removidos** `appId`, `firebaseProjectId`, `firebaseApiKey`; **adicionados** `projectSlug` (obrigatório), `httpClient` (obrigatório), `appsApiBaseUrl` (default prod). Apps que faziam `FeedbackConfig(appId=..., firebaseProjectId="code-cacto", firebaseApiKey=...)` NÃO compilam contra 2.24.0.
- Novos públicos: `FeedbackRequest`, `FeedbackResponse`, `FeedbackSendException`, `FeedbackConfig.Companion.DEFAULT_APPS_API_BASE_URL`.

### Arquivos tocados
- `library/src/commonMain/.../feedback/FeedbackService.kt` — reescrito (Ktor + apps-api, best-effort).
- `library/src/commonMain/.../feedback/FeedbackConfig.kt` — nova config (projectSlug/httpClient/baseUrl).
- `library/src/commonMain/.../feedback/FeedbackData.kt` — +`FeedbackRequest`/`FeedbackResponse`.
- `library/src/androidMain/.../feedback/FeedbackService.android.kt` — só `currentPlatform = "android"`.
- `library/src/iosMain/.../feedback/FeedbackService.ios.kt` — só `currentPlatform = "ios"`.
- `library/src/commonTest/.../feedback/FeedbackServiceTest.kt` — novo (7 casos, MockEngine).
- `library/build.gradle.kts` — `version = "2.24.0"` + `testOptions { unitTests.isReturnDefaultValues = true }` (necessário p/ rodar testes que tocam `AppLogger`→`android.util.Log` no Android unit test JVM).
- `.claude/skills/kmplib-catalog/SKILL.md`, `Lib/kmplib/docs/backlog.md` — atualizados.

### Riscos / pendências
- **Migração obrigatória (breaking).** Até cada app bumpar para 2.24.0 e ajustar o `FeedbackConfig`, ele **não compila** contra a nova versão; e enquanto estiver na 2.23.x continua mandando feedback para o **Firestore `code-cacto`** (dois destinos coexistindo durante a transição). Consumidores: **Super 8, LocAki, Meu Advogado, Influencer** (init em `*Application.kt` Android + `MainViewController.kt` iOS).
- **iOS não publicado localmente** (host Linux — targets iOS desabilitados). Só metadata + `kmplib-android` saíram no mavenLocal. Validar `iosSimulatorArm64Test` e publicar klibs iOS em CI/host macOS (gap já no backlog "Prioridade alta").
- **Suíte Android unit test tem falhas PRÉ-EXISTENTES e ambientais** (não introduzidas aqui): `HandleApiCallTest` (Ktor MockEngine devolve `code=-1` no Android JVM em vez do status real), testes Compose (`AvatarUi`/`LoadingOverlay`/`OfflineBanner` exigem Robolectric — `android.os.Build.FINGERPRINT` null), `PasswordValidatorTest`/`AppPreferencesTest` (asserções de lógica). Esses casos passam no runtime real (`iosSimulatorArm64Test`). Por isso o `FeedbackServiceTest` assere o código HTTP exato só implicitamente (valida robustez best-effort nos dois runtimes; o `429`/`400` precisos ficam para o iOS test em CI).
- `appsApiBaseUrl` default é produção; apps com ambiente de staging devem sobrescrever.

### Próximo passo
- **dev-mobile (com cto):** migrar Super 8 / LocAki / Meu Advogado / Influencer para kmplib 2.24.0 — trocar `FeedbackConfig(appId/firebaseProjectId/firebaseApiKey)` por `FeedbackConfig(projectSlug = "<slug>", httpClient = <Ktor client do app>, appVersion = BuildConfig.VERSION_NAME)`; remover constantes de API key do Firestore `code-cacto`.
- **devops:** rodar `iosSimulatorArm64Test` + publicar artefatos iOS da 2.24.0 em host macOS/CI.
- **product-owner:** persistir este handoff em docs do projeto guarda-chuva da Fase 4, se aplicável.
