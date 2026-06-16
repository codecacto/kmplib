# Backlog de evolução — kmplib

> Dono: lib-mobile. Itens para fazer a kmplib crescer. Priorizar o que serve a ≥2 apps.
> Processo: skill `lib-evolution`. Detecção em massa: comando `/lib-audit`.

## Entregue
- [x] **Feedback IDENTIFICADO + campos estruturados** — entregue na **2.25.0**. `FeedbackRequest`
      ganhou os campos ESTRUTURADOS `name?`/`whatsapp?`/`email?` (colunas dedicadas no banco central,
      contrato já aceito pelo `apps-api`); o `FeedbackService` parou de concatenar email/whatsapp dentro
      da `message` (que passa a ser só `[motivo] + descrição`) e os envia como campos próprios. `email`
      cai no `userEmail` da config se não informado. `sendFeedback(...)` ganhou o parâmetro `nome`.
      `FeedbackScreen` ganhou o campo **nome** (opcional) + params de prefill `defaultName`/
      `defaultEmail`/`defaultWhatsapp`; **WhatsApp obrigatório**, **nome opcional**, e-mail opcional
      (valida formato só se preenchido). 8 testes (`FeedbackServiceTest`). **Consumidores a adotar no
      próximo build:** Super 8 (e demais que usam `FeedbackScreen`) — opcionalmente passar os defaults
      do usuário logado; nenhum é obrigatório (params têm default nulo, mudança retrocompatível).
- [x] **`FeedbackService` → apps-api central** — entregue na **2.24.0** (Fase 4; última peça de
      "Firestore-como-banco" do feedback). `FeedbackService` deixou de gravar no Firestore `code-cacto`
      via REST e passou a fazer `POST {appsApiBaseUrl}/feedback/v1` no backend central **apps-api**
      (endpoint PÚBLICO sem Firebase token, rate-limited no servidor). Reusa `core/network`
      (`handleApiCall`/`ApiResult`) + Ktor `HttpClient` puro (sem ContentNegotiation); best-effort
      (nunca lança/derruba a UI — 400/429/rede viram `FeedbackSendException` com log leve).
      **BREAKING `FeedbackConfig`:** agora `(projectSlug, httpClient, appsApiBaseUrl?, appVersion?,
      userId?, userEmail?)` — removidos `appId`/`firebaseProjectId`/`firebaseApiKey`; removido o
      `expect/actual httpPost` (sobrou só `currentPlatform`). 7 testes (`FeedbackServiceTest`,
      MockEngine). **Consumidores a migrar:** Super 8, LocAki, Meu Advogado, Influencer (passar
      `projectSlug`+`httpClient`). Contrato casado com handoff `apps-api` 2026-06-14-fase-4-feedback.
- [x] **Camada de dados REST genérica (`core/data`)** — entregue na **2.23.0** (Fase 1b FUNDAÇÃO da
      centralização "sair do Firestore-como-banco"). Apps deixam de usar Firestore como BANCO e passam
      a falar com o backend REST central (`apps-api`) via `Repository<T, ID>` (contrato) +
      `RestRepository<T, ID>` (impl Ktor puro online-first, sem real-time/offline), `RestConfig`
      (httpClient/baseUrl/tokenProvider Firebase ID token/onUnauthorized/cacheTtl) e
      `RestRepositoryFactory` (cria repo por entidade, Koin-friendly). Reusa `ApiResult`/`handleApiCall`/
      `PaginatedResponse` de `core/network` (não duplicou). `FirestoreService` e `FeedbackService`
      mantidos intactos (legado). 17 testes (`RestRepositoryTest`, MockEngine). **Próximo:** Fase 2 —
      Meu Advogado consumir `RestRepository` para a entidade `Request`.

## Prioridade alta
- [ ] **Publicar artefatos iOS da kmplib a partir de host macOS** — o naming dos artefatos por-target
      iOS (`kmplib-iosarm64` / `kmplib-iossimulatorarm64` / `kmplib-iosx64`) foi corrigido na 2.3.1
      (módulo Gradle renomeado de `:library` para `:kmplib` no settings; antes saía `library-ios*`,
      quebrando a resolução iOS de TODOS os apps KMP). Porém os klibs/frameworks iOS **só existem
      quando publicados de um host macOS** — em Windows os targets iOS são desabilitados
      (`kotlin.native.ignoreDisabledTargets=true`) e `publishToMavenLocal` gera apenas
      `kmplib` (metadata) + `kmplib-android`. **Ação:** rodar `./gradlew :kmplib:publishToMavenLocal`
      (ou publish para Central) de um Mac/CI macOS para a versão 2.3.1+, completando os artefatos iOS.
      Até lá, apps KMP só resolvem a kmplib no target Android.
- [ ] **SQLDelight / offline-first sync** — abstração de persistência local + sync com Firestore
      (hoje cada app sincroniza manualmente). Serve a apps offline (Meu Fisio, Prospecta) e marketplace.
- [ ] **UI de upload com progresso** — componente reutilizável sobre `rememberFilePicker` +
      `StorageService.uploadBytes` (Residencial e outros refazem).
      **(GAP-06 / Exiba)** Necessário para Exiba Onda 2: composable que exibe progresso de upload
      Firebase Storage + preview de imagem + estado de erro/retry. Versão-alvo: 2.4.0 ou 2.5.0.

### Exiba — Onda 1 (alvo: kmplib 2.4.0)
- [x] **GAP-02 — `MapView` / `MapMarker` (Google Maps wrapper expect/actual)** — entregue na 2.4.0.
      Módulo `map/`: `MapView`, `MapMarker`, `MapScope`, `LatLng`, `CameraPosition`,
      `MapMarkerStatus` (FREE/OCCUPIED/EXPIRING/EXPIRED/INSTALLING), `MapMarkerStatus.color()`
      (via `AppColors.current`), `rememberCameraPositionState`/`CameraPositionState.animateTo`.
      Android: `maps-compose` (pin colorido por hue do `BitmapDescriptorFactory`); long-click
      para posicionar pin de cadastro. **iOS: placeholder** (TODO `GMSMapView` via interop — requer
      macOS + SDK). Clustering segue fora de escopo (candidato 2.5.0).
- [x] **GAP-04 — `LocationProvider` (expect/actual)** — entregue na 2.4.0. Módulo `location/`:
      `LocationProvider.getCurrentLocation(): LatLng?` (suspend, timeout 10s) + `hasLocationPermission()`,
      factory `createLocationProvider()` e helper `@Composable rememberLocationProvider()`.
      Android: Fused Location Provider + pedido de `ACCESS_FINE_LOCATION` via launcher Compose.
      iOS: `CLLocationManager` (when-in-use), fix único. Reusa `map.LatLng`.

### Meu Estacionamento — Ondas 0/1 (origem: ux-designer, 2026-06-06)
- [x] **GAP-ME-01 — `CameraView` + OCR/LPR de placa** — entregue na 2.6.0 (Android; iOS placeholder).
      Decisão do CTO: **OCR on-device** (sem serviço externo) — ML Kit no Android, Apple Vision no iOS.
      Novo módulo `camera/`:
        - `PlateOcrAnalyzer` (expect/actual): `suspend analyzePlate(imageBytes): String?` → placa
          normalizada ou `null`. Android: ML Kit Text Recognition (`text-recognition:16.0.1`).
          iOS: **placeholder** (retorna `null`, TODO `VNRecognizeTextRequest` em host macOS).
        - `CameraView` (Composable expect/actual): `onPlateCaptured`. Android: CameraX
          (`camera-camera2/lifecycle/view:1.4.2`) + `ImageAnalysis` + ML Kit, throttle de 2s entre
          leituras, placeholder quando sem permissão `CAMERA`. iOS: **placeholder** estático
          (Box + ícone + "Câmera disponível apenas em iOS nativo", não chama `onPlateCaptured`).
        - `extractPlate(ocrText): String?` (commonMain, testável): extrai a 1ª placa válida do texto
          de OCR reusando `normalizePlate`/`isValidPlate` do módulo `mask`. Testes: `PlateTextExtractorTest`
          (7 casos). **Pendência iOS:** implementar com Apple Vision em host macOS.
        - **Config do app consumidor (Android):** declarar `android.permission.CAMERA` no manifest e
          solicitar a permissão em runtime antes de exibir o `CameraView`.
- [x] **GAP-ME-02 — `PlateMask` (placa BR Mercosul + antiga)** — entregue na 2.5.0. Módulo `mask`:
      `PlateVisualTransformation` (formata `AAA-0000` para antiga e `AAA0A00` para Mercosul, detectando
      o padrão pelo 5º caractere), `normalizePlate(raw)` (uppercase + remove separadores + máx. 7 chars),
      `isValidPlate(plate)` (valida os dois padrões). Testes em `PlateMaskTest`.
- [x] **GAP-ME-03 — `AppCheckbox`** — entregue na 2.5.0 em `ui/components/AppCheckbox.kt`. Linha
      clicável (`Modifier.toggleable` + `Role.Checkbox`) acessível, cores via `MaterialTheme.colorScheme`,
      com `@Preview`. Usado no aceite LGPD do onboarding (RNF-04).
- [x] **GAP-ME-04 — Geração de PDF (recibo/fechamento)** — **atendido pelo módulo `pdf/` da 2.14.0**
      (ver GAP-MOS-M-02). Montar `OsPdfData` (title="Recibo"/"Fechamento", itens/total) e chamar
      `generateAndShareOsPdf(...)`. iOS placeholder (herda host macOS).

> Já cobertos por gaps existentes do Exiba e reaproveitados pelo Meu Estacionamento:
> **GAP-01 `SegmentedControl`** — entregue na 2.5.0 (ver Concluído) e **GAP-03 `FilterChipRow`**
> — entregue na 2.7.0 (ver Concluído).

### Call Recorder — gaps reportados pelo ux-designer (2026-06-06)
- [x] **GAP-CR-01 — `AudioPlayer` (expect/actual) + `AudioPlayerBar`** — entregue na 2.8.0.
      Novo módulo `media/`: interface `AudioPlayer` (play/pause/seekTo/stop/release + StateFlows
      `currentPosition`/`duration`/`isPlaying`/`state`/`lastError`), enum `AudioPlayerState`
      (IDLE/LOADING/PLAYING/PAUSED/COMPLETED/ERROR), factory `createAudioPlayer()`. Android:
      `MediaPlayer` + `AudioPlayerHolder.init(context)` no Application; progresso via Handler (~250ms),
      sem novas deps. iOS: `AVAudioPlayer` (AVFAudio) + `NSTimer` no run loop principal. UI:
      `AudioPlayerBar(positionMs, durationMs, isPlaying, state, onPlayPause, onSeek)` (Slider de seek +
      play/pause/replay + tempos), cores via `MaterialTheme.colorScheme`, acessível. Helper público
      `formatPlayerTime(millis)` (testado, 4 casos verdes em `AudioPlayerBarTest`).
- [x] **GAP-CR-02 — `PermissionManager` (expect/actual)** — entregue na 2.8.0. Novo módulo
      `platform/permission/`: interface `PermissionManager` (`checkPermission`,
      `requestPermission: Flow<PermissionStatus>`), enums `PermissionStatus`
      (GRANTED/DENIED/PERMANENTLY_DENIED/NOT_REQUESTED) e `AppPermission`
      (MICROPHONE/PHONE_STATE/CALL_LOG/NOTIFICATIONS/CAMERA), factory `createPermissionManager()`.
      Android: `ActivityCompat.requestPermissions` + `shouldShowRequestPermissionRationale`
      (distingue DENIED de PERMANENTLY_DENIED) via `PermissionHostHolder` (setActivity no onResume +
      handlePermissionResult no onRequestPermissionsResult). iOS: `AVAudioSession` (microfone),
      `AVCaptureDevice` (câmera), `UNUserNotificationCenter` (notificações); PHONE_STATE/CALL_LOG
      sem equivalente iOS → GRANTED (no-op). **Config Android (consumidor):** declarar
      `RECORD_AUDIO`/`READ_PHONE_STATE`/`READ_CALL_LOG` no manifest. **iOS:** `NSMicrophoneUsageDescription`
      no Info.plist. **Pendência iOS:** klibs publicados de host macOS (herdam item de prioridade alta).
- [ ] **GAP-CR-03 — `SwipeableListItem` (swipe-to-delete)** — Média. Item de lista com swipe para
      revelar ação destrutiva (excluir gravação). Candidato sobre `SwipeToDismissBox` do Material 3.
- [x] **GAP-CR-04 — `PaywallScreen` reutilizável** — **entregue na 2.20.0** como parte do módulo
      `monetization/entitlement` (padrão freemium-com-limite). `PaywallScreen` stateless + `PaywallState`/
      `PaywallAction`/`PaywallTexts` (ui/screens/paywall) reusam `PurchaseManager`/RevenueCat via
      `EntitlementController.purchase(plan)`. Ver entrada na seção "Concluído".
- [ ] **GAP-CR-05 — `SettingsRow`/`ListItem`** — Baixa → **2º+3º consumidores confirmados**. Linha de
      configuração padrão (título + subtítulo + trailing). Candidato a `ui/components`.
      Pedido por Call Recorder, Salmos (#10) e agora **Doses de Alegria (GAP-DA-03)** — tela Configurações
      (linha "Notificação diária" com toggle trailing). Com ≥3 apps, **reforça promoção**. Contorno no MVP: `Row` local.
- [ ] **GAP-CR-06 — `RecordingIndicator`** — Baixa. Indicador visual animado de gravação em curso.

### Doses de Alegria — gaps reportados pelo ux-designer (2026-06-06)

> Origem: `DosesDeAlegria/docs/design/wireframes.md` (4 telas: Splash, Onboarding, Início, Configurações;
> app standalone offline-first, 100% grátis, sem login). App de baixíssima complexidade que reusa quase
> tudo da kmplib (AppTopBar/BackTopBar/AppButton/ShareHandler/NotificationScheduler/PermissionManager/
> AppPreferences/BuildInfo/DateFormatters/Toast). Apenas 1 gap **novo**; os outros 2 já existem no backlog.

- [x] **GAP-DA-02 — `AppSwitch` (toggle on/off estilizado pelo tema)** — entregue na 2.12.0 em
      `ui/components/AppSwitch.kt`. Wrapper fino sobre o `Switch` do Material 3, par do `AppCheckbox`:
      `AppSwitch(checked, onCheckedChange, modifier, enabled)` com cores de `MaterialTheme.colorScheme`
      (qualquer paleta do `AppTheme`, sem hardcode) e 2 previews. Para linha completa com rótulo,
      compor com `Row` ou futuro `SettingsRow` (GAP-CR-05). Serve a qualquer tela de ajustes (≥2 apps).

> **Reuso de gaps já no backlog (NÃO recriar) — citados no design de Doses de Alegria:**
> **GAP-SAL-06 (`AppTimePicker`)** → GAP-DA-01 — **entregue na 2.12.0** (Onboarding + Configurações);
> **GAP-CR-05 (`SettingsRow`/`ListItem`)** → GAP-DA-03, linha de Configurações.

### ReciboFacil — gaps reportados pelo ux-designer (2026-06-06)

> Origem: `ReciboFacil/docs/design/wireframes.md` (§ GAPS DE LIB) + `flows.md`. App KMP standalone
> (Arq. A) Android/iOS + web (paridade), Firebase/Firestore direto, freemium (5 recibos/mês + marca
> d'água; Premium R$ 12,90/mês). **3 bloqueadores da Onda 2 (núcleo do recibo).** Reusa amplamente a
> kmplib (LoginScreen/RegisterScreen 2.0, AuthRepository, FirestoreService, AppCheckbox, CpfMask/
> CnpjMask + validators, CurrencyMask, AppDatePicker, ShareHandler, BitmapEncoder, PurchaseManager/
> MonetizationManager, ReaderView p/ termos).

- [x] **GAP-RF-M-01 — `valorPorExtenso(centavos: Long): String`** — entregue na 2.15.0 em
      `core/format/ValorPorExtenso.kt` (commonMain puro). Implementa EXATAMENTE as 12 regras do contrato
      `ReciboFacil/docs/design/valor-por-extenso-casos.md`: real/reais + centavo/centavos, "cem"×"cento",
      conjunção interna (R7) e entre grupos (R8), escalas mil/milhão/bilhão/trilhão (R9), "de reais" (R10),
      zero (R11). Rejeita `centavos < 0` (`IllegalArgumentException`). Teste parametrizado
      `ValorPorExtensoTest` com **todos os ~80 casos** das tabelas §2/§3 + negativos — **100% verde**.
      **Nuance do contrato implementada:** singular "real" só quando a parte inteira é um único grupo
      (`reais < 1000 && reais % 100 == 1`), i.e. `1`→"um real" e `101`→"cento e um real"; `21`→"vinte e
      um reais" e `1.000.001`→"um milhão e um reais" ficam plural. **Paridade:** a weblib (GAP-RF-W-02)
      DEVE replicar a mesma string para todos os casos.
- [x] **GAP-RF-M-02 — `SignaturePad` (captura de assinatura por canvas)** — entregue na 2.15.0 em novo
      módulo `signature/SignaturePad.kt` (commonMain, Compose MP). `SignaturePadState`
      (`rememberSignaturePadState()`): `isEmpty` reativo, `clear()`, `undo()`, `toPngBytes(strokeColor,
      strokeWidth): ByteArray?` (PNG **transparente**; `null` se vazio ou área ainda não medida). O
      composable `SignaturePad(state, modifier, strokeColor, strokeWidth, backgroundColor, height)` captura
      o traço via `detectDragGestures` (toque/mouse), desenha paths suavizados (quadráticos) e mede a
      superfície p/ exportar. **Reusa `encodeBitmapToPng` (`platform/BitmapEncoder`)** — render off-screen
      via `ImageBitmap`+`Canvas` do Compose (commonMain, sem expect/actual extra). Testes
      `SignaturePadStateTest` (7 casos de estado, verdes). **Pendência iOS:** o encoding usa o actual
      Skia já existente; validar visualmente em build nativo macOS.
- [x] **GAP-RF-M-03 — Geração de PDF do recibo (layout congelado + marca d'água condicional)** — entregue
      na 2.15.0 em `pdf/ReciboPdfData.kt` (commonMain) + `pdf/ReciboPdf.android.kt` + `pdf/ReciboPdf.ios.kt`.
      API: `expect fun generateReciboPdf(data: ReciboPdfData, watermark: Boolean): ByteArray`,
      `data class ReciboPdfData` (emitente/pagador `ReciboParte`, valorFormatado, valorPorExtenso, descrição,
      localData, numeroRecibo, dataHoraEmissao, emitentePessoaJuridica, logo/assinaturas/Inter bytes opcionais,
      watermarkText), `ReciboPdfNotSupportedException`, `defaultReciboPdfFileName(data)`. **Android:**
      `android.graphics.pdf.PdfDocument`, layout EXATO de `recibo-layout-spec.md` (A4 retrato, margens 20mm,
      cores hex da spec §0, cabeçalho RECIBO/Nº + logo, colunas Emitente/Pagador 81mm, frase do corpo,
      valor 28pt ACCENT, local/data à direita, assinatura(s) ancoradas em y=244mm 1 ou 2 colunas, rodapé,
      marca d'água -45° 12% centralizada §9). **iOS:** `UIGraphicsPDFRenderer` escrito (mesmo layout/medidas) —
      compila/valida só em host macOS (fundador valida). **Distinto** do `OsPdfData`/`OsPdfGenerator` genérico
      (2.14.0, GAP-MOS-M-02): este é dedicado à spec congelada do recibo. Para compartilhar, passar os bytes ao
      `ShareHandler.shareFile(...)`. **Pendência:** fonte **Inter** não embutida (o render usa sans-serif;
      `ReciboPdfData.interRegularBytes/interBoldBytes` permitem injetar o TTF p/ paridade de métricas com a
      weblib). **Pendência iOS:** render nativo em host macOS.
      **2.18.0 — refinamento de paridade entregue:** (1) **negrito inline** na frase do corpo (§5) — os trechos
      `{pagador}`/`{valor}`/`{valor por extenso}`/`{descrição}` agora saem em Bold como na weblib (antes o
      Android desenhava tudo regular); (2) **baseline iOS↔Android unificado** num helper compartilhado
      `pdf/ReciboPdfLayout.kt` (commonMain). Nova API pública: `reciboBodySegments`/`reciboBodyWords`,
      `ReciboTextSegment`/`ReciboWord`, `mmToPt`/`MM_TO_PT`, `textTopFromBaseline`. Ambos renderers passam as
      MESMAS baselines da spec; o iOS converte para topo via ascent da `UIFont`. Compatível (aditivo, não quebra).
- [ ] **GAP-RF-M-04 — `OnboardingPager` (carrossel de onboarding)** — Baixa, contornável.
      **= GAP-SAL-05** já no backlog (carrossel com dots + Pular/Próximo). 2º consumidor confirmado
      (ReciboFacil Onda 1) — reforça promoção. Contorno no MVP: `HorizontalPager` local.

> **Reuso de gaps já no backlog (NÃO recriar) — citados no design do ReciboFacil:**
> **GAP-CR-04 (`PaywallScreen`)** → Paywall/Upsell (Onda 4) — 2º consumidor (modelo freemium definido:
> 5/mês + marca d'água, Premium R$ 12,90); **GAP-CR-05 (`SettingsRow`/`ListItem`)** → Configurações/
> Gestão de assinatura (Onda 5) — +1 consumidor; **GAP-SAL-07 (`LocalIsCompact`)** → responsividade
> compact/expanded (todas as telas).
> **Já disponíveis (usar direto):** `LoginScreen`/`RegisterScreen` 2.0, `AuthRepository` (+ `IAuthRepository`),
> `FirestoreService`/`StorageService`, `AppCheckbox`, `CpfMask`/`CnpjMask`/`CurrencyMask`/`PhoneMask` + validators,
> `AppDatePicker`, `AppTextField`/`AppTextArea`/`FormContainer`, `AppTopBar`/`BackTopBar`/`AppBottomNavBar`,
> `EmptyState`/`SkeletonBox`/`LoadingOverlay`/`OfflineBanner`/`StatusBadge`/`ConfirmationDialog`/`ErrorModal`/
> `NoInternetDialog`/`Toast`, `ShareHandler`/`BitmapEncoder`/`UrlLauncher`, `PurchaseManager`/`MonetizationManager`,
> `BrazilianStates`/`BrazilianCities`, `ReaderView` (termos), `BuildInfo`.

### Salmos — gaps reportados pelo ux-designer (2026-06-06)

> Origem: `Salmos/docs/handoffs/2026-06-06-design-salmos.md` (12 telas, flavors protestante + católico,
> app standalone offline-first sem login). Triagem confirmada pelo lib-mobile: **nenhum** destes
> componentes existe hoje na kmplib (verificado por grep no `library/src`). Ordenação de implementação:
> **GAP-SAL-01 antes da Onda 1** (tela-coração de leitura), **GAP-SAL-03 antes da Onda 3** (motor de
> viralização). Os demais são contornáveis localmente no MVP e devem ser promovidos quando servirem a ≥2 apps.

- [x] **GAP-SAL-01 — `ReaderView` (leitor com fonte ajustável + seleção de trecho)** — entregue na 2.10.0.
      Novo módulo `ui/reader/` (commonMain puro, sem expect/actual): `ReaderView` + tipos públicos
      `ReaderBlock(text, number?)`, enum `ReaderFontSize` (SMALL 0.85× / MEDIUM 1.0× / LARGE 1.3× /
      EXTRA_LARGE 1.6×, com `next()`/`previous()`/`fromScale()`) e helper `List<Pair<Int,String>>.toReaderBlocks()`.
      API genérica (NÃO acoplada a "Salmo"): recebe `List<ReaderBlock>` + `title/subtitle/footer` opcionais.
      `fontSize` escala apenas o corpo (não a UI); todo o conteúdo dentro de `SelectionContainer` (copiar trecho);
      numeração leading discreta (`onSurfaceVariant`) togglável por `showNumbers`; medida de linha limitada no
      modo largo via `maxContentWidth` (default 640dp, ~60–72 caracteres); altura de linha ~1.6; claro/escuro
      via `MaterialTheme.colorScheme` (qualquer paleta de flavor do `AppTheme`); família serifada injetável por
      `bodyFontFamily`/`titleFontFamily`. Persistência do `fontScale` fica no app (via `AppPreferences` —
      `ReaderFontSize.scale`/`fromScale`). Testes `ReaderViewTest` (8 casos: next/previous/saturação/fromScale/
      toReaderBlocks, verdes). `:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL; publicado em mavenLocal como
      `br.com.codecacto:kmplib:2.10.0` (`kmplib` metadata + `kmplib-android`; klibs iOS pendentes de host macOS —
      é commonMain puro, deve compilar em iOS sem mudança). **Implementado antes da Onda 1 (bloqueador resolvido).**
- [x] **GAP-SAL-03 — `ShareCard` + captura para imagem (render → ByteArray)** — entregue na 2.16.0 em novo
      módulo `ui/share/` (`ShareCard.kt` + `ShareCardRender.kt`). **(a)** Composable de card estilizado
      `ShareCard(spec, style, modifier, quoteFontFamily=Serif, brandFontFamily=SansSerif)`: trecho serifado herói
      + referência com acento + marca discreta no rodapé, gradiente sutil; modelo `ShareCardSpec(quote, reference,
      brand="", format)` + enum `ShareCardFormat` (`STORIES` 9:16 / `SQUARE` 1:1) + `ShareCardStyle.fromColorScheme(
      MaterialTheme.colorScheme)` (deriva fundo/texto/acento do flavor — serve aos 2 flavors, sem hardcode) +
      `ShareCardBackground.solid/gradient`. Estética serena conforme `direcao-visual.md` §4. **(b)** Captura
      `renderShareCardToPng(spec, style, textMeasurer, targetWidthPx=1080): ByteArray` — **commonMain pura, SEM
      expect/actual**: desenha o MESMO layout off-screen via `CanvasDrawScope` + `TextMeasurer`
      (`rememberTextMeasurer()`, Compose MP 1.10) e codifica com `encodeBitmapToPng` (`platform/BitmapEncoder`).
      **Decisão de design:** dispensa o `captureToImageBytes` expect/actual previsto (graphicsLayer/Picture +
      UIGraphicsImageRenderer) — desenhar o card programaticamente (mesma filosofia do `SignaturePad`) funciona em
      Android E iOS sem host de UI ativo, evita o placeholder iOS e mantém o preview fiel ao PNG. **Reusa (não
      recriou):** `ShareHandler` (atalhos `shareCardImage` → `shareImage`, `shareCardText` → `shareText`) e
      `BitmapEncoder`. Testes `ShareCardTest` (7 casos JVM, verdes). `:kmplib:compileDebugKotlinAndroid` BUILD
      SUCCESSFUL; `:kmplib:publishToMavenLocal` BUILD SUCCESSFUL → `br.com.codecacto:kmplib:2.16.0` (`kmplib`
      metadata + `kmplib-android`; klibs iOS pendentes de host macOS — é commonMain puro, deve compilar em iOS sem
      mudança, validar render visual em macOS). **Motor de viralização da Onda 3 — bloqueador resolvido.**
- [x] **GAP-SAL-02 — `ThemeChipGrid` / `ChipGrid` (grade de chips clicáveis multi-linha)** — entregue na 2.13.0
      em `ui/components/ThemeChipGrid.kt` (commonMain puro, sem expect/actual). Tipo público
      `ChipItem(id, label, leadingIcon?)` (genérico, NÃO acoplado a "tema de salmo"). `ThemeChipGrid(items,
      onChipClick, modifier, selectedIds, maxItemsPerRowCompact=3, maxItemsPerRowWide=5, wideThreshold=600.dp,
      horizontalSpacing=8.dp, verticalSpacing=8.dp)`: `FlowRow` multi-linha responsivo (nº de colunas cresce no
      modo largo via `BoxWithConstraints`). **Dois modos:** (a) **ação** (default, `selectedIds` vazio) — chips
      `AssistChip`, toque dispara `onChipClick(id)` para navegar (caso da tela #6 Busca por Tema); (b)
      **selecionável** (`selectedIds` não-vazio) — chips `FilterChip` com estado, para reuso futuro multi-select
      (`onChipClick` é sinal de toggle). Cores via `MaterialTheme.colorScheme` (qualquer paleta do `AppTheme`,
      sem hardcode); `contentDescription` por chip; 2 previews. **Não substitui** `FilterChipRow` (2.7.0, LazyRow
      horizontal single-choice de filtro). Sem novas dependências (`FlowRow`/`ExperimentalLayoutApi` já usados no
      `AppMultiSelect`). Testes `ThemeChipGridTest` (4 casos JVM: contrato `ChipItem`/igualdade/semântica de
      seleção, verdes; render/clique via `runComposeUiTest` exige host de UI desktop/instrumentado, indisponível
      no alvo `testDebugUnitTest` em Windows — mesma limitação dos demais testes de UI da lib, ex.: `OfflineBannerTest`).
      `:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL; `:kmplib:publishToMavenLocal` BUILD SUCCESSFUL →
      `br.com.codecacto:kmplib:2.13.0` (`kmplib` metadata + `kmplib-android`; klibs iOS pendentes de host macOS —
      é commonMain puro, deve compilar em iOS sem mudança). Tela #6 desbloqueada.
- [x] **GAP-SAL-06 / GAP-DA-01 — `AppTimePicker` (seletor de hora HH:mm)** — entregue na 2.12.0 em
      `ui/components/AppTimePicker.kt`. Par do `AppDatePicker`: `AppTimePicker(hour, minute, onTimeSelected,
      modifier, title, confirmLabel, dismissLabel, onDismiss)`. Envolve o `TimePicker` do Material 3
      (`rememberTimePickerState`, `is24Hour = true`, hora/minuto inicial com `coerceIn`) num `AlertDialog`
      estilizado por `MaterialTheme.colorScheme` (qualquer paleta do `AppTheme`, sem deps novas). Dialog
      controlado pelo chamador (exibir com `if (show) AppTimePicker(...)`); `onDismiss` default mantém a
      hora/minuto atuais. 1 preview. Atende Salmos (#2 onboarding opt-in, #10 Ajustes) e Doses de Alegria
      (Onboarding + Configurações). **2 consumidores confirmados — gap fechado.**
- [ ] **GAP-SAL-05 — `OnboardingPager` / `OnboardingSlide` (carrossel de onboarding)** — Baixa. Carrossel com
      dots de página, botão Pular/Próximo e slide final de opt-in. Tela #2. Reuso provável ≥2 apps. Contorno no
      MVP: `HorizontalPager` local. Candidato a `ui/components` (ou `ui/onboarding`).
- [ ] **GAP-SAL-07 — Expor `LocalIsCompact` (CompositionLocal de breakpoint)** — Baixa. A skill `compose-mvi`
      já referencia `LocalIsCompact` como padrão de responsividade, mas **ele não existe** na kmplib (grep sem
      resultados). Expor um `CompositionLocal<Boolean>` (compact vs expanded) calculado por `WindowSizeClass`/
      `BoxWithConstraints`, evitando que cada tela chame `calculateWindowSizeClass()` direto. Todas as telas.
      Contorno no MVP: calcular por `BoxWithConstraints` local. Candidato a `ui/theme`.

> **Reuso de gaps já no backlog (NÃO recriar) — citados no design de Salmos:**
> **GAP-CR-05 (`SettingsRow`/`ListItem`)** → linhas de Ajustes (#10); **GAP-CR-03 (`SwipeableListItem`)** →
> swipe-to-remove em Favoritos (#8, opcional); **GAP-CR-04 (`PaywallScreen`)** → Premium real no v2 (#12).
> **Já disponíveis (usar direto):** `SegmentedControl` e `AppCheckbox` (2.5.0), `SkeletonBox`/`AppDatePicker`/
> `AppBottomSheet` (2.3.0), `PermissionManager` c/ NOTIFICATIONS (2.8.0), `FilterChipRow` (2.7.0),
> `ShareHandler`/`BitmapEncoder`/`UrlLauncher`/`NotificationScheduler`/`BuildInfo`/`FeedbackScreen`/`EmptyState`/
> `Toast`/`LoadingOverlay`/`AppTopBar`/`BackTopBar`/`AppButton`/`AppTextField`.

### MinhaOS — gaps reportados pelo ux-designer (2026-06-06)

> Origem: `MinhaOS/docs/design/wireframes.md` (§ GAPS DE LIB) + `flows.md`. App KMP Android/iOS + web
> (painel + portal público), Arq. A standalone sobre Firestore (sem backend Ktor no MVP). Freemium:
> grátis 5 OS/mês + marca d'água no PDF; Pro R$24,90/mês · R$249/ano. Foco de campo (toques grandes,
> offline-first). Escopo do design: Onda 0 (auth/onboarding/perfil) + Onda 1 (criar OS → PDF/link →
> cobrar). Reusa amplamente a kmplib: `LoginScreen`/`RegisterScreen` 2.0, `AuthRepository`(+`IAuthRepository`),
> `FirestoreService`/`StorageService`, `AppTopBar`/`BackTopBar`/`AppBottomNavBar`, `AppTextField`/`AppTextArea`/
> `FormContainer`/`NumberField`, `Card`/`Avatar`/`StatusBadge`/`EmptyState`/`SkeletonBox`/`LoadingOverlay`/
> `OfflineBanner`/`ConfirmationDialog`/`ErrorModal`/`NoInternetDialog`/`Toast`, `ImagePicker`/`FullScreenImageViewer`/
> `ZoomableBox`, `AppBottomSheet`/`SegmentedControl`/`FilterChipRow`/`AppDatePicker`/`AppCheckbox`,
> `PhoneMask`/`CurrencyMask` + validators, `CurrencyFormatters`/`DateFormatters`, `ShareHandler`/`UrlLauncher`,
> `PurchaseManager`/`MonetizationManager`/`PurchaseState`, `ReaderView` (política/termos), `ConnectivityObserver`,
> `AppPreferences`, `BrazilianStates`/`BrazilianCities`.

- [x] **GAP-MOS-M-02 — Geração de PDF da OS (orçamento/OS, marca + marca d'água condicional)** —
      **entregue na 2.14.0 (Android; iOS placeholder).** Consolida e fecha também **GAP-ME-04** (PDF de
      recibo/fechamento) e **GAP-RF-M-03** (PDF do recibo) — 3 consumidores confirmados (MinhaOS, Meu
      Estacionamento, ReciboFacil), promovendo o "contorno por texto" para helper real de PDF. Novo módulo
      `pdf/`:
        - Modelo comum `OsPdfData` (kotlinx.serialization, genérico — NÃO acoplado ao MinhaOS) + tipos
          `OsPdfCompany` (name/phone/email/address/`logoBytes`), `OsPdfClient`, `OsPdfItem`
          (description/quantity/unitPrice/subtotal). Dinheiro = **string decimal** ("320.00"); campos
          `title` (default "Ordem de Serviço", permite "Orçamento"/"Recibo"), `status`, `description`,
          `discount`, `total`, `notes`, `footer`, `watermark` (bool, regra `plan==free`), `watermarkText`.
        - `interface OsPdfGenerator { generate(data): ByteArray }` + `expect fun createOsPdfGenerator()`.
          Android: `AndroidOsPdfGenerator` via `android.graphics.pdf.PdfDocument` (A4 72dpi, sem novas
          deps, sem `Context`) — cabeçalho com logo+nome+contato e título/nº/status à direita, bloco do
          cliente, descrição, tabela de itens (desc × qtd × unit = subtotal), desconto condicional, total
          em caixa escura destacada, observações, rodapé e marca d'água rotacionada ~8% quando
          `watermark=true`. iOS: **placeholder** que lança `OsPdfNotSupportedException` (TODO
          `UIGraphicsPDFRenderer` em host macOS).
        - Funções públicas: `generateOsPdfBytes(data)`, `generateAndShareOsPdf(data, fileName?,
          shareTitle?, shareHandler?)` (integra o `ShareHandler` existente — `shareFile` PDF) e
          `defaultOsPdfFileName(data)`. Exceção `OsPdfNotSupportedException` para fallback no iOS.
        - Reusa `formatCurrencyBRL` (core/format) via helper interno `OsPdfFormat` (parse string-decimal
          tolerante → BRL). Testes `OsPdfTest` (11 casos commonTest, verdes). Paridade visual com o PDF
          da weblib (GAP-MOS-W-04). **Pendência iOS:** render nativo em host macOS (herda item de
          prioridade alta). Telas: A9 (Detalhe), A10 (Pré-visualização/Compartilhar).
- [ ] **GAP-MOS-M-01 — `AppFab` (FloatingActionButton themável)** — Baixa. FAB padronizado pelo tema
      (cores via `MaterialTheme.colorScheme`, ícone + `contentDescription`) para a ação "Nova OS" na Home
      e atalhos. A kmplib não expõe um FAB no catálogo. Contorno no MVP: `FloatingActionButton` Material 3
      local. Candidato a `ui/components`. Telas: A5 (Home), A6 (Criar OS).

> **Reuso de gaps já no backlog (NÃO recriar) — citados no design do MinhaOS:**
> **GAP-CR-04 (`PaywallScreen`)** → A16 Paywall — modelo freemium **definido** (5 OS/mês + marca d'água;
> Pro R$24,90/mês · R$249/ano), **2º+ consumidor confirmado, reforça promoção**;
> **GAP-CR-05 (`SettingsRow`/`ListItem`)** → A15 Perfil/Configurações — +1 consumidor;
> **GAP-SAL-05 / GAP-RF-M-04 (`OnboardingPager`)** → A2 Onboarding — +1 consumidor (contorno: `HorizontalPager`);
> **GAP-09 (single-select em `AppMultiSelect`)** → A14 unidade do serviço (contorno: `SegmentedControl`);
> **GAP-11 (`ClipboardHandler`)** → A9 copiar link público (contorno: `ShareHandler`);
> **UI de upload com progresso** (prioridade alta/média) → A4/A6 upload do logo/fotos (contorno: `LoadingOverlay`).

## Prioridade média
- [ ] **GAP-RF-M-05 — `RemoteConfigService` (wrapper genérico de Firebase Remote Config)** — reportado por
      lib-mobile durante a entrega da 2.15.0 (ReciboFacil). Hoje a kmplib só tem `AdRemoteConfig` (internal,
      acoplado a ads). ReciboFacil precisa de Remote Config para o **limite Free parametrizável** (DP-07:
      "Limite (5) parametrizável via Remote Config"). Candidato a `core/config` (ou `firebase/config`):
      wrapper suspend tipado sobre GitLive `Firebase.remoteConfig` (`getLong/getBoolean/getString` com
      defaults, `fetchAndActivate`, `minimumFetchInterval` configurável). A dependência GitLive
      `firebase-config` **já está no classpath** (ver `library/build.gradle.kts`). **Contorno no MVP do
      ReciboFacil:** usar o SDK Firebase Remote Config direto (GitLive `Firebase.remoteConfig`) no app, ou
      constante local até o wrapper existir. Serve a qualquer app freemium com parâmetros remotos (≥2 apps).
- [x] **GAP-SAL-08 — Agendamento de notificação local DIÁRIA RECORRENTE** — **entregue na 2.17.0**
      (Salmos Onda 3, lembrete diário das telas #2/#10; serve também Doses de Alegria e Call Recorder — ≥2 apps).
      Adicionado ao `NotificationScheduler` (`platform`, expect/actual) o método
      **`scheduleDailyNotification(id: Int, title, body, hour: Int, minute: Int, data, channelId?, isCritical)`**
      que dispara TODO dia no horário local `hour:minute`. `scheduleNotification` (disparo único por `Instant`)
      mantido intacto; `cancelNotification(id)` cancela tanto o único quanto o diário (cobre o toggle de desligar
      o lembrete). **Android:** `AlarmManager.setExactAndAllowWhileIdle` (fallback `setAndAllowWhileIdle` quando
      `canScheduleExactAlarms()==false` em API 31+) com **reagendamento do próximo dia dentro do
      `NotificationReceiver`** após cada disparo; helper público/testável `AndroidNotificationScheduler
      .nextDailyTriggerMillis(hour, minute, now)` calcula o próximo horário (hoje se ainda não passou, senão
      amanhã); extras de recorrência (`EXTRA_DAILY/HOUR/MINUTE/CRITICAL`) propagados no Intent; canais
      default/critical reaproveitados. **iOS:** `UNCalendarNotificationTrigger` com `DateComponents(hour, minute)`
      e `repeats=true` (compilável; valida em host macOS). **Limitação Android (documentada):** a lib NÃO registra
      `BOOT_COMPLETED` receiver — após reiniciar o aparelho o alarme é perdido e só é restaurado quando o app
      reabre; **recomendação ao dev-mobile:** reagendar o lembrete no `onCreate`/abertura do app (idempotente,
      mesmo `id`). Consumidor Android deve declarar `POST_NOTIFICATIONS` (API 33+),
      `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` e registrar `NotificationReceiver` no manifest.
      `:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL; `:kmplib:publishToMavenLocal` BUILD SUCCESSFUL →
      `br.com.codecacto:kmplib:2.17.0` (`kmplib` metadata + `kmplib-android`; klibs iOS pendentes de host macOS).
- [ ] **i18n** — suporte a múltiplos idiomas (hoje strings pt-BR/parametrizadas).
- [ ] **Deep-link router** — parsing centralizado de payloads de push/deep links (apps fazem manual).
- [ ] **UI de upload com progresso (frontend visual)** — `UploadProgressBar`/`UploadCard` sobre o
      `UiResource` agora disponível; complementa o item de upload da prioridade alta (ver GAP-06).

## Prioridade baixa
### Exiba — contornáveis no MVP
- [x] **GAP-03 — `FilterChipRow` / `ChipGroup`** — entregue na 2.7.0 (ver Concluído). Chips de
      filtro single-choice scrolláveis para listas filtráveis.
- [x] **GAP-01 — `SegmentedControl` / Tabs inline** — entregue na 2.5.0 (ver Concluído).
- [ ] **GAP-10 — Lista reordenável drag-to-reorder** — componente de lista com drag para reordenar
      itens (ex.: fila de próximos contratantes).
- [ ] **GAP-09 — Modo single-select em `AppMultiSelect`** — `AppMultiSelect` só faz multi por
      construção. Adicionar modo `singleSelect=true` ou criar `AppSingleSelect` com busca.
- [ ] **GAP-11 — `ClipboardHandler` expect/actual** — helper `copyToClipboard(text: String)` para
      copiar código de convite. Contorno atual: usar `ShareHandler`.

> **GAP-05 — descartado (não é gap):** `AppTopBar` e `BackTopBar` já expõem
> `actions: @Composable RowScope.()->Unit`. Não registrar.

## Candidatos (validar via /lib-audit, cruzar com KMPLIB_REUSE_ANALYSIS.md)
- [ ] [item] — onde aparece — esforço estimado

## Concluído
- [x] **2.22.0 — `AdminApiEntitlementRepository`: modo user-auth (`/me`) com Firebase ID token**
      (Migração assinatura Fase 1, origem Super 8 + LocAki). Até a 2.21.0 o repositório só montava o
      prefixo fixo `{base}/v1/{slug}` (rotas service-token). Apps que leem o **próprio** entitlement
      precisam da árvore Firebase-authed `{base}/v1/projects/{slug}/me/...`. Mudança **aditiva,
      retrocompatível**:
      - Novo parâmetro `pathPrefix: String? = null` no construtor. `null` → prefixo legado
        `"$base/v1/$projectSlug"` (service-token, inalterado). Informado → `"$base$pathPrefix"`
        (ex.: `"/v1/projects/super8/me"`).
      - Factory `AdminApiEntitlementRepository.forUserAuth(httpClient, baseUrl, projectSlug,
        tokenProvider, cacheTtlMillis)` que já monta `pathPrefix = "/v1/projects/$projectSlug/me"`;
        `tokenProvider` deve devolver o **Firebase ID token** do usuário logado.
      - As 3 leituras (`/entitlement`, `/usage/{feature}`, `/plans`), desserialização, cache 60s e a
        regra "nunca conceder offline" são idênticas nos dois modos. `EntitlementController` inalterado.
      - Testes em `commonTest` cobrindo `pathPrefix` e o factory `/me` + envio do Bearer.
      - **Próximo (Fase 2):** migrar Super 8 e LocAki para instanciar via `forUserAuth(...)`.
- [x] **2.21.0 — Compra CONSUMÍVEL / pay-per-action no módulo de purchase** (Onda 4c, origem Meu Advogado).
      Até a 2.20.0 o módulo `monetization/purchase` só suportava assinatura/entitlement (`purchase()` lê
      `entitlements.active`). Meu Advogado cobra **por AÇÃO** via loja (IAP não-renovável,
      `NON_RENEWING_PURCHASE`): após a compra precisa do **transactionId/productId** da transação da loja
      para enviar à admin-api, que libera AQUELA solicitação no Firestore — `entitlements.active` NÃO
      contém o produto. APIs novas (aditivas, **não quebram** `purchase()`/`subscriptionState`/`isPremium`):
      - **`ConsumablePurchaseResult`** (sealed, `monetization/purchase/PurchaseState.kt`):
        `Success(transactionId: String, productId: String, store: String)` (store="play_store"|"app_store"),
        `Error(message: String, code: PurchaseErrorCode)`, `Cancelled`.
      - **`PurchaseRepository.purchaseConsumable(productId): ConsumablePurchaseResult`** (interface) + impl
        `RevenueCatPurchaseRepository`: reusa o padrão de `purchase()` (resolve `cachedProducts`,
        `suspendCancellableCoroutine`, `mapErrorCode`, trata `userCancelled`); no `onSuccess = {
        storeTransaction, _ -> }` lê `storeTransaction.transactionId` (nullable) e
        `storeTransaction.productIds.firstOrNull()` (nomes reais confirmados no jar `purchases-kmp-models
        2.2.13+17.23.0`). NÃO altera `_subscriptionState`. transactionId nulo/branco →
        `Error("transacao sem id", UNKNOWN)`.
      - **`expect/actual fun currentStore(): String`** (novo, em `PurchaseInitializer.kt`/.android/.ios):
        Android="play_store", iOS="app_store" (a `StoreTransaction` do SDK não expõe a store; segue o
        padrão android/ios do `PurchaseInitializer`).
      - **`PurchaseManager.purchaseConsumable(productId)`** (internal, delega ao repository; null →
        `Error("purchase nao inicializado", UNKNOWN)`).
      - **`MonetizationManager.purchaseConsumable(productId): ConsumablePurchaseResult`** (público — ponto
        de entrada do app, mesmo estilo dos métodos existentes).
      - Testes: `monetization/purchase/ConsumablePurchaseResultTest` (4 casos de shape, commonTest). O
        `RevenueCatPurchaseRepository` é `internal` e depende do SDK RevenueCat (não testável em commonTest
        sem fake do SDK).
      - **NÃO publicado pelo agente** (sandbox não builda KMP); o loop principal roda `:kmplib:
        publishToMavenLocal`. iOS: criado o `actual currentStore()` no `.ios.kt` (sem `expect` órfão);
        klibs iOS pendentes de host macOS. **Fase 2 (dev-mobile):** consumir no Meu Advogado — chamar
        `MonetizationManager.purchaseConsumable(productId)` no fluxo de cobrança por solicitação e enviar
        `Success.transactionId`/`productId`/`store` à admin-api para validação e liberação.
- [x] **2.20.0 — `monetization/entitlement`: padrão freemium-com-limite (paywall + UsageMeter).**
      Implementa o doc `03-monetizacao-spec.md` §4. Quota é **server-side** (admin-api/backlib-quota é a
      fonte de verdade); o cliente só LÊ/EXIBE "X de Y" e abre paywall — NUNCA decide/incrementa limite.
      **Reusa** `PurchaseManager`/RevenueCat (compra) e `core/network` (`ApiResult`/`handleApiCall`);
      não recria billing. APIs novas (todas em `monetization/entitlement/`, exceto UI):
      - Modelos `@Serializable`: `Entitlement` (`hasFeature`/`isFree`/`FREE`), `UsageSnapshot`
        (`remaining`/`isExhausted`/`fraction`/`isUnlimited`, limite -1 = ilimitado), `Plan` (preço como
        string decimal canônica, nunca Double).
      - 402/429 → Paywall: `QuotaExceeded` + `ResponseException.quotaExceededOrNull()` +
        `parseQuotaExceeded(body)` (tolerante) + `toUsageSnapshot()`.
      - Leitura: `interface EntitlementRepository`, impl `AdminApiEntitlementRepository(httpClient,
        baseUrl, projectSlug, authToken?=null, tokenProvider?, cacheTtlMillis=60_000)` — Ktor core puro
        (`bodyAsText` + kotlinx-json, sem exigir ContentNegotiation no consumidor; `expectSuccess=true`
        por request para que 4xx/5xx virem `ApiResult.Error` com o status correto). Cache curto em
        memória só p/ leitura degradada — **nunca concede cota offline** (erro não é cacheado).
        Rotas: `GET {baseUrl}/v1/{slug}/entitlement|usage/{feature}|plans`; header `Bearer` quando token.
      - MVI: `EntitlementState` (embute no State da tela) + `EntitlementController(repository)` (reducer
        não-ViewModel: `refresh`/`refreshUsage`/`plans(cache)`/`purchase(plan)` via
        `PurchaseManager.repository?.purchase(storeProductId)`/`restore`; usa `MonetizationManager.isPremium`).
      - Offline/UX: `LocalUsageCounter(prefs, projectSlug)` via `AppPreferences` — só UX otimista.
      - UI: `ui/components/UsageMeter` + `UsageBadge` (cor warning/error perto do limite via `AppColors`,
        "Ilimitado" quando isUnlimited; sem cor hardcoded) e `ui/screens/paywall/PaywallScreen` stateless
        (`PaywallState`/`PaywallAction`/`PaywallTexts` pt-BR; cards com preço via `Money.formatBRL` p/ BRL;
        responsivo via `BoxWithConstraints`).
      - Testes (commonTest, **verdes** em Android — 28 casos): `QuotaExceededTest`, `UsageSnapshotTest`,
        `EntitlementStateTest`, `AdminApiEntitlementRepositoryTest` (MockEngine 200/402/500 + rota + header).
      - **Limitação de build:** publicado em mavenLocal apenas como `kmplib` (metadata) + `kmplib-android`.
        Targets iOS estão desabilitados no servidor Linux (`kotlin.native.ignoreDisabledTargets`); como o
        módulo é 100% commonMain (sem expect/actual), basta republicar de um host macOS p/ completar iOS.
      - 2º consumidor do `PaywallScreen` (GAP-CR-04): destrava Call Recorder + ReciboFacil + MinhaOS.
- [x] **2.19.0 — Onda 2 do MinhaOS: (L2-M1) `core/money/Money` + (L2-M2) `FirestoreService.runTransaction`.**
      - **L2-M1 — `Money` promovido para a kmplib** (novo módulo `core/money/Money.kt`, commonMain puro).
        Primitiva de **cálculo exato de dinheiro em centavos** (`Long`) com string decimal canônica
        `"123.45"` ponta a ponta — NUNCA `Double` (plano-técnico §5). Origem: `MinhaOS/composeApp/.../core/
        util/Money.kt` (conceito duplicado no web). API genérica, **sem regra de negócio** (o gate de limite
        grátis do app NÃO entrou): `Money.ZERO`, `toCents`/`fromCents`/`normalize`, `subtotal(unitPrice,qty)`,
        `sum(values)` (NOVO), `total(subtotals,discount)` (piso 0), `balance(base,deduction)` (NOVO, piso 0),
        `fromDigits`/`toDigits` (máscaras), `formatBRL`. Distinto de `core/format/formatCurrencyBRL` (`Double`,
        só exibição) — Money é a primitiva de CÁLCULO. Testes `core/money/MoneyTest` (27 casos commonTest,
        **verdes**: ida-e-volta centavos, truncamento da 3ª casa, piso 0, qtd×preço, soma, saldo, BRL/negativo).
        Serve ≥2 consumidores (MinhaOS + web com a mesma convenção).
      - **L2-M2 — `FirestoreService.runTransaction`** (transação atômica idiomática sobre GitLive
        `FirebaseFirestore.runTransaction`, sem expect/actual — a API GitLive é commonMain). Novo
        `suspend fun <R> runTransaction(block: suspend TransactionScope.(TransactionScope) -> R): Result<R>`
        + classe `TransactionScope` (espelha `BatchScope`): leituras `get<T>(coll,id,deserializer): T?` /
        `exists(coll,id): Boolean` (devem vir ANTES das escritas, regra do Firestore) e escritas
        `set<T>`/`set(map)`/`update(map, aceita dotted-paths)`/`delete`. Exceção lançada no bloco propaga como
        `Result.failure` (ex.: gate de limite). **Objetivo:** permitir ao app fazer `reserveNextOrderNumber`
        100% atômico (read da conta + gate de limite + incremento de `osSequence`/`usage.osCount` na MESMA
        transação) — hoje é read-modify-write não-atômico. **Contrato atual do `FirestoreService` intacto**
        (aditivo: `batch`, CRUD, queries inalterados).
      - `:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL; `:kmplib:testDebugUnitTest --tests
        core.money.*` **27/27 verdes**; `:kmplib:publishToMavenLocal` BUILD SUCCESSFUL →
        `br.com.codecacto:kmplib:2.19.0` (`kmplib` metadata + `kmplib-android`; klibs iOS pendentes de host
        macOS — **L2-M1 é commonMain puro** e **L2-M2 usa só API commonMain do GitLive**, devem compilar em
        iOS sem mudança). **Migração MinhaOS** (consumo via `includeBuild`, bump automático): (1) `Money.kt`
        do app passa a delegar para `br.com.codecacto.kmplib.core.money.Money` (ou ser removido, ajustando
        imports nos call-sites de `features/os`, `features/serviceitem`); (2) `FirestoreAccountRepository
        .reserveNextOrderNumber` reescrito com `firestore.runTransaction { tx -> ... }` (read da conta +
        gate `MonetizationConfig.freeOsPerMonth` + update de `osSequence`/`usage` atômico). Ver handoff.
- [x] **2.18.0 — Paridade do PDF do recibo: negrito inline na frase do corpo + baseline iOS↔Android
      unificado** (tarefa paralela da Onda 1 do ReciboFacil; paridade com a weblib `ReciboPdfDocument.tsx`).
      Novo arquivo compartilhado `pdf/ReciboPdfLayout.kt` (commonMain) centraliza duas regras antes divergentes:
      - **Composição da frase (§5) em trechos regular/bold** — `reciboBodySegments(data): List<ReciboTextSegment>`
        é a fonte única de verdade (espelha a weblib: `{pagador}`/`{valor}`/`{valor por extenso}`/`{descrição}`
        bold; parênteses regular; "Recebi"/"Recebemos" por PF/PJ). `reciboBodyWords(segments): List<ReciboWord>`
        achata em palavras preservando bold e **pontuação colada** (`(cento`, `reais)`, `violão.` sem espaço
        espúrio) para wrap por palavra. Ambos os renderers (Android `Canvas`, iOS `NSString`) desenham palavra a
        palavra com a fonte regular/bold do trecho — antes o Android desenhava a frase inteira em regular.
      - **Conversão baseline/coordenadas unificada** — `mmToPt`/`MM_TO_PT` (mm→pt) e `textTopFromBaseline(
        baselineY, ascent)`. A spec dá todas as posições como **baseline**; o Android `drawText` já usa baseline,
        mas o iOS `drawAtPoint` ancora o **topo**. Antes o iOS compensava com offsets *ad hoc* divergentes
        (ex.: "RECIBO" em y=24mm no iOS vs baseline 30mm no Android). Agora **ambos passam as MESMAS baselines da
        spec** e o iOS converte internamente via `ascender`/`capHeight` da `UIFont` — eliminando a divergência.
      API pública nova (aditiva, **não quebra consumidores**): `ReciboTextSegment(text, bold)`,
      `ReciboWord(text, bold, spaceBefore)`, `reciboBodySegments`, `reciboBodyWords`, `mmToPt`, `MM_TO_PT`,
      `textTopFromBaseline`. Testes `ReciboPdfLayoutTest` (8 casos commonTest, verdes — composição/bold/
      reconstrução/conversões). `:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL; `:kmplib:testDebugUnitTest
      --tests pdf.*` verde (ReciboPdfLayoutTest 8 + ReciboPdfDataTest 4 + OsPdfTest); `:kmplib:publishToMavenLocal`
      BUILD SUCCESSFUL → `br.com.codecacto:kmplib:2.18.0` (`kmplib` metadata + `kmplib-android`; klibs iOS
      pendentes de host macOS — o iOS `ReciboPdf.ios.kt` foi atualizado para o helper compartilhado e valida em
      macOS). **Impacto ReciboFacil:** bumpar a dependência kmplib 2.15.0/2.17.0 → **2.18.0** quando for consumir
      o PDF do recibo (Onda 2); a API `generateReciboPdf(data, watermark)` é a mesma.
- [x] **2.17.0 — GAP-SAL-08 (notificação local DIÁRIA RECORRENTE)** para Salmos Onda 3 (lembrete diário
      #2/#10; serve também Doses de Alegria e Call Recorder — ≥2 apps). Adicionado ao `NotificationScheduler`
      (`platform`, expect/actual) o método `scheduleDailyNotification(id, title, body, hour, minute, data,
      channelId?, isCritical)` que dispara todo dia no horário local `hour:minute`. `scheduleNotification`
      (disparo único por `Instant`) mantido intacto; `cancelNotification(id)` cobre único e diário.
      **Android:** `AlarmManager.setExactAndAllowWhileIdle` + reagendamento do próximo dia no
      `NotificationReceiver` após cada disparo (helper `AndroidNotificationScheduler.nextDailyTriggerMillis`).
      **iOS:** `UNCalendarNotificationTrigger(DateComponents(hour,minute), repeats=true)` (compilável; valida
      em macOS). **Limitação:** sem `BOOT_COMPLETED` na lib → reagendar na abertura do app após reboot.
      `:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL; `:kmplib:publishToMavenLocal` BUILD SUCCESSFUL →
      `br.com.codecacto:kmplib:2.17.0` (`kmplib` metadata + `kmplib-android`; klibs iOS pendentes de host macOS).
- [x] **2.16.0 — GAP-SAL-03 (`ShareCard` + captura para imagem)** para Salmos Onda 3 (tela #9, motor de
      viralização). Novo módulo `ui/share/`: composable `ShareCard` (preview do card devocional, estética
      serena por flavor via `ShareCardStyle.fromColorScheme`) + captura **commonMain pura**
      `renderShareCardToPng(spec, style, textMeasurer): ByteArray` (desenha o card off-screen via
      `CanvasDrawScope`+`TextMeasurer` e codifica com `encodeBitmapToPng` — sem expect/actual, funciona em
      Android e iOS). Modelo `ShareCardSpec` + `ShareCardFormat` (9:16/1:1) + `ShareCardBackground`. Atalhos
      `shareCardImage`/`shareCardText` reusando o `ShareHandler` existente. Testes `ShareCardTest` (7 casos
      JVM, verdes). `:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL; `:kmplib:publishToMavenLocal` BUILD
      SUCCESSFUL → `br.com.codecacto:kmplib:2.16.0` (`kmplib` metadata + `kmplib-android`; klibs iOS pendentes
      de host macOS). **GAP-SAL-06 (`AppTimePicker`) reconfirmado como JÁ entregue na 2.12.0** — nenhuma ação.
      **Reportado ao dev-mobile:** o `NotificationScheduler` (`platform`, já na lib) agenda 1 disparo por
      `Instant`, mas **NÃO tem recorrência diária nativa** — o re-agendamento diário do lembrete (#2/#10) é
      wiring do app (Android-only). Ver "Lacunas conhecidas".
- [x] **2.15.0 — GAP-RF-M-01 (`valorPorExtenso`) + GAP-RF-M-02 (`SignaturePad`) + GAP-RF-M-03 (PDF do
      recibo via `generateReciboPdf`)** — os 3 bloqueadores da Onda 2 do ReciboFacil.
      - `core/format/ValorPorExtenso.kt`: `valorPorExtenso(centavos: Long): String` (commonMain puro),
        12 regras do contrato congelado, rejeita negativo. `ValorPorExtensoTest` com ~80 casos §2/§3 +
        negativos, **100% verde** (contrato de paridade com a weblib GAP-RF-W-02).
      - novo módulo `signature/SignaturePad.kt`: `SignaturePadState` (`rememberSignaturePadState()`,
        `isEmpty`/`clear()`/`undo()`/`toPngBytes(): ByteArray?` PNG transparente) + `SignaturePad(...)`
        composable de captura por canvas. Reusa `encodeBitmapToPng` (`platform`). `SignaturePadStateTest`
        (7 casos verdes).
      - `pdf/ReciboPdfData.kt` + `pdf/ReciboPdf.{android,ios}.kt`: `expect/actual generateReciboPdf(data,
        watermark)` seguindo `recibo-layout-spec.md` à risca (A4, mm→pt, cores hex, blocos, assinaturas
        ancoradas, marca d'água -45°/12%). Android valida; iOS (`UIGraphicsPDFRenderer`) escrito p/ host
        macOS. `ReciboPdfDataTest` (4 casos verdes). **Distinto** do `OsPdfData` genérico da 2.14.0.
      - Novo gap registrado: **GAP-RF-M-05 (`RemoteConfigService`)** para o limite Free parametrizável
        (DP-07) — contorno: SDK Firebase Remote Config direto.
      - `:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL; novos testes verdes; `:kmplib:publishToMavenLocal`
        BUILD SUCCESSFUL → `br.com.codecacto:kmplib:2.15.0` (`kmplib` metadata + `kmplib-android`; klibs iOS
        pendentes de host macOS). **Pendências:** fonte Inter não embutida (injetável via
        `ReciboPdfData.inter*Bytes`); negrito inline por trecho na frase do corpo (refinamento visual);
        validação iOS de `SignaturePad`/`generateReciboPdf` em build nativo macOS pelo fundador.
- [x] **2.14.0 — GAP-MOS-M-02 (Helper de PDF da OS) — fecha também GAP-ME-04 e GAP-RF-M-03**
      para MinhaOS Onda 1 (bloqueador A9/A10), Meu Estacionamento (recibo/fechamento) e ReciboFacil Onda 2.
      Novo módulo `pdf/`. commonMain: modelo serializável genérico `OsPdfData` (+ `OsPdfCompany` com
      `logoBytes`, `OsPdfClient`, `OsPdfItem`), `interface OsPdfGenerator`, `expect createOsPdfGenerator()`,
      funções públicas `generateOsPdfBytes` / `generateAndShareOsPdf` (integra `ShareHandler.shareFile`) /
      `defaultOsPdfFileName`, e `OsPdfNotSupportedException`. Helper interno `OsPdfFormat` (parse de
      string-decimal → BRL, reusa `formatCurrencyBRL`). Android: `AndroidOsPdfGenerator` via
      `android.graphics.pdf.PdfDocument` (A4 72dpi, sem novas dependências, sem `Context`) — cabeçalho
      logo+nome+contato, título/nº/status, cliente, descrição, tabela de itens, desconto condicional,
      total destacado em caixa, observações, rodapé e marca d'água rotacionada condicional. iOS:
      **placeholder** (`OsPdfNotSupportedException`; TODO `UIGraphicsPDFRenderer` em host macOS). Testes
      `OsPdfTest` (11 casos commonTest, verdes). `:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL;
      `:kmplib:testDebugUnitTest` (OsPdfTest) verde; `:kmplib:publishToMavenLocal` BUILD SUCCESSFUL →
      `br.com.codecacto:kmplib:2.14.0` (`kmplib` metadata + `kmplib-android`; klibs iOS pendentes de host
      macOS — herdam o item de prioridade alta).
- [x] **2.13.0 — GAP-SAL-02 (`ThemeChipGrid` + `ChipItem`)** para Salmos Onda 2 (tela #6 Busca por Tema).
      `ui/components/ThemeChipGrid.kt` (commonMain puro): grade `FlowRow` multi-linha de chips clicáveis,
      responsiva (3 col. compacto / 5 col. largo via `BoxWithConstraints`, `wideThreshold=600.dp`). Tipo
      público `ChipItem(id, label, leadingIcon?)`, genérico (não acoplado a domínio). Dois modos: ação
      (`AssistChip`, toque→`onChipClick(id)` para navegar — caso da #6) e selecionável (`FilterChip` com
      `selectedIds`, para reuso multi-select futuro). Cores do tema (sem hardcode), `contentDescription` por
      chip, 2 previews. **Não substitui** `FilterChipRow` (LazyRow single-choice de filtro). Sem novas
      dependências. Testes `ThemeChipGridTest` (4 casos JVM, verdes). `:kmplib:compileDebugKotlinAndroid`
      BUILD SUCCESSFUL; `:kmplib:publishToMavenLocal` BUILD SUCCESSFUL → `br.com.codecacto:kmplib:2.13.0`
      (`kmplib` metadata + `kmplib-android`; klibs iOS pendentes de host macOS).
- [x] **2.12.0 — GAP-SAL-06/GAP-DA-01 (`AppTimePicker`) + GAP-DA-02 (`AppSwitch`)** para Salmos e
      Doses de Alegria (2 apps pedindo cada componente).
      - `ui/components/AppTimePicker.kt`: `AppTimePicker(hour, minute, onTimeSelected, modifier, title,
        confirmLabel, dismissLabel, onDismiss)` — par do `AppDatePicker`. `TimePicker` Material 3
        (`rememberTimePickerState`, 24h, `coerceIn` no init) num `AlertDialog` estilizado por
        `MaterialTheme.colorScheme`. Dialog controlado pelo chamador. 1 preview.
      - `ui/components/AppSwitch.kt`: `AppSwitch(checked, onCheckedChange, modifier, enabled)` — par do
        `AppCheckbox`. Wrapper fino sobre `Switch` Material 3 com cores do tema (sem hardcode), 2 previews.
      - Sem novas dependências (só Material 3, já no classpath). `:kmplib:compileDebugKotlinAndroid`
        BUILD SUCCESSFUL; `:kmplib:publishToMavenLocal` BUILD SUCCESSFUL → `br.com.codecacto:kmplib:2.12.0`
        (`kmplib` metadata + `kmplib-android`; klibs iOS pendentes de host macOS — ambos componentes são
        commonMain puro, devem compilar em iOS sem mudança).
- [x] **2.11.0 — `IAuthRepository` (testabilidade de ViewModels sem Firebase)** — gap transversal a todos
      os apps (bloqueava `LgpdViewModelTest`/`SplashViewModelTest`/`OperadoresViewModelTest` no Meu
      Estacionamento, que tinham contornos por `AuthRepository` ser classe `final` concreta). Extraída a
      interface `IAuthRepository` em `firebase/auth/IAuthRepository.kt` (commonMain) com todos os membros
      públicos (`currentUser`/`isLoggedIn` Flows, `currentUserSync`/`isLoggedInSync`, `signInWithEmail/
      Google/Apple`, `signUpWithEmail`, `sendPasswordResetEmail`, `updateProfile`, `changePassword`,
      `deleteAccount`, `signOut`, `sendEmailVerification`, `getIdToken`). `AuthRepository : IAuthRepository`
      (impl Firebase inalterada; defaults movidos p/ a interface, `override` sem default). Sem novas
      dependências; sem quebra de API (a classe concreta continua válida). Consumo recomendado: ViewModels
      recebem `IAuthRepository`; Koin binda `single { AuthRepository() } bind IAuthRepository::class`.
      `:kmplib:publishToMavenLocal` BUILD SUCCESSFUL → `br.com.codecacto:kmplib:2.11.0`. **Migração feita
      no Meu Estacionamento:** 13 ViewModels + `App.kt` + `AcessoNegadoRoute` migrados p/ a interface;
      `assembleDebug` + `testDebugUnitTest` verdes. **Plano p/ demais apps:** trocar tipo de recepção de
      `AuthRepository`→`IAuthRepository` nos ViewModels e ajustar o bind do Koin (1 linha).
- [x] **2.10.0 — GAP-SAL-01 (`ReaderView`)** para Salmos Onda 1 (bloqueador da tela-coração de leitura, #4).
      Novo módulo `ui/reader/` (commonMain puro): `ReaderView(blocks, title?, subtitle?, footer?, fontSize,
      showNumbers, bodyFontFamily?, titleFontFamily?, maxContentWidth, contentPadding, listState)` + tipos
      públicos `ReaderBlock`, enum `ReaderFontSize` e helper `toReaderBlocks()`. Leitor de texto longo genérico
      (não acoplado a domínio) com fonte ajustável aplicada só ao corpo, seleção de trecho (`SelectionContainer`),
      numeração leading discreta togglável, medida de linha limitada no modo largo e família serifada injetável;
      tema claro/escuro via `MaterialTheme.colorScheme`. Sem novas dependências. Testes `ReaderViewTest` (8 casos,
      verdes). `:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL; publicado em mavenLocal como
      `br.com.codecacto:kmplib:2.10.0` (`kmplib` metadata + `kmplib-android`; iOS herda host macOS).
- [x] **2.8.0 — GAP-CR-01 (`AudioPlayer` + `AudioPlayerBar`) + GAP-CR-02 (`PermissionManager`)**
      para Call Recorder (bloqueadores do MVP — telas de reprodução e permissões).
      Novos módulos `media/` e `platform/permission/` (expect/actual). Android compila
      (`:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL) sem novas dependências (MediaPlayer +
      ActivityCompat já disponíveis). Testes `AudioPlayerBarTest` (4 casos de `formatPlayerTime`,
      verdes). Holders `AudioPlayerHolder` e `PermissionHostHolder` adicionados às exclusões do Kover.
      Publicado em mavenLocal como `br.com.codecacto:kmplib:2.8.0` (`kmplib` metadata + `kmplib-android`;
      klibs iOS pendentes de host macOS — herdam o item de prioridade alta). **Pendência iOS:** validar
      `AVAudioPlayer`/`AVAudioSession`/`AVCaptureDevice`/`UNUserNotificationCenter` em build nativo macOS.
- [x] **2.7.0 — GAP-03 (`FilterChipRow`)** para Meu Estacionamento Onda 2 (filtro de mensalistas por
      status: Todos / Ativo / A Vencer / Vencido / Inativo). Componente commonMain puro (sem
      expect/actual) em `ui/components/FilterChipRow.kt`:
      `FilterChipRow(options, selectedIndex, onOptionSelected, modifier)` — `LazyRow` scrollável de
      `FilterChip` (Material 3), seleção simples (`selected = index == selectedIndex`), `8.dp` de
      espaçamento, cores do tema (sem hardcode), `contentDescription` por chip e 2 previews. Sem
      novas dependências. `:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL; publicado em
      mavenLocal como `br.com.codecacto:kmplib:2.7.0` (`kmplib` metadata + `kmplib-android`; klibs
      iOS pendentes de host macOS — herdam o item de prioridade alta).
- [x] **2.6.0 — GAP-ME-01 (`CameraView` + `PlateOcrAnalyzer`)** para Meu Estacionamento Onda 1.
      Novo módulo `camera/` (expect/actual): `PlateOcrAnalyzer.analyzePlate(imageBytes): String?`,
      `@Composable CameraView(onPlateCaptured, modifier)` e helper commonMain `extractPlate(ocrText)`.
      OCR on-device: ML Kit (`com.google.mlkit:text-recognition:16.0.1`) + CameraX
      (`androidx.camera:camera-{camera2,lifecycle,view}:1.4.2`) no Android; **iOS placeholder**
      (PlateOcrAnalyzer retorna `null`; CameraView mostra Box estático — nunca lança, não chama
      `onPlateCaptured`). Reusa `normalizePlate`/`isValidPlate` do módulo `mask`. Testes:
      `PlateTextExtractorTest` (7 casos, verde). `:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL;
      publicado em mavenLocal como `br.com.codecacto:kmplib:2.6.0` (`kmplib` metadata + `kmplib-android`;
      iOS klibs pendentes de host macOS). **Pendência iOS:** Apple Vision (`VNRecognizeTextRequest`) +
      `AVCaptureSession` em macOS.
- [x] **2.5.0 — GAP-ME-03 (`AppCheckbox`) + GAP-01 (`SegmentedControl`) + GAP-ME-02 (`PlateMask`)**
      para Meu Estacionamento Ondas 0/1.
      - `ui/components/AppCheckbox.kt`: `AppCheckbox(checked, onCheckedChange, label, modifier, enabled)`
        — linha clicável acessível (`Role.Checkbox`), cores de `MaterialTheme.colorScheme`, 2 previews.
      - `ui/components/SegmentedControl.kt`: `SegmentedControl(options, selectedIndex, onOptionSelected,
        modifier)` sobre `SingleChoiceSegmentedButtonRow` (Material 3), `contentDescription` por segmento,
        2 previews.
      - `mask/PlateMask.kt`: `PlateVisualTransformation`, `normalizePlate(raw): String`,
        `isValidPlate(plate): Boolean` (Mercosul `AAA0A00` + antiga `AAA0000`). Testes: `PlateMaskTest`
        (17 casos cobrindo normalização e validação).
      - Build: adicionada dependência do próprio Compose MP `compose.components.uiToolingPreview` para
        habilitar `@Preview` em commonMain (sem deps de terceiros). Android compila; `testDebugUnitTest`
        verde. Publicado em mavenLocal como `br.com.codecacto:kmplib:2.5.0`.
- [x] **2.4.0 — GAP-02 (MapView/MapMarker) + GAP-04 (LocationProvider)** para Exiba Onda 1.
      Novos módulos `map/` e `location/`. Deps Android adicionadas: `maps-compose:6.7.2`,
      `play-services-maps:19.2.0`, `play-services-location:21.3.0`. Android compila (BUILD SUCCESSFUL);
      iOS do `MapView` é placeholder (TODO Google Maps iOS SDK em macOS); iOS do `LocationProvider`
      implementado via `CLLocationManager` (compila em metadata; build final exige macOS).
      **Config do app consumidor (Android):** declarar `com.google.android.geo.API_KEY` no
      `AndroidManifest.xml` (meta-data) e a permissão `ACCESS_FINE_LOCATION`. iOS: pod `GoogleMaps`
      + `GMSServices.provideAPIKey` + `NSLocationWhenInUseUsageDescription`.
      Side-fix de teste: `FakeCrashlyticsService.userId` → `capturedUserId` (clash de assinatura JVM
      que impedia `:kmplib:testDebugUnitTest` de compilar).
- [x] **2.3.1 — fix de publicação iOS (naming dos artefatos por-target)**: módulo Gradle renomeado
      de `:library` para `:kmplib` (mapeado à pasta `library/` via `project(":kmplib").projectDir`).
      O Kotlin Multiplatform deriva o artifactId dos targets Native do nome do projeto Gradle; com
      `:library` os artefatos iOS saíam como `library-iosarm64` etc., quebrando a resolução iOS de
      qualquer consumidor (que espera `kmplib-iosarm64`). Agora o metadata referencia `kmplib-ios*`,
      restaurando o comportamento da 1.0.0. **Pendência:** publicar os klibs iOS de um host macOS
      (ver prioridade alta).
- [x] (histórico em KMPLIB_REUSE_ANALYSIS.md: formatters, file picker, MVI base, ApiResult+Connectivity, push, feedback)
- [x] **2.3.0 — paginação + recurso assíncrono de UI + componentes de UE**:
      `PaginatedResponse<T>` (core/network), `PaginatedState<T>` + `UiResource<T>` + `asyncLoad`
      (ui/mvi), e componentes `SkeletonBox`, `AppDatePicker`, `AppBottomSheet`, `AppMultiSelect`
      (ui/components). Cobertura nova: testes de máscara (CPF/CNPJ/telefone), `PaginatedResponse`,
      `UiResource`.
