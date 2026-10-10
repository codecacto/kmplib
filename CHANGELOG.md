# Changelog — kmplib

> **Pisos por impacto (08/out/2026).** Todo release que CORRIGE defeito visto pelo usuário ou pela loja
> acrescenta a sua entrada em [`docs/pisos.yaml`](docs/pisos.yaml) no mesmo commit desta nota (skill
> `lib-evolution`, passo 6-A). O selo "Revisão da fábrica" só cobra bump de quem está abaixo de um piso
> que o atinge — estar fora da última versão, sozinho, não reprova mais app nenhum.

## 2.285.0 — Comparador de fotos (`PhotoCompare`) e gráfico com faixa de referência (`kmplib-ui`)

**Por quê.** Vitalis, Onda 2 (pacote LM-K02, `GAP-VIT-K02`): o app do médico precisa comparar fotos de
evolução do paciente (antes/depois, linha do tempo de sessões, "mostrar ao paciente" em tela cheia) e
mostrar a evolução de um analito de exame contra a faixa de referência. A weblib já tinha o
`PhotoCompare` (0.237.0); a kmplib não tinha nenhum dos dois, e o `LineChart` não desenhava banda.

**O que entrou (aditivo — nada muda para quem não usa).**
- **`ui.compare` (pacote novo):** `PhotoCompare(sessions, state, modes, showTimeline, aspectRatio,
  maxFrameHeight, fit, showTitle, title, loading, emptyContent, photoSource, timeZone, formatDate,
  onFullScreen, texts)`, `PhotoCompareDialog` (tela cheia, mesmo estado), `CompareTimeline`,
  `PhotoCompareState`/`rememberPhotoCompareState` (modo, par, posição, vista e zoom; salvo na rotação
  sem foto nem URL). Contrato `CompareSession`/`ComparePhoto`/`ComparePair` **`@Serializable` e com os
  nomes da weblib** — o mesmo JSON serve às duas pontas. `CompareMode` `SLIDER`/`SIDE_BY_SIDE` (a
  linha do tempo é o seletor do par, nos dois modos, como na weblib), `CompareFit`.
  - Regras puras espelho de `compare.logic.ts`: `parseCompareDate` (data civil = meia-noite UTC; ISO
    com Z/offset/sem offset no fuso pedido; formato livre recusado), `formatCompareDate` (região do
    aparelho), `comparableSessions`, `defaultComparePair`, `normalizeComparePair`, `nextComparePair`,
    `photoViewKey`/`compareViews`/`photoForView`/`resolveCompareView`, `clampComparePosition`,
    `comparePositionFromPointer`, `compareVisibleShares`, `compareDragIntent`, `compareFrameSize`.
  - **Divisória acessível**: arrasto horizontal em qualquer ponto (rolagem vertical passa adiante),
    `setProgress` + `progressBarRangeInfo` + estado "40% antes, 60% depois" para TalkBack/VoiceOver.
  - **Zoom e pan SINCRONIZADOS** (além da weblib): pinça e duplo toque ampliam as duas fotos juntas
    (`CompareTransform`, `applyCompareTransform`, `toggleCompareZoom`); ampliado, um dedo move a
    imagem e a alça continua arrastável; ação de acessibilidade "Voltar ao tamanho original".
  - Foto privada por `photoSource = { PhotoSource.authenticated(api, it.src, accountId) }` (sem cache de
    disco). `toString` de `ComparePhoto`/`CompareSession` sem `src` nem `description`.
  - Ids Maestro (`PhotoCompareTestTags`): `comparar`, `comparar-modo-deslizar`/`-lado-a-lado`,
    `comparar-divisoria`, `comparar-lado-a-lado`, `comparar-btn-tela-cheia`, `comparar-linha-do-tempo`,
    `comparar-sessao-<id>`, `comparar-vista-<chave>`; tela cheia com `dialogo` e `dialogo-btn-fechar`.
- **`LineChart(…, referenceBand = ReferenceBand(min, max, color))`** nos dois overloads: banda
  translúcida atrás das linhas com bordas tracejadas; lado aberto vai até a borda; a escala Y passa a
  incluir os limites da banda. Default `null` = o desenho de antes.
- **`ReferenceBandChart(points, band, valueFormatter, lineColor, chartHeight, maxXLabels, showLegend,
  texts)`** — par do `ReferenceBandChart` da weblib: `ReferencePoint(label, value, date, status)`
  (eixo X proporcional ao tempo quando todo ponto tem data), `ReferenceStatus(label, tone)` do servidor
  ou derivado da faixa (`classifyAgainstBand` → `BandPosition`), ponto pintado pelo status **com
  leitura e legenda em texto** (a cor nunca sozinha), toque escolhe o ponto (começa no último), leitor
  de tela com resumo + "Próxima medição"/"Medição anterior". `referenceChartXFractions`,
  `nearestReferencePoint`, `ReferenceBandChartTestTags`.
- 4 idiomas (`kmplib_compare_*`, `kmplib_chart_*`).

**Testes.** 42 novos (`CompareLogicTest` 35 — os casos de `compare.logic.test.ts` —, `ReferenceBandChartTest`
7); `LineChartTest` segue verde. `compileKotlinIosArm64` verde (28 tarefas, nenhuma SKIPPED).

**Ação nos apps:** nenhuma. Aditivo: sem aviso, sem piso.

## 2.284.1 — Fastfile iOS: o ARCHIVE também leva `-allowProvisioningUpdates` quando o app assina pelo `match`

Patch, só `ci/fastlane/Fastfile` (nenhum artefato Kotlin mudou). A lane `release` só passava
`-allowProvisioningUpdates` + a chave da API da App Store Connect quando o app **não** tinha perfil no
`match`. Com perfil, o `match` assina só o export; o archive seguia na assinatura automática sem
autorização, e o Xcode caía no perfil curinga `iOS Team Provisioning Profile: *`, que não aceita
capability nenhuma além do básico. Caso: LocAki 1.2.0, 10/out/2026, depois de ligar o Push no App ID —
`Provisioning profile "iOS Team Provisioning Profile: *" doesn't include the aps-environment entitlement`.
Agora a autorização vai sempre no `xcargs`; o `signingStyle: automatic` do export continua só para
quem não tem `match`.

**Ação nos apps:** nenhuma — o Fastfile é importado da `main`.

## 2.284.0 — Vídeo em laço, mudo e toque para pausar (`kmplib-video`)

**Por quê.** App do Personal (L2): o vídeo de demonstração do exercício toca em laço, sem som, e o
aluno toca para pausar. O `VideoPlayerConfig` não tinha laço nem mudo — e mudo "de mentira" (só volume
zero) ainda pausaria a música que a pessoa ouve no treino.

**O que entrou (aditivo, defaults = comportamento anterior).**
- `VideoPlayerConfig.loop` — laço sem emenda. Android `Player.REPEAT_MODE_ONE`; iOS `AVPlayerLooper`
  sobre `AVQueuePlayer` (recomendação da Apple; o item vira modelo, a legenda embutida é reaplicada
  em cada réplica, falha do looper vira `VideoStatus.Error`). Nunca chega a `Ended`.
- `VideoPlayerConfig.startMuted` + `VideoPlayerState.isMuted`/`setMuted(muted)`/`toggleMuted()`.
  Mudo não disputa o áudio: Android volume `0` + `handleAudioFocus = false` (como o feed), com som
  volta o foco; iOS sessão `.ambient` enquanto todos os players de aula estão mudos, `.playback` +
  ativa quando algum tem som.
- `VideoPlayerConfig.soundControl` (default = `startMuted`) — botão de som (`VolumeOff`/`VolumeUp`,
  `VideoPlayerTexts.turnSoundOn`/`turnSoundOff`): na barra dos controles, ou sozinho no canto quando
  `controls = false`. Some em erro.
- `VideoPlayerConfig.tapToTogglePlayback` — toque no quadro pausa/retoma (com controles, pausar os
  traz); clique semântico equivalente para leitor de tela.
- `VideoPlayerTestTags.FRAME` (`video-quadro`) e `.SOUND` (`video-btn-som`).

**Mudança de comportamento no iOS (sem efeito visível):** a sessão de áudio dos players de aula agora
é coordenada por processo e **restaura a categoria anterior** quando o último player é solto (antes
ficava `.playback` para sempre).

**Testes.** `VideoLoopAndSoundTest` (13: defaults, derivação do `soundControl`, decisão do toque e do
lugar do botão, textos, ids). Android `testDebugUnitTest` verde; `compileKotlinIosArm64` +
`compileTestKotlinIosArm64` verdes no servidor (não SKIPPED). Pendente de aparelho: laço sem emenda
e mistura com a música. Sem aviso, sem piso (aditivo).

## 2.283.0 — Estado da permissão de saúde sem abrir diálogo (`kmplib-health`)

**Por quê.** App do Personal (LN18): a tela de integração com Saúde/Health Connect não tinha como
saber se o acesso já tinha sido dado sem chamar `requestPermissions` — que abre o diálogo. E no iOS
um `Boolean` seria mentira: o HealthKit não revela a resposta de LEITURA.

**O que entrou (aditivo).**
- `HealthRepository.permissionStatus(types): HealthPermissionStatus` — `suspend`, **nunca abre
  diálogo**. `HealthPermissionStatus(availability, byType)` com `overall` (pior parte:
  `UNAVAILABLE` > `NOT_GRANTED` > `NOT_REQUESTED` > `NOT_REVEALED` > `GRANTED`), `allGranted`,
  `hasNotRequested`, `get(type)`. Conjunto vazio = `GRANTED`.
- `HealthPermissionState` = `GRANTED` · `NOT_REQUESTED` · **`NOT_REVEALED`** · `NOT_GRANTED` ·
  `UNAVAILABLE`.
  - Android: `PermissionController.getGrantedPermissions()` contra as permissões do tipo
    (`EXERCISE_SESSION` = ler E gravar treino) → `GRANTED`/`NOT_GRANTED` (o Health Connect não
    distingue "nunca pedido" de "negado"); SDK fora → `UNAVAILABLE` + `availability`.
  - iOS: leitura via `getRequestStatusForAuthorization(toShare:read:)` — `.shouldRequest` →
    `NOT_REQUESTED`; respondido/desconhecido → `NOT_REVEALED`. Escrita de treino via
    `authorizationStatus(for: workoutType)` → `GRANTED`/`NOT_GRANTED`/`NOT_REQUESTED`.
    `EXERCISE_SESSION` no iOS chega no máximo a `NOT_REVEALED`.
  - **UI com `NOT_REVEALED`: tratar como "pode ter acesso"** — ler normalmente, "sem dado" com a
    saída para o app Saúde; pedir de novo é inofensivo (a folha não reabre para tipo já respondido).
- Implementação padrão na interface (dublê de app continua compilando): `UNAVAILABLE`/`NOT_REVEALED`,
  nunca um `GRANTED` inventado.

**Testes.** 13 novos em `DefaultHealthRepositoryTest` (Android unit + `compileTestKotlinIosArm64`).
Sem aviso, sem piso (aditivo).

## 2.282.0 — Permissão de Bluetooth no monitor de FC, `toString` sem dado de saúde, treino fora do manifesto (`kmplib-health`)

**Por quê.** App do Personal (MA6, relógio/cinta): a lib mandava o app pedir `BLUETOOTH_SCAN`/
`BLUETOOTH_CONNECT`, mas não oferecia como — o `PermissionManager` do `kmplib-platform` não tem esse
tipo, e cada app teria de escrever o pedido (com o "já pedida?" que o Android não expõe) à mão. E o
manifesto da lib declarava `READ/WRITE_EXERCISE` para todo consumidor, inclusive quem só lê FC — o app
do aluno já tirava as duas com `tools:node="remove"`.

**O que entrou.**
- **Permissão de Bluetooth (aditivo):** `HeartRateMonitor.permissionStatus()` e `requestPermission()`,
  ambos `suspend`, → `BluetoothPermissionStatus` (`GRANTED`/`DENIED`/`PERMANENTLY_DENIED`/
  `NOT_REQUESTED`, o mesmo vocabulário do `PermissionStatus` do platform).
  - Android: `ActivityResultRegistry` + `RequestMultiplePermissions` na Activity de
    `HealthActivityHolder` — `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` (12+) ou `ACCESS_FINE_LOCATION`
    (11-, declarada pelo app); "já pedida" num `SharedPreferences` próprio (o `shouldShowRequestPermissionRationale`
    é `false` antes do 1º pedido e depois da negação definitiva). Permissão não declarada no manifesto
    final = aviso no logcat e status sem diálogo (não vira "negada de vez" falsa).
  - iOS: `permissionStatus()` lê `CBManager.authorization` (sem diálogo); `requestPermission()` cria o
    `CBCentralManager` do monitor — é isso que abre o diálogo — e espera a resposta. Negada/restrita =
    `PERMANENTLY_DENIED`.
  - Concedida depois de um `scan()` que deixou `Unavailable(PERMISSION_DENIED)`: o `state` volta a `Idle`.
  - Os dois métodos têm default na interface (`GRANTED`) — dublê de `HeartRateMonitor` mantido por app
    continua compilando. `SimulatedHeartRateSensor.setPermission(current, afterRequest)` (default
    concedida, como antes).
  - **Não é `AppPermission.BLUETOOTH` no `kmplib-platform`, de propósito:** a App Store recusa (ITMS-90683)
    app cujo binário referencie o CoreBluetooth sem `NSBluetoothAlwaysUsageDescription`, e quase todo app
    leva o platform. KDoc do `AppPermission` aponta para cá.
- **`toString` redigido (higiene):** `HeartRateSummary`, `EnergyReading` (só `source`), `HealthMetric.Value`,
  `HeartRateMeasurement` (contato + contagens), `HeartRateMonitorState.Connected` (`hasBpm`)/`Connecting`
  (`attempt`)/`Lost`, `HeartRateDevice` (sem id/nome) e os internos `HeartRateStats`/`StoredWorkout` — mesmo
  critério do `kmplib-workout` 2.272/2.273. Igualdade continua por valor.
- **Manifesto:** `android.permission.health.READ_EXERCISE` e `WRITE_EXERCISE` SAÍRAM do manifesto da lib.
  Quem grava treino (`HealthDataType.EXERCISE_SESSION`, `writeWorkout`, `hasDeviceWorkoutOverlapping`)
  declara as duas no próprio manifesto. Pedido sem declaração: `requestPermissions` avisa no logcat, pede o
  resto e devolve `false`; `writeWorkout` devolve `PERMISSION_DENIED`. Quem usava `tools:node="remove"` nas
  duas pode apagar a linha (vira no-op). Consumidor único hoje (App do Personal) não grava treino: nada deixa
  de funcionar.
- Testes: `BluetoothPermissionTest` (9), `HealthToStringRedactionTest` (4), `HeartRateMonitorTest` +1; nomes
  de teste sem vírgula (o `commonTest` do iOS não compilava — Kotlin/Native recusa `,` em nome de função).

Aditivo + higiene: sem aviso, sem piso.

## 2.281.0 — `AppInputDialog(confirmEnabled)` + `typedConfirmationMatches` (`kmplib-ui`)

**Por quê.** App do Personal (exclusão de conta, LN22): a regra da casa manda DIGITAR "EXCLUIR" para
excluir a conta, e o wireframe pede o confirmar **desligado até a palavra conferir**. O
`AppInputDialog` não tinha como desligar o botão — a tela só ignorava o toque no `onConfirm`, com o
botão aceso convidando a um toque que não fazia nada.

**O que entrou (aditivo).**
- `AppInputDialog(…, confirmEnabled: Boolean = true)` — `false` liga o `enabled = false` do `Button`
  do Material3 por baixo do `AppButton`: estado visual desabilitado do tema, semântica `Disabled`
  (TalkBack/VoiceOver anunciam "desativado") e `onConfirm` não é chamado. `isLoading` continua
  desligando sozinho; o cancelar não muda. Default = o comportamento de sempre.
- `typedConfirmationMatches(typed, expected): Boolean` (`br.com.codecacto.kmplib.ui.components`,
  puro) — ignora caixa e **todo** espaço; `expected` em branco nunca confere. Mesma régua do
  `AccountDeletionConfirmation.matches` da backlib, que confere o `{"confirmacao": …}` no servidor.
- Testes: `TypedConfirmationMatchesTest` (7 casos).
- `casca-mobile`: o diálogo de exclusão passa `confirmEnabled` e usa o helper (antes `trim()` +
  `equals(ignoreCase)`, que recusava "EX CLUIR" que o servidor aceita).

Aditivo: sem aviso, sem piso. A lib não tem diálogo próprio de exclusão (`AccountDeletionService` é
só o serviço), então não há outro ponto a ajustar.

## 2.280.0 — Temporários de PDF e impressão não sobram no disco; `clearKmpLibTemporaryFiles` (`kmplib-platform`, `kmplib-pdf`, `kmplib-auth`, `kmplib-sync`)

**Por quê.** Security-review do Vitalis: dado clínico sobrava no `cacheDir` do Android.
- `PdfViewer`: o temporário em `cacheDir/kmplib_pdfviewer` (o `PdfRenderer` exige arquivo) só saía no
  `close()`. Sobrava com o **processo morto** com o PDF aberto e quando o `LaunchedEffect` era
  **cancelado** entre `AndroidPdfDocument.open` e a atribuição (rotação, voltar durante a carga): o
  `withContext` lança na volta e o documento aberto fica sem dono (arquivo, descritor e renderer).
- `PrintHandler`: `cacheDir/kmplib_print` só era limpo depois de **24 h**, e só na impressão seguinte.

**Correção.**
1. **PDF aberto não fica no disco** — o temporário é apagado **assim que o descritor abre**: o
   `PdfRenderer` lê pelo descritor, que segue válido depois do `unlink` (POSIX). A janela em que o
   arquivo existe passa a ser só gravar → abrir. Ao abrir um PDF, as sobras da pasta que não
   pertencem a uma abertura em curso são apagadas.
2. **Abertura cancelada fecha o que abriu** — `openOwned` (interno, `kmplib-pdf`) guarda o recurso
   antes da volta do `withContext` e o fecha no `CancellationException`; o `PdfViewer` também fecha
   se a corrotina já não está ativa na atribuição.
3. **Impressão** — antes de gravar, apaga tudo da pasta que não é impressão em curso (era: > 24 h).
   O arquivo vive até o `onFinish` do spooler, que pode chamar `onWrite` mais de uma vez.
4. **`clearKmpLibTemporaryFiles(olderThanMillis = DEFAULT_SHARED_FILE_TTL_MILLIS): Int`** (público,
   `br.com.codecacto.kmplib.platform`) — apaga compartilhamento (`shared_files`), PDF
   (`PDF_VIEWER_TEMP_DIRECTORY`) e impressão (`PRINT_TEMP_DIRECTORY`); `0` apaga tudo. O que está em
   uso **neste processo** (diálogo de impressão aberto, PDF sendo aberto) nunca sai — registro em
   memória (`KmpLibTempFiles`, `@KmpLibPlatformInternalApi`). Nunca lança; sem `initKmpLibPlatform`
   devolve 0 com aviso. Android tem ainda `clearKmpLibTemporaryFiles(context, olderThanMillis)`.
   **No bootstrap: `clearKmpLibTemporaryFiles()`; no logout/exclusão: `clearKmpLibTemporaryFiles(0L)`**
   — substitui o `getShareHandler().clearSharedFiles()` (que continua valendo, só para compartilhamento).
5. `SyncAccountDataPurger` e `AccountDeletionService` (passo "arquivos compartilhados", flag
   `clearSharedFiles`) passam a chamar `clearKmpLibTemporaryFiles(0L)`: logout/exclusão com o purger
   levam também PDF e impressão.

**iOS conferido:** o `PdfViewer` lê com `PDFDocument(data:)` e a impressão entrega o `NSData` em
`printingItem` — nenhum grava arquivo. No iOS a função limpa só o compartilhamento.

**Não coberto (de propósito):** `createPdfCache()` é cache **persistente** escolhido pelo app (sai pelo
`BlobStore`, não é temporário). App com PDF sensível não deve passá-lo, ou deve limpá-lo no logout.

Testes: `platform/androidUnitTest/KmpLibTempFilesTest` (7: em uso preservado, idade, pastas alheias
intocadas, as três pastas), `pdf/androidUnitTest/OwnedOpenTest` (3: cancelamento fecha). Higiene de
dado sensível: sem aviso, sem piso (nada deixa de funcionar).

## 2.279.0 — `details` do envelope de erro com lista e objeto (`kmplib-core`)

**Por quê.** Origem: Vitalis. O envelope de erro traz `details` com **lista** em casos reais —
`503 PDF_RENDER_UNAVAILABLE` com `details.documentIds: ["…","…"]` (a receita foi emitida, o PDF sai
depois) e `422 ALERTS_NOT_ACKNOWLEDGED` com `details.alertIds`. O `parseServerErrorEnvelope` só
guardava valores primitivos e **descartava** a lista, então o app não sabia quais documentos/alertas.
A correção é na fundação: o backend não passa a mandar "string com vírgula".

**API (aditiva, nada muda para quem já lê `details`).**
- `ServerErrorEnvelope`, `DomainResult.Error` e `ApiResult.Error` ganham, no FIM do construtor,
  **`detailsJson: JsonObject`** (default vazio) = o objeto `details` inteiro, como o servidor mandou.
  O `DomainApiClient` e o `handleApiCall` o preenchem.
- Leitura nos três: **`detailList(key): List<String>?`** e **`detail(key): JsonElement?`**; em
  `DomainResult.Error`/`ApiResult.Error` também **`detailObject(key): JsonObject?`** e
  **`decodeDetail<T>(key): T?`** (kotlinx, campo a mais ignorado). Nenhuma lança.
- Regra do `detailList`: lista de primitivos → os valores como texto, na ordem (`null` e branco
  saem); um primitivo sozinho → lista de um; lista com objeto dentro, objeto, ausente → `null`.
  **Vírgula não divide**: `"a,b"` volta `["a,b"]`.

**Decisão — por que `detailsJson` ao lado, e não trocar o tipo de `details`.** `details:
Map<String, String>` é a **legenda de campo** (`fieldError`, `hasFieldErrors`, `RecentAuthChallenge`):
virar `Map<String, JsonElement>` quebraria toda tela que lê `details[campo]` e faria lista aparecer
como `toString()` de JSON numa legenda. Mantido o mapa primitivo, o JSON cru vai num campo próprio, e a
leitura tipada (`detailList`/`decodeDetail`) fica num lugar só (`ServerErrorDetails`, interno) para
os três tipos. Consequência: `hasFieldErrors` continua `false` num erro que só traz listas. O
`detailsJson` é o do servidor, verbatim — a janela de reautenticação tirada do `WWW-Authenticate`
continua só em `details`.

**Compatibilidade.** Fonte: chamadas existentes inalteradas (parâmetro novo com default, no fim).
Binário: o construtor e o `copy` das três `data class` ganham um parâmetro — app compila a kmplib pela
fonte (`includeBuild`) ou contra o artefato novo, sem efeito. Desestruturação posicional ganha um
`component` a mais, no fim.

**Contrato do servidor.** O `ErrorResponse.details` da backlib ainda é `Map<String, String>`: um
backend que queira lista no `details` precisa serializá-lo como objeto JSON (lacuna registrada para a
backlib). Sem isso, a lista não chega — e não há o que o cliente fazer.

Testes: `core/commonTest/.../sync/rest/ServerErrorDetailsTest` (9 casos: parser, regras do
`detailList`, `decodeDetail`, `DomainApiClient` 503/422/502 e `handleApiCall`). Aditivo: sem aviso,
sem piso.

## 2.278.0 — Seletor de foto com tamanho e qualidade configuráveis; câmera com guia sobreposto (`kmplib-ui`, `kmplib-camera`)

**Por quê.** Origem: App do Personal (MP3, fotos de avaliação física — corpo de frente, costas e
lado, comparadas mês a mês). O seletor reduzia sempre para 1024 px, pouco para uma foto que alguém vai
examinar; e não havia câmera com silhueta para a pessoa se alinhar sempre no mesmo enquadramento.
Aditivo: sem aviso, sem piso.

**1. `rememberImagePickerLauncher` / `rememberMultiImagePickerLauncher` — `maxDimension` e `jpegQuality`.**
- Parâmetros novos, no FIM da assinatura (chamadas existentes não mudam): `maxDimension: Int =
  PICKED_IMAGE_MAX_DIMENSION` (1024) e `jpegQuality: Int = PICKED_IMAGE_JPEG_QUALITY` (85). O teto é
  preso em `PICKED_IMAGE_MIN_DIMENSION` (64) .. `PICKED_IMAGE_MAX_DIMENSION_LIMIT` (4096) e a
  qualidade em 1..100 — com aviso no log, nunca exceção.
- Continua o contrato inteiro do `PickedImage`: JPEG, em pé, sem EXIF/GPS, HEIC→JPEG, medida do que saiu.
- **Codificação única por plataforma** (antes eram duas cópias, uma por seletor):
  - Android — `encodePickedImage` (`PickedImageEncoder.android.kt`): decodificação em duas
    passadas com `inSampleSize` (`decodeSampleSize`, pura e testada) e orçamento de 4096² pixels —
    uma foto de 48 MP não é mais decodificada inteira. **`maxDimension` é teto, não alvo**: 48 MP com
    teto 4096 sai com 4000 px. As OITO orientações EXIF agora são aplicadas (antes só as três
    rotações; foto espelhada chegava espelhada). O seletor de uma foto passou a decodificar **fora da
    main thread** (o múltiplo já fazia), e `OutOfMemoryError` virou `IMAGE_UNREADABLE` em vez de
    derrubar o app.
  - iOS — `NSData.toPickedImage` via **ImageIO** (`CGImageSourceCreateThumbnailAtIndex` +
    `kCGImageSourceThumbnailMaxPixelSize` + `…WithTransform`, a redução recomendada pela Apple):
    decodifica já na medida pedida, aplica a orientação aos pixels e não carrega metadado. A câmera
    do `UIImagePickerController` (que entrega `UIImage`) usa `UIImage.toPickedImage` com
    `UIGraphicsImageRenderer` (escala 1, faixa padrão) no lugar do
    `UIGraphicsBeginImageContextWithOptions` depreciado no iOS 17. O retorno passou a chegar **na
    fila principal** (a galeria entregava da fila do `NSItemProvider`).
- `@KmpLibUiInternalApi` (novo, `RequiresOptIn` ERROR): marca o encoder como ponte entre módulos da
  lib — não é contrato para app.

**2. `GuidedCamera` (novo, `kmplib-camera`, pacote `camera.guided`).** Tela de foto da lib com guia
sobreposto:
- `GuidedCamera(onCaptured, modifier, guide, hint, initialLens, allowLensSwitch, allowFlash,
  maxDimension, jpegQuality, texts, onError, onClose, overlayContent)` → devolve o MESMO
  `PickedImage` do seletor (mesmo encoder).
- `CameraGuide.painter(painter, alignment, widthFraction, heightFraction, alpha, tint, contentScale)`
  e `CameraGuide.drawing(…) { DrawScope }` — silhueta por `Painter`/`ImageVector` ou desenho livre;
  moldura em fração do preview, opacidade e tinta configuráveis; o guia não é gravado na foto.
- **O preview mostra exatamente a foto:** captura 4:3 e preview inteiro (sem corte) numa caixa 3:4 —
  Android `ImageCapture`/`Preview` com `AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY` e
  `PreviewView` em `FIT_CENTER`; iOS preset `Photo` e `AVLayerVideoGravityResizeAspect`.
- Disparo (76 dp, descrição e anúncio de "tirando a foto"), troca frontal/traseira (só com as duas;
  lente ausente cai na outra), flash OFF→AUTO→ON (só se a câmera em uso tiver; iOS confere
  `supportedFlashModes` — modo não suportado lançaria), frontal **sem espelhar** na foto.
- Estados com saída (`GuidedCameraState`): permissão pedida na abertura e reconsultada ao voltar das
  Configurações, negada / negada em definitivo, sem câmera (emulador/simulador), falha da sessão
  (Tentar novamente; iOS observa `AVCaptureSessionRuntimeErrorNotification`). Cada um em `onError`
  (`GuidedCameraError`), mais `CAPTURE_FAILED` no disparo.
- Sem vazar sessão: Android `unbind` no `onDispose` + executor encerrado; iOS `stopRunning` e
  entradas/saídas removidas, observador retirado, delegate do disparo com referência forte até o fim.
- Textos `GuidedCameraTexts`/`rememberGuidedCameraTexts()` em pt-BR/pt-PT/en/es
  (`kmplib_guided_camera_*` no `kmplib-ui`); ids `GuidedCameraTestTags` (`camera-guiada-*`).
- Requisitos do app: `android.permission.CAMERA` no manifesto; `NSCameraUsageDescription` no Info.plist.

**Testes.** `DecodeSampleSizeTest`, `PickedImageEncodingRangeTest` (ui); `GuidedCameraStateTest`,
`GuidedCameraLensAndFlashTest`, `GuidedCameraLayoutTest`, `GuidedCaptureHandleTest`,
`GuidedCameraTextsTest` (camera). `compileKotlinIosArm64` e `compileTestKotlinIosArm64` de `ui` e
`camera` no servidor. **Pendente de aparelho/Mac:** preview e disparo reais (CameraX e AVFoundation),
alinhamento silhueta×foto, flash, troca de lente, e a redução ImageIO/UIGraphicsImageRenderer no iOS.

## 2.277.1 — URL de mídia sem assinatura no log, no `toString` e na mensagem de erro (`kmplib-video`, `kmplib-video-download`, `kmplib-ui`)

**Por quê.** `VideoPlayerState.ios.kt` registrava `"URL de vídeo inválida: ${media.url}"` — a URL
inteira, com a assinatura da URL pré-assinada (S3/CloudFront), quando o `NSURL` não a interpretava. A
varredura achou o mesmo padrão em outros pontos: o log da legenda externa (a mensagem do Ktor traz a
URL inteira), o feed do iOS, as mensagens de exceção do download, e o `toString` gerado dos `data
class` que levam URL (`VideoMedia`, `MediaDownloadRequest`…) e cabeçalho (`PhotoSource.Url` imprimia o
`Authorization`). Higiene (régua de 28/set/2026): só CHANGELOG — sem aviso, sem piso; entra no
próximo bump de quem usa.

**Regra única — `redactMediaUrl(url)` / `redactMediaUrlsIn(texto)` (`kmplib-core`, `core.util`).**
Fica **esquema · host (com porta) · caminho**; saem query, fragmento e `usuário:senha@` da
autoridade. Texto sem esquema não vai para o log: vira `[url omitida]`. Marcados com
`@KmpLibCoreInternalApi` (`RequiresOptIn` de nível ERROR) — ponte entre módulos da lib, não contrato
para app.

**Onde mudou**
- `kmplib-video`: URL inválida no player e no feed (iOS) só com a forma reduzida; legenda externa que
  falha loga a URL reduzida + o tipo da exceção; as mensagens de erro de reprodução (Media3 /
  AVFoundation) passam pela redação no log **e** no `VideoStatus.Error.cause`; `toString` de
  `VideoMedia` e `VideoSubtitleTrack` (este também sem o conteúdo da legenda — só o tamanho).
- `kmplib-video-download`: mensagens de falha de preparo, de download, de `onRenewUrl` e do serviço
  sem URL; `toString` de `MediaDownloadRequest`, `MediaDownloadRecord` (também sem `localPath`) e
  `MediaDownload`.
- `kmplib-ui`: logs dos seletores de foto/vídeo (Android/iOS) passam pela redação; `toString` de
  `PhotoSource.Url` (só os NOMES dos cabeçalhos), `PhotoStripItem` e `PickedVideo` (sem a referência
  local e sem o nome do arquivo).

Igualdade, `hashCode`, `copy` e serialização dos `data class` não mudaram — só o `toString`.

## 2.277.0 — `PickedVideo.readRange(offset, length)` e `readChunks(fromByte = …)`: ler o vídeo a partir de um deslocamento

**Por quê.** App do Personal (MP6, upload multipart de vídeo retomável): o `readChunks` só lia desde o
byte 0. Retomar um envio, ou subir a parte N de um multipart, obrigava a reler o arquivo inteiro
pulando o que já tinha subido — até 100 MB de disco e bateria para mandar os últimos 5.

**`kmplib-ui` — aditivo (sem piso, sem aviso)**
- `expect suspend fun PickedVideo.readRange(offset: Long, length: Long? = null, chunkSize: Int =
  DEFAULT_VIDEO_CHUNK_BYTES, onChunk): Long` — lê só a faixa pedida, em pedaços, buffer reaproveitado;
  devolve quantos bytes entregou (menos que `length` só se o arquivo acabou antes; `offset` além do fim = 0).
  `offset`/`length` negativos ou `chunkSize <= 0` → `IllegalArgumentException`; `length = 0` não abre o arquivo.
- `suspend fun PickedVideo.readChunks(chunkSize = DEFAULT_VIDEO_CHUNK_BYTES, fromByte: Long, onChunk): Long`
  — sobrecarga (o `fromByte` é obrigatório, então `readChunks { }` e `readChunks(n) { }` continuam
  resolvendo para a original, sem ambiguidade). Atalho de `readRange(fromByte, null)`.
- **Android:** `ContentResolver.openFileDescriptor(uri, "r")` + `FileChannel.position(offset)` (salto
  direto). Provedor sem descritor posicionável (`FileNotFoundException`/`statSize < 0`/`position` com
  `IOException`) cai em `openInputStream` + `skip` **verificado** (`skip` que devolve 0 é confirmado
  lendo um byte — nunca entrega bytes do lugar errado). `offset = 0` segue o fluxo comum de antes.
- **iOS:** `fileHandleForReadingFromURL:error:` + `seekToOffset:error:` + `readDataUpToLength:error:` +
  `closeAndReturnError:`. O `readChunks` original passou a usar o mesmo caminho: o
  `readDataOfLength` antigo sinalizava falha de E/S com `NSException`, que o Kotlin/Native não
  captura (o app caía no meio do upload); agora vira `IllegalStateException`. Buffer reaproveitado
  em vez de um `ByteArray` novo por pedaço.
- Núcleo comum puro (`streamVideoRange`, `skipVideoBytesExactly`, `requireValidVideoRange`) com testes em
  `commonTest` (11) e o caminho Android com arquivo/fluxo reais em `androidUnitTest` (7).

## 2.276.0 — dado sensível fora do estado salvo e do log: `AppSearchField(saveable)`, `FormRunner` não salvável, `ShareHandler` sem nome de arquivo no log

*(Não existe 2.274.0: o número foi reservado nesta rodada e saltado quando a 2.275.0 saiu antes.)*

**Por quê.** Security-review do Vitalis (saúde): (1) o `AppSearchField` sempre gravava o termo no estado salvo
do sistema — busca por nome/CPF/telefone de paciente ia parar no `Bundle`; (2) o campo de número do
`FormRunner` usava `rememberTextFieldState` (salvável) e as datas usavam o calendário salvável — resposta de
formulário de saúde (peso, pressão, escala, data) ia para o estado salvo; (3) a falha de `shareFile` logava o
nome do arquivo (e a exceção, cuja mensagem traz o caminho) — nome como "laudo-joao-silva.pdf" é dado de saúde.

**`kmplib-ui` — aditivo + higiene (sem piso, sem aviso)**
- `AppSearchField(…, saveable: Boolean = true)` — último parâmetro, repassado ao `rememberSyncedTextFieldState`
  (2.271.0). **Busca por nome, CPF ou telefone de pessoa usa `saveable = false`.** Default = comportamento de antes.
- `FormRunner` (`ui/form`): o rascunho do campo de número passa a `remember { TextFieldState(…) }` (só memória),
  com o mesmo `TextInputReconciler`; os campos de data (pergunta e item de lista) passam `ephemeral = true` ao
  `AppDatePicker`. Texto curto/longo já eram não salváveis (`keepTextLocally` = `remember`). O mesmo
  `ephemeral = true` na data do `QuestionnaireRunner`. Contrapartida: morte de processo perde o rascunho em
  andamento (a resposta enviada mora no ViewModel/fila).

**`kmplib-platform` — higiene (sem piso, sem aviso)**
- `ShareHandler.shareFile` (Android e iOS): a falha loga só o tipo do erro (`Erro ao compartilhar arquivo:
  IllegalArgumentException`), sem o nome do arquivo nem a exceção; a limpeza de `shared_files` também deixou de
  logar o nome. A exceção continua sendo relançada ao chamador.

## 2.275.0 — workout: duração informada da série por tempo (`CompleteSet(durationSeconds)`) e troca de exercício no cursor (`SwapExercise`)

**Por quê.** App do Personal, lote MA4a (treino guiado, review de 10/out): (1) a série por tempo só tinha a
diferença `completedAt − startedAt`, que inclui a preparação antes de disparar o cronômetro — o tempo que o
aluno de fato segurou a prancha se perdia; (2) a troca de exercício virava `SkipExercise(swappedTo)` e as
séries feitas com o substituto nunca eram registradas.

**`kmplib-workout` — aditivo (sem piso, sem aviso)**
- `WorkoutEvent.CompleteSet(…, durationSeconds: Int? = null)` e `CommandEvent.CompleteSet(…, durationSeconds: Int? = null)`
  → `SetResult.durationSeconds: Int? = null`. Gravada só em passo `SetTarget.Timed` com valor em
  `SET_DURATION_RANGE` (1..3600, o `DURATION_MAX` do backend); fora disso a série sai sem ela (o motor nunca produz
  sessão inválida). `SetResult.effectiveDurationSeconds` (informada > diferença de instantes, nunca negativa) é o
  que `timeUnderTensionSeconds()` soma agora.
- `WorkoutEvent.SwapExercise(stepId, toExerciseId, reason = null)` / `CommandEvent.SwapExercise` — troca o exercício
  do item a partir do cursor: as séries que faltam saem com `SetResult.exerciseRefId` (o feito) e
  `swappedFromExerciseId` (o ORIGINAL do plano, mesmo em troca encadeada); prescrição e descanso continuam os do
  item; em `InSet` do próprio item o relógio da série recomeça. Sem efeito para o mesmo exercício, item
  inexistente, concluído ou pulado, ou id em branco. `WorkoutRun.swaps: List<ExerciseSwap>` (default vazio) guarda
  as trocas; `Undo` desfaz em ordem inversa (troca depois do último passo sai primeiro). `WorkoutRun.currentExerciseId(step)`.
- `validateRun`: `RunIssue.InvalidDuration(i)` (`INVALID_DURATION`), `RunIssue.InvalidSwap(i)` (`INVALID_SWAP`),
  `RunIssue.InvalidSwapRecord(swapIndex)` (`INVALID_SWAP_RECORD`). `exerciseRefId` igual ao do plano sem troca é válido.
- `WORKOUT_PROTOCOL_VERSION` 2 → **3** (campo e comando novos na ponte); mensagem v2 continua sendo lida.
- `toString` sem dado de saúde: `hasDuration` nos dois `CompleteSet` e no `SetResult`; `SwapExercise`/`ExerciseSwap`
  com ids + `hasReason`; `WorkoutRun` com `swaps=<n>`.
- **Fixtures** (`kmplib-workout-fixtures`): `session-validation.json` 37 → 51 casos e `engine-transitions.json` 12 → 15;
  os antigos ficam (com os campos novos em `null`). Formato estendido: `completeSet.durationSeconds` opcional, evento
  `swapExercise {stepId, toExerciseId, reason}` e `expect.swaps`. **Leitor que falha em evento desconhecido precisa
  mapear `swapExercise`; teste que conta casos precisa da contagem nova.**

## 2.273.0 — `Haptics.vibrate(pattern)`; `Keep` que guarda rascunho local; `toString` do motor do workout sem dado de saúde

**Por quê.** App do Personal: (1) o fim do descanso precisa avisar mesmo com o aparelho no Silencioso — o
`SoundEffectPlayer` não toca ali e o `LocalHapticFeedback` do Compose é só um toque curto (GAP-PT-M08);
(2) sessão perdida sem ação do aluno (refresh vencido) chamava `purgeOnSignOut(conta, Keep)`, e o `Keep`
apagava o treino em andamento — linha LIMPA de `LocalRepository`, que não é pendência da fila;
(3) o review da 2.272.0 deixou `WorkoutEvent`/`CommandEvent.CompleteSet`, `SkipExercise` e `Metrics`
imprimindo carga, repetições, FC, kcal e o motivo do "pular".

**`kmplib-platform` — aditivo (sem piso, sem aviso)** — pacote `br.com.codecacto.kmplib.platform.haptics`
- `object Haptics : HapticPlayer` (`isSupported`, `vibrate(pattern, usage = HapticUsage.NOTIFICATION): HapticOutcome`,
  `cancel()`), `interface HapticPlayer` (para dublê no teste), `@Composable rememberHaptics(): HapticPlayer`.
- `VibrationPattern(segments: List<HapticSegment>)` (`HapticSegment.Vibrate(durationMillis, intensity = 1f)` /
  `Pause(durationMillis)`; `of(...)`, `waveform(vararg timingsMillis, intensity)` no formato do Android,
  `pulses(count, onMillis, gapMillis, intensity)`, presets `Tick`/`Confirm`/`Alert`; teto `MAX_TOTAL_MILLIS` = 30 s).
- `HapticUsage` `TOUCH`/`NOTIFICATION`/`ALARM` (o que o sistema usa para aplicar a configuração da pessoa) e
  `HapticOutcome` `PLAYED`/`UNSUPPORTED`/`SUPPRESSED_BY_SYSTEM`/`FAILED`.
- **Android:** `VibratorManager.defaultVibrator` (31+)/`Vibrator`; `VibrationEffect.createWaveform` com amplitudes
  quando há controle de amplitude, liga/desliga quando não há, `vibrate(long[], -1)` abaixo da 26; atributos de uso
  (`VibrationAttributes` 33+, `AudioAttributes` antes). `NOTIFICATION` não vibra no Silencioso, `TOUCH` respeita
  "vibração ao tocar" (< 33; na 33+ o sistema aplica). **Permissão `VIBRATE` no manifesto do `kmplib-platform`**
  (normal, sem prompt) — o app não declara nada; exige `initKmpLibPlatform(context)` (o umbrella já chama).
- **iOS:** Core Haptics (`CHHapticEngine`, um evento contínuo por trecho, `playsHapticsOnly`, auto-shutdown);
  sem Core Haptics, `UIImpactFeedbackGenerator` aproximando o padrão (impacto a cada 100 ms dentro do trecho).
  "Tátil do Sistema" desligado silencia — o iOS não informa (retorno `PLAYED`).
- **Segundo plano NÃO é coberto:** o iOS suspende a engine do app e o Android ignora vibração de app em segundo
  plano fora de alarme/chamada. Aviso com o app fechado vai pela notificação (que vibra pelo canal).

**`kmplib-sync` — aditivo (sem piso, sem aviso)**
- `SyncAccountDataPurger(…, keepLocalEntities: Set<String> = emptySet(), keepLocalRow: (Synced_entity) -> Boolean = { false }, extraCleanup)`
  — no `purgeOnSignOut(conta, SignOutPendingPolicy.Keep)`, as linhas dessas entidades (ou que o predicado aceita)
  ficam no bucket da conta mesmo limpas, e voltam quando a MESMA conta entra. `Discard` e `purgeAccount` continuam
  apagando tudo. Predicado que lança = falha no relatório e **nada apagado do espelho** naquela limpeza. Os dois
  parâmetros entram ANTES de `extraCleanup` (lambda final continua valendo; todos os consumidores conhecidos usam
  argumentos nomeados). Default = comportamento de antes.

**`kmplib-workout` — higiene (sem piso, sem aviso)**
- `toString()` de `WorkoutEvent.CompleteSet` (`hasReps`/`hasLoad`/`hasHeartRate`), `WorkoutEvent.SkipExercise` e
  `CommandEvent.SkipExercise` (`hasReason`), `CommandEvent.CompleteSet` (`hasReps`/`hasLoad`) e `protocol.Metrics`
  (`seq`, `v`, `hasHeartRate`, `hasKcal` — sem o instante). `equals`/`hashCode`/serialização não mudam.

## 2.272.0 — `DomainApiClient.getJsonWithEtag` (GET condicional, 304); `toString` sem dado de saúde no `kmplib-workout`; `foldForSearch` público no core

**Por quê.** App do Personal (MA3, motor offline do aluno): o programa publicado vem com ETag opaco, mas o
`getJson` só devolvia o corpo — o `ETag` se perdia e o 304 caía em "Erro do servidor (304)". E duas higienes
apontadas no review: o `toString` gerado de `SetResult`/`WorkoutRun` imprimia carga, reps, FC, esforço e
comentário; e o app teve de depender do `kmplib-brdata` só para dobrar acento numa busca.

**`kmplib-core` — aditivo (sem piso, sem aviso)**
- `DomainApiClient.getJsonWithEtag(path, etag: String?, headers = emptyMap()): DomainResult<EtagResult<String>>` —
  envia `If-None-Match` verbatim (ETag é opaco; RFC 9110 §13.1.2). **304 → `EtagResult.NotModified(etag)`**;
  2xx → `EtagResult.Modified(body, etag)` com o `ETag` da resposta (`null` se não veio); 2xx cujo `ETag` casa
  com o enviado (comparação FRACA, §8.8.3.2 — intermediário que revalidou) também vira `NotModified`. Sem
  `etag`, é a primeira carga (e um 304 sem condição é erro). 401 → refresh + 1 retry com o MESMO
  `If-None-Match`; 402/429/5xx/transporte como no `getJson`. `If-None-Match` passado em `headers` é ignorado.
  `getJson` continua tratando 304 como erro (não mudou).
- `EtagResult<out T>` (`Modified<T>(body, etag: String?)` · `NotModified(etag: String)`, `etag` abstrato) — o
  `toString` do `Modified` não imprime o corpo.
- `br.com.codecacto.kmplib.core.text`: **`foldForSearch(text)`** (minúsculas + sem acento por **NFD do sistema**
  — `java.text.Normalizer` / `decomposedStringWithCanonicalMapping` — removendo `NON_SPACING_MARK`; ß/æ/œ/ø/ł/
  đ/ð/þ/ı como o `unaccent` do Postgres; espaços colapsados), **`searchTerms(query)`** e
  **`matchesSearch(query, vararg fields)`** (todos os termos como pedaço; branco casa tudo). O `foldForSearch`
  `internal` de `core/locale/Countries.kt` saiu (o `Countries.search` usa o público; resultado igual).
  Quem usava `removeAccents()` do `kmplib-brdata` só para busca pode trocar e tirar a dependência.

**`kmplib-workout` — higiene (sem piso, sem aviso: dado em log não para nada para o usuário)**
- `SetResult.toString()` = ids, índices (`setIndex`/`stageIndex`/`roundIndex`) e `skipped` — sem meta, carga,
  reps nem FC. `WorkoutRun.toString()` = ids + contagens (`sets`, `skippedExercises`) + `finished`/`hasEffort`/
  `hasComment` — sem instantes, esforço, comentário nem as séries. `SkippedExercise.toString()` sem o `reason`
  (texto livre do aluno). `equals`/`hashCode`/serialização não mudam. Ainda com `toString` gerado (dado de
  treino em evento/protocolo): `WorkoutEvent`/`CommandEvent.CompleteSet` (reps/carga) e `Metrics` (FC/kcal) — não
  logue esses objetos.

**Correção de documentação.** Na 2.269.0, `engine-transitions.json` tem **12** casos (estava "13").

**Testes.** `DomainApiClientEtagTest` (13), `SearchTextTest` (7), `ModelToStringTest` (5); suíte do core (457) e do
workout (jvm + android) verdes; `compileKotlinIosArm64` (core, workout) e `compileKotlinWatchosArm64` (workout)
compilados no servidor, nenhum SKIPPED.

## 2.271.0 — estado NÃO salvável para dado sensível: `rememberSyncedTextFieldState(saveable = false)` e `AppDatePicker`/`AppDatePickerDialog(ephemeral = true)`

**Por quê.** App do Personal, lote MA2 (anamnese = dado de saúde; review de 09/out): o texto e a data de um formulário
de saúde não podem ir ao `Bundle` da Activity / estado restaurado da cena. O app tinha uma cópia local do campo
sincronizado que guardava só o último valor emitido — e perdia letra no iOS com eco atrasado.

**`kmplib-ui` — aditivo (sem piso, sem aviso)**
- `rememberSyncedTextFieldState(text, onTextChange, saveable: Boolean = true)`: com `saveable = false` o estado é
  `remember { TextFieldState(text) }` (antes, sempre `rememberTextFieldState`). O `TextInputReconciler` com a fila de
  pendentes é o mesmo nos dois caminhos — teste novo mostra que a guarda "último emitido" perde a letra e a fila não.
- `AppDatePickerDialog(…, ephemeral: Boolean = false)` e `AppDatePicker(…, ephemeral: Boolean = false)` (repassa ao
  diálogo): com `true`, `remember { DatePickerState(locale, …) }` em vez de `rememberDatePickerState` — a data marcada
  com o calendário aberto não entra no estado salvo. `minDate`/`maxDate`, conversão UTC, botões e ids não mudam. O
  idioma do calendário é o mesmo que o Material usa por dentro (`currentCalendarLocale`, interno: 1º locale da
  `Configuration` no Android, `NSLocale.currentLocale` no iOS).
- **Contrapartida (KDoc):** em recriação de processo o valor em andamento se perde (campo: volta só o que o ViewModel
  tiver; calendário: reabre na data confirmada). Mudar `minDate`/`maxDate` com o calendário efêmero aberto recomeça a
  marcação. Não alternar `saveable`/`ephemeral` durante a vida do componente.
- Testes: `TextInputReconcilerTest` (+1), `EstadoNaoSalvavelTest` (androidUnitTest, 6: o caminho efêmero não passa
  por `rememberSaveable`/`rememberTextFieldState`/`rememberDatePickerState`; data inicial UTC; min/max).

## 2.270.0 — log de rede sem query string (PII); `Clipboard` com `sensitive`, `hasText()` e `readText()`; máscaras de telefone e CEP para `TextFieldState` no `kmplib-mask`; `SecureContent { }`

**Por quê.** App do Personal: revisão de segurança (dois achados médios — PII da busca no log de rede do release e
senha temporária copiada sem marca de sensível) + GAP-PT-M22/M23 (máscara para campo com estado e colar).

**`kmplib-core` — correção (piso em `docs/pisos.yaml`, símbolo `createHttpClient`)**
- O `Logging` do `createHttpClient` (ligado por default, nível `INFO`) escrevia a URL inteira (`REQUEST: …`/`FROM: …`)
  no `AppLogger`, e isso sai no **release** — a query de uma busca (`?busca=maria@x.com`, final de telefone, nome) ia
  para o logcat/console. Agora toda linha passa por `redactHttpLogMessage` (interna): fica método · esquema · host ·
  caminho · status, e a query vira `?…` (URL absoluta e caminho relativo). O **host fica** (é o que denuncia o
  endereço errado). O log da nova tentativa (`HttpRetry`) redige igual, inclusive a mensagem da causa.
- O nível mínimo do `AppLogger` **não** muda no release: a regra da fábrica é logar toda requisição; o que protege é
  não escrever PII. O formato `OkHttp` do Ktor (que traria o tempo) foi descartado de propósito: ele omite o host.
- **Sem aviso no Nexus** (régua de 28/set: PII em log é higiene, não deixa nada de funcionar).

**`kmplib-platform` — aditivo**
- `Clipboard.copy(text, label = "Texto", sensitive = false)`: com `sensitive = true`, Android põe
  `ClipDescription.EXTRA_IS_SENSITIVE` nos extras do clip (API 33+; abaixo, a chave literal
  `"android.content.extra.IS_SENSITIVE"`) e a prévia do sistema mostra pontos; iOS usa
  `setItems(_:options:)` com `UIPasteboardOptionLocalOnly` + `UIPasteboardOptionExpirationDate` (agora + 120 s,
  `SENSITIVE_CLIP_EXPIRATION_SECONDS`). Chamadas antigas `copy(text)`/`copy(text, label)` não mudam.
- `Clipboard.hasText()` (não dispara o aviso de colagem: `primaryClipDescription`/`hasStrings`) e
  `Clipboard.readText(): String?` (`coerceToText` / `UIPasteboard.string`; `null` sem texto ou leitura negada).
  Chamar no clique de "Colar", nunca ao abrir a tela (aviso do iOS 16+ / Android 12+).
- `SecureContent(modifier, enabled, cover) { }` (`platform.privacy`): Android = `FLAG_SECURE` enquanto composto (por
  `HideFromRecents`, contagem aninhada) — bloqueia print/gravação/espelhamento; iOS = cobre o bloco enquanto
  `UIScreen.captured` (gravação, espelhamento, AirPlay; `UIScreenCapturedDidChangeNotification`) + desfoque do
  seletor de apps. **O print no iOS NÃO é bloqueado** — não há API pública; documentado no KDoc. Tag da cobertura:
  `SECURE_CONTENT_COVER_TAG` (`conteudo-seguro-cobertura`).
- ⚠️ Interface `Clipboard` ganhou dois membros abstratos — quem a implementa fora da lib (nenhum conhecido no
  monorepo) precisa implementá-los.

**`kmplib-mask` — aditivo (GAP-PT-M22)**
- `PhoneInputTransformation` (só algarismos, até 11; colar "+55 (65) 99999-8888" guarda "65999998888"),
  `PhoneBrOutputTransformation` (`(AA) NNNNN-NNNN` / `(AA) NNNN-NNNN`, a mesma regra do `PhoneVisualTransformation`),
  `CepInputTransformation`/`CepOutputTransformation` — eram `internal` no `AddressFields` (`kmplib-brdata`), que
  passou a usar os públicos (sem mudança de comportamento).

**Testes.** `HttpRequestLogRedactionTest` (cliente real sobre `MockEngine`: nenhuma linha com a query, host e caminho
ficam), `ClipboardContractTest`, `ClipboardAndroidTest`, `TextFieldStateMasksTest` (inclui paridade com o
`PhoneVisualTransformation`). iOS: `compileKotlinIosArm64` de core/mask/platform/brdata/auth/central/umbrella neste
servidor, sem `SKIPPED`; validação em aparelho (prévia sensível, aviso de colar, cobertura na gravação) é do Mac.

## 2.269.0 — `health`: FC ao vivo por BLE (`HeartRateMonitor`, `0x180D`/`0x2A37`) com dublê + regra do repositório de saúde em `commonMain`; `workout`: pausa que devolve o tempo, `Load` e as tabelas de casos para o backend (`kmplib-workout-fixtures`)

**Por quê.** App do Personal, plano da Onda 1: o MA6 precisa de FC ao vivo por BLE provável com dublê no
emulador/simulador; o MA3 e o A5 precisam da MESMA tabela de casos no celular e no backend (o risco do F1 era o
servidor recusar a sessão boa que o celular produz). Retoma o trabalho que ficou sem commit na sessão de 09/out.

**`kmplib-health`**
- **`HeartRateMonitor`** (novo, `health.heartrate`): `createHeartRateMonitor()` · `scan()` (sensores com `0x180D`,
  cada um uma vez) · `measurements(device)`/`heartRate(device)` (conecta, assina a `0x2A37`, **reconecta sozinho** pela
  `HeartRateReconnectPolicy`; cancelar desconecta) · `state: StateFlow<HeartRateMonitorState>` (`Idle`/`Unavailable(reason)`/
  `Scanning`/`Connecting(device, attempt)`/`Connected(device, lastBpm)` — `lastBpm = null` é "aguardando", nunca 0 —
  /`Lost`). `HeartRateMeasurementParser` = o formato do Bluetooth SIG (UINT8/UINT16 pelo bit 0, contato, energia, RR;
  truncado = `null`). Android pela API oficial `android.bluetooth` (`ScanFilter`, `connectGatt(TRANSPORT_LE)`, CCCD
  `0x2902` nas duas formas, `close()` sempre); iOS por CoreBluetooth em cinterop. Manifesto da lib com
  `BLUETOOTH_SCAN` (`neverForLocation`)/`BLUETOOTH_CONNECT` e os legados com `maxSdkVersion=30`; o app pede em runtime e
  declara `NSBluetoothAlwaysUsageDescription` (sem ela o iOS encerra o app).
- **Dublê público** `SimulatedHeartRateSensor` + `createSimulatedHeartRateMonitor(sensor)`: o MESMO motor com rádio de
  mentira mandando pacotes `0x2A37` reais (`setBpm`, `dropLink`, `setUnavailable`, `connections`) — para emulador,
  simulador e teste do app.
- **Repositório**: a regra saiu dos `actual` e foi para `DefaultHealthRepository` (commonMain, testada com gateway
  falso); a plataforma virou porta fina (`HealthConnectGateway`/`HealthKitGateway`, `internal` — os nomes antigos
  `HealthConnectRepository`/`HealthKitRepository` também eram `internal`: nenhuma quebra). Aditivo na interface:
  `availability(): HealthAvailability` (`NEEDS_PROVIDER_UPDATE` = levar à Play Store; `isAvailable()` virou default
  sobre ela), `WorkoutWriteResult.ALREADY_WRITTEN`/`RUN_NOT_FINISHED`/`INVALID_RUN`/`FAILED`.
- **Corrigido** (piso): `writeWorkout` passa a aplicar a guarda DENTRO dele (treino de outro app/relógio no intervalo →
  não grava; mesmo `run.localId` → `ALREADY_WRITTEN`) e é idempotente: Android grava com
  `Metadata.activelyRecorded(clientRecordId = run.localId)` (era `manualEntry()` sem id — reenviar DUPLICAVA o treino);
  iOS grava pelo `HKWorkoutBuilder` com `HKMetadataKeyExternalUUID` e passou a LER os treinos do intervalo (até a
  2.268.0 `hasDeviceWorkoutOverlapping` devolvia sempre `false` e gravava por cima do Apple Watch); cardio/mobilidade no
  HealthKit = `mixedCardio`/`flexibility` (eram `other`). Health Connect: permissões no manifesto da lib (só os 4 tipos
  usados) + `<queries>` do pacote, exigido pelo `getSdkStatus`.

**`kmplib-workout`**
- **Corrigido** (piso): `Resume` devolve o tempo parado — o descanso termina tanto depois quanto durou a pausa e a
  série por tempo não conta o intervalo parado (`Paused.pausedAt`). Até a 2.268.0 o descanso corria durante a pausa.
- `WorkoutEvent.Load(plan, localRunId?)` + `WorkoutState.Ready(plan, localRunId?)`: `Start` a partir de `Ready` com o
  UUID do cliente (o `session.id` idempotente do backend); sem ele, id determinístico. `engine.start(...)` continua.
- `RunIssue` `@Serializable` (`type` = `PLAN_MISMATCH`/`UNKNOWN_STEP`/`DUPLICATE_STEP`/`NEGATIVE_DURATION`/
  `UNKNOWN_SKIPPED_EXERCISE`) — `details` do `INVALID_SESSION`.
- `HealthPlatformMapping.healthConnectExerciseTypeName(category)` (tipo da SESSÃO); `healthKitActivityTypeName(CARDIO)`
  = `mixedCardio` (era `cardioDance`, aula de dança).
- **Tabelas de casos** em dados, rodando em todo alvo: `engine-transitions.json` (evento → estado, 12 casos — para
  outra implementação da máquina, Connect IQ) e `session-validation.json` (sessão → veredito do `validateRun`: toda
  sessão produzida pelo motor + casos à mão). Novo artefato **`br.com.codecacto:kmplib-workout-fixtures`** (JAR só de
  teste, `kmplib/workout/fixtures/*.json`): o backend roda o `session-validation.json` com o `kmplib-workout-jvm`.
- Kover nos dois módulos (fundação, ≥ 95% na regra; gateways e transportes de plataforma fora — só se provam em
  aparelho).

**Pendente de aparelho (spike 0.7):** FC por BLE com cinta/relógio reais, reconexão física, `bluetooth-central` em
2º plano, leitura/escrita real de Health Connect e HealthKit.

## 2.268.0 — `ui/form`: o `FormRunner` sobre o **FormSchema v1** (LM-K05b) + depreciação da extensão do `Questionnaire` (D11)

**Por quê.** D11 do Vitalis: o FormSchema v1 (`contratos-api.md` §7.1) é o formato canônico de formulário nas três libs
— o motor de servidor é o `backlib-forms` (0.156.0), o runner web é o `FormRunner` da weblib (0.238.0), e faltava o do
app, pré-requisito do M1.3b (triagem AM-13, público RECEPTION) e do M1.4 (pré-consulta pelo médico, AM-11). D14: o
motor de verdade mora na backlib — o runner do app desenha, valida no campo e enfileira; **visibilidade por público e
pontuação são do servidor** (o cliente nunca soma escore).

**O que entrou** (pacote `br.com.codecacto.kmplib.ui.form`, artefato `kmplib-ui` — o umbrella traz):

- **Contrato puro** (o `/forms` da weblib): `FormSchemaV1`/`FormSection`/`FormQuestion` e as configurações por tipo
  (`FormNumberConfig`, `FormDateConfig`, `FormListConfig`, `FormFileConfig`, `FormConsentConfig`, `FormLikertScale`,
  `FormScoring`, `FormInstrument`), `FormScoreResult` (o `ScoreResultDto`, só exibido), `FormJson` (leitura TOLERANTE:
  campo desconhecido ignorado, tipo desconhecido → `FormQuestionType.UNSUPPORTED`, `display` desconhecido → padrão,
  anexo de tipo desconhecido descartado). **`FormDecimal`** = decimal EXATO do literal do JSON (o `BigDecimal` do
  servidor; casas, passo e pontos da régua sem `Double`). **`FormAnswerValue`** guarda o JSON da resposta e lê pelo tipo
  (`toString()` redigido — dado de saúde); `FormAnswerEntry`, `FormFileRef`, `FormListItem`, `FormContext`, `FormRole`,
  `FormAnswerValuesSerializer` (mapa do fio, `null` descartado), `FormAnswerPatchSerializer` (apagada = `null` EXPLÍCITO).
  `FormCondition` + `evaluate` (total: malformada = `Invalid`, sempre falsa, JSON preservado; teto de aninhamento 32),
  `evaluateVisibility`/`pruneAnswers` (cascata, ordem do documento), `validateValues` (salvamento) e
  `validateSubmission` (envio: `onlyQuestionIds`, `requireConsent`) com `FormIssueCode`/`FormAnswerIssue`/`FormIssueParams`.
- **Regras de interface** (o `formRunner.logic`): `formRunnerSteps`, `resolveFormStepIndex`, `formRunnerProgress`,
  `firstIncompleteFormStep`, `clearFormIssuesOnEdit`, `pendingReservedIds`, `toggleFormMultiChoice`,
  `sanitizeFormNumberDraft`/`parseFormNumberDraft`/`formatFormNumberDraft`, `formFileMatchesAccept`/`formFileExceedsSize`,
  `safeFormLinkHref`/`resolveFormLink` (`javascript:` vira texto), `isCompactFormQuestion`.
- **MVI**: `FormRunnerState` (`start(…, resume)`, `reduce`) → `FormRunnerUpdate(state, events)`; ações
  `FormRunnerAction` (resposta, blur, continuar, enviar, voltar, ir à etapa, seção de escala, anexos, foco, erros do
  servidor); eventos `FormRunnerEvent` (`Answered` → fila, `UploadRequested`/`UploadCancelled` → upload, `Submit` com as
  respostas JÁ descartadas, `StepChanged`, `Exited`).
- **Tela**: `FormRunner(state, onAction, …)` — uma seção por passo no compacto (`LocalIsCompact`); agrupado no expandido
  (índice de etapas com ✓/!/reservadas pendentes + perguntas curtas em duas colunas); os 11 tipos; lista repetível;
  anexo por callback (foto pelo seletor de imagem, PDF pelo seletor de arquivo, tipo/tamanho/teto recusados aqui,
  progresso real, X cancela, "Tentar de novo"); consentimento (barra o envio, não o "Continuar"); reservada com selo e
  "pendente" só com `markReserved` (nunca cinza — o servidor já tirou o que o público não vê); travada
  (`lockedQuestions`); erro no campo depois do envio, foco no primeiro, erro do servidor no campo até a pessoa mexer;
  seção `likert` **delegada ao `QuestionnaireRunner`** (mesma régua e ritmo). `FormRunnerTexts`/`rememberFormRunnerTexts`
  e `FormIssueMessages`/`rememberFormIssueMessages` nos 4 idiomas; ids `FormRunnerTestTags` (`formulario-*`).
- **Fila e anexo**: `FormAnswerQueue` (o `useFormAnswerQueue`: `AnswerPatch`, quietude de 600 ms, uma requisição por
  vez, retentativa 0,5 s → 8 s sem sobrescrever o mais novo, `flush()`, `sendNow()` que sobrevive ao fim do
  `viewModelScope`) e `FormFileUploader` (o `onUploadFile`: o app passa a rota; progresso e desfecho voltam como ação;
  `FormUploadException` leva a frase do servidor à tela, mensagem técnica nunca).

**Contrato provado com as MESMAS fixtures** do `backlib-forms`/weblib (`form-schema-v1.fixtures.json`,
`fixturesVersion` 1, cópia byte a byte em `ui/src/commonTest/fixtures/`): `conditions` 51/51, `visibility` 10/10,
`prune` 3/3, `validation` 66/66 (130/130) e os 19 schemas nomeados lidos e reescritos sem perda. A tarefa
`generateFormSchemaFixtures` transforma o JSON em fonte do `commonTest` — o contrato roda no Android e compila no iOS.

**Aditivos fora do pacote** (nenhuma assinatura existente muda):
- `QuestionnaireRunner(…, progress, headerExtra, questionExtra)` — a tríade que a weblib ganhou na 0.238.0 para o
  `FormRunner` delegar a seção de escala.
- `AppDatePicker(…, minDate, maxDate, onClear, clearText)` e `AppDatePickerDialog(…, minDate, maxDate)`: dias fora da
  faixa desligados no calendário (`SelectableDates` oficial do Material) e "x" de limpar data opcional
  (`DatePickerTestTags.CLEAR` = `data-btn-limpar`; string `kmplib_date_clear` nos 4 idiomas).
- `AppTextArea(…, keepTextLocally)` — o mesmo do `AppTextField`: o texto mora no campo (iOS não perde letra).
- `QuestionnaireAnswerQueue` passa a usar o núcleo da `FormAnswerQueue` (mesmo comportamento: os 7 testes dela
  seguem verdes sem mudança).

**Depreciado (D11), sem quebrar ninguém** — `@Deprecated` com mensagem apontando o `FormRunner`; quem usa continua
compilando e funcionando: no `Questionnaire`, `QuestionnaireQuestion.type` (+ `QuestionnaireQuestionType`) e a
configuração por tipo (`options`/`QuestionnaireOption` com `score`, `min`, `max`, `decimals`, `unit`, `multiline`,
`maxLength`), `visibleIf` (bloco e pergunta, `QuestionnaireCondition`), `reserved` (bloco e pergunta),
`Questionnaire.scores` (`QuestionnaireScore`, `…Product`, `…Band`, `QuestionnaireTone`, `toStatusTone()`) e a parte de
escore/condição/reserva da avaliação (`QuestionnaireEvaluation.scores`/`score()`/`scoresOwnedBy()`/`holds()`/
`isReserved()`/`reservedPending`, `QuestionnaireScoreResult`, `QuestionnaireProgress.reservedPending`) e
`QuestionnaireRunnerAction.EditNumber`. O parâmetro `showScores` do `QuestionnaireRunner` fica (parâmetro não aceita
`@Deprecated` sem sobrecarga ambígua) e está marcado no KDoc. **O `QuestionnaireRunner` Likert puro continua.**

**Limites conhecidos** (registrados no catálogo): a foto do anexo passa pelo seletor de imagem da lib (JPEG de até
1024 px, sem EXIF — documento fotografado pode ficar pequeno para leitura); uma foto por toque (sem seleção múltipla);
faixa que admite negativo cai no teclado de texto (o Compose não expõe teclado numérico com sinal); a seção `likert`,
desenhada pelo `QuestionnaireRunner`, usa os ids `questionario-*`.

**Sem piso** (aditivo + depreciação; nada que já está validado é afetado) e **sem aviso**.

## 2.267.0 — `sync` não depende mais do `kmplib-monetization` (app sem loja pode ter outbox)

**Por quê.** O `kmplib-sync` declarava `api(project(":kmplib-monetization"))` ("o banner de sincronização
respeita a cota do plano"), mas **não usava nenhum símbolo do módulo**: a cota (402) chega como
`DomainResult.Quota(QuotaExceeded)`, e os dois moram no `kmplib-core` desde a modularização. A aresta só
arrastava o RevenueCat (`purchases-kmp`) e a permissão `com.android.vending.BILLING` para todo app com banco
local — e impedia de usar a outbox o app que NÃO PODE ter compra no binário (o app do aluno do App do
Personal: a compra é do personal, Apple 3.1.3; roadmap 1.1/1.10).

**O que mudou.** Só o `sync/build.gradle.kts`: a dependência saiu. Grafo `kmplib-sync` (runtime) depois:
`kmplib-core`, `kmplib-mask`, `kmplib-platform`, `kmplib-ui` — **zero** RevenueCat/purchases.

**Compatibilidade.** Nenhuma API muda. Quem pegava `PaywallScreen`/`MonetizationManager`/`UsageMeter`/…
**de carona** no `kmplib-sync` deixa de compilar e precisa declarar `kmplib-monetization` (ou o umbrella).
Conferido no monorepo em 09/out: os 8 apps que declaram `kmplib-sync` por módulo (ExtinRota, Escuta,
FolhaDeAxe, App do Personal, RedeDeOfertas, MinhaEstadia, Vitalis, QueiMap) declaram também
`kmplib-monetization` no mesmo módulo — nenhum quebra. O umbrella `kmplib` continua trazendo tudo.

**Sem piso** (empacotamento; ninguém que já está validado é afetado — mesmo critério da 2.260.0).

## 2.266.0 — `workout`: bi-set e circuito por VOLTA, drop-set com ESTÁGIOS (L-WK1, corrige a 2.263.0)

**O defeito.** Desde a 2.263.0 o `GuidedWorkoutEngine` percorria bi-set e circuito **por exercício**
(rosca série 1, 2, 3 e só depois o tríceps), sem descanso nenhum dentro do bloco, e o drop-set não tinha
estágio: fazia todas as séries do exercício emendadas, sem descanso entre elas. O treino guiado do App do
Personal (design `wireframes.md` B.0, contrato `contratos-onda-1.md` §4.6) e a validação de sessão do
backend dependem da regra certa.

**A regra agora (uma só, em `WorkoutPlan.executionOrder()`, usada pelo motor e pelo backend):**

| Método | Ordem | Descanso |
|---|---|---|
| `NORMAL` | exercício → série | `restSeconds` do exercício depois de cada série |
| `DROP_SET` | exercício → série → **estágio** | nenhum entre estágios; `restSeconds` depois do último estágio |
| `BI_SET`/`CIRCUIT` | **volta** → exercício | nenhum dentro da volta; depois do último exercício da volta, o `restSeconds` DELE |

Voltas = maior número de séries entre os exercícios do bloco (`Block.roundCount`); o exercício com menos
séries fica fora das voltas que faltam. O descanso da última série/volta de um bloco também separa o bloco
seguinte; o último passo do plano termina sem descanso. Exercício pulado sai das voltas que faltam (se ele
fechava a volta, o descanso passa a vir depois de quem de fato a fechou); pular nunca abre descanso.

**API (pacote `br.com.codecacto.kmplib.workout`):**

- **Modelo:** `DropStage(reps, load)`; `SetTarget.Reps(reps, load, stages = emptyList())` +
  `SetTarget.Reps.dropSet(stages)`; `SetTarget.hasStages`/`stageCount`/`stageTarget(i)`;
  `Block.isRoundBased`/`roundCount`; `SetResult.stageIndex: Int?` e `SetResult.roundIndex: Int?` (um
  `SetResult` POR ESTÁGIO, com a meta daquele estágio — volume conta cada um com a carga dele).
- **Motor:** `Cursor(blockIndex, exerciseIndex, setIndex, stageIndex = 0)` + `Cursor.set(plan)`;
  `ExecutionStep` (cursor, exercício, `exerciseCount`, `setCount`, `roundIndex`/`roundCount`,
  `stageIndex`/`stageCount`, `target` do estágio, `isLastStage`) = "Volta 2 de 3", "Série 2 de 3 ·
  Estágio 2" sem a tela refazer regra; `WorkoutPlan.executionOrder()`/`stepAt(cursor)`/
  `roundExercises(cursor)` (a lista do circuito)/`plannedSetCount()`;
  `GuidedWorkoutEngine.upcoming(plan, cursor, run)` ("Próximo: …") e
  **`restAfter(plan, cursor, run)`** (0 = "Concluir estágio", >0 = abre descanso). `CompleteSet` conclui
  o passo corrente (série ou estágio).
- **Backend (A5):** `validateRun(plan, run): List<RunIssue>` (`PlanMismatch`, `UnknownStep`,
  `DuplicateStep`, `NegativeDuration`, `UnknownSkippedExercise`) — a régua do `INVALID_SESSION`;
  `WorkoutRun.completedSetCount()` (drop-set conta a série UMA vez, mesma régua do `plannedSetCount`);
  `reconcile` passou a contar série, não estágio.
- **Protocolo:** `WORKOUT_PROTOCOL_VERSION = 2`; `StateSnapshot.stageIndex` (default 0 — a mensagem v1 lê
  como estágio 0) + `StateSnapshot.cursor`; `PlanSnapshot` leva os estágios.

**Outras correções de passagem:** `start` em plano sem nenhuma série termina em `Finished` (antes lançava
índice fora do intervalo) e pula bloco vazio; `Undo` sem passo registrado não volta mais ao início do
plano depois de um pulo (devolve o estado igual); `Undo` de volta a um exercício pulado o devolve ao
treino. A suíte do módulo **nunca tinha compilado em nativo** (vírgula e parênteses em nome de teste —
`Name contains illegal characters` no `compileTestKotlinIosArm64`); corrigido, 69 casos em `commonTest`.

**Fatia JVM publicada no mavenLocal deste servidor** (`br.com.codecacto:kmplib-workout-jvm`, alvo puro,
sem Mac) para o backend do App do Personal vendorizar. A release completa (iOS/watchOS) segue sendo do Mac.

**Migração:** sem consumidor em produção. Fonte compatível para quem só lia `Cursor(b, e, s)` e
`exercise.sets[setIndex]` (o `wear-molde` da casca); mudou o COMPORTAMENTO de `BI_SET`/`CIRCUIT`/`DROP_SET`
— é o conserto. Piso em `docs/pisos.yaml` (símbolos do motor).

## 2.265.0 — `ui`: `QuestionnaireRunner` — o runner de questionário da weblib, no app, sobre o MESMO JSON (GAP-VIT-K05)

**Por quê.** O Vitalis (app do médico/secretária, Onda 1) roda a triagem da secretária (AM-13) e a
pré-consulta preenchida dentro da consulta (AM-10/11) no app, e o servidor manda o formulário
(modelo → bloco → pergunta) no MESMO JSON que o `QuestionnaireRunner` da weblib consome — um contrato
para web e app. Sem o par na kmplib, cada app reescreveria bloco → pergunta → condicional → escore na
tela, e as duas pontas dariam escores diferentes para a mesma resposta.

**Pacote novo `br.com.codecacto.kmplib.ui.questionnaire` (artefato `kmplib-ui`; o umbrella traz):**

- **Contrato** `Questionnaire`/`QuestionnaireBlock`/`QuestionnaireQuestion`/`QuestionnaireScale` — campo a
  campo os da weblib: um documento da weblib de hoje roda sem mudança. **Extensões, todas opcionais e com
  default = comportamento da weblib:** `type` (`scale` default, `choice`, `multi-choice`, `number`, `text`,
  `date`; tipo desconhecido vira `UNSUPPORTED` — aviso na tela, nunca obrigatório), `options` com `score`,
  `scale` por pergunta, `min`/`max`/`decimals`/`unit`, `multiline`/`maxLength`, `visibleIf` em bloco e
  pergunta (`question`/`context`/`score` + `equals`/`notEquals`/`in`/`gt`/`gte`/`lt`/`lte`/`contains`/
  `answered` + `all`/`any`/`not`; sem resposta toda comparação é falsa), `reserved` em bloco e pergunta (o
  SERVIDOR filtra; o runner só marca "pendente") e `scores[]` (soma + `products` peso × resposta × resposta
  + `bands` com `min`/`max`/`tone`/`when`; incompleto não tem faixa). `QuestionnaireJson` (leitura tolerante:
  campo, tipo e tom desconhecidos não derrubam), `QuestionnaireValue` (resposta crua no fio — régua =
  número, inteiro sem `.0`, como o JS), `QuestionnaireAnswerItem` (`{questionId, value}` = `AnswerBatchItem`;
  `value` nulo = resposta apagada), `QuestionnaireAnswersSerializer` (mapa tolerante para DTO do app).
- **Motor puro** `questionnaire.evaluate(answers, context)` → `QuestionnaireEvaluation`: visibilidade com
  cascata (resposta de pergunta oculta não existe) e guarda de ciclo, `progress`, `errorOf`, `pendingIn`,
  `scores`, `reservedPending`, `firstUnansweredStep` (retomada), `effectiveAnswers`, `hiddenAnsweredIds`,
  `holds`. `questionnaire.validate()` → `QuestionnaireIssue` (o runner registra no log).
- **MVI** `QuestionnaireRunnerState` (imutável, mora no ViewModel; `start(…, resume)`, `reduce(action)` →
  `QuestionnaireRunnerUpdate(state, event)`), ações `Answer`/`EditNumber`/`Next`/`Back`/`AutoAdvance`/
  `GoTo`/`FocusHandled`, eventos `Answered` (→ fila) / `Finished(answers, cleared)` / `Exited`,
  `QuestionnaireFocusRequest` (pedido de foco como estado, a recomendação oficial do Android),
  `QuestionnairePosition`, `QuestionnairePace` (`Auto`/`Grouped`/`OneByOne`).
- **Tela** `QuestionnaireRunner(state, onAction, …, pace, autoAdvance, notice, showScores, respondentName,
  contentPadding, texts)` — stateless; agrupado em `LocalIsCompact`, uma por vez fora dele (avanço
  automático 260 ms, nunca na última do bloco); régua em linha (`LikertScaleField`) ou empilhada com o
  rótulo de cada ponto no celular; erro NO campo só depois do envio, limpo ao editar (só aquela pergunta),
  rolagem + foco na primeira pendente, "Faltam N" em região viva; marca da reservada e "Respondida por…";
  escore com o tom do tema (sem `Color(0x…)`); `QuestionnaireTestTags` (`questionario-*`, por pergunta);
  `rememberQuestionnaireTexts()` em pt-BR/en/es/pt-PT; `LocalReduceMotion` desliga a transição.
- **Fila** `QuestionnaireAnswerQueue(scope, save)` — o `useAnswerQueue` da weblib: uma requisição por vez,
  o que chega no meio se junta (mais novo vence), o que falhou volta sem sobrescrever o novo, retentativa
  sozinha 0,5 → 8 s, `flush()` antes de concluir.

**Onde diverge da weblib, de propósito (registrado para o `lib-web` alinhar):** "Voltar" entre blocos no
ritmo uma-por-vez cai na ÚLTIMA pergunta do bloco anterior (lá, na primeira); responder limpa o erro só
daquela pergunta (lá, todos); "Concluir" confere o questionário inteiro e descarta resposta de pergunta
oculta.

**Limites conhecidos:** `number` sem sinal (o `NumberField` não aceita "-"); data escolhida não se apaga;
anexo/foto, tabela e lista repetível ficam fora do runner (a tela do app desenha).

**Aditivo — nenhuma API existente muda; sem piso.** O módulo `ui` passa a aplicar o plugin
`kotlinx.serialization` (o runtime já vinha por `core`). **De passagem:** dois nomes de teste com vírgula
no `OnBrandContainerColorTest` (2.262.3) quebravam o `compileTestKotlinIosArm64` do `ui` (o Kotlin/Native
não aceita vírgula em nome de função) — renomeados.

Testes: 102 novos em 9 classes — contrato do fio, condições, avaliação, **as 12 escalas do Vitalis como
vetor de conformidade** (PHQ-2, GAD-2, Epworth, STOP-Bang, IPAQ curto, AUDIT-C, Bristol, ADAM, AMS, IIEF-5,
IPSS, MRS — o mesmo documento tem de dar o mesmo escore na web e no servidor), reducer, fila, validação,
textos e paridade pt-BR. `ui`: 844 testes, 0 falha. Compilados: Android, `compileKotlinIosArm64` e
`compileTestKotlinIosArm64` (sem `SKIPPED`). Catálogo: `kmplib-catalog` → `references/ui-questionnaire.md`.

## 2.264.0 — `health` (NOVO): Health Connect (Android) + HealthKit (iOS), leitura/escrita pós-treino

**Por quê.** Par do `kmplib-workout` (2.263.0): o treino guiado precisa ler FC e calorias do
relógio/repositório de saúde depois da sessão, e gravar a nossa sessão quando o relógio não tiver
gravado uma. Fase 1 do relógio no App do Personal (`05-relogios-integracao.md`).

**Módulo novo `kmplib-health`**, Android + iOS (**sem watchOS** — a sessão ao vivo é nativa e fica
no projeto). `HealthRepository` (`isAvailable`, `requestPermissions`, `readSessionMetrics`,
`hasDeviceWorkoutOverlapping`, `writeWorkout`) com `HealthMetric.Unavailable`/`NoData`/`Value<T>`
("sem dado ainda" é estado, nunca zero) e `EnergyReading`/`HeartRateSummary`. Depende de
`kmplib-workout` por `EnergySource`/`ExerciseCategory`.

- **Android**: Health Connect (`connect-client` 1.1.0 — exige minSdk 26 no app consumidor, maior
  que o 24 padrão; artefato separado, só quem declara sobe o minSdk). Permissão pelo
  `ActivityResultRegistry` direto (mesmo padrão do `PermissionManager` da `kmplib-platform`, sem
  depender de registro antes do `onCreate`). `initKmpLibHealth(context)` + `HealthActivityHolder`
  (holder próprio deste módulo).
- **iOS**: HealthKit chamado DIRETO do Kotlin via cinterop (sem Swift), compilado neste servidor.
  Duas armadilhas de cinterop novas (documentadas em `ios-cinterop.md` §3/§6 e no catálogo):
  `HKQuery.predicateForSamplesWithStartDate`/`HKUnit.kilocalorieUnit` exigem import do nome da
  função + `.Companion.` explícito (categoria ObjC); e `Energy.inKilocalories` (Android), não
  `.kilocalories` — o getter JVM bate (`getKilocalories()`), mas o nome Kotlin é outro.
- **Armadilha de versão**: `kotlinx.datetime.Instant` virou `typealias` de `kotlin.time.Instant` na
  0.7.1, e as extensões `toJavaInstant()`/`toNSDate()` mudaram de pacote — a falha de resolução
  cascateava em erros enganosos mais abaixo no código. Conversão por **época** em vez da extensão,
  nos dois módulos (ver `references/health.md`).

**O que NÃO foi escrito nesta rodada, de propósito:** `HeartRateMonitor` (FC ao vivo por BLE, perfil
0x180D/0x2A37) — protocolo GATT/CoreBluetooth sem aparelho físico para validar é risco alto de erro
silencioso; fica para o spike 0.7 (Onda 0 do App do Personal), com cinta/relógio reais em mãos.
`hasDeviceWorkoutOverlapping` no iOS devolve sempre `false` (leitura de `HKWorkout` por intervalo
não escrita ainda) — `writeWorkout` iOS não deve ir a produção antes disso.

Compilado neste servidor (Android + `compileKotlinIosArm64`, sem `SKIPPED`); sem teste automatizado
nesta rodada (integração real de SDK de saúde, sem mock disponível no projeto — validação é por
aparelho, no spike 0.7). CHANGELOG + `kmplib-catalog` (`references/health.md`) no mesmo commit.

## 2.263.0 — `workout` (NOVO): domínio puro do treino guiado, compilando também para watchOS e jvm

**Por quê.** O App do Personal (projeto novo, `Estudo-App-Personal/`) precisa da MESMA máquina de
estados do treino guiado no celular, no relógio (Wear OS e watchOS) e no backend (conciliação
planejado × feito). Promovido direto como fundação — sem esperar um 2º consumidor — porque é
exatamente esse o ponto que quebra se divergir: o celular diz "série 3", o relógio diz "série 4".
Estudo completo: `Estudo-App-Personal/06-relogio-kmp-e-biblioteca.md`.

**Módulo novo `kmplib-workout`**, domínio puro (só `kotlinx.serialization` + `kotlinx.datetime`,
**sem Compose/Koin/Ktor/persistência**), com o convention plugin novo `kmplib.module.pure`:

- `model`: `WorkoutPlan`/`Block`/`ExerciseStep`/`SetTarget.Reps|Timed`/`WorkoutRun`/`SetResult`/`SkippedExercise`.
- `engine.GuidedWorkoutEngine.reduce(state, event, now)`: `Idle→Ready→InSet→Resting→…→Finished`,
  mais `Paused`/`Undo`; descanso por método do bloco (`NORMAL` sempre descansa; `BI_SET`/`CIRCUIT`
  não descansam entre exercícios do bloco; `DROP_SET` não descansa entre séries); `restEndsAt`
  **absoluto** (nunca "segundos restantes"); nunca lê o relógio do sistema (`now` vem de fora).
- `protocol`: `PlanSnapshot`/`StateSnapshot`/`Command`/`Ack`/`Metrics` versionados (`v`), com
  `seq` crescente e `SequenceGuard` para deduplicar mensagem fora de ordem no canal nativo
  (WatchConnectivity/Data Layer).
- `energy.CalorieEstimator`: prioridade dispositivo → FC (Keytel et al. 2005, com teto de 2x a
  estimativa por MET) → MET (*2024 Adult Compendium of Physical Activities*, citado, não
  republicado); `Estimate(kcal, low, high, source)`, nunca sem a fonte.
- `metrics`: `WorkoutRun.volumeKg()`/`heartRateAvgOverall()`/`timeUnderTensionSeconds()`,
  `reconcile(plan, run)` (planejado × feito por exercício).
- `mapping.HealthPlatformMapping`: categoria → nome da constante Health Connect/HealthKit/chave
  Garmin (devolve `String`/`null`, nunca a constante do SDK — este módulo não pode depender dele).

**Convenção de build nova — `kmplib.module.pure`** (`build-logic/convention/.../kmplib.module.pure.gradle.kts`):
mesma base do `kmplib.module` (Android + Apple + publicação Maven), com `jvm()` e os 3 alvos watchOS
(`watchosArm64`/`watchosDeviceArm64`/`watchosSimulatorArm64`) sob a MESMA trava de host
(`HostManager.hostIsMac`/`-Pkmplib.forceAppleTargets=true`). Só módulo de domínio puro usa este
plugin — `compose-runtime` não existe para `watchosDeviceArm64`.

**Compilado neste servidor, sem Mac** (`compileKotlinIosArm64`/`compileKotlinWatchosArm64`/
`compileKotlinWatchosDeviceArm64`/`compileKotlinWatchosSimulatorArm64`, nenhum `SKIPPED` no log) —
o que falta é só o link final (Xcode) e a validação em aparelho físico, ambos do fundador.

**NÃO está no umbrella `kmplib`** (como `kmplib-video-download`): artefato de propósito específico,
declarado só por quem tem treino guiado. Testes em `commonTest`, roda em Android e `jvm`
(`./gradlew :kmplib-workout:testDebugUnitTest :kmplib-workout:jvmTest`), cobertura total da máquina
de estados (tabela evento → estado esperado, inclusive `BI_SET`/`DROP_SET`/`CIRCUIT`/`Undo`/`Pause`)
e do `CalorieEstimator` (caso de referência: 60 min, 70 kg, esforço 6 → 350 kcal, fonte MET, com
faixa). Catálogo: `kmplib-catalog` → `references/workout.md`.

## 2.262.5 — `ui`: `FormContainer(contentPadding = innerPadding)` — teclado não soma mais o banner e a barra de gestos

**Por quê.** O `FormContainer` aplica `imePadding()` + `verticalScroll` por dentro, e o próprio KDoc mandava usar
`FormContainer(modifier = Modifier.padding(innerPadding))` debaixo de um `Scaffold`. `Modifier.padding` não CONSOME
inset: com o teclado aberto, teclado + `bottomBar` (banner) + barra de gestos se somavam e o formulário virava uma
tira, com o "Salvar" inalcançável. Corrigido app a app em 10 apps em 08/out/2026 (Tanque Cheio `8380347`, ExtinRota
`9e38b38`, PontoFirme `f208b49`, TorneioDePenalti `bcd908e`…) com `consumeWindowInsets(innerPadding)`.

**Aditivo — nenhuma chamada quebra.** Parâmetro novo `contentPadding: PaddingValues = PaddingValues(0.dp)` (antes do
`content`): aplica `padding(it).consumeWindowInsets(it)` ANTES do `imePadding` interno — o padrão oficial do Compose
WindowInsets. Zero (default) não acrescenta nó à cadeia. KDoc com o exemplo certo.

**Migrar (quando o app for tocado):** `FormContainer(modifier = Modifier.padding(innerPadding))` →
`FormContainer(contentPadding = innerPadding)`. Quem já passa `Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)`
está certo e pode ficar. `LoginScreen`/`RegisterScreen` já usavam `windowInsetsPadding` (que consome) — sem mudança;
não há outro `imePadding` na lib.

Testes: `FormContainerContentPaddingTest` (4 — `isZero` nos 4 lados/2 direções, zero não mexe na cadeia, padding
do Scaffold = padding + consumo).

## 2.262.4 — `platform`/`signature`: o `SignaturePad` passa a ter semântica de acessibilidade

**Por quê.** O `SignaturePad` era um `Canvas` só com `pointerInput`: sem nome, o TalkBack/VoiceOver não anunciava
o quadro e, no iOS, o nó podia sumir da árvore de acessibilidade — levando junto o `testTag` do app (o Maestro não
achava `assinatura-pad-emitente`). O ReciboFácil contornou no app (`8a8c1cf`) com `semantics { contentDescription }`
no `modifier`; ChecklistVeicular (`SignatureScreen.kt`) e ExtinRota (`OsAssinaturaContent.kt`/`OsDevolucaoContent.kt`)
têm o mesmo quadro mudo.

**Aditivo — nenhuma chamada existente quebra.** Parâmetros novos no fim da assinatura, com default:

- `contentDescription: String` — nome do quadro; default "Quadro de assinatura" no idioma da tela. Com mais de um
  quadro, passe o que os distingue ("Assinatura do emitente").
- `texts: SignaturePadTexts` (`rememberSignaturePadTexts()` / `signaturePadTexts(languageTag)`) — nome, estado e
  rótulos das ações em pt-BR, en, es e pt-PT, escolhidos por `appLanguageTag()` (o mesmo `FactoryLocales.match` da
  pasta do compose-resources; o `kmplib-platform` não tem `Res` próprio).
- Semântica no MESMO nó do `Canvas`, aplicada ANTES do `modifier` do app (o `testTag` do app fica nesse nó):
  `contentDescription`; `stateDescription` "Sem assinatura"/"Assinado"; com traço, as ações de acessibilidade
  **"Limpar assinatura"** e **"Desfazer último traço"** (menu de ações do TalkBack / rotor do VoiceOver). Sem `role`
  (o Compose não tem papel para superfície de desenho; `Role.Image` anunciaria "imagem").

**Migrar:** tirar `semantics { contentDescription = … }` do `modifier` do pad e passar `contentDescription = …` — a
semântica da lib vem antes na cadeia e prevalece sobre a do `modifier`.

Testes: `SignaturePadSemanticsTest` (4, androidUnitTest — nome, estado, ações chamando `clear`/`undo`, ordem lib →
app com o `testTag` intacto) + `SignaturePadTextsTest` (4 — 4 idiomas e queda em pt-BR). platform 439 verdes;
Android + iosArm64 (inclusive testes iOS) compilados.

## 2.262.3 — `ui`/tema: `on*Container` legível sobre o container tonal da marca, nos temas claro e escuro

**Por quê.** Sinaleiro (08/out): o cartão "Quick" do `CommunicationTile` na Home usa `secondaryContainer`/
`onSecondaryContainer`, e o tema montava o par como a marca a 10% + a própria marca crua. Com marca clara
(amarelo `#EAB308`) o texto dava ~1,7:1. O mesmo valia para `primary`/`tertiary`/`errorContainer`, e no escuro
(marca a 90% sobre a marca a 20%) com marca escura. O app contornou sobrescrevendo `onSecondaryContainer`.

**API pública inalterada** — nada a mudar no app além do bump (e retirar contorno local, se houver).

- Cada `on*Container` passa a ser a **cor da marca escurecida (tema claro) / clareada (tema escuro)** só o
  necessário para ≥ 4,5:1 sobre o container **como aparece** — a marca translúcida composta sobre o **fundo** E
  sobre a **superfície** (`#FAFAFA`/branco no claro, `#121212`/`#1E1E1E` no escuro, ou as `lightSurfaces`/
  `darkSurfaces` do app). Usa `ColorContrast.adjustForContrast` (mistura com o neutro → o matiz da marca fica).
- Marca que já contrastava volta **intacta** no claro (azul, roxo, marinho…). No escuro o `on*Container` deixa de
  ser a marca a 90% de alpha e passa a ser **opaco** (diferença imperceptível onde já era legível).
- Alto contraste (`highContrast = true`) não muda.

Testes: `OnBrandContainerColorTest` (6 — caso Sinaleiro, 11 marcas claras/escuras/extremas nos dois temas, com e
sem superfícies próprias, paletas prontas, marca legível intacta, direção e matiz). ui 738 verdes; Android +
iosArm64 compilados.

## 2.262.2 — `ui`/tema: `onPrimary`/`onSecondary`/`onTertiary`/`onError` por contraste WCAG, nos temas claro e escuro

**Por quê.** Teste do Minha Ficha (08/out): no tema escuro o `createDarkColorScheme` fixava os quatro `on*` em
`#1C1C1C` qualquer que fosse a marca — botão vermelho `#DC2626` com texto ~3,6:1 e secundária `#111827` com texto
praticamente invisível. O `createLightColorScheme` tinha o espelho do defeito: branco fixo, ilegível sobre
amarelo/âmbar/verde-claro. Todo app com tema escuro (ou marca clara no tema claro) herdava.

**API pública inalterada** — nada a mudar no app além do bump.

- Cada `on*` passa a ser escolhido entre **branco** e o quase-preto **`#1C1C1C`** pelo MAIOR contraste WCAG
  (luminância relativa) sobre a cor **exibida**: no claro, a marca; no escuro, a marca a 90% composta sobre o fundo
  (`#121212`, ou `darkSurfaces.background` quando o app informa). Se nem o melhor dos dois chega a 4,5:1 (faixa
  estreita de luminância ~0,18), o quase-preto cede ao preto puro — o par branco/preto garante ≥ 4,58:1 para
  qualquer cor.
- **Muda o visual (para melhor)**: no escuro, marca escura/saturada passa a ter texto branco (antes quase-preto);
  no claro, marca clara (âmbar, amarelo, verde `#10B981`) passa a ter texto quase-preto (antes branco). Marcas que
  já davam contraste continuam iguais (ex.: Teal claro segue branco).
- A paleta não tem override de `on*` (nunca teve): app que quer outro tom continua usando `contentColor` no
  componente. Alto contraste (`highContrast = true`) não muda.

Testes: `OnBrandColorTest` (8 — vermelho/azul-escuro → branco, âmbar/verde/amarelo → quase-preto nos dois temas,
fundo de superfícies próprias, paletas prontas, varredura de 256 cinzas e de 216 matizes ≥ 4,5:1). ui 732 / auth
295 verdes; Android + iosArm64 compilados.

## 2.262.1 — `ui`/`brdata`: campo numérico que não engole dígito no iOS — `NumberField`, `DigitBoxField`, `AddressFields`

**Por quê.** Fila de teste do Mac, Meu Controle `e24adea` (07/out): "120" digitado no `NumberField` virava "10" no
iOS. É o mecanismo da busca da 2.258.0: o campo era CONTROLADO pelo `value` do `StateFlow` do ViewModel, o dígito
voltava um quadro depois e, na digitação em rajada (Maestro ou pessoa), o campo era recomposto com o valor velho e a
tecla seguinte caía sobre ele. ~43 apps usam `NumberField`.

**API pública inalterada** — os apps continuam passando `value`/`onValueChange`; nada a mudar no app além do bump.

- **`NumberField`** — `OutlinedTextField(state = rememberSyncedTextFieldState(value, onValueChange))` (texto no
  campo, forma oficial *state-based TextField*) + **`InputTransformation`** com o filtro de sempre: só algarismos
  (inteiro) ou algarismos e UMA vírgula (decimal); fora de `minValue`/`maxValue` a tecla é desfeita no próprio
  campo. `value` que muda de fora (reset do formulário, edição carregada) reescreve o campo com o cursor no fim; o
  eco atrasado do que foi digitado é ignorado (`TextInputReconciler`). `keyboardActions` segue valendo (adaptado
  para `onKeyboardAction` — `KeyboardActions.toKeyboardActionHandler`, interno). **Melhoria:** no decimal, "."
  digitado vira "," quando o texto não tem vírgula — o teclado decimal do iOS mostra "." em região que usa ponto e
  o ponto era descartado em silêncio; "1.234,56" colado continua virando "1234,56". O contrato do `value` segue com
  vírgula (`toDoubleFromNumberField`).
- **`DigitBoxField`** — os algarismos moram no campo (estado local + `TextInputReconciler`), mesmo contrato.
- **`AddressFields`** — o CEP virou campo com `TextFieldState` + `InputTransformation` (8 algarismos; colar
  "78000-000" guarda "78000000") + `OutputTransformation` (máscara 00000-000, oficial no lugar da
  `VisualTransformation`); logradouro/número/complemento/bairro com `keepTextLocally = true`. A busca de CEP e o
  preenchimento por ela seguem iguais (valor de fora é aplicado).
- Não há `MoneyField`/`PercentField` na lib: o módulo `mask` só tem `VisualTransformation` + filtros puros. Campo
  numérico/mascarado montado no APP com `AppTextField(value = state.x, …)` tem o mesmo defeito e se corrige no app
  (`keepTextLocally = true`, ou `rememberSyncedTextFieldState` + `inputTransformation` se o ViewModel recusa a tecla).

Testes: `NumberFieldInputTest` (13 — filtro, recusa por faixa, ponto→vírgula, digitação rápida contra ViewModel
atrasado sem perder dígito, reset externo, valor externo com cursor no fim, `DigitBoxField` em rajada, adaptador de
`KeyboardActions`) e `CepTransformationsTest` (3). Suítes `ui` 724 e `brdata` 195 verdes; Android +
`compileKotlinIosArm64` (ui, brdata) compilados.

## 2.262.0 — `platform`: modo automação ligado pelo RUNNER DE QA (`AutomationSignal.QA_RUNNER`), sem linha no app

**Por quê.** O `AutomationMode` (2.238.0) só ligava pelo dublê da loja (app que vende pela loja), pelo Test
Harness do Android (o runner não pode ligar: apaga o aparelho) ou por `activate()` no app. Os ~46 apps que só
vivem de anúncio ficavam sem o modo; com `clearState: false` o contador de avaliação sobrevive entre rodadas e o
"Está gostando da experiência?" abria no meio da suíte Maestro (Lua Certa, 07/out). A correção por subflow em
cada app é exatamente o que não se quer repetir 46 vezes.

- **`AutomationSignal.QA_RUNNER`** — 4º sinal, lido a cada consulta (folga `QaRunnerSignal.CACHE_TTL` = 5 s,
  porque o `AppReviewDialog` consulta na composição). Nomes do contrato em **`QaRunnerSignal`**
  (`ANDROID_PROPERTY` = `debug.codecacto.automacao`, `IOS_DEFAULTS_KEY` = `CodecactoAutomacao`, `isOnValue`).
  - **Android:** o runner faz `adb shell setprop debug.codecacto.automacao 1` (espaço `debug.*`: `shell` grava,
    app lê, some no reboot); a lib lê pelo binário `getprop` (`ProcessBuilder`, API pública — sem reflexão em
    `SystemProperties`, fora do SDK; `Settings.Global` descartado: app comum não lê chave não pública no 12+).
    **Só em app depurável** (`ApplicationInfo.FLAG_DEBUGGABLE`, lido do `Context` que `initKmpLibPlatform`/
    `KmpLib.init` registram — sem ele, desligado). Não há detecção oficial de emulador no Android.
  - **iOS:** o runner grava `xcrun simctl spawn <udid> defaults write -g CodecactoAutomacao -bool YES`
    (domínio GLOBAL — vale para app recém-instalado/reinstalado); a lib lê `NSUserDefaults.standardUserDefaults`.
    **Só em binário Kotlin debug** (`Platform.isDebugBinary`), **só em alvo de simulador** (decidido na
    compilação: `iosArm64` não tem o caminho) e só num processo `.app` (o `test.kexe` da suíte não conta).
  - **Nunca vale em release:** em binário não depurável a marca nem é lida (`getprop`/`NSUserDefaults` não rodam).
  - **Efeito:** o mesmo dos outros sinais, e só ele — `suppressesAutomaticPrompts` (avaliação e atualização
    opcional). Anúncio, paywall, cobrança e atualização obrigatória não mudam. Declaração (`activate`, dublê)
    vence a marca; `reset()` não a desliga (é do aparelho).
- Runner: `Ferramentas/qa-runner` liga a marca no aparelho antes de cada execução (`src/sinal-automacao.ts`).
- Testes: platform 431 (+13: `QaRunnerSignalTest` 7, `QaRunnerSignalAndroidTest` 3, `AutomationModeTest` +3) +
  `QaRunnerSignalIosTest` (compilado; roda no Mac) — verdes; Android + iosArm64/iosSimulatorArm64/iosX64 compilados.
- Aditivo: nenhum app precisa mudar. App que compila a kmplib pela fonte (`includeBuild`) ganha o sinal no
  próximo build.

## 2.261.0 — `auth`/`core`: reautenticação para ação sensível (step-up, par do `requireRecentAuth` da backlib 0.151.0)

**Por quê.** A backlib passou a exigir, nas rotas que não se desfazem (primeiro: excluir a conta), que a pessoa
tenha provado a credencial há pouco (`auth_time` do access token; o refresh NÃO o renova). Fora da janela a rota
responde **401 `REAUTH_REQUIRED`** (`details.maxAgeSeconds` + `WWW-Authenticate: Bearer
error="insufficient_user_authentication", …, max_age=<s>`, RFC 9470). Sem esta versão, esse 401 caía no
tratamento de sessão expirada: o `DomainApiClient` renovava e repetia (voltava o mesmo 401, gastando uma rotação),
o `RestRepository` chamava `onUnauthorized` (o app deslogava) e a exclusão de conta mostrava "Sessão expirada".

- **`core`** — `RecentAuthChallenge` (`matches(status, serverCode, wwwAuthenticate)`, `maxAgeSeconds(details,
  header)`; o cabeçalho é lido parâmetro a parâmetro, um `error=` dentro de outro texto entre aspas não conta),
  **`ReauthRequiredException(maxAgeSeconds)`**, `Throwable.isReauthRequired()`.
  - `DomainApiClient`: o 401 de reautenticação **não** renova nem repete; vira `DomainResult.Error` com
    **`isReauthRequired`**, `reauthMaxAgeSeconds`, `toReauthRequiredException()` e a frase
    `DomainApiTexts.reauthRequired` (traduzida pelo `loadDomainApiTexts()`). O 401 comum segue igual.
  - `ApiResult.Error` ganhou `serverCode`/`details` (com default — fonte compatível) + `isReauthRequired`/
    `toReauthRequiredException()`; `handleApiCall` os preenche e o `RestRepository` **não** chama
    `onUnauthorized` no 401 de reautenticação.
- **`auth`** — **`RecentAuthCoordinator`** (`ownAuth.recentAuth(social = socialSignIn)`):
  `withRecentAuth(prompt: ReauthPrompt) { ação }` executa; no `ReauthRequiredException` pede a credencial
  (`ReauthRequest` → `ReauthCredential.Password`/`.Social`), reautentica **sem adotar**, confere que o `sub` do
  token novo é o da sessão (`OwnAuthTokenManager.adoptIfSameAccount`, atômico sob a trava da renovação),
  troca, revoga a família antiga e **repete a ação UMA vez** — nova recusa volta como erro, sem laço. Outra
  conta → `ReauthAccountMismatchException`, tokens novos revogados; sessão encerrada no meio →
  `NotAuthenticated`; desistiu → `ReauthCancelledException` (`isReauthCancelled()`); senha errada/rede →
  pergunta de novo com `previousError`, até `maxAttempts` (5). Troca + revogação em `NonCancellable`.
  `SocialSignIn` implementa `SocialReauthenticator` (`reauthenticate(provider)`: mesmo fluxo, nos dois modos,
  devolvendo os tokens sem adotar).
  - **UI pronta:** `rememberRecentAuthState(coordinator)` → `RecentAuthState.run { ação }` +
    **`RecentAuthHost(state)`** — diálogo com a senha **com o olho**, "Concluído" envia, senha errada no campo,
    resto junto do botão, botões "Continuar com Google/Apple" (provedor da sessão primeiro), 4 idiomas
    (`RecentAuthTexts`/`rememberRecentAuthTexts()`, `errorMessage(e)` = `null` no cancelamento). Ids:
    `dialogo-input`/`dialogo-btn-confirmar`/`dialogo-btn-cancelar` + `reauth-btn-google`/`reauth-btn-apple`.
  - `AccountDeletionService`: wipe/exportação com `REAUTH_REQUIRED` → `Result.failure(ReauthRequiredException)`,
    nada apagado, sessão intacta.
- Testes: core 432 (+6), auth 295 (+15: `RecentAuthCoordinatorTest` 14, `AccountDeletionServiceTest` +1), ui 711,
  sync 217 — verdes; Android (todos os módulos) + iosArm64 (core/ui/auth/sync) compilados.
- **Pendência (security-review, Médio):** no caminho SOCIAL a reautenticação refaz o login normal — o provedor não
  é forçado a pedir a credencial de novo (`prompt=login`/`max_age=0`), nem o backend confere o `auth_time` do
  id_token do provedor. Por senha não há essa lacuna. Registrado em `docs/backlog.md` (mudança cruzada
  kmplib + backlib).

Sem aviso: aditivo (nada deixa de funcionar para quem está na 2.260.0; só passa a funcionar quando o backend ligar
`requireRecentAuth`). **Quem ligar `requireRecentAuth` num backend precisa do app nesta versão**, usando
`withRecentAuth`/`RecentAuthHost` na tela sensível — a casca já nasce assim.

## 2.260.0 — `sync`: deixa de depender do `kmplib-firebase` (app com banco local não leva mais `firebase-analytics`)

**Por quê.** O `kmplib-sync` declarava `api(project(":kmplib-firebase"))` só para usar três modelos de dados
(`UploadItem`, `UploadStatus`, `UploadRequest`) na fila REST e no `UploadProgressItem`. Com isso, todo app que
usa banco local (`LocalRepository`/SQLDelight) — mesmo declarando a kmplib por módulo — recebia o SDK do Firebase
inteiro, inclusive **`firebase-analytics`** (e a permissão `AD_ID` que ele mescla). Achado na revisão de segurança
do ABC Divertido (app infantil, declaração de dados sem analytics).

- `UploadItem`, `UploadStatus` e `UploadRequest` mudaram para o **`kmplib-core`**, **no mesmo pacote**
  (`br.com.codecacto.kmplib.firebase.storage`) — nenhum import muda; o `kmplib-firebase` continua expondo-os
  (ele tem `api(kmplib-core)`).
- `kmplib-sync` passa a depender só de `core`, `ui` e `monetization`.
- **Compatibilidade:** app que usa Firebase (Auth/Storage) e declarava só `kmplib-sync` passando a receber o
  Firebase de carona precisa declarar `kmplib-firebase` — conferido: os 5 apps da casa que usam `kmplib-sync`
  por módulo já o declaram; o umbrella `kmplib` segue com tudo.
- Suítes: core 426, firebase 28, sync 214 verdes; Android + iosArm64 compilados.

Sem aviso (ninguém deixa de funcionar).

## 2.259.1 — `monetization`: compra PROMOVIDA da App Store também passa pelo portão de pais

**Correção (achado de revisão de segurança, ABC Divertido).** Na 2.259.0 a compra promovida da App Store
(`PurchasesDelegate.onPurchasePromoProduct`, só iOS — inclui o *purchase intent*) chamava o `startPurchase` sem
passar pelo `ParentalGate`, também no modo infantil; os outros caminhos de compra (`purchasePackage`,
`purchaseProduct`, consumível, item) já exigiam `awaitPass()`.

- No modo KIDS o `startPurchase` da promo agora só roda **depois** de o adulto passar no portão; negado (errou,
  cancelou, sem host na tela, outro portão já aberto) a compra é **descartada** — o `startPurchase` não é chamado,
  que é como a RevenueCat trata "não comprar agora" — e fica um `AppLogger.i`.
- Vale também quando um delegate anterior do app recebe a promo: ele recebe o `startPurchase` já embrulhado.
- Callback não suspenso → mesmo padrão não suspenso das outras saídas (`ParentalGate.request`, par do `guard`),
  sem escopo de corrotina novo. Interno: `gatePromoPurchase`.
- Fora do modo infantil nada muda (compra na hora, mesma thread).
- Teste: `PromoPurchaseGateTest` (5).

**Quem precisa subir:** só app em `AppAudience.KIDS` (hoje só o ABC Divertido, não publicado). Sem aviso.

## 2.259.0 — `platform`/`ui`: PORTÃO DE PAIS para app infantil — `KmpLibAudience`, `ParentalGate`, `ParentalGateDialog`

**Por quê.** O ABC Divertido vai para a categoria INFANTIL (Google Play **Famílias** / Apple **Kids**), decisão do
fundador em 06/out/2026. As duas lojas exigem que, em app infantil, todo toque que leva para FORA do app (link,
loja, e-mail, compra, anúncio que abre site) passe por um **portão de pais** — um desafio que só adulto resolve
(Apple App Review Guideline 1.3 + Kids Category; Google Play Families Policy). A lib não tinha, e cada saída mora
num lugar diferente (house ad, "Desenvolvido por", "Avaliar", termos, compartilhar, compra).

**Novo (aditivo):**
- **`KmpLibAudience.configure(AppAudience.KIDS)`** (`platform.audience`) — uma linha na inicialização. Default
  `GENERAL`: nada muda para os apps atuais.
- **`ParentalGate`** — `guard { ação }`, `request(onResult)`, `awaitPass()` (suspenso), `pending`, `resolve`. Fora
  do modo infantil executa na hora (mesma thread, exceções como antes). No infantil: a ação só roda se o adulto
  acertar; errar/cancelar/fechar = não roda; segundo pedido com o portão aberto é negado (sem empilhar); **sem host
  na tela, NEGA** (fecha em segurança) e loga.
- **`ParentalGateDialog`** + **`ParentalGateHost`** (`ui.components`) — desafio "toque nos números nesta ordem:
  sete, dois, nove" (3 dígitos distintos de 1–9 por extenso, teclado 0–9 de 64×56 dp, andamento lido pelo leitor
  de tela), pelos wrappers de janela da lib, em pt-BR/en/es/pt-PT, sem coletar nada. `ParentalGateChallenge`
  (puro), `ParentalGateTexts`/`rememberParentalGateTexts()`, ids `ParentalGateTestTags` (`portao-pais`,
  `portao-pais-desafio`, `portao-pais-digito-<n>`, `portao-pais-btn-cancelar`; `portao-pais-digito-0` é sempre
  resposta errada — o flow Maestro usa). **O `AppTheme` já instala o host**; app com tema próprio chama
  `ParentalGateHost()` na raiz.
- `UrlLauncher.withParentalGate()` / `ShareHandler.withParentalGate()` para implementação própria do app.

**Automático no modo infantil (nenhuma linha por tela):** todo `getUrlLauncher()` (URL, e-mail, telefone,
WhatsApp, mapa, loja, assinaturas, Configurações) e todo `getShareHandler()` passam pelo portão — logo clique em
house ad (banner e intersticial), `DeveloperScreen`, `HtmlDocumentView`, `PaywallHost`, `PermissionBanner`,
`AppServiceGate`; `LocalUriHandler` do `AppTheme` (link em texto); "Entrar em contato" da `DeveloperScreen`
(formulário com nome/e-mail); compra no `RevenueCatPurchaseRepository` (negado = `Cancelled`); `AppReviewDialog`
não desenha o formulário (e-mail/WhatsApp) e vai à loja pelo portão; `AppReviewManager` não pede avaliação sozinho.
Embed do YouTube (`ui.components.video`, Android): "assistir no YouTube"/canal passa pelo portão no player inline;
na tela cheia (`KmplibVideoActivity`, Activity de View sem host do portão) a saída é negada.
House ads de app infantil só com criativos aprovados = `contentPolicy: CURATED` no apps-api (o app não sabe).

**Limite conhecido:** compra disparada por ponte NATIVA em Swift (fora do repositório da lib) não passa pelo
portão — app infantil que venda pelo Swift chama `ParentalGate.awaitPass()` antes.

## 2.258.0 — `ui`: busca que não perde letra — `AppSearchField`, `rememberSyncedTextFieldState`, `AppTextField(keepTextLocally)`

**Por quê.** MinhaOS, rodada R8 do teste do agente (06/out/2026, docs/42): o campo de busca era
`AppTextField(value = state.query, onValueChange = { onAction(QueryChanged(it)) })`. Cada letra fazia a ida e volta
pelo `StateFlow` do ViewModel e voltava ao campo um quadro depois; na digitação rápida do iOS o campo era recomposto
com o valor VELHO entre duas teclas e a letra seguinte caía sobre ele — "teste qa" virou "tete a" e a lista mostrou
"Nenhum resultado". A regra oficial do Google (*Effective state management for TextField* / *state-based TextField*)
é o texto morar na UI, atualizado no mesmo quadro, e o ViewModel só RECEBER. A lib não tinha campo de busca que
fizesse isso — todo app escrevia o seu sobre o `AppTextField` controlado (50 campos em 27 apps).

**Novo (aditivo, `br.com.codecacto.kmplib.ui.components`):**
- **`AppSearchField(query, onQueryChange, modifier, placeholder, label, enabled, helperText, onSearch, …)`** — o campo
  de busca padrão sobre o `OutlinedTextField(state = TextFieldState)` oficial: lupa, "x" que limpa (id
  `SearchFieldTestTags.LIMPAR` = `busca-btn-limpar`), teclado com ação Buscar, sem autocorreção, uma linha.
  Migrar = trocar `AppTextField(value = state.query, onValueChange = { … }, leadingIcon = Icons.Default.Search)`
  por `AppSearchField(query = state.query, onQueryChange = { … })`.
- **`rememberSyncedTextFieldState(text, onTextChange): TextFieldState`** — a mesma sincronização para o projeto que
  desenha o próprio campo (protótipo) com `BasicTextField(state = …)`. *Saveable*: sobrevive a rotação/morte de
  processo, e o texto restaurado é reenviado ao ViewModel que nasceu vazio.
- **`AppTextField(…, keepTextLocally = true)`** — o `AppTextField` de sempre com o texto local, para a busca que
  já tem estilo próprio (troca de uma linha). Default `false`: campo que RECUSA a tecla (`if (ok) set(it)`) depende
  do caminho controlado e continua nele.
- Regra de ressincronização (`TextInputReconciler`, interno): o valor de fora só reescreve o campo quando muda para
  um valor que ninguém digitou (limpar, restaurar, preencher pela escolha de um item, transformar); o eco atrasado
  — inclusive conflado pelo `StateFlow` — é reconhecido e ignorado.

**Corrigido:** `PlacePicker` (map) cancela a busca de endereço anterior a cada tecla — a resposta de "rua" chegando
depois da de "rua das flores" trocava a lista pela do termo velho.

**Auditoria dos componentes da lib:** `SearchTopBar` (texto no `SearchTopBarState`, local), `AppPickerField`
(busca do sheet em `remember` local — inclui a cidade do `AddressFields`), `AppDropdownField`/`AppMultiDropdownField`
(sem campo de busca) e `PlacePicker` já guardavam o texto na UI; o furo era a ausência de um campo de busca da lib.

**Teste:** `TextInputReconcilerTest` (11) — reproduz o defeito no modelo do campo controlado e prova que, com o
ViewModel atrasado de 1 a 5 teclas e com `StateFlow` conflado, "teste qa" chega inteiro; apagar e redigitar em
rajada, limpar/restaurar pela tela, valor transformado, estado restaurado. Suíte `kmplib-ui` 704 (0 falhas);
Android de todos os módulos; `compileKotlinIosArm64` de `ui`/`map`/`brdata` e `compileTestKotlinIosArm64` de `ui`
executados (não SKIPPED). **Não é crítico** (o defeito é do app que monta o campo; aditivo) → sem aviso.

## 2.257.0 — `ui`: invólucros das janelas do Material3 que o Maestro enxerga

**Por quê.** Teste do agente (06/out/2026, docs/42): no Android, `AlertDialog`, `DropdownMenu`,
`ExposedDropdownMenu`, `ModalBottomSheet`, `DatePickerDialog`, `Dialog` e `Popup` abrem OUTRA janela, que não herda
o `testTagsAsResourceId` ligado na raiz (`AppTheme`/`WithTestTagsAsResourceId`) — os ids das opções e dos botões
saem com `resource-id=""` e o flow reprova sem defeito de UX (Prospecta: opções da categoria; Meu Fisio: menu ⋮ e
diálogo de excluir; LocAki já tinha escrito um `LocakiAlertDialog` só para isso). Os componentes da lib já se
expunham desde a 2.234.0 (`DialogTestTags`); faltava o caminho para o app que chama o Material3 direto.

**Novo (aditivo, `br.com.codecacto.kmplib.ui.components`, arquivo `AppMaterialWindows.kt`):** mesma assinatura do
original (nomes, ordem e defaults), só acrescentam `Modifier.exposeTestTagsAsResourceId()` no nó-raiz da janela —
migrar é trocar o nome:
- `AppAlertDialog(onDismissRequest, confirmButton, …)` — sobrecarga do `AppAlertDialog(show, …)` pronto.
- `AppBasicAlertDialog(…)` (`@ExperimentalMaterial3Api`, como o original).
- `AppDialog(onDismissRequest, properties) { }` — sobrecarga do `AppDialog(show, …)`; conteúdo num
  `Box(propagateMinConstraints = true)` (o `Dialog` não tem `modifier`).
- `AppDatePickerDialog(onDismissRequest, confirmButton, …) { }` — sobrecarga do seletor pronto; experimental.
- `AppModalBottomSheet(…)` (experimental), `AppDropdownMenu(…)`, `ExposedDropdownMenuBoxScope.AppExposedDropdownMenu(…)`
  (experimental), `AppPopup(…)` (as duas sobrecargas do `Popup`).

Nenhum id é acrescentado (o `testTag` é do app) e nada mais muda — teclado, insets, cores. No iOS delegam ao original
(lá a `testTag` já vira `accessibilityIdentifier`).

**Auditoria das janelas da lib:** todas as 23 chamadas de janela própria nos módulos (`ui`, `platform`, `central`,
`media`, `video`, `ads`) já religavam a flag; o `ExposeTestTagsAsResourceIdTest` continua reprovando janela nova sem ela.

**Teste:** `AppMaterialWindowsTest` (8) — a lista de parâmetros de cada invólucro é comparada com a do Material3
lida do PRÓPRIO bytecode em uso (source information `C(X)N(…)` do compilador do Compose), então um bump do Material3
que acrescente parâmetro reprova aqui; e todo invólucro chama o original e religa a flag. Compilado Android +
`compileKotlinIosArm64`/`compileTestKotlinIosArm64` (executados, não SKIPPED). **Não é crítico** (aditivo) → sem aviso.

## 2.256.0 — `ui`: `FullScreenImageViewer` com botão de BAIXAR (opcional)

**Por quê.** LocaSys (06/out/2026): a listagem de produtos mostra a foto pequena, o toque amplia e dali tem de
dar para baixar o arquivo — o mesmo que a weblib 0.235.0 entregou no `ImageLightbox`.

**Novo (aditivo, sem quebra):**
- `FullScreenImageViewer(onDismiss, onDownload = null, isDownloading = false, content)`: com `onDownload`, o
  botão de baixar aparece ao lado do X (mesmo estilo); `isDownloading` troca o ícone por um indicador e
  desliga o botão. O viewer não sabe de onde a imagem vem (o `content` é livre), então quem chama busca os
  bytes e salva — `rememberFileSaver` (`platform/print`, SAF / seletor do iOS).
- `imageDownloadFileName(url, mimeType, nome)`: nome do arquivo (o dado, ou o último trecho do caminho — o
  Firebase codifica `/` como `%2F`), extensão pelo tipo, `/`, `\` e `:` viram hífen. Mesma regra da weblib.
- `DialogTestTags.BTN_BAIXAR` = `dialogo-btn-baixar` (flows Maestro).
- String `kmplib_download_image` nos 4 idiomas.

Quem não passa `onDownload` não vê diferença. Sem aviso.

## 2.255.0 — `ui`: rótulo da `AppBottomNavBar` nunca é cortado (fonte reduz até o piso) + insets laterais

**Por quê.** Print do teste do Meu Estacionamento no iPhone 17 Pro (06/out/2026): com 4 abas "Pátio · Mensalistas ·
Relatórios · Configurações", o rótulo "Configurações" encostava na borda direita do item e saía cortado (no
Android coube por pouco). O `maxLines = 1` + reticência da 2.213.0 só segurava a linha; não garantia o rótulo inteiro.

**Mudou (sem quebra de API):**
- O rótulo de cada item é **medido com o `TextMeasurer`** no estilo do tema (`labelMedium`, 12 sp) na largura que
  o item recebe. Cabe numa linha → fica igual. Não cabe → a fonte **reduz de 0,5 em 0,5 sp** até caber, com
  piso de **10 sp**; só abaixo do piso entra a reticência. Nunca quebra linha nem parte palavra. A altura de
  linha é a do tema — a barra não muda de altura. Cada item continua com a mesma fração da largura (`weight(1f)`
  do `NavigationBar`).
- **Insets:** default novo `BottomNavDefaults.windowInsets` = insets do Material (`systemBars` horizontal +
  embaixo) **∪ `displayCutout` horizontal** — em paisagem a Dynamic Island/notch não cobre o 1º/último item.

**Novo (aditivo):** `AppBottomNavBar(…, windowInsets, labelMinFontSize)`; `BottomNavDefaults.LabelMinFontSize`
(10 sp), `LabelFontStep` (0,5 sp), `LabelMaxFontSizeFallback`, `windowInsets`; função pura
`bottomNavLabelFit(maxSp, minSp, stepSp, fits): BottomNavLabelFit(fontSizeSp, ellipsized)`.

**Teste:** `BottomNavLabelFitTest` (7). Compilado Android + `compileKotlinIosArm64`/`compileTestKotlinIosArm64`
(executados, não SKIPPED). **Não é crítico** (não para nada; visual) → sem aviso.

## 2.254.0 — `ads`: banner que acompanha o conteúdo da lista · `ui`: `CommunicationTile` não parte palavra

**Por quê.** Pedido do fundador (06/out/2026, Favoritas do Piadaria): com a lista vazia sobra quase a tela inteira
em branco abaixo do estado vazio, e o banner de rodapé continuava pequeno. Com itens, o grande atrapalha a leitura.

**Novo (aditivo, `br.com.codecacto.kmplib.ads`):**
- `ListAdState` (`LOADING`/`EMPTY`/`CONTENT`) + `listAdStateOf(isLoading, isEmpty)` — **carregando ≠ vazio**:
  enquanto carrega não há banner nenhum (nunca o grande e depois o pequeno; sem impressão dupla).
  Erro e "busca sem resultado" contam como `CONTENT` (o quadrado não aparece e some a cada tecla).
- `footerBannerSizeFor(state): BannerSize?` e **`ListFooterBannerAd(state, modifier)`** — o `bottomBar`:
  `STANDARD` com itens; carregando/vazia, só a folga da barra de gestos (o `innerPadding` da lista não muda de base).
- **`EmptyStateWithBannerAd(icon, title, modifier, description, action, maxBannerWidth, minSquareSide)`** e a
  variante de slot `EmptyStateWithBannerAd(modifier, …) { emptyContent }` — o texto do estado vazio é medido
  primeiro e nunca é empurrado; o banner recebe a altura que SOBROU e `emptyStateBannerSpec(availableWidth,
  availableHeight, maxWidth, minSquareSide)` escolhe **quadrado** (lado ≥ 240 dp, encolhe para caber inteiro),
  **grande** (3:1 na largura útil) ou **padrão** (tela baixa demais; rola). Teto de 400 dp de largura (tablet).
  Rola quando a altura é limitada: filho direto do `RefreshableBox`, sem `ScrollableFillBox` em volta.
  Sem anúncio (premium, `off`, sem criativo) a tela fica igual a um `EmptyState` comum.
- `AdDefaults.EMPTY_STATE_BANNER_MAX_WIDTH` (400 dp) e `EMPTY_STATE_SQUARE_MIN_SIDE` (240 dp).
- `CustomBannerAd`/`ManagedBannerAd(…, windowInsets = WindowInsets.navigationBars)` — dentro do conteúdo passe
  `WindowInsets(0)` (o `innerPadding` já desconta a barra). Default inalterado.

Impressão: a mesma VIEWABLE (≥50% por ≥1 s, uma por exibição); ids `ads-banner`/`ads-banner-carregado` valem nos
dois lugares, e o lugar do grande leva **`AdsTestTags.BANNER_ESTADO_VAZIO` = `ads-estado-vazio-banner`** (o flow
Maestro prova o banner grande no vazio afirmando este + `ads-banner-carregado`). Testes: `ListBannerAdTest` (9). Compila Android + `iosArm64` (main e test, executado).

### `ui` — `CommunicationTile`: palavra nunca se parte no rótulo

Print do teste do Minha Voz no iOS (06/out): "Sentimentos" saiu **"Sentiment / os"** no iPhone 17 Pro (no Android
coube). O quebrador de linha parte a palavra que não cabe na largura do tile — e isso não é "transbordo" para o
`autoSize` do Compose (a linha cabe; quem sobra é a palavra). Agora o rótulo:
1. calcula a fonte TETO = a maior (do `titleMedium` para baixo, passo 1 sp) em que a **palavra mais longa** cabe
   inteira na largura, medida com o `TextMeasurer` no estilo real; piso legível `COMMUNICATION_TILE_MIN_FONT_SIZE`
   (12 sp);
2. desenha com `BasicText(autoSize = TextAutoSize.StepBased(…))` (oficial) a partir desse teto, até
   `COMMUNICATION_TILE_MAX_LINES` (3) linhas — frases continuam quebrando **entre** palavras
   (`LineBreak.Heading`, `Hyphens.None`).
Sem mudança de API. Testes: `CommunicationTileLabelTest` (7).

## 2.253.0 — `monetization`: purchases-kmp 3.11.0 — o SDK do RevenueCat vem no klib (⚠️ MIGRAÇÃO OBRIGATÓRIA no Xcode)

**Por quê.** Com a purchases-kmp 2.x, todo app em **Kotlin 2.3.20** (a casca, Chamada Fácil, Piadaria) parava no
link do iOS em `Undefined symbols: _kniprot_cocoapods_PurchasesHybridCommon1_RCPurchasesDelegate` — defeito conhecido
do fornecedor (purchases-kmp#759), exposto pela 2.250.0, que passou a registrar o listener de `CustomerInfo`
(primeira referência ao protocolo `RCPurchasesDelegate`). Não era guarda-chuva × módulos nem loja simulada × real:
Kotlin 2.3.0 linkava, 2.3.20 não. A saída do fornecedor é a 3.x.

**O que muda na lib:** só `revenuecatKmp = "3.11.0"` (era `2.2.13+17.23.0`). A API pública da kmplib é a mesma; o
código Kotlin compilou sem mudança. `revenuecatIosSpm` saiu do catálogo de versões (não há mais pacote SPM amarrado).

### ⚠️ Migração obrigatória — projeto Xcode do app

A purchases-kmp 3.x **embute o SDK nativo** (cinterop `kn-core-…-RevenueCat`, com a biblioteca estática). Portanto:

1. **Tirar do alvo do app os produtos SPM `PurchasesHybridCommon` e `RevenueCat`**, e os pacotes
   `purchases-hybrid-common`/`purchases-ios-spm` do projeto e do `Package.resolved`:
   `python3 Nexus/fabrica/spm_ios.py <mobile>` faz isso e valida o pbxproj por parse;
   `--check` acusa quem ainda os tem.
2. **Manter os dois = RevenueCat ligado DUAS vezes: o build passa e o app trava na abertura** em
   `Purchases.configure` (purchases-kmp#882). Por isso o `spm_ios.py --check` reprova.
3. A purchases-kmp 3.11.0 é compilada com **Kotlin 2.3.20** — app em 2.3.0 sobe junto.
4. Nenhuma mudança no Kotlin do app.

⚠️ **Todo app compila a kmplib pela FONTE (`includeBuild`)**: no primeiro build iOS depois que esta versão estiver na
`main` da kmplib, o app recebe a purchases-kmp 3.x — com ou sem bump do `libs.versions.toml`. Quem não tirar os dois
produtos SPM sai com o app travando na abertura.

Testes: suíte inteira (3.474) verde; compila `iosArm64` em todos os 26 módulos (main e test, executado). De
passagem: 5 nomes de teste do `kmplib-monetization` com `,`/`()` (proibidos no Kotlin/Native) renomeados — o
`compileTestKotlinIosArm64` do módulo falhava desde a 2.233.0. **Link validado só no Mac** (piloto: Chamada Fácil).

## 2.252.4 — `core/network`: corpo cortado depois do `200` agora entra na nova tentativa + `handleApiCall` registra a causa real

LocAki (Android real, OkHttp) com 2.252.3: depois de cadastrar/excluir cliente, `list()` voltava
`ApiResult.Error(-1, …)` — na tela, "Não foi possível falar com o servidor" e "0 clientes cadastrados" — com
`RESPONSE: 200` no log do `HttpClient` e o Traefik registrando 200 com o corpo inteiro (23.220 B).

**Causa (provada):** o `HttpRequestRetry` decide pelos **cabeçalhos**. Chegou o `200`, a chamada sai do plugin e o
corpo é lido depois, no `HttpStatement` — fora do alcance da nova tentativa. Um corte do corpo no meio (conexão móvel
que cai, stream HTTP/2 resetado) virava `Error(-1, "unexpected end of stream")`; a mensagem crua não é uma das
frases da lib, e o app a traduziu na frase genérica de conexão. Reproduzido em `RestRepositoryOkHttpTest` (OkHttp
REAL contra um servidor HTTP real que manda `200` + metade do corpo e derruba a conexão): **sem a correção a rodada
1 reprova com exatamente esse erro; com ela, 30 de 30 leituras chegam em 3 execuções seguidas**. O corte acontecer
justo após uma mutação vem do leque de recargas que a mutação dispara (várias leituras saindo juntas na rede móvel).

- **`ReadBodyInsideRetry`** (plugin interno, instalado pelo `createHttpClient` junto da nova tentativa, por dentro
  dela): para GET/HEAD/OPTIONS cuja resposta o próprio Ktor já guardaria em memória (`isSaved`), faz o mesmo
  `HttpClientCall.save()` que o Ktor faria logo depois — só que **antes** da decisão de repetir. Falha de leitura vira
  `IOException` e segue a política normal (até 2 tentativas, teto total de 30 s). Downloads em fluxo
  (`prepareGet { execute { } }`) não são tocados; POST/PATCH continuam sem repetir.
- **`handleApiCall` registra a causa real** em todo `ApiResult.Error(-1)` (sem resposta HTTP): `AppLogger.w`, tag
  **`ApiCall`**, "Falha sem resposta HTTP: Tipo: mensagem ← causa: …" (até 3 causas, query de URL cortada, nunca
  cabeçalho nem corpo). Antes o `catch (Throwable)` sumia com ela.
- Descartado com prova: a coalescência/geração não entrega dado nem erro de uma leitura a outra (`InFlightRequestsTest`,
  `RestRepositoryOkHttpTest` com recargas cancelando no meio e estresse de 6 telas + cadastros/exclusões, OkHttp real);
  o `catch (CancellationException)` do `handleApiCall` relança.

Testes: `RestRepositoryOkHttpTest` (5, OkHttp real + gzip + corpo em pedaços), `ApiCallFailureLogTest` (4), +1 no
`HttpRetryTest` (download em fluxo continua em fluxo). Suíte da lib: 3.474 verdes. Compila `iosArm64` (main e test,
executado).

## 2.252.3 — `core/data`: `RestConfig.requestDispatcher` — o dispatcher das leituras compartilhadas é injetável (testabilidade)

A 2.252.2 passou a rodar a leitura coalescida do `RestRepository` num escopo próprio fixado em `Dispatchers.Default`.
Em teste de CONSUMIDOR isso quebrou (LocAki, 4 de 670): a requisição corria fora do agendador do `runTest`, o
`advanceUntilIdle()` não a via e as asserções rodavam antes da resposta; e uma resposta que chegava depois do fim do
teste retomava o ViewModel com o `Main` já resetado, vazando como `UncaughtExceptionsBeforeTest` no teste seguinte.

- **`RestConfig(…, requestDispatcher: CoroutineDispatcher = Dispatchers.Default)`** — onde rodam as leituras
  compartilhadas. Produção não muda nada. **Em teste, passe o dispatcher do `runTest`**
  (`StandardTestDispatcher(testScheduler)`/`UnconfinedTestDispatcher(testScheduler)`) — o mesmo que o
  `MockEngine.create { dispatcher = … }` já precisava receber.
- Nenhuma falha de leitura compartilhada vai ao tratador de exceções não tratadas: ela fica no `Deferred` do `async`
  (filho de `SupervisorJob`) e só chega a quem faz `await()` — inclusive quando todos desistiram e a leitura falha
  ao ser cancelada (coberto em teste).

Aditivo (parâmetro com default no fim do construtor). Testes: `RestRepositoryDispatcherTest` (5 — default;
`StandardTestDispatcher` injetado + `advanceUntilIdle` vê a resposta e o fluxo excluir→reler; `UnconfinedTestDispatcher`;
falha depois de todos desistirem não vira exceção não tratada; falha com chamador chega só a ele). Suíte da lib:
3.464 verdes. Compila `iosArm64` (main e test, executado).

## 2.252.2 — `core/data`: cancelar a carga de uma tela não derruba mais a leitura de outra que pegou carona

Achado no aparelho com o LocAki. Na coalescência da 2.252.0 o primeiro chamador executava a requisição
compartilhada **dentro da própria corrotina**. O padrão comum de ViewModel (`loadJob?.cancel()` e recomeçar)
interrompia então a leitura de quem estava esperando por ela: o `ClientesViewModel` iniciou `GET clientes page=2`,
o `LocacoesViewModel` pediu a mesma página (carona), o `ClientesViewModel` cancelou o `loadJob` 4 ms depois — e o
`combine` de locações + clientes + equipamentos caiu ("Parent job is Cancelling; job=FlowCoroutine"), com a tela
parando de carregar.

Correção (`InFlightRequests`, interno):
- a requisição compartilhada roda num **escopo próprio do `RestRepository`** (`SupervisorJob` + `Dispatchers.Default`,
  vivo enquanto o repositório viver — nada de `GlobalScope`), via `async(start = LAZY)`;
- **todo** chamador, inclusive o primeiro, só faz `await()`: cancelar um chamador cancela só a espera DELE;
- a requisição só é cancelada quando o **último** interessado desiste (contagem de referências);
- falha real chega a todos como a **exceção original**, nunca como a `CancellationException` de outra corrotina.

Mantidos: a geração do cache da 2.252.1 (leitura de geração antiga não grava cache nem serve quem pediu depois da
mutação) e o token na chave. Sem mudança de API.

**Afeta** quem está na 2.252.0 ou 2.252.1 com `RestRepository`. **Ação:** subir para 2.252.2.

Testes: `InFlightRequestsTest` (9 — uma execução para chamadas iguais; chaves separadas; líder cancelado e o outro
recebe o resultado de UMA requisição; seguidor cancelado não afeta o líder; todos desistem → a requisição é
cancelada e a chave volta a funcionar; falha `IOException` chega aos 3 com a exceção original; **`combine` de 3
flows sobrevive ao cancelamento do líder externo**), `RestRepositoryCoalescingTest` +1 (tela que pediu primeiro
cancela e a outra recebe a lista, 1 GET), `RestRepositoryGenerationTest` (5) seguem verdes; 5 execuções seguidas
sem intermitência. Suíte da lib: 3.459 verdes. Compila `iosArm64` (main e test, executado).

## 2.252.1 — `core/data`: `RestRepository` não mostra mais o registro excluído/antigo depois de gravar (geração do cache)

Achado no teste do LocAki no iOS: "exclui o cliente" fez `DELETE` 204, o app releu (`GET` 200 no mesmo segundo) e a
lista **continuou mostrando o cliente por 30 s**. Corrida mutação × leitura em voo: uma `list()` que saiu ANTES da
mutação (recarga do `ON_RESUME`, por exemplo) e voltou DEPOIS do `clearCache()`
1. **gravava a lista antiga no cache recém-limpo** — defeito que já existia em todas as versões com o cache de TTL;
2. **servia de carona à releitura pós-mutação** pela coalescência da 2.252.0, que alargou a janela.

Correção — **geração do cache**:
- o estado do cache virou um valor imutável trocado por compare-and-set (`kotlin.concurrent.atomics.AtomicReference`,
  sem `java.util.concurrent` no commonMain) com um contador `generation` que avança a cada `create`/`update`/`delete`
  bem-sucedido e a cada `refresh()`;
- a chave da coalescência leva a geração: leitura pedida depois de uma mutação **nunca** reaproveita a que saiu antes;
- o resultado só é gravado em `listCache`/`byIdCache` se a geração ainda for a de quando a leitura começou.

Quem pediu a leitura antiga continua recebendo o que pediu (ela não é cancelada); só deixa de contaminar o cache e
as releituras. Mesma geração continua coalescendo e cacheando como antes. Sem mudança de API.

**Afeta** quem usa `RestRepository`/`RestRepositoryFactory`: o efeito (1) em qualquer versão com cache; o (2) só na
2.252.0. **Ação:** subir para 2.252.1.

Testes: `RestRepositoryGenerationTest` (5 — releitura pós-`delete` com leitura antiga em voo vai à rede e vê o dado
novo; o resultado antigo não fica no cache; `refresh` no meio descarta; coalescência na mesma geração;
`getById` + `update`). 4 deles **reprovam contra a 2.252.0** (conferido). Suíte da lib: 3.455 verdes. Compila
`iosArm64` (main e test, executado).

## 2.252.0 — `core/network`: nova tentativa automática no `createHttpClient` (rede móvel que pisca) + leituras idênticas em voo viram UMA no `RestRepository`

Origem: LocAki em produção (05/out/2026). Cliente num moto g15, rede móvel Vivo/IPv6: o app abre, 12–15 GETs
saem juntos ao apps-api, uma queda momentânea (`java.net.SocketException: Connection reset`) vira erro na hora —
com **todos os servidores respondendo 200** (logs do Traefik/apps-api) e a mesma conta carregando tudo pelo Wi-Fi.
O `createHttpClient` não tinha política nenhuma de nova tentativa. **Aditivo; ligado por default.**

- **`HttpClientOptions.retry: HttpRetryPolicy`** (default ligada) — plugin oficial `HttpRequestRetry` do Ktor.
  - **Repete:** só **GET, HEAD, OPTIONS**; em falha de **transporte** (`IOException`: connection reset,
    unexpected end of stream, conexão recusada, DNS na troca de rede, `ConnectTimeoutException`,
    `SocketTimeoutException` — no iOS todo `NSURLErrorTimedOut` —, perda de conexão `-1005` do Darwin, corpo
    interrompido) e em **502/503/504**. Até **2 tentativas a mais**, espera exponencial com *equal jitter*
    (200–400 ms, depois 400–800 ms; teto 2 s por espera). `Retry-After` ≤ 2 s é respeitado como piso; maior que
    isso, **não repete**.
  - **Nunca repete:** **POST/PATCH** (o construtor recusa — duplicaria cobrança/registro); PUT/DELETE fora do
    default (entram por `methods`, para quem garante o contrato); **4xx**; **500** e demais 5xx;
    `CancellationException` (a tela saiu); `HttpRequestTimeoutException`; falha **permanente** de transporte
    (certificado recusado, cleartext proibido, ATS, URL inválida — `expect/actual` por engine).
  - **O teto de tempo não cresce:** o retry é instalado DEPOIS do `HttpTimeout` e fica dentro dele — o
    `requestTimeoutMillis` (30 s) vale para a chamada INTEIRA com as tentativas (provado em teste: 2ª tentativa
    travada termina no teto, sem abrir a 3ª).
  - **Log:** cada nova tentativa sai no `AppLogger` (tag `HttpClient`, aviso) com método · URL · `n/2` · tipo e
    mensagem curta da falha — nunca cabeçalho nem corpo. O log de requisição continua em `INFO`. Desligado junto
    com `enableLogging = false`/`HttpLogLevel.NONE`.
  - `HttpRetryPolicy.Disabled` volta ao comportamento anterior.
- **`RestRepository`: `list`/`getById` idênticos e simultâneos fazem UM GET** (*single-flight*,
  `InFlightRequests`, interno). Quem chega enquanto a leitura está em voo espera por ela e recebe o mesmo
  `ApiResult`. A chave inclui o **token** (sessões diferentes nunca compartilham resposta); não é cache (sai do
  mapa ao terminar — o TTL continua o do `RestConfig`); mutação nunca é coalescida. Sem escopo próprio: líder
  cancelado não cancela quem esperava (um deles assume); quem espera e é cancelado sai sozinho. O token passou a
  ser lido uma vez por leitura (antes dentro do builder, igual na prática).
- ⚠️ App que monta o próprio `HttpClient` em vez de usar o `createHttpClient` **não ganha** a nova tentativa.

Testes: `HttpRetryTest` (23, sobre a configuração REAL do factory com `MockEngine`: GET recupera de
`Connection reset`; HEAD/OPTIONS; 503 até o teto; 502/504; com `expectSuccess`; timeouts de conexão/socket;
POST/PATCH/PUT/DELETE não repetem; 4xx e 500 não; `Retry-After` longo não; cancelamento lançado e cancelamento
de quem chamou não; teto total; regras puras de jitter/política), `HttpRetryAndroidTest` (2, exceções reais do
OkHttp), `InFlightRequestsTest` (6), `RestRepositoryCoalescingTest` (5). Suíte da lib: 3.450 testes verdes.
Compila `iosArm64` (main e test, executado — não SKIPPED).

## 2.251.0 — `monetization`: o `MonetizationManager` acompanha a TROCA do repositório da loja

Achado no E2E instrumentado do Super 8 (emulador). O `initialize` assinava o `subscriptionState` do repositório
instalado **naquele instante**; quando o repositório era trocado depois (`PurchaseTestHooks.instalar(jaAssinante())`
no meio do teste, `PurchaseManager.initializeWith`/`reset`, `PurchaseTestHooks.limpar()`), o manager continuava
ouvindo o ANTIGO: `premiumStatus` ficava `Free` e `isPremium` não mudava, enquanto o paywall (que lê o
`PurchaseManager`) já via o novo.

- **`PurchaseManager.currentRepository: StateFlow<PurchaseRepository?>`** — `initialize`, `initializeWith` e
  `reset` emitem. O `PurchaseTestHooks` repropaga sem mudar nada.
- **`MonetizationManager` assina o repositório corrente** (`flatMapLatest` sobre `subscriptionState` +
  `subscriptionReadState`). **A troca de repositório é uma geração nova:** `premiumStatus` é reavaliado a partir de
  `Unknown` — resolve na hora se o novo já nasce lido (dublês: `jaAssinante` → `Premium`, `comOfertas` → `Free`),
  senão recebe a 1ª leitura (com `reconcile` da identidade antes) e um teto próprio. É a única exceção documentada
  ao "resolvido nunca volta a `Unknown`". Sem repositório (`limpar()`) → `Free(NOT_SOLD)`.
- **Nada vaza entre repositórios:** o repositório que sai é desligado — no adaptador da RevenueCat o listener de
  `CustomerInfo` para de publicar e, se o delegate ainda for o dele, volta o anterior (`detach()`); a geração de
  identidade é por repositório; a leitura e o teto do repositório anterior são cancelados; o fluxo antigo deixa
  de ser coletado.

Testes: 5 novos em `PremiumStatusTest` (27: não assinante → assinante, assinante → não assinante, loja nova
ainda não lida volta a `Unknown` e é lida, `limpar` → `Free(NOT_SOLD)`, a loja antiga não publica mais) e 2 no
`kmplib-testing` pelo `PurchaseTestHooks` (`comOfertas`→`jaAssinante` depois do `initialize`;
`jaAssinante`→`comOfertas`→`limpar`) — 356 no `kmplib-monetization`, 30 no `kmplib-testing`. Compila Android +
`iosArm64` (executado).

## 2.250.0 — `monetization`: o premium se corrige sozinho (listener de `CustomerInfo`), sem corrida de identidade, e releitura sem cache pós-compra

Ajustes da segunda revisão da 2.249.0. **Aditivo.**

- **Listener de atualização do `CustomerInfo`** no adaptador da RevenueCat (`Purchases.sharedInstance.delegate`
  → `onCustomerInfoUpdated`), o caminho recomendado pelo fornecedor. Na 2.249.0, `Free(STORE_FAILURE)` só era
  corrigido se alguém relesse: o assinante que reinstalava e abria sem rede e sem cache ficava grátis a sessão
  inteira. Agora o SDK avisa ao voltar ao primeiro plano, após transações (renovação, expiração, compra em
  outro aparelho) e o `premiumStatus` se corrige. Fecha o `GAP-MON-CUSTOMERINFO-LISTENER-01`.
  - **Encadeia** o delegate que já existir (nenhum app da fábrica define um hoje). O app NÃO deve sobrescrever
    `Purchases.sharedInstance.delegate` depois da inicialização.
  - **Compra promovida da App Store** (`onPurchasePromoProduct`, só iOS): com delegate registrado o SDK adia a
    compra até `startPurchase`. Sem delegate anterior a lib inicia na hora (o comportamento de quando não havia
    delegate) e publica o resultado. **Validar no Mac** (ver `kmplib-catalog` → `references/monetization.md`).
- **Corrida de identidade corrigida:** a leitura de abertura podia publicar o `CustomerInfo` do sujeito ANTERIOR
  se respondesse depois de um `logIn`. Agora o `initialize` faz `reconcile()` da identidade e SÓ DEPOIS lê, no
  mesmo `launch`, e o adaptador tem uma **geração de identidade** (avança em `logIn`/`logOut`) que descarta a
  leitura iniciada antes da última troca.
- **`PurchaseRepository.refreshSubscriptionState()`** (default: `syncSubscriptionState()`) e
  **`MonetizationManager.refreshSubscriptionState()`**: releitura **ignorando o cache** (`FETCH_CURRENT`). Para
  a ponte `onNativePurchaseCompleted` do iOS (`SubscriptionStoreView`): com `CACHED_OR_FETCHED` ela tende a
  voltar sem a compra recém-feita.
- `isPremiumResolved` agora é **derivado** de `premiumStatus` (`map` + `stateIn` em escopo `Unconfined`) e nunca
  diverge dele; `reset()` também cancela a primeira leitura em voo.
- Regras do adaptador extraídas para `SubscriptionStateHolder` (interno, puro): `nextReadState` (sucesso → READ;
  falha → FAILED só de PENDING; falha após READ preserva) e a geração.

Testes: `SubscriptionStateHolderTest` (11) + 4 novos em `PremiumStatusTest` (22: guarda de "nunca volta a
Unknown", derivado em sincronia, `refreshSubscriptionState`, `reset` cancelando a leitura em voo) — 351 no
`kmplib-monetization`, 28 no `kmplib-testing`. Compila Android + `iosArm64` (executado).

## 2.249.0 — `monetization`: premium com resolução explícita (`PremiumStatus`) — o gate para de tratar assinante como grátis na abertura

Origem: revisão do Super 8. `MonetizationManager.isPremium` nasce `false` e não tinha como dizer "a loja
ainda não respondeu": um gate premium decidido na abertura (antes do 1º `CustomerInfo`) tratava o
assinante como grátis — no Super 8, o assinante que abria o Chaveamento via o modal "recurso premium" e,
tocando "Agora não", era expulso da tela. Vale para todo app com gate premium. **Aditivo**: `isPremium`
continua igual.

- **`PremiumStatus`** (`br.com.codecacto.kmplib.monetization`): `Unknown` · `Premium` ·
  `Free(reason)` com `FreeReason` `STORE` (a loja disse) · `NOT_SOLD` (modo sem assinatura / app sem
  monetização) · `STORE_FAILURE` · `TIMEOUT` (os dois últimos = `isAssumed`). `isResolved`, `isPremium`,
  `PremiumStatus.DEFAULT_TIMEOUT` (4 s).
- **`MonetizationManager.premiumStatus: StateFlow<PremiumStatus>`** e
  **`isPremiumResolved: StateFlow<Boolean>`** — saem de `Unknown` no 1º estado de assinatura lido da
  loja (cache do SDK ou rede), na leitura que falhou (`Free(STORE_FAILURE)`), no teto
  (`Free(TIMEOUT)`), na hora em `AdsOnly` (`Free(NOT_SOLD)`), e na instalação do dublê
  (`kmplib-testing`: `jaAssinante` abre `Premium`). **Nunca voltam a `Unknown`**; free presumido é
  corrigido pela leitura que chegar depois.
- **`awaitPremiumStatus(timeout)`** / **`awaitPremiumResolved(timeout): Boolean`** — espera suspensa com
  teto; estourado, devolve `Free(TIMEOUT)`/`false` sem mexer no estado global. Funciona chamado antes do
  `initialize` (app que inicializa a loja depois do login).
- **`initialize(config, userId, premiumResolutionTimeout = PremiumStatus.DEFAULT_TIMEOUT)`** — teto
  configurável (parâmetro novo com default).
- **`declareNotMonetized()`** — app sem monetização (`MonetizationMode.NONE` da casca) resolve na hora
  como `Free(NOT_SOLD)`; não marca o manager como inicializado.
- **`PurchaseRepository.subscriptionReadState: Flow<SubscriptionReadState>`** (`PENDING`/`READ`/`FAILED`)
  com **default `READ`** — dublês e repositórios dos apps seguem iguais. `PurchaseManager.subscriptionReadState`
  repassa.
- **Correção de comportamento (RevenueCat):**
  - o `initialize` passa a **pedir a primeira leitura** (`getCustomerInfo`, `CACHED_OR_FETCHED` — o
    caminho da RevenueCat). Até a 2.248.0 nada lia na abertura: o premium só aparecia quando alguma tela
    chamava `syncSubscriptionState()`;
  - **`syncSubscriptionState()` que falha não rebaixa mais o assinante** — até a 2.248.0 a leitura que
    falhava publicava `isActive = false` (assinante sem rede ao voltar ao app virava grátis). Agora a
    falha preserva o último estado lido. `getSubscriptionInfo()` mantém o contrato (falha → inativa).
- `MonetizationManager.reset()` cancela a coleta e o teto pendente (isolamento entre testes).
- `kmplib-testing`: `unitTests.isReturnDefaultValues = true` (a mesma opção da convenção) + teste do
  dublê com `PurchaseTestHooks`.

**Como usar num gate** (ver `kmplib-catalog` → `references/monetization.md` §"Gate premium"):
`Unknown` = carregando (não bloquear, não liberar); `Free` = paywall; `Premium` = libera.

Testes: `PremiumStatusTest` (18) no `kmplib-monetization` (336 no módulo) + `PremiumStatusComDubleTest`
(2) no `kmplib-testing` (28). Compila Android + `iosArm64` (cross-compilation, executada).

## 2.248.0 — Módulo novo `kmplib-tournament`: motor de torneio (chaveamento) para pênalti e raquete

Origem: o estudo de chaveamento do Super 8 (`1-Apps-Offline-Ads/super8/docs/estudo-chaveamento.md`,
aprovado pelo fundador em 03/out/2026) — chaveamento como recurso premium do Super 8 e um app próprio de
beach tennis, com **a mesma lógica**. O motor já existia, puro, no TorneioDePenalti; com três consumidores
ele vira fundação em vez de cópia. Promovido e generalizado para esporte de raquete.

- **Artefato `br.com.codecacto:kmplib-tournament`** (pacote `br.com.codecacto.kmplib.tournament`), no
  umbrella. **Domínio puro**: só stdlib — sem Compose, Koin, datetime nem persistência. Nenhum texto sai
  da lib (fase e grupo são dados: `PhaseIdentity`; erro de placar é código: `ScoreError`). Acaso
  injetável (`RandomSource`, default `SystemRandomSource`).
- **Estrutura (do TorneioDePenalti, invariantes preservadas)**: `RoundRobin` (método do círculo com
  folga), `Groups.split` (tamanhos nunca diferem > 1, nº de grupos ≤ total/2) **+ `singleGroupBelow`**
  (regra SESC: < 6 duplas = grupo único), `Knockout` (potência de 2, semeadura recursiva — cabeças 1 e 2
  em metades opostas —, BYE nas melhores cabeças), `TournamentGenerator` com os 3 formatos
  (`KNOCKOUT`, `ROUND_ROBIN`, `GROUPS_THEN_KNOCKOUT`), plano puro (`TournamentPlan`; BYE ≠ vaga a
  definir), `Advancement`, `TournamentConclusion` (pódio). Novo: **disputa de 3º lugar opcional**
  (`thirdPlaceMatch`, default `false` — o comportamento do TorneioDePenalti), fase `THIRD_PLACE` e
  `Advancement.thirdPlaceDestination`; com 3 participantes a fase não nasce e o pódio dá o 3º ao único
  semifinalista derrotado. Turno e returno (`doubleRoundRobin`).
- **Placar genérico**: `Score.Points(a, b)` (games = pontos, sets 0) e `Score.Sets(SetScore(a, b,
  TieBreak?))`, com `SetScore.matchTieBreak` para o super tie-break (um set e um game, ITF).
- **`MatchFormat`** (raquete): `ONE_SET_OF_SIX` (padrão beach tennis), `PRO_SET_OF_EIGHT`,
  `BEST_OF_THREE_MATCH_TIE_BREAK`; `validate` → `ScoreValidation`/`ScoreError` (6-4 ✓, 6-5 ✗, 7-6 exige
  tie-break, 8-6 impossível num set de 6…), vencedor derivado do placar (raquete não empata),
  `walkoverScore`/`walkover` (6-0).
- **`MatchOutcome`**: `InProgress`, `Decided`, `Draw`, `Walkover`.
- **Classificação configurável**: `Standings.compute(participants, results, StandingsRules, withdrawn)`;
  `StandingsRules` com pontos por V/E/D, `tieBreakers` (lista ordenada de `TieBreaker`: confronto direto,
  saldo de sets/games, sets/games ganhos, média de sets/games, aproveitamento, vitórias, sorteio) e
  `twoWayTieBreakers` (todo empate de 2, inclusive o que sobra no meio de um de 3+). Presets
  **`BEACH_TENNIS`** (SESC/ITF, recursivo 3→2) e **`FOOTBALL`** — este dá a **tabela idêntica** à
  `Classificacao` do TorneioDePenalti (provado contra o algoritmo antigo em 3.000 tabelas aleatórias).
  Comparação de média/aproveitamento **exata** (produto cruzado), nunca `Double`.
- **Desistência**: `WithdrawalPolicy.AnnulMatches` (SESC) ou `CountAsLosses(placar do W.O.)`; quem
  desistiu vai para o fim e nunca se classifica.
- **Classificados**: `Qualifiers.forKnockout` (1ºs, depois 2ºs, com o cruzamento que evita reencontro do
  mesmo grupo na estreia) **+ `KnockoutSeeding.ByCampaign`** (SESC: cabeças = melhores campanhas entre os
  1ºs, grupos de 3 e de 4 comparados por média, não por soma); `Standings.rankAcrossGroups`.
- Suíte `tournament/src/commonTest` (46 testes): as invariantes do `VerificacaoDoMotorTest` portadas
  (2 a 32 participantes nos 3 formatos, semeadura, simulação até a final, varredura 1–8 grupos × 1–4
  classificados), validação de placar, os dois regulamentos de desempate (recursão 3→2, ciclo de 3),
  grupos desiguais por média, 3º lugar, W.O., desistência, BYE, `singleGroupBelow`.

Aditivo (módulo novo). Ninguém precisa bumpar; o TorneioDePenalti migra quando for tocado (tabela
antigo → novo em `kmplib-catalog/references/tournament.md`). Compila Android + iosArm64.

## 2.247.0 — Id por OPÇÃO em `SegmentedControl`, `ChoiceChipGroup` e `FilterChipRow`

Origem: o flow `funcionalidades/03-calculo` do Arroba Certa (03/out/2026) escolhia "Kg" tocando a
**metade direita** do id do grupo (`point: "75%,50%"`) — o `SegmentedControl` só tinha id no
contêiner, e o segmento não era endereçável. Frágil a qualquer mudança de largura, de ordem ou de
número de opções; e tocar pelo rótulo quebra no idioma do aparelho. Os dois irmãos de escolha única
(`ChoiceChipGroup`, `FilterChipRow`) tinham o mesmo buraco.

- **`OptionTestTags`** (`ui.components`, público): `option(group, key)`, `options(group, count, keys)`,
  `normalizeKey`, e os prefixos padrão `SEGMENTED_GROUP` (`segmento`), `CHOICE_CHIP_GROUP`
  (`chip-escolha`), `FILTER_CHIP_GROUP` (`chip-filtro`). Id da opção = **`<grupo>-<chave>`**.
- **Parâmetros novos, no fim e com default, nos três componentes:** `testTag: String? = null` (id do
  GRUPO, aplicado no contêiner e prefixo das opções — passe aqui em vez de `Modifier.testTag`, que o
  componente não enxerga) e `optionTestKeys: List<String>? = null` (chave por opção, normalizada para
  minúsculo com hífen; sem ela, o **índice** — que não muda com o idioma). Chave repetida ou em branco
  não gera id duplicado (desempate pelo índice).
- **Estado selecionado** exposto explicitamente na semântica (`selected`) de cada segmento/chip — o M3
  já o fazia via `selectable` (`Role.RadioButton` no segmento, `Role.Checkbox` no chip); explícito para
  o contrato não depender do interno do Material. ⚠️ No Android o Compose publica `selected` como
  **`checked`** para todo papel que não seja `Role.Tab` — no Maestro, `checked: true` (só Android);
  `selected: true` não casa. Melhor ainda: provar a escolha pelo efeito na tela.
- `OptionTestTagsTest` (8 casos).

Aditivo: assinatura existente intacta, ids novos só acrescentam. A `contentDescription` com o sufixo
", selecionado" **não mudou** (há flow que o afirma — MinhasHoras); a troca dela pela semântica nativa
fica no backlog (GAP-A11Y-SELECIONADO-01). Compila Android + iosArm64.

## 2.246.0 — Intersticial só abre com a ARTE pronta (fim da tela preta com só o "X")

Origem: print da abertura do Piadaria no emulador Android (02/out/2026), ~5 s depois de abrir — o
intersticial "ao abrir" em **tela preta com só o "X"**. A arte do apps-api estava no ar (5 criativos,
HTTP 200, WebP 1440×2560 de ~100–150 KB, ~1,5 s do Firebase Storage); o defeito era a ordem: escolhido
o anúncio, o `Dialog` abria **na hora** e a imagem só começava a baixar depois. Durante o download a
pessoa via o fundo preto do diálogo; com a URL fora do ar, via isso para sempre — e a impressão já
tinha sido contada no `LaunchedEffect` de montagem. O check `anuncio-intersticial` da suíte passava
porque espera o `ads-interstitial-carregado` por até 30 s, e a arte chegava segundos depois.

- **`kmplib-ads` — o host do intersticial (`CustomInterstitialAd` e `ManagedInterstitialAd`) pré-carrega
  a arte** pelo Coil (`SingletonImageLoader.execute`, no tamanho da janela, precisão inexata) **antes**
  de abrir o diálogo, até o novo `creativeLoadTimeout` (default
  `AdDefaults.INTERSTITIAL_CREATIVE_LOAD_TIMEOUT` = 5 s). Com a arte no cache de memória, o
  `AsyncImage` a pinta no primeiro frame. Falhou, URL em branco ou teto estourado → `onDismiss` sem
  impressão e sem `onShown` (mesma regra do `firstLoadTimeout` da 2.236.0).
- **Impressão e `onShown` contam com a arte PINTADA** (Coil `Success`), não com o diálogo montado. Se
  a arte falhar com o diálogo aberto (cache despejado + rede caída), ele fecha em vez de ficar preto.
- `awaitInterstitialCreative` (interno, puro) + `InterstitialCreativeTest`: carregou → exibe; falhou,
  exceção, URL em branco ou teto → pula.

Aditivo na API (parâmetro novo com default); quem já usa não muda nada. Flow Maestro mais forte em
`kmplib-catalog` → `references/monetization.md` §"Intersticial só abre com a ARTE pronta". Compila
Android + iosArm64.

## 2.245.0 — `AdaptiveScaffold`: id `nav-item-<id>` em cada destino (barra e rail)

O id por item da 2.240.0 entrou só na `AppBottomNavBar`; a barra e o rail do `AdaptiveScaffold` (que
desenham os próprios itens) ficaram sem ele, e o flow tocava a aba pelo rótulo. Rótulo de aba é palavra
comum — e o Maestro casa o **primeiro** texto igual na tela: no Palpite Certo, `tapOn text "Jogos"` saindo
da aba Gerar caiu no rótulo "Jogos" do contador do card "Gerar vários jogos", a tela não mudou e o passo
quebrou em `jogos-lista is visible` (02/out/2026, Android e iOS). Agora cada destino leva
`BottomNavTestTags.item(destino.id)` — `nav-item-<id>`, o mesmo contrato da `AppBottomNavBar`:

```yaml
- tapOn:
    id: "nav-item-JOGOS"   # AdaptiveDestination(id = Aba.JOGOS.name, …)
```

Aditivo; nada muda na tela. Compila Android + iosArm64.

## 2.244.1 — `topbar-voltar` também nas telas da lib que desenham o próprio voltar

`DeveloperScreen`, `ContactScreen`, `FeedbackScreen`, `PaywallScreen` e a `SearchTopBar` (fora do modo
busca; `topbar-menu` no hambúrguer dela) não usam a `AppTopBar` — desenham o `IconButton` de voltar à
mão — e por isso ficaram sem o id da 2.244.0. Flow que voltava da tela "Desenvolvido por" precisou tocar
pelo texto de acessibilidade ("Voltar|Back|Volver", CréditoNaMão `05-mais`). Agora `tapOn id
"topbar-voltar"` vale em toda tela da lib com voltar. Aditivo; nada muda na tela.

## 2.244.0 — `AppTopBar`: ids `topbar-voltar` e `topbar-menu` no ícone de navegação (voltar portável no Maestro)

`TopBarTestTags.VOLTAR` (`topbar-voltar`) e `TopBarTestTags.MENU` (`topbar-menu`), postos no `IconButton`
de navegação da `AppTopBar` (e portanto da `BackTopBar` e da `MenuTopBar`). Aditivo: nada muda na tela.

### Por quê

O `- back` (e o `pressKey: back`) do Maestro é o botão Voltar do **Android**. No iOS ele **não existe**
e o comando passa sem fazer nada: o flow continua na mesma tela e quebra no passo seguinte, esperando a
tela de baixo. Caso de origem: Minha OS, `funcionalidades/02-cadastros` (02/out/2026) — Android verde,
iPhone parado na lista de Clientes com `home-tela is visible` falso. O ícone de voltar é desenhado pela
lib, então um id posto no app não chegava nele; agora o flow tem um passo único que vale nas duas:

```yaml
- tapOn:
    id: "topbar-voltar"
```

Tela sem barra da lib (ou voltar que só existe pelo gesto do sistema) → `testTag` próprio no app; `- back`
só dentro de `runFlow when platform: Android`. O auditor de prontidão (`auditar-prontidao-loja.py`) cobra.

### Prova

`TopBarTestTagsTest` trava os literais (são contrato com os flows de todos os apps).

## 2.243.0 — `kmplib-navigation`: `NavType` de enum para rota type-safe (o app não fecha mais ao abrir no iOS)

Módulo novo, `br.com.codecacto:kmplib-navigation` (pacote `br.com.codecacto.kmplib.navigation`), e no
umbrella `kmplib`.

### Por quê

Rota type-safe com argumento `enum` **fecha o app ao abrir no iOS**: a navegação (2.9.x) resolve enum
sozinha só no Android, por reflexão; fora dele o tipo é desconhecido e o `NavHost` lança
`IllegalArgumentException: Route … could not find any NavType for argument … - typeMap received was {}`
no primeiro frame. O Android do mesmo commit passa em tudo. Caso de origem: Esquecido (02/out/2026); o
mesmo defeito estava em Barista de Casa, MinhaObra e ReciboFacil — os quatro ganharam hoje uma cópia
local do mesmo arquivo. Era a promoção pendente registrada no docs/42.

### API

- `enumNavType<T>()` / `enumNullableNavType<T>()` — o `NavType` (valor pelo `name` da constante; nulo
  como o literal `"null"`, a convenção dos anuláveis da própria navegação).
- `enumTypeMap<T>()` / `enumNullableTypeMap<T>()` — o `typeMap` pronto (chave `typeOf<T>()` ou
  `typeOf<T?>()`). Dois enums na mesma rota: `enumTypeMap<A>() + enumTypeMap<B>()`.

```kotlin
@Serializable enum class Aba { RESUMO, HISTORICO }      // @Serializable na declaração: no iOS é obrigatório
@Serializable data class Detalhe(val id: String, val aba: Aba)

composable<Detalhe>(typeMap = enumTypeMap<Aba>()) { … }
// e no ViewModel que lê a rota: savedStateHandle.toRoute<Detalhe>(enumTypeMap<Aba>())
```

Dependência: só o `org.jetbrains.androidx.navigation:navigation-common` **2.9.1** (onde mora o
`NavType`), a versão que todo app do portfólio já pina — nada novo entra no app. Nenhum outro módulo da
lib depende de navegação; por isso módulo próprio.

### Migração (quando o app for tocado — não é campanha)

Esquecido, Barista de Casa, MinhaObra e ReciboFacil: apagar `core/navigation/EnumNavType.kt` e importar
de `br.com.codecacto.kmplib.navigation` (mesmos nomes, mesma assinatura). App por módulos declara
`api(libs.kmplib.navigation)`; com o umbrella, já vem.

### Prova

`EnumNavTypeTest` (7 casos, `commonTest`: ida e volta pela rota de cada constante, nulo, valor inválido
falha alto, chave do `typeMap` exata e anulável, soma de mapas) e `EnumNavTypeSavedStateIosTest` (3 casos,
`iosTest`: `put`/`get` no `SavedState` de verdade, com e sem nulo, chave ausente — compila aqui, roda no
Mac com `./gradlew :kmplib-navigation:iosSimulatorArm64Test`; no Android o `SavedState` é o `Bundle`,
que na JVM do teste de unidade é um dublê vazio).

## 2.242.1 — `ScrollableFillBox`, `ErrorState` e `FormContainer` não derrubam mais o app dentro de rolagem

Os três rolam por conta própria (`verticalScroll`). Colocados dentro de outro rolável vertical —
`Column(Modifier.verticalScroll)`, item de `LazyColumn`/`LazyVerticalGrid`, outro `ScrollableFillBox`,
ou com `verticalScroll` no próprio `modifier` — eram medidos com altura máxima infinita e o Compose
abortava: *"Vertically scrollable component was measured with an infinity maximum height
constraints"* (no iOS, `SIGABRT` em `MetalRedrawer.draw`). Compila verde, cai ao abrir a tela — e,
no caso do `ErrorState`, só na hora do erro, o ramo que ninguém abre no teste. A varredura de
02/out/2026 achou 68 casos em 21 apps (Todos a Bordo, PalpiteCerto, PontoFirme, MinhasHoras…); hoje
restam 4, todos no LocaSys (`ErrorState` dentro de `Column(verticalScroll)` em Fechamento do dia,
Locação de caçamba, Financeiro e Relatórios).

### O que mudou

- **Medidos sem teto de altura, não aplicam a própria rolagem**: ocupam a altura do conteúdo e quem
  rola é o pai. Com altura limitada (o uso certo — corpo de tela, filho do `RefreshableBox`) nada muda:
  preenchem, centram e rolam como antes.
- A decisão lê as restrições que chegam **depois** do `modifier` do app (`BoxWithConstraints`, a API
  oficial do Compose para adaptar o layout às restrições recebidas) — por isso um `verticalScroll`
  passado no `modifier` também é percebido.
- Regra única e testada: `scrollsItself(constraints) = constraints.hasBoundedHeight` (interna).
- A posição da rolagem fica fora da troca de ramo (`rememberScrollState` antes do `BoxWithConstraints`).

### O que continua valendo

Aninhar segue **não sendo o desenho certo** — dentro de rolagem não há "espaço disponível" para
preencher, então o estado de erro deixa de ficar centrado e o formulário perde a rolagem própria com
teclado. Erro dentro de lista: item com `Modifier.heightIn(max = 320.dp)` volta a centrar. O auditor
(`Nexus/fabrica/rolagem_aninhada.py`) continua cobrando. Os outros roláveis da lib que são
`LazyColumn` (`RefreshableBox`, `MultiSelectList`, `UploadQueueView`, `SyncQueueView`…) **não** ficaram
tolerantes: lista preguiçosa sem teto não tem como existir — limite a altura deles.

### O que o app faz

**Nada — basta subir a versão** (app que compila a kmplib por `includeBuild` pega no próximo build).

### Prova

`ScrollsItselfTest` (5 casos: altura limitada e fixa rolam; sem teto não, mesmo com mínimo; largura
infinita não decide); suíte da lib e `compileKotlinIosArm64` de todos os módulos. Sem teste de UI
automatizado (decisão da casa para mobile); a prova visual é abrir uma das 4 telas do LocaSys no ramo
de erro.

## 2.242.0 — iOS: exceção de Kotlin não tratada chega ao GlitchTip como exceção de Kotlin

### O defeito

Num app Compose, exceção de Kotlin não tratada chegava ao GlitchTip como
`C++ Exception: N12_GLOBAL__N_122ExceptionObjHolderImplE` — sem tipo, sem mensagem, sem pilha de
Kotlin (issue 444 do projeto 25) — e a saída do app só mostrava
`libc++abi: terminate_handler unexpectedly returned`. Todo crash de iOS da fábrica era indiagnosticável.

O Kotlin/Native lança exceção, por dentro, como exceção de C++ (`ExceptionObjHolderImpl`). Num app
Compose quase todo código roda num callback chamado pelo UIKit; quando a exceção escapa dali, o
**monitor de C++ do Sentry Cocoa** a vê primeiro e grava o relatório genérico — e o gancho de exceção
não tratada do `sentry-kotlin-multiplatform`, que sabe transformar o `Throwable` em tipo + mensagem +
causas + pilha, nem chega a rodar.

### A correção (o caminho oficial)

- **`sentry-kotlin-multiplatform` 0.13.0 → 0.27.0.** A 0.27.0 trouxe a opção
  `enableUnhandledCppExceptionMonitoring`, com a recomendação expressa no CHANGELOG dela (#554): *em
  Compose Multiplatform, desligue no Apple — exceções de Kotlin não tratadas podem chegar como crash de
  C++ genérico (`ExceptionObjHolderImpl`) em vez de pilha de Kotlin útil.* No caminho, a 0.23.0 passou a
  gravar o crash de Kotlin pelo `uncaughtExceptionHandler` do SentryCrash, **com o escopo** (tags,
  usuário, breadcrumbs) e pilha correta.
- **O `CrashReporter` desliga o monitor de C++** — `applyCrashReporterPolicy` (interna), a política da
  casa sobre as `SentryOptions`, agora separada do `Sentry.init` e testada.
- **O crash nativo não se perde:** continuam monitorados Mach exception, sinal (o `abort()` de um C++ de
  verdade chega como SIGABRT) e NSException. Só some o relatório que carimbava "C++ Exception" em cima
  de exceção de Kotlin. No Android a opção não tem efeito.
- Android: o sentry-kmp 0.27.0 traz o Sentry Java 8.41.0 (era 8.15.1). API usada pela lib inalterada.

### ⚠️ O que o app TEM de fazer no iOS: `sentry-cocoa` EXATO 8.58.2

O binding cinterop do sentry-kmp 0.27.0 é gerado contra o **Sentry Cocoa 8.58.2** (era 8.49.1 na
0.13.0). Com a versão velha no SPM, **o link para** (`_OBJC_CLASS_$_SentrySDKInternal`, classe que não
existe na 8.49.1). App que compila a kmplib por `includeBuild` pega o binding novo na hora — por isso a
casca e os apps já foram alinhados nesta rodada (`Nexus/fabrica/spm_ios.py`, pbxproj + `Package.resolved`).
Projeto que ficou de fora: `python3 Nexus/fabrica/spm_ios.py <pasta-mobile>` antes do próximo build iOS.

A versão nativa passou a morar **na lib**: `gradle/libs.versions.toml` ganhou `sentryCocoa = "8.58.2"`
e `revenuecatIosSpm = "5.50.0"` (não são dependências Gradle — são o número do pacote SPM, que o
`spm_ios.py` lê e cobra da casca).

### Prova

- Servidor: `CrashReporterPolicyTest` (4 casos — o default do SDK liga o monitor de C++ e a política
  desliga; DSN/ambiente/release da config; sem PII, sem tracing, sem sessão, sem screenshot/view
  hierarchy; PII e tracing só mudam se a config mandar); suíte inteira (3270 testes) e
  `compileKotlinIosArm64` de todos os módulos com `-Pkmplib.forceAppleTargets=true`, executados.
- **Mac (a prova real) — roteiro:**
  1. `git pull` na kmplib e no app; no Xcode, *File → Packages → Resolve Package Versions*; conferir
     Sentry **8.58.2**, PurchasesHybridCommon 17.23.0, RevenueCat 5.50.0.
  2. Build **Release** no aparelho com o DSN do projeto configurado (`isActive = true` no log de boot).
  3. Provocar uma exceção de Kotlin não tratada num callback de Compose (ex.: botão de teste num build
     de QA com `onClick = { error("teste-crash-kotlin 2.242.0") }`). O app fecha.
  4. Abrir o app de novo (o relatório sobe na abertura seguinte) e conferir no GlitchTip: título com o
     **tipo** (`IllegalStateException`) e a **mensagem** (`teste-crash-kotlin 2.242.0`), pilha com
     frames do app, tags/usuário do escopo presentes — e **nenhuma** issue nova
     `C++ Exception: …ExceptionObjHolderImpl` para o mesmo crash.
  5. Crash nativo ainda chega: num build de QA, `kill(getpid(), SIGABRT)` (ou `NSException.raise`)
     gera issue própria.
  Sem o dSYM do build enviado ao GlitchTip, a pilha vem em endereços (tipo e mensagem chegam assim mesmo).

## 2.241.2 — `MapView` (Android): `animateTo` antes de o mapa carregar derrubava o app

`CameraPositionState.animateTo` montava o comando com `CameraUpdateFactory.newLatLngZoom(...)`, e a
fábrica só existe **depois de o Maps SDK inicializar** — o que acontece quando o primeiro mapa é
criado. Chamado antes (o `LaunchedEffect` que centra no GPS ou no primeiro pino, que roda junto com a
primeira composição), o app fechava com `NullPointerException: CameraUpdateFactory is not initialized`.
É o mesmo crash achado no Prospecta (02/out/2026), que usava o SDK direto.

Quem chamava cedo: o próprio `route/PlacePicker` (`LaunchedEffect(initial)`, sem posição inicial e com
`locationProvider`) e, nos apps, o Exiba (`MapaScreen`) e o Meu Frete (pelo `PlacePicker`).

### O defeito era maior que o crash

No Android o `CameraPositionState` embrulhava um estado do maps-compose **que nunca era ligado a mapa
nenhum** — o `MapView` cria o dele, e só lia a `cameraPosition` na primeira composição. Ou seja:
chamado cedo, `animateTo` derrubava o app; chamado tarde, **não movia nada**. "Centrar no primeiro
pino", "minha localização" e "ir para o resultado da busca" do `PlacePicker` nunca funcionaram no
Android (no iOS, sim: lá o estado é uma posição observável e o mapa a segue).

### O que mudou

- **`CameraPositionState` (Android) virou o que já era no iOS: o pedido de câmera**, uma posição
  observável. `animateTo`/`position = …` não tocam no SDK — podem ser chamados a qualquer momento,
  antes ou depois de o mapa existir.
- **`MapView` (Android) passou a seguir a `cameraPosition`**: mudou depois de o mapa carregar → anima
  até lá; mudou antes → escreve a posição no estado (`position = …`, que não passa pela fábrica) e o
  mapa nasce nela. É o mesmo portão do `NativeMap`: "carregado" vem do `onMapLoaded`, encadeado com o
  callback do consumidor. A decisão é `cameraSyncFor(requested, applied, loaded)`, pura e testada —
  **sem mapa carregado ela nunca devolve o caminho que usa `CameraUpdateFactory`**.
- Posição igual à já aplicada não gera comando: o carregamento do mapa não anima até onde ele já está.
- KDoc de `CameraPositionState`: é o **pedido**, não o espelho do mapa — `position` não acompanha o
  arrasto do dedo. Para o estado real da câmera, use o `NativeMap` com `rememberMapController()`.

### O que o app faz

**Nada — basta subir a versão.** A API pública não mudou (`rememberCameraPositionState`, `position`,
`animateTo`, `MapView(cameraPosition = state.position)`). Atenção a um efeito que agora APARECE: no
Android a câmera passa a obedecer ao `animateTo`; tela que dependia de ele não fazer nada (não achei
nenhuma) vai ver o mapa se mover.

### Prova

`MapViewCameraTest` (6 casos, `androidUnitTest`) — a JVM do teste é o cenário do crash (Maps SDK não
inicializado): `animateTo` e `position = …` antes de existir mapa não lançam e guardam o pedido; mapa
não carregado nunca anima; carregado anima; posição já aplicada não gera comando; só o zoom mudar é
pedido novo. O movimento na tela precisa de prova em aparelho: abrir o mapa do Exiba (centra no
primeiro pino, botão "minha localização") e o `PlacePicker` do Meu Frete sem posição inicial.

## 2.241.1 — login social pelo navegador: fechar a aba sem concluir encerra o login (Android)

Fecha o **GAP-AUTH-SOCIAL-01**. No modo `BACKEND`, quem tocava em "Entrar com Google", via o navegador
abrir e **fechava a aba sem concluir** voltava para uma tela de login morta: `isGoogleLoading` preso em
`true`, botões desligados, e nada além de reabrir o app resolvia. `SocialBrowserLogin.authenticate` só
terminava pelo *deep link* de volta (`SocialBrowserRedirect.handleRedirect`) ou por um `cancel()` que
ninguém chamava — o navegador não avisa quando a pessoa desiste. iOS nunca teve o defeito
(`ASWebAuthenticationSession` devolve o cancelamento), nem o modo `NATIVE`.

### O que mudou

- **Voltar ao aplicativo com o pedido ainda sem resposta = cancelado.** É a regra do AppAuth-Android
  (`AuthorizationManagementActivity`: retomou sem resposta → `RESULT_CANCELED`). Aqui, sem Activity
  própria, quem observa é um `Application.ActivityLifecycleCallbacks` registrado dentro do
  `authenticate`, antes de abrir o navegador, e removido no `finally` — por qualquer saída.
- O login termina com `SocialBrowserException(reason = "cancelado")`, o mesmo de quem fecha a folha do
  Google no modo nativo: `foiCancelado()` responde `true` e a tela fica quieta, sem "falha no login".
- **Folga de 750 ms** entre a volta e o cancelamento (`BROWSER_RETURN_TOLERANCE_MILLIS`). No caminho
  normal ela nem é usada — a `AuthCallbackActivity` entrega o *deep link* no `onCreate`/`onNewIntent`,
  e a tela de baixo só é retomada depois, com o pedido já concluído. Cobre a tela que aparece um
  instante enquanto o seletor "abrir com" entrega o navegador (nova pausa dentro da folga desarma o
  cancelamento) e o aparelho em que os dois eventos chegam invertidos.
- Observa **todas** as telas do app, não só a que abriu o navegador: girar o aparelho com o navegador
  na frente faz o Android recriar a tela na volta, e a instância que retoma é outra.
- **Tela dividida não cancela sozinha** (`isInMultiWindowMode`): o navegador pode estar vivo ao lado,
  com a pessoa no meio do login. Ali continua valendo `SocialBrowserRedirect.cancel()`.
- *Deep link* que chega **depois** do cancelamento é ignorado — não há pedido para ele completar, e o
  código que ele traz não vale sem o `verifier`, descartado junto com a tentativa.
- Falha ao abrir o navegador que **não** seja `ActivityNotFoundException` também desfaz o pedido
  (antes ele ficava pendente, e o próximo *deep link* completaria um login que nunca abriu).

### O que o app faz

**Nada — basta subir a versão.** O `SocialBrowserRedirect.cancel()` continua existindo, para cancelar
por decisão do app (sair da tela de login com o navegador aberto).

### Prova

- `BrowserReturnWatchTest` (8 casos, `androidUnitTest`): fechar a aba cancela depois da folga · o
  `onResume` de quem vai abrir o navegador não cancela · login concluído antes da volta não é
  cancelado · *deep link* dentro da folga vence · volta de um instante seguida de nova saída não
  cancela · tela dividida · dois retornos cancelam uma vez · login encerrado desarma o agendado.
- A regra é uma classe pura (`BrowserReturnWatch`), sem `Activity` nem relógio; a ligação com o
  Android (`BrowserReturnWatchRegistration`) **precisa de prova em aparelho** — roteiro: modo
  `BACKEND`, tocar em "Entrar com Google", fechar a aba com o voltar → o botão volta a responder em
  menos de 1 s, sem mensagem de erro; repetir concluindo o login → entra normalmente; repetir
  girando o aparelho com o navegador aberto e fechando a aba.
- Security review (checklist do `security-reviewer`): conforme; um item Baixo registrado no backlog
  (`GAP-AUTH-SOCIAL-02`).

### Também nesta versão (documentação)

`IOS_INTEGRATION.md` §"Versão do RevenueCat no iOS": `PurchasesHybridCommon` **exato 17.23.0** (o sufixo
do `purchases-kmp` `2.2.13+17.23.0`) e `purchases-ios-spm` **exato 5.50.0**. A casca pedia "14.x / 5.x"
e 111 repos resolviam 14.3.0 + 5.32.0 com link verde; corrigidos na casca e nos apps
(`Nexus/fabrica/spm_ios.py`), e o auditor de loja passou a cobrar a versão.

## 2.241.0 — login com a cara do app: status bar legível, um idioma só, marca no topo

Origem: os prints da tela de login do Meu Estacionamento (02/out/2026). A suíte automática passou;
quem olhou a imagem viu três defeitos — e os três eram da lib, valendo para todo app com o
`LoginScreen` (49 rotas no portfólio).

### 1. Ícones das barras do sistema pela cor que o app DE FATO desenha (`SystemBarsAppearance`)

O relógio e a bateria saíam **brancos sobre fundo claro**. Quem escolhia a cor era o
`enableEdgeToEdge()` da Activity (`SystemBarStyle.auto`): pelo modo do APARELHO, uma vez, no
`onCreate`. A tela pode discordar dele — login claro num aparelho em modo escuro, `darkTheme` fixo,
troca de modo com o app aberto (`configChanges="uiMode"` não recria a Activity). E app sem
`enableEdgeToEdge()` em Android 15+ (edge-to-edge forçado) ficava com o que o tema da janela dissesse.

- **`AppTheme` aplica sozinho** (`systemBars: Boolean = true`, parâmetro novo): ícones escuros sobre
  fundo claro e claros sobre fundo escuro, pelo `background` da paleta EFETIVA. App nenhum precisa
  lembrar; basta subir a versão.
- **`SystemBarsAppearance(statusBarBackground, navigationBarBackground = statusBarBackground)`**
  (`ui.theme`, público) para a tela que pinta OUTRO fundo atrás da barra — faixa de marca, foto de
  capa, tema próprio sem `AppTheme`. Os pedidos formam uma pilha: o da tela vale enquanto ela está em
  cena e, ao sair, volta o do tema. `LoginScreen` e `RegisterScreen` já chamam com o `colors.background`.
- Android: `WindowInsetsControllerCompat.isAppearanceLightStatusBars`/`…NavigationBars` na janela da
  Activity (a API oficial; é o que o `enableEdgeToEdge()` usa por dentro). Barra transparente decide
  pelo conteúdo; barra opaca (Android ≤ 14 sem edge-to-edge) decide pela cor dela; película
  translúcida, pela mistura — `needsDarkSystemBarIcons(barColor, contentBackground)`, pura e testada.
- **iOS: não faz nada, de propósito.** A status bar segue o `userInterfaceStyle` da janela, e mudá-lo
  por código (`overrideUserInterfaceStyle`, o mesmo do `.preferredColorScheme`) muda também o que o
  `isSystemInDarkTheme()` do Compose devolve — um app que segue o sistema ficaria preso no modo
  forçado. No iOS a correção é a origem (item 2): a tela segue o tema. App de tema FIXO declara
  `UIUserInterfaceStyle` no `Info.plist`. Controle por tela no iOS: backlog `GAP-IOS-STATUSBAR-01`.

### 2. Login e cadastro usam as cores do tema (`LoginDefaults.colors()`)

O default de `colors` era `LoginColors()`: roxo `#6C63FF` sobre cinza-claro, **fixo**. Quem não
passava `colors` abria o app com um login de outra marca; e, passando ou não, a tela era sempre
clara — é ela que ficava com a status bar apagada em aparelho escuro, nas duas plataformas.

- `LoginScreen`/`RegisterScreen`: `colors: LoginColors = LoginDefaults.colors()` — as cores do
  `MaterialTheme` em volta (a paleta do app e o claro/escuro efetivo). `loginColorsFrom(ColorScheme)`
  é a regra pura. **`LoginColors(...)` explícito continua valendo como sempre.**
- **Mudança visível para quem NÃO passava `colors`:** sai o roxo, entra a paleta do app; em modo
  escuro o login fica escuro como o resto do app.
- O esquema interno das duas telas passou a levar também `onBackground`/`onSurface`/
  `onSurfaceVariant`/`outlineVariant` do `LoginColors`: só fundo e superfície eram trocados, e com
  `colors` claro num app em modo escuro o texto digitado saía claro sobre campo claro.
- **Recomendado a quem passa `LoginColors` fixo e claro** (o molde antigo da casca,
  `AppConfig.loginColors()`): tirar o argumento. No Android a status bar já fica certa só com o bump;
  no iOS, só seguindo o tema.

### 3. Rótulo do identificador: o default do SERVIDOR não vence mais a tradução

Aparelho em inglês mostrava "E-mail ou usuário" ao lado de "Password / Forgot my password / Sign
in". A lib tinha o texto nos quatro idiomas (`kmplib_identifier_label` e irmãos) e nunca o usava: a
regra era `state.identifierLabel.ifBlank { local }`, e a `backlib-auth-local` **nunca manda vazio**
— sem rótulo configurado, `GET /config` devolve o default do modo, em português fixo.

- **`resolveIdentifierLabel(serverLabel, localLabel)`** (pública, pura): o rótulo do servidor só
  vence quando é PRÓPRIO do produto ("Matrícula"); o default do servidor ("E-mail", "Usuário",
  "E-mail ou usuário") cede ao texto local. Nada muda no ViewModel do app.
- **No app:** não escreva o rótulo à mão — `LoginTexts(identifierLabel = { "E-mail ou usuário" })`
  é a segunda origem do mesmo defeito (era o caso do Meu Estacionamento). Sem `texts`, a tela sai
  inteira no idioma do aparelho.
- A paridade de chaves entre `values`, `values-en`, `values-es` e `values-pt-rPT` já é travada por
  `LibStringResourcesParityTest` (2.219.0) — as 252 chaves conferem.

### 4. Marca no topo do login (`AppBrandHeader`, `rememberAppIconPainter`)

Sem `logo` e sem título a tela nascia só com os campos e um vazio em cima, sem dizer de qual app era.

- **`LoginScreen(appBrand = true)`** (parâmetro novo, no fim): sem `logo` e sem `LoginTexts.title`,
  desenha o **ícone do app + o nome**, lidos do sistema. Quem passa `logo` ou título não é afetado;
  com o `brandPanel` em cena (janela expandida) também não. `appBrand = false` desliga.
- **`AppBrandHeader(appName, icon, tagline, …)`** (`ui.components`, público) — o mesmo cabeçalho para
  a tela de entrada escrita no app. `AppBrandDefaults.iconModifier` (88dp, cantos de 20dp) serve de
  `logoModifier` para quem passa o ícone como `logo`. Id de automação: `app-marca`.
- **`rememberAppIconPainter(): Painter?`** (`platform.brand`) — o ícone do launcher
  (`PackageManager.getApplicationIcon`, adaptativo incluído) no Android; no iOS, o `AppIcon` do bundle
  (`CFBundleIcons` → `UIImage(named:)`). `null` quando não dá para ler: sai só o nome. O nome é o
  `BuildInfo.appName` de sempre (rótulo do launcher / `CFBundleDisplayName`) — certo por flavor e por idioma.

### Testes
`SystemBarsTest` (régua de cor + pilha de pedidos), `LoginRulesTest` (rótulo, marca, cores do tema),
`AppIconCandidatesTest`. Suíte: 3252 testes verdes; `compileKotlinIosArm64` executado em todos os módulos.

**Sem validação visual nesta entrega** (o build do app é do Mac/Windows do fundador): conferir no
print, em modo claro e escuro, o login de um app que subiu para esta versão — em especial o ícone do
app no iOS (resolução do arquivo do bundle).

## 2.240.1 — `LoginScreen`: botão Entrar e "Concluído" do teclado decidem pela MESMA condição

Patch, **sem API nova**. Origem: os dois itens de severidade Baixa do review de segurança da
2.239.0 (`373f1e6`, veredito aprovado). A 2.239.0 fez o Done da senha enviar o login, com a guarda
`!isLoading` repetida em dois lugares (o `enabled` do botão e `loginImeDoneAction`) — e nenhum dos
dois olhava as outras operações de entrada da tela.

- **`loginSubmitEnabled(state)`** (interna, pura) passa a ser a condição ÚNICA dos dois gatilhos:
  `!isLoading && !isGoogleLoading && !isAppleLoading && !showForgotPasswordDialog`. O botão Entrar
  usa em `enabled`; `loginImeDoneAction` devolve `null` fora dela.
- Efeito visível: com o login do Google/Apple em andamento, ou com o diálogo de "esqueci a senha"
  aberto, o botão Entrar fica desabilitado e o Done da senha só baixa o teclado. Antes os dois
  enviavam um segundo login por cima da operação em curso.
- Travado por `LoginImeDoneActionTest`: um caso por condição, e um que varre os estados e exige
  que botão e teclado nunca divirjam.

**Limite conhecido, anterior a esta versão (backlog `GAP-AUTH-SOCIAL-01`):** no Android em modo
`BACKEND`, fechar a aba do navegador sem concluir o login social deixa `isGoogleLoading = true` até a
tela morrer — nada cancela o pedido pendente. Antes o Entrar ficava clicável (e girando sem navegar,
porque o coletor de efeitos da rota está suspenso no login social); agora fica desabilitado. Nos
dois casos a saída é reabrir o app; a correção é do fluxo social, não desta guarda.

**No ViewModel do app (não é da lib, e a guarda de tela não substitui):** o envio tem de ser
idempotente no ponto único de entrada —

```kotlin
private fun signIn() {
    if (currentState.isLoading) return                       // 1ª linha
    …validação…
    setState { copy(isLoading = true, errorMessage = null) } // ANTES do launch
    launch { … }
}
```

Com o `setState` dentro do `launch`, dois gatilhos no mesmo frame (botão + Done) leem
`isLoading = false` os dois e saem duas requisições de login. A `casca-mobile` já nasce assim, com
`LoginViewModelTest` cobrindo o disparo duplo.

## 2.240.0 — `AppBottomNavBar`: id de automação POR ITEM (`nav-item-<route>`)

Minor, **aditiva**. Origem: 02/out/2026, Minha Agenda — a suíte `funcionalidades` reprovou no iOS em
`Element not found: Text matching regex: Serviços, Child of: id: main-nav`, com a barra e o rótulo
"Serviços" visíveis no print. A barra só tinha o `testTag` que o app punha no contêiner, e o flow
achava a aba pelo **rótulo dentro dele** (`text` + `childOf`): no Android funciona, no iOS o
`childOf` de um contêiner Compose não casou. E rótulo é texto traduzido — o flow passava a depender
do idioma do aparelho.

- **`BottomNavItem.testTag: String? = null`** (parâmetro novo, no fim) e
  **`effectiveTestTag`** = `testTag ?: BottomNavTestTags.item(route)`.
- **`BottomNavTestTags.item(route)`** = `nav-item-<route>` (`ITEM_PREFIX = "nav-item-"`).
- `AppBottomNavBar` aplica o id no `NavigationBarItem` — o nó `selectable` da aba, que é o que se
  toca. **Todo item já nasce com id**, sem o app mudar nada; rota feia (com `/` ou `.`) → o app
  passa `testTag`.
- Travado por `BottomNavTestTagsTest`.

**Flows Maestro:** tocar na aba por `tapOn: { id: "nav-item-<route>" }`. Não usar
`text: "<rótulo>"` + `childOf: { id: "main-nav" }` — vale para qualquer contêiner Compose no iOS:
prefira id no próprio alvo (ou `id` + `text` no MESMO nó, quando ele é clicável e funde o texto).

## 2.239.0 — `LoginScreen`: o "Concluído" do teclado na senha ENVIA o login

Minor, **mudança de comportamento** (sem API nova para o app). Origem: 02/out/2026, Todos a Bordo —
o `testar-no-emulador --login` do iOS reprovou em `sessao-nasce` com o formulário PREENCHIDO e
parado na tela. O campo de senha era `ImeAction.Done` **sem** `keyboardActions`, e a ação padrão do
Compose para Done é só baixar o teclado: a pessoa digitava a senha, tocava em "Concluído" e nada
acontecia — tinha de achar o botão. O padrão das duas plataformas (e do exemplo oficial de login do
Compose, `onImeAction = { onSubmit() }`) é o último campo do formulário enviar.

- **`LoginScreen`**: o campo de senha passa `KeyboardActions(onDone = …)` — baixa o teclado
  (`defaultKeyboardAction(ImeAction.Done)`, como antes) **e** dispara `LoginAction.Click.Login`, a
  MESMA ação do botão Entrar. A validação continua no ViewModel do app; nada muda no contrato.
- **Não envia duas vezes**: `loginImeDoneAction(state)` (interna, pura) devolve `null` com
  `isLoading = true` — a mesma condição que desabilita o botão. Travado por `LoginImeDoneActionTest`.
- **Só o login.** `RegisterScreen` e `ForcePasswordChangeDialog` seguem com o Done baixando o
  teclado: ali ainda há o aceite dos Termos / a conferência antes do envio.

**Flows Maestro — o que muda (e corrige a nota da 2.237.0):** `pressKey: Enter` com a senha focada
no iOS passa a **enviar** o login em app nesta versão; em app com kmplib < 2.239.0 ele só baixa o
teclado. O flow tem de servir aos dois: depois do Enter, tocar em `login-btn-entrar` **se o
formulário ainda está na tela**, e sem reprovar se a tela já trocou —

```yaml
- runFlow:
    when:
      platform: iOS
    commands:
      - pressKey: Enter
      - runFlow:
          when:
            visible:
              id: "login-input-senha"
          commands:
            - scrollUntilVisible:
                element:
                  id: "login-btn-entrar"
                direction: DOWN
                optional: true
            - tapOn:
                id: "login-btn-entrar"
                optional: true
```

O Android não muda (`pressKey: back`/`hideKeyboard` + toque no botão, obrigatório). Flow que só faz
o Enter e espera a Home quebra em app antigo; flow que faz Enter + toque OBRIGATÓRIO quebra em app
novo (a Home já abriu e o botão não existe mais). A `casca-mobile` e os flows do portfólio já estão
no padrão acima.

## 2.238.0 — Modo automação: build de teste nunca pede avaliação (`AutomationMode`)

Minor, **aditiva**. Origem: teste do agente de 02/out/2026, Chamada Fácil — o `AppReviewDialog`,
disparado por `AppReviewManager(triggerCount = 3)` (o `ReviewGate` do app), abriu no meio da suíte
Maestro e escondeu a tela. O contador fica gravado no APARELHO; rodada após rodada no mesmo emulador,
ele chega ao gatilho. Todo app que pede avaliação por contagem quebraria a suíte do mesmo jeito, num
passo diferente a cada vez.

**A regra:** build de teste não pede avaliação nem oferece atualização opcional.

- **Novo `AutomationMode`** (`platform.automation`, artefato `kmplib-platform`): `isActive`,
  `signal: AutomationSignal?`, `suppressesAutomaticPrompts`. Liga por três sinais:
  - **`STORE_DOUBLE`** — `PurchaseTestHooks.instalar(...)` (`kmplib-testing`) liga sozinho. É o sinal
    que o build de QA já tem: a `kmplib-testing` só entra no binário com `-Pqa.paywallDemo=true` /
    `QA_PAYWALL_DEMO=1`, e o plugin `kmplib.store-double` reprova a variante publicável. **Nenhuma linha
    no app.** `PurchaseTestHooks.limpar()` não desliga (o processo continua sendo de teste).
  - **`DEVICE_TEST_HARNESS`** — Android em *Test Harness Mode* (`ActivityManager.isRunningInUserTestHarness()`,
    API 29+, ligado por `adb shell cmd testharness enable`) ou sob o Monkey (`isUserAMonkey()`): o sinal
    oficial do sistema para "aparelho de teste automatizado". Lido a cada consulta. iOS: sem equivalente.
  - **`TEST_BUILD`** — `AutomationMode.activate()` explícito, para build de teste sem o dublê. Exige
    `@OptIn(AutomationModeApi::class)` (nível ERROR) — não é API de produção.
- **`AppReviewManager`**: com o modo ligado, `onCompletion()`/`shouldShow()` devolvem `false` e o
  **contador não anda**. Novo parâmetro `suppressed: () -> Boolean` (default = o modo), para teste.
- **`AppReviewDialog`**: com o modo ligado não desenha, mesmo com `show = true` — cobre o app que conta
  por conta própria. Novo `suppressInAutomation: Boolean = true`; passe `false` quando o diálogo abre
  por GESTO da pessoa ("Avaliar o app" no menu), que o flow aciona de propósito.
- **`AppServiceGate`/`AppUpdateGate`**: o `SoftUpdateDialog` (atualização OPCIONAL) não é oferecido.
  Atualização **obrigatória** e **manutenção** continuam bloqueando — são estados reais, e o teste tem
  de enxergá-los.
- **Fora, de propósito:** o intersticial (é testado como produto, `AdsTestTags`) e pedidos de
  permissão (o app pede quando quer; o Maestro responde com `permissions:` no `launchApp` — calar o
  pedido mudaria o comportamento sob teste). A lib não chama In-App Review do Play nem
  `SKStoreReviewController`: o pedido nativo é o "Sim" do `AppReviewDialog`, que já não aparece.
- **Release não muda:** sem `kmplib-testing` no binário, sem Test Harness no aparelho do usuário (ligar
  o modo apaga o aparelho) e sem `activate()` (opt-in de erro), `suppressesAutomaticPrompts` é `false`.

**Migração:** nenhuma. O app que bumpar e rodar a suíte com o dublê (`--loja simulada`) deixa de ver o
diálogo. App cujo build de QA NÃO usa o dublê: chamar `AutomationMode.activate()` no ponto em que o build
de teste já se distingue (ex.: o `*QaDemo`), ou ligar o Test Harness no emulador.

## 2.237.0 — Título da `AppTopBar` com id para automação (`topbar-titulo`): fechar o teclado no iOS

Minor, **aditiva**. Origem: teste do agente de 02/out/2026, Minha Voz no **iOS** — o flow que
passou no Android quebrou no `hideKeyboard` ("Hide Keyboard... FAILED | Instead of hideKeyboard, try
tapping on non-interactive element to hide keyboard").

**Por quê.** No iOS não há API para baixar o teclado: o `hideKeyboard` do Maestro dá swipes no meio da
tela e torce para o layout reagir — em tela Compose, não reage. A documentação oficial do Maestro
(`hideKeyboard` → *Workarounds*) indica tocar num elemento **não interativo** (título, cabeçalho).

- **Novo `TopBarTestTags.TITULO = "topbar-titulo"`** (`platform.automation`, artefato
  `kmplib-platform`), no texto do título da `AppTopBar` — e por ela `BackTopBar`, `MenuTopBar`,
  `SimpleTopBar`. Literal travado por `TopBarTestTagsTest` (é contrato com os flows dos apps).
- O toque só baixa o teclado com **`Modifier.dismissKeyboardOnTapOutside()` na raiz do app**
  (2.194.0; a `casca-mobile` já nasce com ele). App sem o modifier: aplicar no `Box` que envolve o
  `AppNavHost` — é também o comportamento que o usuário de iPhone espera.
- **`InputDialog`** (o `AlertDialog` com campo) passa a trazer `dismissKeyboardOnTapOutside()` na
  superfície, como `AppDialog`/`AppBottomSheet` já traziam: diálogo é outra janela e o modifier da
  raiz não o alcança. Alvo do toque no flow: `dialogo-titulo`.
- **Login da lib** (`LoginScreen`): não há barra superior; no iOS o flow usa `pressKey: Enter` com a
  SENHA focada — o campo é `ImeAction.Done` sem `keyboardActions` próprio, e a ação padrão do Compose
  para Done é baixar o teclado (não envia o formulário).
  **⚠️ Desde a 2.239.0 o Done da senha ENVIA o login** — o padrão do flow está na nota daquela versão.
- Padrão do flow (Android segue com `hideKeyboard`, que lá é o "voltar" do sistema e é confiável):
  `runFlow` com `when: { platform: Android }` → `hideKeyboard`; `when: { platform: iOS }` →
  `tapOn: { id: "topbar-titulo" }`. Comandos **inline** (`commands:`), não `file:` — vários runners
  copiam o flow para outra pasta antes de rodar, e o caminho relativo do subflow quebraria.

## 2.236.0 — Intersticial espera a primeira carga dos anúncios (a abertura não se perde mais)

Minor, **aditiva** — e corrige a corrida que fazia o intersticial "ao abrir" nunca aparecer.
Origem: teste do agente de 02/out/2026, Piadaria no Android (o iOS passou por sorte).

**O defeito (todas as versões até a 2.235.1).** O intersticial de abertura é pedido na primeira
exibição da tela, quando a lista de house ads do apps-api (`CustomAdManager.ads`) ainda está
**vazia** — a resposta não voltou. Sem anúncio, o `CustomInterstitialAd` chamava `onDismiss` na hora
e a sessão perdia a abertura. A mesma corrida existia no `ManagedInterstitialAd` com o roteamento:
até a `/public/ad-config` responder, o `AdRouter.routing` é o `defaults` do app — com `defaults = OFF`
e o painel em `custom`, o pedido também era descartado no primeiro frame. Lista vazia não dizia se
não havia anúncio ou se ele ainda não tinha chegado.

**A correção.**
- `CustomInterstitialAd` e `ManagedInterstitialAd` **esperam a primeira carga** (anúncios e, no
  gerenciado, também o roteamento) até `firstLoadTimeout` — default
  **`AdDefaults.INTERSTITIAL_FIRST_LOAD_TIMEOUT` = 5 s** (primeiro pedido de um app frio em rede
  móvel; o valor provado no Piadaria). **Nada é desenhado durante a espera** — a tela de baixo segue
  usável; o anúncio entra por cima quando o criativo existe. Criativo já carregado exibe na hora.
- Carga resolvida sem criativo, fonte que falhou ou teto estourado → `onDismiss` **sem** contar
  impressão. Criativo que chega depois do teto **não** aparece (não cai no meio do uso).
- **Premium nunca vê**: `shouldShowAds = false` pula na hora, também se virar `false` durante a
  espera; com o anúncio na tela, fecha.
- **`off` do painel respeitado**: o gerenciado espera o roteamento para lê-lo, mesmo com criativo
  pronto e `defaults = ALL_CUSTOM`. Roteamento que falhou → vale o `defaults` do app.
- Fonte nunca inicializada (`IDLE`) não é esperada — inicialize `CustomAdManager`/`AdRouter` no
  bootstrap, como a casca faz.

**API nova (aditiva):**
- `AdLoadState` (`IDLE`/`LOADING`/`READY`/`FAILED`, `isSettled`).
- `CustomAdManager.loadState` / `CustomAdManager.awaitFirstLoad(timeout)`;
  `AdRouter.loadState` / `AdRouter.awaitFirstLoad(timeout)`.
- `CustomInterstitialAd(…, onShown, firstLoadTimeout)` e `ManagedInterstitialAd(…, onShown,
  firstLoadTimeout)`. **`onShown`** dispara só quando o anúncio aparece de fato — é o lugar de contar
  frequência ("uma vez por sessão"); `onDismiss` continua chegando nos dois casos. Intersticial que
  segura uma navegação no `onDismiss` pode passar `firstLoadTimeout = Duration.ZERO`.
- Fonte de anúncios/roteamento que **lança** deixou de subir ao handler do scope (podia derrubar o
  processo): vira `FAILED`.

**Banner não tinha a corrida:** o `CustomBannerAd` observa a lista e aparece quando ela chega, com
impressão viewable — nada se perde.

**Para o app:** subir a versão basta. Quem contornou no app (Piadaria `JokeRoute`, commit `dd6927d`:
`withTimeoutOrNull` sobre `CustomAdManager.ads`) pode voltar a pedir o intersticial direto. Quem conta
frequência no `onDismiss` deve passar a contar no `onShown`.

Testes: `InterstitialDecisionTest` (14 — carregando → espera e exibe; falha/teto → segue sem anúncio;
premium → nunca; `off` do painel; roteamento OFF→CUSTOM), `CustomAdManagerTest` e `AdRouterTest`
(estado da carga, fonte que lança, fonte muda, reinicialização).

## 2.235.1 — `OnboardingPager` respeita as barras do sistema

Patch. O carrossel de introdução é tela cheia sem `Scaffold`: com o edge-to-edge (Android 15 forçado;
iOS sempre) o "Pular" nascia sob o relógio e o "Próximo"/"Começar" sob a barra de gestos. Agora aplica
`WindowInsets.safeDrawing` na raiz. Quem já passava o inset no `modifier` (Cidade Conectada) não dobra —
o inset consumido vira zero aqui. Coberto pelo `BarrasRespeitamInsetsTest`.

## 2.235.0 — Barras da lib respeitam o edge-to-edge (status bar, barra de gestos, notch)

Minor, **aditiva**. Origem: Chamada Fácil no Android (02/out/2026) — o título da Home nascia sob o
relógio. Com `targetSdk` 35+ o Android 15 **força** o edge-to-edge (e o Compose no iOS sempre desenha sob
o notch e o home indicator): `TopAppBar`/`NavigationBar` do Material aplicam o inset sozinhos, mas uma
barra feita de `Row`/`Box`/`Column`/`Surface` não — e compila verde. A varredura da lib achou o mesmo
defeito nela:

- **`AdaptiveScaffold`** — a barra de abas de telefone (`Row` própria no `bottomBar`) ficava sob a barra de
  gestos: agora aplica `WindowInsets.navigationBars` (fundo atrás, itens acima). O **rail** de tablet
  pinta atrás da status bar/barra de navegação e começa abaixo/acima delas (e depois da barra de 3 botões
  ou do recorte na borda inicial, em paisagem); o `Scaffold` ao lado **consome** a borda inicial para não
  somá-la de novo.
- **`ConnectivityGate` (`ConnectivityStyle.Banner`)** — o banner "sem conexão" fica no topo da janela e
  nascia sob a status bar. Agora aplica a status bar dentro do próprio fundo e o app embaixo a recebe como
  **consumida** enquanto o banner aparece (sem vão duplo sob a `TopAppBar` da tela).
- **`OfflineBanner(…, windowInsets = WindowInsets(0))`** — parâmetro novo, nas duas sobrecargas: o inset
  aplicado DENTRO do fundo. Default = nenhum (no meio do conteúdo não deve somar status bar); no topo da
  janela, passe `WindowInsets.statusBars`.
- **`ManagedBannerAd`** desligado (`AdProvider.OFF`) reserva a barra de navegação, como o `CustomBannerAd`
  já fazia: o `Spacer` de altura zero no `bottomBar` zerava o padding inferior do `Scaffold` e o último
  item da lista passava sob a barra de gestos.
- **`LoginScreen`/`RegisterScreen`, `HardUpdateScreen`, `MaintenanceScreen` (platform)** — telas cheias
  sem `Scaffold`: o conteúdo respeita barras do sistema e recorte (`systemBars ∪ displayCutout`/
  `safeDrawing`); o fundo continua de borda a borda. O `imePadding` do `FormContainer` desconta o que já
  foi aplicado.
- **Teste-trava** `BarrasRespeitamInsetsTest` (`kmplib-ui` androidUnitTest): varre os fontes de todos os
  módulos e reprova slot `topBar`/`bottomBar` feito de layout cru sem inset; confere as barras acima.

**Para o APP (o que a lib não alcança):** barra PRÓPRIA no `topBar` → `.background(cor)` e DEPOIS
`.windowInsetsPadding(WindowInsets.statusBars)` (ou `TopAppBarDefaults.windowInsets`); no `bottomBar` →
`WindowInsets.navigationBars`; tela cheia sem `Scaffold` → `WindowInsets.safeDrawing`. O `padding` que o
`Scaffold` passa ao conteúdo já inclui a altura da barra — não somar inset de novo. E `enableEdgeToEdge()`
na `MainActivity` (a `casca-mobile` já nasce com ele).

## 2.234.0 — Diálogos, folhas e menus da lib visíveis ao Maestro (ids `dialogo-*`)

Minor, **aditiva**. Origem: teste do Mac no Palpite Certo (02/out/2026) — no Android todo diálogo da lib
abre em **outra janela** (`Dialog`/`AlertDialog`/`ModalBottomSheet`/`DropdownMenu`/`DatePickerDialog`/
`TimePickerDialog`), que **não herda** o `testTagsAsResourceId` que o `AppTheme` liga na raiz. Na
hierarquia, todo nó dentro deles saía com `resource-id=""` e o Maestro não tocava em nada — inclusive no
`AppInputDialog` do "digite EXCLUIR" da exclusão de conta. A 2.232.0 já tinha corrigido só o intersticial.

- **`kmplib-platform` — `Modifier.exposeTestTagsAsResourceId()`** (`br.com.codecacto.kmplib.platform.automation`,
  `expect/actual`): liga a flag no nó-raiz de uma janela própria, sem nó de layout extra. Android =
  `semantics { testTagsAsResourceId = true }`; iOS = no-op (a `testTag` já vira `accessibilityIdentifier`).
  Mora no `platform` porque módulos que não dependem do `kmplib-ui` (`SoftUpdateDialog`, menus do
  `VideoPlayer`) também abrem janela. `WithTestTagsAsResourceId` (ui) passou a usá-lo. **App que escreve o
  próprio `Dialog {}`/`ModalBottomSheet {}` aplica no `modifier` do nó-raiz dele.**
- **`DialogTestTags`** (mesmo pacote) — ids estáveis: `dialogo` (contêiner), `dialogo-titulo`,
  `dialogo-mensagem`, `dialogo-input`, `dialogo-btn-confirmar` (ação principal; diálogo de um botão só usa
  este), `dialogo-btn-cancelar`, `dialogo-btn-fechar` (X das telas cheias), `dialogo-folha`, `dialogo-menu`.
- **Aplicado em toda janela da lib**: `AppDialog`/`AppAlertDialog`/`AppInputDialog` (e o
  `ForcePasswordChangeDialog`, que é um `AppDialog` e mantém os ids `force_password_*`), `ConfirmationDialog`,
  `InputDialog`, `ErrorModal`, `NoInternetDialog`, `NoInternetModal` (`ConnectivityGate`), `AppTimePicker`,
  `AppDatePicker`/`AppDatePickerDialog`, `AppTimeField`/`AppTimePickerDialog`, `FullScreenImageViewer`,
  `FullScreenGallery`, `AppBottomSheet`, folha "câmera ou galeria" do `rememberImagePickerLauncher`/
  `rememberVideoPickerLauncher` (Android), `AppDropdownField`/`AppMultiDropdownField`, menus do
  `AppDayTimePicker`/`AppWeeklyScheduleEditor`, `SoftUpdateDialog`, `AppReviewDialog`, `DictationOverlay`,
  menus de velocidade/legenda do `VideoPlayer`. O id do contêiner vai num nó da lib — um `testTag` que o app
  passe no `modifier` continua valendo.
- **Teste-trava** (`ExposeTestTagsAsResourceIdTest`, `kmplib-platform` androidUnitTest): confere que o
  modificador liga a flag e **varre os fontes de todos os módulos** — janela própria nova sem
  `exposeTestTagsAsResourceId()`/`WithTestTagsAsResourceId` reprova o `testDebugUnitTest`.
- Flow de exemplo:
  ```yaml
  - tapOn: { id: "dialogo-input" }
  - inputText: "EXCLUIR"
  - tapOn: { id: "dialogo-btn-confirmar" }
  ```

## 2.233.0 — A compra pela loja amarrada à conta logada, e o dublê da loja travado pela variante

Minor, **aditiva**. Origem: lições da 3ª leva (02/out/2026) que cada app remendava à mão — a compra
saía ANÔNIMA quando o app esquecia `identify`/`resetIdentity` (Folha de Axé, Arroba Certa, Minha
Despensa; 5 apps e a casca com cópias de `StoreIdentity`), e a trava do dublê de QA passava por
abreviação de tarefa (`aR`) e por `Release-<flavor>` no Xcode (Diária Certa). Fecha **GAP-MON-IDENT-01**.

- **`kmplib-monetization` — identidade da loja amarrada à sessão (`StoreIdentityBinder`).**
  `MonetizationManager.bindIdentity(auth: IAuthRepository, alerts, subjectOf = { it.id })` (uma linha na
  raiz do app, suspende enquanto a sessão emitir) e `bindIdentity(subjectIds: Flow<String?>, alerts)`
  (multi-tenant: o id da ORGANIZAÇÃO), `syncIdentity(subjectId)` (pontual) e a porta
  **`ensureIdentityForPurchase(): StoreIdentityStatus`** (`BOUND`/`UNMANAGED`/`NO_STORE`/`NO_SUBJECT`/
  `MISMATCH`, `allowsPurchase`). Regras: identifica no login e na sessão restaurada; anonimiza quando o
  sujeito SOME (logout, conta excluída, refresh expirado) — abrir sem sessão não chama a loja; tudo
  serializado por `Mutex`; a porta compara **igualdade** com a conta logada (não "não é anônimo": com
  reset e identify falhando em sequência a loja seguiria na conta ANTERIOR do aparelho), tenta
  identificar de novo antes de responder e recusa id reservado/anônimo. O sujeito já declarado entra
  na **configuração** do SDK quando a loja é inicializada depois (`appUserID` — o caminho recomendado
  pela RevenueCat), validado por `PurchaseIdentity.check`.
- **`PaywallViewModel` recusa comprar/restaurar** quando a porta não libera (novo parâmetro
  `identityGate`, default `MonetizationManager::ensureIdentityForPurchase`), com a mensagem
  `PaywallMessages.identityUnconfirmed` (`kmplib_paywall_identity_unconfirmed`, 4 idiomas). Porta que
  lança também recusa. **App que não declarou a identidade (`UNMANAGED`, app sem conta) compra como
  sempre** — nada muda para quem não chama `bindIdentity`.
- **Alertas novos**: `PaymentAlertKind.IdentificacaoNaLojaFalhou` (identify recusado — rede e build sem
  loja não alertam) e `CompraSemIdentidade` (compra recusada pela porta). `detalhe` só com o motivo
  tipado — o id da conta não sai do aparelho.
- **Novo artefato `br.com.codecacto:kmplib-gradle-plugin`** (build próprio em `gradle-plugin/`, mesma
  versão da lib, publicado junto pelo `publishToMavenLocal`/Central da raiz) com o plugin
  **`br.com.codecacto.kmplib.store-double`**: lê `-Pqa.paywallDemo=true`/`QA_PAYWALL_DEMO=1`, expõe
  `kmplibStoreDouble.enabled` e **reprova na execução** qualquer build publicável com o dublê —
  variante Android de build type **não-debuggable** (guarda antes do `pre<Variante>Build`: `assemble`,
  `bundle`, `build`, `publish`, abreviações, flavors), link Kotlin/Native **RELEASE** e o Xcode com
  `CONFIGURATION` `Release*`, `KOTLIN_FRAMEWORK_BUILD_TYPE=release` ou `ACTION=install` (Archive). O
  build de QA (debug) segue passando. App aplica com `alias(libs.plugins.kmplib.storeDouble)` e resolve
  por `pluginManagement { includeBuild(<kmplib>/gradle-plugin) }` (ou mavenLocal/Central).
- **`IOS_INTEGRATION.md` corrigido**: `FirebaseMessaging` é obrigatório com o guarda-chuva (cinterop do
  KMPNotifier, com ou sem push), `PurchasesHybridCommon` na tabela, `FirebaseCore` vem junto, tabela
  símbolo → produto no *Undefined symbols*, checklist com `export` granular (o `export(libs.kmplib)`
  da seção Gradle era anterior à 2.163.0).

Testes: `StoreIdentityBinderTest` (18) e 5 novos no `PaywallViewModelTest` (porta recusa compra e
restauração, `UNMANAGED` compra, exceção recusa); `kmplib-gradle-plugin`: `StoreDoubleRulesTest` (6) +
`StoreDoublePluginFunctionalTest` (6, TestKit com AGP 8.11 + Kotlin 2.3 reais e configuration cache:
release e flavor-release por abreviação reprovam, debug passa, link nativo release ganha a guarda,
Xcode `Release-<flavor>` reprova e `Debug` passa). Suíte `testDebugUnitTest`: 3.174 testes, 0 falhas;
`:kmplib-monetization:compileKotlinIosArm64` executado (não SKIPPED).
**Não é aviso**: aditivo e opt-in — quem não chama `bindIdentity` segue igual; a casca já nasce com
as duas peças. Plano para os apps que têm cópia (Backhand, Acervo, TaFeito, PalpiteCerto, Meu Fisio,
ChecklistVeicular, ControleDeValidade…): trocar a cópia por `bindIdentity` + o paywall da lib (ou
`ensureIdentityForPurchase` no paywall próprio) e o bloco de trava pelo plugin, **quando cada um for
tocado** (baseline).

## 2.232.0 — Publicidade com prova automatizada: ids de "o anúncio APARECEU" para o Maestro

Minor, **aditiva** (+ uma correção de automação). Origem: pedido do fundador (02/out/2026) — antes da
1ª publicação, o teste automatizado (Maestro, pela fila do Mac) tem de **provar** que a publicidade
aparece nos apps com anúncio. Até aqui só o "X" do intersticial tinha id.

- **`kmplib-ads` — `AdsTestTags` ganhou quatro ids** (vocabulário `tela-elemento-acao`):
  `ads-banner` (contêiner do banner, montado quando há criativo escolhido), **`ads-banner-carregado`**
  (só depois de a imagem do criativo ser decodificada e pintada — Coil `Success`), `ads-interstitial`
  (contêiner em tela cheia) e **`ads-interstitial-carregado`**; mais `AdsTestTags.all`. O teste afirma
  o `-carregado`: contêiner sem ele = anúncio escolhido mas arte que não chegou (URL/CDN/rede); nenhum
  dos dois = nada a mostrar (premium, roteamento `off`, sem criativo do formato).
- **`CustomBannerAd`/`CustomInterstitialAd`** (e por tabela `ManagedBannerAd`/`ManagedInterstitialAd`)
  aplicam os ids. O banner virou `Box` (contêiner) + `AsyncImage` (criativo): o `clickable` ficou no
  nó da imagem, porque um pai clicável mescla a semântica dos filhos e a `testTag` do pai venceria a
  do criativo. Layout, proporção, inset, impressão viewable e clique: **inalterados**.
- **Correção — o intersticial agora é visível ao Maestro no Android.** O `Dialog` do Compose é outra
  janela (outro `AndroidComposeView`, árvore de semântica própria) e **não herda** o
  `testTagsAsResourceId` que o `AppTheme` liga na raiz: desde a 2.166.0 o `ads-btn-fechar-interstitial`
  existia para o Compose e não aparecia como `resource-id`. O conteúdo do diálogo passa a ser embrulhado
  em `WithTestTagsAsResourceId` (no iOS é no-op; lá a tag já vira `accessibilityIdentifier`).
- Não há AdMob na lib (house ad do apps-api desde a 2.38.0): todo anúncio é nó Compose, então não
  existe janela nativa de SDK a detectar por fora. Como montar o flow: `kmplib-catalog` →
  `references/monetization.md` §"Prova de publicidade no Maestro".

Testes: `AdsTestTagsTest` (6) — literais travados, unicidade, vocabulário, id de carregado só no
`Loaded`, `Empty`/`Loading` do Coil não contam. Suíte `testDebugUnitTest`: 3.151 testes, 0 falhas;
`:kmplib-ads:build` e `:kmplib-ads:compileKotlinIosArm64` (executado, não SKIPPED) verdes.
**Não é aviso** (aditivo; a correção é de automação de teste, nada para de funcionar para o usuário).

## 2.231.0 — Teste grátis de 7 dias PELA LOJA: o paywall só promete o que a loja confirma

Minor, **aditiva**. Origem: o botão "Começar 7 dias grátis" do **Backhand** cobrava na hora — não havia
oferta na loja — e a Apple recusa isso pela **3.1.2**. Decisão do fundador (30/set/2026): o trial de app
com compra pela loja é a **oferta introdutória da loja** (`trial: P7D` no `monetizacao.yaml`, aplicada
pelo provisioner). Desenho completo: `docs/43-trial-pela-loja.md`.

- **`kmplib-monetization` — elegibilidade oficial da RevenueCat.** `PurchasePackage` ganhou
  `freeTrial: FreeTrialPeriod?`, `trialEligibility: TrialEligibility` e `canSkipFreeTrial`, mais o
  derivado `offerableFreeTrial` (só `ELIGIBLE`; `UNKNOWN` nunca vira promessa). iOS:
  `checkTrialOrIntroPriceEligibility` sobre o `introductoryDiscount` `FREE_TRIAL` (timeout 5 s);
  Android: a fase grátis em `subscriptionOptions.freeTrial` (o Play já filtra por elegibilidade).
- **Paywall:** `PaywallPlan.trial: PaywallTrial?` (preenchido pelos mapeadores, nunca à mão);
  `PaywallTexts.ctaLabel(plan)` ("Começar 7 dias grátis" | "Assinar"), `trialTerms(plan)` (o termo de
  cobrança — duração, preço e período da loja, renovação — **junto do botão**) e `pricePerPeriod(plan)`,
  nos 4 idiomas. `PaywallScreen` usa os três; tela própria chama os mesmos helpers.
  `PaywallPlanLabels.trialLabel` (semana vira "7 dias"), `PaywallTestTags.termoDoTrial(plan)`.
- **Uma conta = um trial (produto web + app):** `PaywallConfig.trialPolicy =
  PaywallTrialPolicy(alreadyUsed, offeringWithoutTrial)`. Trial já usado no backend → Android compra o
  **plano base sem a fase grátis**; iOS segue a opção do fundador por configuração (A: aceita; B:
  offering sem trial). Contrato novo, todos com default: `PurchaseRepository.getOfferings(offeringId)`,
  `PurchaseRepository.purchasePackage(packageId, withoutFreeTrial)`, `EntitlementProvider
  .loadOfferings(offeringId)`, `EntitlementProvider.purchasePackage(packageId, withoutFreeTrial)`.
- **`kmplib-core` — `Entitlement.trialUsadoEm`/`trialOrigem`/`trialUsed`**, lidos do `/me/entitlement`
  (registro `trial_usage` do admin-api); sobrevivem ao rebaixamento para free.
- **`kmplib-testing`:** `FakePurchaseRepository.comTrial(...)`, `ofertasPorOffering`, `comprasSemTrial`.

**Nada muda para quem não tem trial na loja:** sem oferta introdutória, `freeTrial = null` e o card
continua "Assinar". **Não é aviso** (aditivo; não conserta nada que esteja quebrado na lib).

## 2.230.0 — "Desenvolvido por": a mensagem do WhatsApp diz DE QUAL APP a pessoa veio

Minor, **aditiva**. Origem: mensagem "Olá! Vim pelo app e gostaria de falar com vocês." chegando no
WhatsApp da CodeCacto sem dizer o app (era o LocAki) — todos os apps caem no mesmo contato.

- **`kmplib-core` — `BuildInfo.appName: String?`**: o nome que o sistema mostra (Android: rótulo do
  `applicationInfo`; iOS: `CFBundleDisplayName`, senão `CFBundleName`). Por flavor. `null` sem
  `initKmpLib` no Android ou com rótulo vazio.
- **`kmplib-central` — `rememberDeveloperTexts(phoneFormat, appName = BuildInfo.appName)`**:
  `whatsappMessage` = "Olá! Vim pelo app **LocAki** e gostaria de falar com vocês." e `emailSubject` =
  "Contato via app **LocAki**", nos 4 idiomas (`kmplib_dev_whatsapp_message_from_app`,
  `kmplib_dev_email_subject_from_app`). Sem nome, o texto genérico de antes. Nada a mudar no app que
  usa `DeveloperScreen` sem `texts`; quem passa `DeveloperTexts(...)` próprio não ganha.

## 2.229.0 — own-auth: `username` no `User` (lido do `GET /me`) e edição do próprio nome (`PATCH /me`)

Minor, **aditiva**. Origem: **Folha de Axé** — par da **backlib 0.140.0** (`username` no `GET <auth>/me`,
`PATCH <auth>/me {name, username}`).

**`kmplib-core` — `User.username: String?`** (último parâmetro, default `null`). Nulo no Firebase, em
backend anterior à 0.140.0 e na sessão com senha temporária (o servidor o omite de propósito).

**`kmplib-auth` — perfil pelo servidor.**
- `OwnAuthApi.me(accessToken): Result<OwnAuthProfile>` e `OwnAuthApi.updateMe(accessToken, name, username)`;
  `OwnAuthProfile(id, email, name, username)` público. Campo nulo não vai no corpo do `PATCH`.
- **Depois de cada login** (senha, cadastro, social nativo e pelo navegador, primeiro acesso) o
  `EmailPasswordAuthRepository` lê o `/me` e mescla nome, e-mail e `username` na sessão — **best-effort**:
  falha da leitura não derruba o login. Efeito colateral bom: o login por senha deixa de nascer com
  `displayName = null`, e o login pelo navegador deixa de nascer com e-mail vazio.
- `OwnAuthService.updateOwnProfile(name, username): Result<User>` (PATCH + atualiza o `currentUser`) e
  `refreshOwnProfile(): Result<User>` (relê o `/me`; para o `ON_RESUME` da tela de perfil). Ambos com
  implementação default `Unsupported` na interface (quem implementa a porta fora da lib não quebra).
- `IAuthRepository.updateProfile(displayName)` **deixou de ser "não suportado"** no own-auth: grava o nome
  pelo `PATCH`. Com `photoUrl` a chamada falha inteira (foto não tem endpoint), sem gravar o nome.
- `OwnAuthException.ProfileRejected(message, code, serverCode, fieldErrors)` + `fieldError("name"|"username")`
  — 400/403/409/422 do `/me`, com o envelope da backlib inteiro (erro **no campo**). `OwnAuthErrorCodes`:
  `NOTHING_TO_UPDATE`, `PASSWORD_CHANGE_REQUIRED`, `USERNAME_CHANGE_DISABLED`, `USERNAME_TAKEN`. 404 do
  `PATCH` (backend sem `AuthLocalProfileEditor`) → `Unsupported`; 401 → `NotAuthenticated`; 5xx não
  vaza a mensagem interna. Chamar sem nada a alterar falha local, sem ida ao servidor.
- `OwnAuthSession.username` persistido no cofre; o refresh de token o preserva; sessão gravada antes
  desserializa com `null`. `OwnAuthTokenManager.applyProfile(profile)` só aplica se o perfil é da
  **mesma conta** da sessão (a pessoa pode ter trocado de conta com a leitura em voo) e não troca valor
  conhecido por vazio.
- `OwnAuthTexts.profileRejected` + recurso `kmplib_auth_profile_rejected` nos 4 idiomas.
- ⚠️ `OwnAuthException` ganhou um subtipo: `when` **exaustivo sem `else`** sobre ela deixa de compilar
  (nenhum consumidor do monorepo faz isso hoje).

Testes: `OwnAuthProfileTest` (21). Compilado `compileKotlinIosArm64` (core, ui, auth) no servidor.

## 2.228.0 — "Reduzir movimento", `AppTheme` com duas famílias, ambiente sonoro em laço, `.ics` e o motor de desenho → PNG

Minor, **aditiva**. Origem: design do **Folha de Axé** (`docs/design/wireframes.md` §16, GAP-FA-K01…K05).

**`kmplib-platform` — "reduzir movimento" do sistema (K01).** `isReduceMotionEnabled()`,
`reduceMotionChanges(): Flow<Boolean>`, `rememberReduceMotion(): State<Boolean>`, `LocalReduceMotion` e
`ProvideReduceMotion { }` (pacote `platform.motion`). Android: `Settings.Global.ANIMATOR_DURATION_SCALE`
**ou** `TRANSITION_ANIMATION_SCALE` em 0 ("Remover animações" / Opções do desenvolvedor), ao vivo por
`ContentObserver`; iOS: `UIAccessibilityIsReduceMotionEnabled()` + `UIAccessibilityReduceMotionStatusDidChangeNotification`.
**O `AppTheme` já provê `LocalReduceMotion`** — app da fábrica lê sem configurar nada. O contexto vem do
`initKmpLibPlatform` (novo `ReduceMotionHolder`); sem ele, `false`. Compose no Android já encurta
`animate*AsState` com a escala 0; a preferência existe para animação infinita/`Canvas` e para o iOS.

**`kmplib-ui` — `AppTheme(displayFontFamily = …)` e `createAppTypography(fontFamily, displayFontFamily)` (K02).**
Divisão *brand* × *plain* dos tokens do Material 3: `displayFontFamily` em display, headline e
**titleLarge**; `fontFamily` em titleMedium/titleSmall, body e label. Default = `fontFamily` (quem passa
uma só não muda nada). O parâmetro novo do `AppTheme` entra **depois** de `highContrast` — chamadas
posicionais antigas continuam compilando.

**`kmplib-ui` — `renderDrawingToPng`/`renderDrawingToImageBitmap(widthPx, heightPx, density, layoutDirection) { DrawScope }` (K03).**
O motor que o `renderShareCardToPng`/`renderGameShareCardToPng` usavam por dentro, exposto sem layout:
`CanvasDrawScope` sobre o `Canvas` de um `ImageBitmap` + `encodeBitmapToPng` — a API oficial do Compose,
em `commonMain`. Density default: `widthPx` = 360 dp (`DRAWING_REFERENCE_WIDTH_DP`). Os dois renders de
card passaram a usá-lo (saída idêntica). Para card de story com o layout do protótipo do projeto.

**`kmplib-media` — `AmbientSoundPlayer` (K04).** Ambiente sonoro em laço sem emenda, com volume e fade:
`createAmbientSoundPlayer(AmbientSoundConfig(mixWithOthers))`, `rememberAmbientSoundPlayer()` (pausa em
`ON_STOP`, retoma em `ON_START`, libera no dispose), `AmbientSoundBackgroundPause(player)` para o player do
ViewModel; `load(bytes)` suspenso, `play(fadeInMillis)`, `pause/stop(fadeOutMillis)`,
`setVolume(volume, fadeMillis)`, `state: StateFlow<AmbientSoundState>` (`AmbientSoundStatus`),
`AmbientSoundOutcome`/`AmbientSoundError` (nunca lança). Android **Media3/ExoPlayer** (`REPEAT_MODE_ONE`,
`ByteArrayDataSource` — sem arquivo temporário, `setHandleAudioBecomingNoisy`, foco de áudio só com
`mixWithOthers = false`); iOS **`AVAudioPlayer`** `numberOfLoops = -1` + `AVAudioSession` **`.ambient`**
(mistura e respeita o Silencioso; `.playback` com `mixWithOthers = false`), sessão configurada só no
primeiro `play`, retomada após interrupção com `ShouldResume`. O fade é uma rampa comum de 20 ms/passo
(reversível no meio: tocar durante um fade-out sobe do ponto atual). `kmplib-media` passa a depender de
`media3-exoplayer`/`media3-datasource` (Android) e `lifecycle-runtime-compose`. Prefira **`.m4a` (AAC)**
ou WAV: MP3 tem *padding* que o `AVAudioPlayer` não remove (emenda audível no iPhone).

**`kmplib-core` — `IcsCalendar` (K05)** (`core.ics`): `IcsCalendar.build(event|events, IcsCalendarOptions)`,
`IcsEvent`, `IcsTime.Timed/AllDay`, `IcsRecurrence` (`IcsFrequency`, `IcsWeekday`), `IcsAlarm`,
`IcsEventStatus`, `escapeText`, `foldLine`, `MIME_TYPE`. RFC 5545: CRLF, dobra em 75 octetos UTF-8 sem
partir acento/emoji, escape, `UID`/`DTSTAMP`, dia inteiro com `DTEND` exclusivo, `TZID` + **`VTIMEZONE`
gerado da base IANA do aparelho**, `RRULE` com `UNTIL` em UTC, `VALARM DISPLAY`. **Paridade byte a byte
com o `buildIcs` da weblib 0.224.0** — a suíte compara 6 saídas geradas pela weblib (`IcsWeblibGolden.kt`;
regenerar empacotando `src/utils/ics.ts` com o `esbuild` da weblib e rodando no node). Única diferença
intencional: `PRODID` default `-//CodeCacto//kmplib//PT-BR`. **`kmplib-platform` — `ShareHandler.shareIcs(ics,
fileName, title)`**: `shareFile` com `text/calendar`, garante a extensão `.ics`.

**`kmplib-astro` — KDoc do `MoonCalculator` corrigido.** O exemplo dizia que `phaseOn(2026-08-10)` era
`WAXING_GIBBOUS` com 87%; o cálculo dá **`WANING_CRESCENT`, 6%** (a lua nova de 12/08/2026 17:36:35 UTC
vem dois dias depois). Só documentação — o cálculo sempre esteve certo; teste novo trava o exemplo.

**Testes:** `IcsCalendarTest` (16, 6 de paridade), `ShareIcsTest` (3), `ReduceMotionRuleTest` (3),
`AppTypographyFamiliesTest` (2), `DrawingRenderTest` (2), `AmbientSoundPlayerTest` (12), +1 no
`MoonCalculatorTest`. `compileKotlinIosArm64` + `compileTestKotlinIosArm64` de core/platform/ui/media/astro
verdes (sem SKIPPED). **Pendente no Mac:** ouvir o laço/fade do `AmbientSoundPlayer` num device, alternar
"Reduzir Movimento" com o app aberto, e importar um `.ics` compartilhado no Calendário.

## 2.227.0 — `PriceHistoryChart` (preço em degrau) e `PermissionBanner`; notificação desligada passa a ser lida

Minor, **aditiva**. Origem: design do **Rede de Ofertas** (`docs/design/gaps-de-lib.md` G1, G10, G11).

**`kmplib-ui` — `PriceHistoryChart` (G1).** Histórico de preço em **degrau** (a linha fica no valor até a
próxima captura e muda na vertical — reta ou curva entre R$ 100 e R$ 80 inventaria um R$ 90 que nunca
existiu; por isso não é o `LineChart`/`AreaChart`). Canvas do Compose em `commonMain`, sem WebView.
- `PriceHistoryChart(points, modifier, startMillis, endMillis, chartHeight, lineColor, lowestColor,
  priceFormatter, axisPriceFormatter, timeZone, showLowestLegend, texts)`; `PricePoint(atEpochMillis,
  priceCents)` — centavos em `Long`.
- **Eixo Y real** em marcas redondas (`priceAxisTicks`, 1/2/2,5/5×10ⁿ), sem forçar o zero; BRL por default
  (`defaultPriceFormatter`/`defaultPriceAxisFormatter` — `R$ 2.500` no eixo), trocável para outra moeda.
- A captura anterior ao início da janela vira o **preço de abertura** (`PriceStep.carriedOver`) — a janela
  de 30 dias não começa "no vazio".
- **Menor preço** marcado (cor de sucesso do tema, guia tracejada, legenda "Menor preço: … em …"); empate =
  ocorrência mais recente. Pontos de captura quando cabem (≥ 8 dp cada).
- **Toque/arrasto** mostra o balão "preço em data hora" (`ddMMjjmm` pela região).
- **Acessível:** `contentDescription` com atual/menor/maior e datas, `stateDescription` do ponto escolhido e
  ações "Próxima/Anterior mudança de preço" e "Limpar seleção" (TalkBack/VoiceOver navegam sem tocar).
- Lógica pura e testada: `buildPriceHistory` → `PriceHistorySeries`/`PriceStep`/`PriceExtreme`,
  `priceStepIndexAt`, `priceAxisTicks`. Textos nos 4 idiomas (`kmplib_price_history_*`,
  `rememberPriceHistoryChartTexts()`).

**`kmplib-ui` — `PermissionBanner` (G11).** Faixa de permissão negada = `AppBanner` (aviso, suave) ligado ao
`PermissionState`: "Permitir" enquanto o sistema ainda pergunta, "Abrir configurações" na negação
definitiva; **reconsulta no `ON_RESUME`** (some sozinha na volta das Configurações); não pede nada ao
aparecer (nunca pedida = escondida, salvo `showWhenNotRequested`). Sobrecarga sem estado
`PermissionBanner(status, message, onRequest, onOpenSettings, …)` para tela MVI;
`permissionBannerAction(status, showWhenNotRequested)` puro; `PermissionBannerTexts`/
`rememberPermissionBannerTexts()` (4 idiomas).

**`kmplib-platform` — o que o banner precisava e a lib não tinha:**
- **`UrlLauncher.openNotificationSettings()`** — Android `ACTION_APP_NOTIFICATION_SETTINGS` +
  `EXTRA_APP_PACKAGE` (API 26+, cai na página do app se o fabricante não resolver); iOS 15.4+
  `openNotificationSettingsURLString`, antes disso `openSettingsURLString`. Default da interface delega a
  `openAppSettings()` (fakes de app continuam compilando).
- **`PermissionState.openSettings()`** — abre a tela certa (notificações do app para `NOTIFICATIONS`,
  página do app para o resto). `openAppSettings()` continua.
- **`PermissionManager.currentStatus(permission)`** (suspenso, default = `checkPermission`). No iOS lê
  notificação por `getNotificationSettings` — o `checkPermission` síncrono respondia sempre
  `NOT_REQUESTED`. `PermissionState.refresh()` passou a usá-lo (assíncrono) + **`refreshNow()`** suspenso.
- **Correções de leitura de notificação:** Android considera o interruptor do app
  (`areNotificationsEnabled`) — abaixo da API 33 era "concedida" para quem desligou tudo
  (`combineNotificationStatus`, puro); iOS trata `provisional`/`ephemeral` como concedida (antes virava
  "não pedida" e o app perguntava de novo). Nenhuma das duas para de funcionar algo — só passa a dizer a
  verdade — por isso sem aviso.

**G10 `AppSwitch` não era lacuna:** existe desde a 2.12.0 (`ui/components/AppSwitch.kt`).

**Testes:** `PriceHistoryLogicTest` (15), `PermissionBannerActionTest` (4), `NotificationPermissionStatusTest` (7).
`compileKotlinIosArm64` + `compileTestKotlinIosArm64` de `platform`/`ui`/`camera` verdes (sem SKIPPED).

## 2.226.1 — PDF no iOS não derruba mais o app: cor do texto chega ao CoreText como objeto

Patch, **correção de crash** no `kmplib-pdf` (iOS). Achado pela primeira rodada da suíte iOS no
simulador (verbo `testar-lib` do qa-runner): o `PdfCanvasIosRenderTest` **abortava o processo**.

**Causa.** A cor do texto ia ao `NSAttributedString` como `CGColorRef` cru
(`addAttribute("CTForegroundColor", value = uiColor.CGColor)`). O Kotlin/Native não entrega o
`CGColor`: embrulha o ponteiro num objeto Kotlin, e o CoreText, ao desenhar a linha, manda `-CGColor`
para o embrulho → `NSInvalidArgumentException: unrecognized selector` → `SIGABRT`. Valia para **todo
texto de PDF no iOS**: o canvas de layout livre (`buildPdf`/`recordPdf`, 2.225.0) **e** os 9 geradores
legados (`IosPdfCanvas` — recibo, OS, relatórios, carteira de vacinação…).

**Correção.** `coreTextForegroundColor()` = `CFBridgingRelease(CGColorRetain(cgColor))`, a ponte
oficial de tipo Core Foundation para `id` (posse +1 equilibrada). Sem mudança de API.

**Afeta:** quem gera PDF com texto no iOS em qualquer versão com `IosPdfCanvas` (2.77.0+) ou com o
canvas (2.225.0–2.226.0). `PlatformCapabilities.pdfGeneration` continua `false` no iOS (validação
visual dos 9 geradores pendente) — o flag não muda nesta versão.

**Testes (suíte iOS passa a rodar de verdade).**
- `ReciboPdfIosRenderTest` (novo, iosTest) — gera o recibo pelo renderizador legado e relê no PDFKit.
- `PlatformCapabilityTest."android entrega camera e pdf"` saiu do commonTest (falhava no simulador
  iOS) para `PlatformCapabilitiesAndroidTest` (androidUnitTest); `PlatformCapabilitiesIosTest` trava
  o valor declarado no iOS.
- `assert()` nos testes do `ui` virou `assertTrue` (no Native exige `ExperimentalNativeApi`; no JVM,
  sem `-ea`, não provava nada).
- Nomes de teste com `,` `(` `)` — ilegais no Kotlin/Native — corrigidos nos 13 módulos restantes, e
  `Runnable` importado de `kotlinx.coroutines` no `sync`: **`compileTestKotlinIosArm64` e
  `compileTestKotlinIosSimulatorArm64` verdes em TODOS os módulos** (fecha a dívida da 2.225.0).

## 2.226.0 — `ContactScreen` pré-preenchida: assunto, mensagem inicial e lista de assuntos

Minor, **aditiva** (três parâmetros opcionais com default `null`; quem não passa nada vê a tela de
sempre). Origem: **Colinha do Voto** (`GAP-CV-05`) — "Informar erro neste candidato" precisa abrir o
"Entrar em contato" já com o assunto e um texto inicial com o cargo e o número PÚBLICO do candidato.

**`kmplib-central` — `ContactScreen(…, initialSubject, initialMessage, subjects, onSent)`.**
- `initialSubject: String?` — assunto com que a tela abre, editável. Vai no campo **`subject`** do
  `POST /contact/v1`, que **já existe** no contrato do apps-api (`CreateContactRequest.subject`, até
  300 caracteres) e já era enviado pelo `ContactService` — nada é prefixado na mensagem.
- `initialMessage: String?` — texto inicial da mensagem, editável e **não aparado** (termine em
  `"\n\n"` para a pessoa escrever embaixo do contexto; o envio apara).
- `subjects: List<String>?` — com lista, o assunto vira seletor (`AppDropdownField`) em vez de texto
  livre; `null`/vazia mantém o texto livre. A lista é aparada, sem vazios e sem repetidos; um
  `initialSubject` que não está nela entra **no topo** (nunca some em silêncio). Continua opcional.
- Os três entram **antes** de `onSent`, que segue podendo ser trailing lambda.

**Suíte iOS do `central` volta a compilar** (`compileTestKotlinIosArm64`): dois nomes de teste com
vírgula, ilegal no Kotlin/Native (dívida registrada na 2.225.0 — restam 12 módulos no backlog).

Testes: `ContactPrefillTest` (5). `compileKotlinIosArm64` e `compileTestKotlinIosArm64` do `central`
executados (não SKIPPED).

## 2.225.0 — PDF de layout livre, impressão (térmica 58/80 mm), salvar arquivo e `DigitBoxField`

Minor, **aditiva** (nada existente mudou de assinatura). Origem: o desenho do **Colinha do Voto**
(`GAP-CV-01/02/03`) — seis formatos de papel de medida própria (cartão 85×55, tira 70×297, A4 com 8
recortáveis, bobina térmica 58 e 80 mm de comprimento variável), botão Imprimir com papel térmico e o
campo de número de candidato em caixas. A lib só tinha geradores de PDF de domínio em A4, **nenhuma
impressão** e nenhum "salvar como".

**`kmplib-pdf` — `br.com.codecacto.kmplib.pdf.canvas` (GAP-CV-01).** `buildPdf { page(PdfPageSize) { … } }`
(suspend, `Dispatchers.Default`) e `recordPdf` → `RecordedPdf` (`pages` com a altura final, `toByteArray()`).
Geometria em **mm** (origem topo-esquerda), corpo e traço em **pt**. `text` (y = topo da linha; âncora ou
caixa com "…"), `textBlock` (quebra por palavra, `\n`, `maxLines`), `textInBox` (dígito na caixa, centro
pela altura de versal), `line`/`rect` com `PdfStroke(widthPt, color, dash)` e `PdfDash.CutLine`,
`image(fit)`, `offset {}`, `markExtent`, `measureText` → `PdfTextMetrics`. `PdfPageSize`
(`A4`/`A5`/`LETTER`/`THERMAL_58`/`THERMAL_80`/`fixed`/`roll`) e `PdfPageHeight.FitContent` — bobina que
passaria do máximo **lança `PdfLayoutException`** em vez de cortar o rodapé. `PdfTextStyle(tabularNumbers)`
e `PdfFontFamily.fromBytes(name, regular, bold)` — fonte do app embutida (papel idêntico nas duas
plataformas; Android 10+ `Font.Builder(ByteBuffer)`, antes arquivo estável no cache; iOS
`CTFontManagerCreateFontDescriptorFromData`). Android: `PdfDocument` (Skia, `DashPathEffect`, texto com
`SUBPIXEL`+`LINEAR_TEXT` para a medida bater com o desenho). iOS: `UIGraphicsPDFRenderer.beginPageWithBounds`
(uma medida por página) + CoreText.

**`kmplib-platform` — `platform.print` + `FileSaver` (GAP-CV-02).** `rememberPrintHandler()`/`getPrintHandler()`
→ `PrintHandler.printPdf(pdf, jobName, paper, colorMode, onResult)` + `isPrintingAvailable`; `PrintPaper`
(`A4`/`A5`/`LETTER`/`THERMAL_58`/`THERMAL_80`/`sheet`/`roll` — bobina com o comprimento da página do
PDF), `PrintColorMode`, `PrintResult`. Android: `PrintManager` + `PrintDocumentAdapter` (contagem pelo
`PdfRenderer`, escrita fora da main, `MediaSize` customizado em mils, `NO_MARGINS`). iOS:
`UIPrintInteractionController`, papel por `printInteractionController(_:choosePaper:)` com
`UIPrintPaper.bestPaper(forPageSize:)` e corte da bobina por `printInteractionController(_:cutLengthFor:)`;
iPad ancorado. `rememberFileSaver(onResult)` → `FileSaver.save(bytes, fileName, mime)`: SAF
`ACTION_CREATE_DOCUMENT` (sem permissão) / `UIDocumentPickerViewController(forExportingURLs:asCopy:)`;
`FileSaveResult`. Compartilhar segue sendo `ShareHandler.shareFile`. O `topViewController()` do
`IosShareHandler` virou helper interno (`IosPresentation.kt`) e serve aos três.

**`kmplib-ui` — `DigitBoxField` + `DigitBoxDisplay` (GAP-CV-03).** N caixas de dígito sobre UM
`BasicTextField` (teclado numérico, colar, apagar volta a caixa, toque em qualquer ponto da linha, um nó
só para o leitor de tela: número inteiro + rótulo + "2 de 4 dígitos preenchidos"). Erro no campo:
`errorMessage` do app (incompleto pós-envio com `rememberDigitBoxTexts().missing(n)`, duplicado, externo);
**excedente** tratado pelo próprio campo — colar mais algarismos do que cabe **não corta** (manteria um
número que a pessoa não escreveu), mantém o anterior e avisa. `masked` (PIN), `autoFocus`, `onFilled`,
`onOverflow`, caixas que encolhem até 28 dp, algarismos tabulares. Regras puras `applyDigitBoxInput`
(`DigitBoxInput.Accepted/Overflow/Ignored`) e `digitBoxMissingCount`. 5 recursos novos
(`kmplib_digitbox_*`) nos 4 idiomas.

**Suíte iOS de `pdf` e `platform` voltou a compilar.** Nomes de teste entre crases com `,`/`()` são
aceitos no JVM e recusados pelo Kotlin/Native — `compileTestKotlinIosArm64` falhava nos dois módulos
(e falha em mais 13: registrado no `docs/backlog.md`). Corrigidos os de `pdf`/`platform`, sem mudar o
que os testes provam.

Testes: `PdfCanvasRecordingTest` (38), `PrintRulesTest` (10), `DigitBoxLogicTest` (15);
`PdfCanvasIosRenderTest` (iOS — gera, reabre no PDFKit, confere medida, texto e `tnum`) compila aqui e
**roda no Mac** (`./gradlew :kmplib-pdf:iosSimulatorArm64Test`). `iosMain` dos três compilado com
`compileKotlinIosArm64 -Pkmplib.forceAppleTargets=true` (não `SKIPPED`).

**Ação nos apps:** nenhuma — aditivo.

## 2.224.1 — Fastfile iOS: a versão vai só por argumento do xcodebuild; o `.pbxproj` não é mais reescrito

Patch, só `ci/fastlane/Fastfile` (nenhum artefato Kotlin mudou). A lane `release` chamava
`increment_version_number_in_xcodeproj` e `increment_build_number_in_xcodeproj` **e também** passava os
dois valores em `xcargs` (desde `0fcfb16`). O argumento do xcodebuild vence a cadeia de build settings,
então a gravação no `.pbxproj` não decidia nada — só deixava o clone do Mac com a árvore suja, e o
qa-runner recusa testar por cima de árvore suja. Com o `release.sh` da casca (≥ `724860d1`) mandando
sempre o `MARKETING_VERSION` do `version.properties`, as duas gravações saíram. A lane `build` também
deixou de gravar a versão no `.pbxproj` e passa `MARKETING_VERSION` por `xcargs`.

**Ação nos apps:** nenhuma — o Fastfile é importado da `main`. O plugin `fastlane-plugin-versioning`
deixou de ser usado pelas lanes da lib (pode sair do `Pluginfile` do app quando ele for tocado).

## 2.224.0 — `PaywallViewModel` + `PaywallHost`: a lógica do paywall sai dos apps e vem para a lib

Minor, **aditiva** (nada existente mudou de assinatura). Origem: a lógica em volta da `PaywallScreen`
— ler a oferta, montar os planos, cair para a loja quando a oferta central não vem, comprar,
restaurar, abrir a gestão de assinatura, reler ao voltar ao primeiro plano e decidir quando alertar —
estava **copiada à mão em ~15 apps**, cada cópia com uma parte da régua (uma alertava paywall vazio
sem rede, outra não relia no `ON_RESUME`, outra usava `plans()` e não via a oferta ilegível). As
duas cópias mais completas eram a do **LocAki** (oferta central + fallback da loja) e a da
**casca-mobile** (só loja, régua de alertas inteira); a peça nova é a soma das duas.

`kmplib-monetization`, pacote `br.com.codecacto.kmplib.ui.screens.paywall` (o mesmo da `PaywallScreen`):

- **`PaywallViewModel(entitlementProvider, config, paymentAlerts, loadMessages)`** — `BaseViewModel` da
  lib; estado `PaywallHostState(paywall, isRefreshing)`, ações `PaywallHostAction`
  (`Load`/`Refresh`/`ShowUsage`/`Paywall`), efeitos `PaywallHostEffect`
  (`Close`/`OpenUrl`/`OpenSubscriptionManagement`/`OpenDeveloper`/`ShowMessage`).
- **`PaywallConfig`** — URLs legais, **`offerSource: PaywallOfferSource`** (`Store` = app de login
  próprio; `CentralWithStoreFallback(controller | readPlans)` = oferta do admin-api ∩ loja, com
  **fallback pela loja quando a oferta é ILEGÍVEL** — oferta vazia continua vazia), selo forçado,
  benefícios do card vindo da loja, `savings`, `afterActivation` (`ShowActive`/`Close`) e
  `onPremiumActivated` (ex.: invalidar o cache do entitlement).
- **`PaywallHost(viewModel, onClose, onOpenDeveloper, …)`** — a tela inteira: relê a cada `ON_RESUME`,
  puxa para atualizar (`RefreshableBox`), abre documento legal e a **gestão de assinatura da loja da
  plataforma** (`getUrlLauncher().openSubscriptionManagement()`), snackbar. Repassa os slots e o
  `headerIcon` da `PaywallScreen`.
- **`PaywallSavings`** + **`withStoreSavings`** / **`savingsPercent`** — "Economize N%" e preço por mês
  sobre o mensal, com os **micros da loja** em `Long` e só na mesma moeda (o que LocAki e Super 8
  calculavam cada um do seu jeito). Opt-in.
- **`PaywallMessages`** + **`loadPaywallMessages()`** — os textos que o ViewModel monta fora da
  composição, nos 4 idiomas (3 recursos novos: `kmplib_paywall_nothing_to_restore`,
  `kmplib_paywall_unavailable`, `kmplib_paywall_savings`).
- **`PurchaseManagerEntitlementProvider`** (`monetization.entitlement`) — o provider sobre o
  `PurchaseManager` **que o app já inicializou**, sem inicializar nada. É o do app que inicializa a
  loja com o usuário depois do login (LocAki, Super 8): o `RevenueCatEntitlementProvider` criado cedo
  pelo Koin inicializaria a loja com um app user anônimo. O `RevenueCatEntitlementProvider` passou a
  delegar a ele (comportamento idêntico).

**Régua de alertas** (GlitchTip → Discord): `OfertaCentralIndisponivel` quando a oferta central não
veio **e o usuário não está sem rede**; `PaywallSemPlano` quando a loja respondeu vazia ou nada vendável
sobrou; `LojaIndisponivel` só em falha de loja que é incidente; `CompraFalhou`/`RestauracaoFalhou` só
em incidente. Sem rede, cancelamento, cartão recusado e build sem chave **não** alertam.

Dependência nova no módulo: `lifecycle-runtime-compose` (`implementation`; o `kmplib-ui` já a tinha).

Testes: `PaywallViewModelTest` (41) e `PaywallSavingsTest` (6).

**Migração** (não obrigatória — quem não usa não muda nada): trocar o ViewModel próprio do paywall por
`PaywallViewModel` no Koin e a tela por `PaywallHost`. Feita na `casca-mobile` e no LocAki nesta
rodada; demais apps, quando forem tocados.

## 2.223.0 — `WithTestTagsAsResourceId` público (app com tema próprio expõe os `testTag` ao Maestro)

Minor, **aditiva**. Origem: **Super 8** (29/set/2026) — o app tem tema próprio (nasceu antes do
`AppTheme` da lib), então nenhum `testTag` virava `resource-id` no Android e o flow Maestro do print
de tela no Mac não achava nada por id; por texto, quebrou no emulador em inglês.

- `@Composable fun WithTestTagsAsResourceId(content)` (`kmplib-ui`, `ui.theme`) deixa de ser `internal`.
  Quem usa o `AppTheme` da lib já está coberto; app com tema próprio embrulha a raiz uma vez.
  Android: `semantics { testTagsAsResourceId = true }` na raiz; iOS: no-op (o Compose já publica a tag
  como `accessibilityIdentifier`). Nada muda para quem não usa.

## 2.222.0 — `DomainApiClient.postJsonForBytes(path, body)`: POST com corpo JSON e resposta binária

Minor, **aditiva**. Origem: **ExtinRota** (GAP-ER-12) — o backend trocou `GET /v1/etiquetas/pdf?ids=…`
por `POST /v1/etiquetas/pdf` com `{"ids":[…],"formato":…}`: acima de ~110 UUIDs a query estourava a
linha inicial do servidor, que respondia 400 antes de a aplicação ver o pedido. O cliente de domínio
só tinha binário por `GET` (`getBytes`), e o app não tinha como pedir o PDF sem montar um `HttpClient`
próprio (perdendo o Bearer, o refresh no 401, o 402 → `Quota` e o envelope de erro).

- `suspend fun postJsonForBytes(path: String, body: String): DomainResult<ByteArray>` (`kmplib-core`,
  `sync.rest.DomainApiClient`) — mesmo núcleo das demais chamadas (`execute` + leitura do corpo
  protegida, como em `getBytes`). Nada muda para quem não usa.
- Testes: 3 novos em `DomainApiClientTest` (método/Content-Type/corpo/Bearer e bytes crus; envelope de
  erro com `serverCode`/`fieldError` e 402 como `Quota`; refresh + 1 retry no 401).

## 2.221.0 — exclusão de conta com a confirmação digitada no corpo (`deleteAccountAndData(confirmation)`); lista reordenável por arrasto e acessível (`ReorderableList`)

Minor, **aditiva**. Origem: **ExtinRota** (lacuna da exclusão de conta + ER-04, reordenar paradas).

### 1. `AccountDeletionService.deleteAccountAndData(confirmation: String?)` (`kmplib-auth`)
- Nova sobrecarga que leva no corpo do `DELETE {dataPath}` a palavra digitada pela pessoa:
  `{"confirmacao": "<texto aparado>"}` (JSON montado pelo serializador, via `DomainApiClient.deleteJson(path, body)`).
  Nome do campo: parâmetro de construtor `confirmationField` (default `"confirmacao"`).
- Para o backend que confere o "EXCLUIR" do lado do servidor (o ExtinRota responde
  `400 CONFIRMATION_REQUIRED` a um `DELETE` sem corpo). **Não é contrato da backlib**: a backlib não
  implementa `/v1/me/data` (cada backend implementa o seu) e a `casca-backend` ignora o corpo — mandar não quebra.
- `deleteAccountAndData()` sem argumento continua idêntico (`DELETE` sem corpo).
- A `casca-mobile` passa a mandar o que foi digitado no `AppInputDialog`.
- Testes: 4 novos em `AccountDeletionServiceTest` (corpo enviado, sem corpo no caminho antigo, recusa
  do servidor preserva a sessão, escape do JSON).

### 2. `ReorderableList` (`kmplib-ui`, ER-04)
- `ReorderableList(items, key, onMove, modifier, onReorderFinished, state, contentPadding,
  verticalArrangement, enabled, dragOnLongPress = true, texts) { item, index, isDragging -> }` —
  segurar e arrastar (ou arrastar pela alça `DragHandle()` / `Modifier.dragHandle()` do escopo, sem
  espera); autoscroll na borda, vizinhos animados, háptico ao pegar/trocar/soltar.
- **Acessível sem arrasto:** cada item tem ações de acessibilidade *Mover para cima/baixo/para o
  início/para o fim* (só as possíveis naquela posição) e estado "Posição 2 de 5"; a alça fica fora da
  árvore de acessibilidade. Textos nos 4 idiomas (`rememberReorderableListTexts()`).
- Contrato: `onMove` aplica a troca **síncrona** na lista (`reorderMove(itens, from, to)`); persistir
  em `onReorderFinished` (uma vez ao soltar, e após cada ação de acessibilidade).
- Base: **Reorderable** (`sh.calvin.reorderable:reorderable:3.1.0`, KMP) — dependência nova,
  `implementation` (nenhum tipo dele na API pública).
- Puras e testadas (9 casos): `reorderMove`, `reorderActionsFor`, `reorderTargetIndex`.

## 2.220.0 — mapa nativo de carteira (`NativeMap`: pinos coloridos, agrupamento, rota, posição do usuário, enquadrar) com **MapKit real no iOS**; escala de status com cor arbitrária (`StatusScale`/`StatusChip`); `ProportionalBar` e `AppStepper`

Minor, **aditiva**. Origem: design do **ExtinRota** (carteira de extintores num mapa, semáforo de
vencimento, rota de visitas) — `GAP-ER-01`, `-06`, `-07`, `-08`. Referência promovida (só leitura):
o mapa nativo do Prospecta (Maps Compose + MapKit).

### 1. `NativeMap` — mapa de carteira no SDK oficial dos dois sistemas (`kmplib-map`, GAP-ER-01)
- `NativeMap(items, modifier, controller, polylines, clustering = true, showUserLocation = false,
  fitOnFirstLoad = true, onItemClick, onClusterClick, onMapClick, onMapLongClick)`.
- **Android:** Google Maps SDK (`maps-compose`) + **agrupamento pelo `ClusterManager` oficial**
  (`maps-compose-utils`/android-maps-utils — dependência nova, `implementation`), com renderizador
  próprio: o pino sai na cor do item com texto curto dentro, o grupo na cor do **membro mais grave**
  com o contador exato (o `DefaultClusterRenderer` só tem pino vermelho e "10+"/"20+").
- **iOS:** **MapKit** (`MKMapView` via `UIKitView`) — SDK da Apple, sem chave, sem SPM, sem ponte
  Swift. `MKMarkerAnnotationView` com `markerTintColor`/`glyphText`, agrupamento nativo por
  `clusteringIdentifier`, `MKPolyline`, `showsUserLocation`, toque/toque longo no mapa.
- Modelos: `MapItem(id, position, title, snippet, style, priority)`, `MapMarkerStyle(color, glyph,
  glyphColor)` (glifo até 3 caracteres, sem partir emoji; cor do glifo por contraste quando nula),
  `MapPolyline(id, points, color, width)`, `LatLngBounds.of(points)`.
- Câmera: `rememberMapController()` → `MapController.fitTo(points, padding, maxZoom)` /
  `moveTo(...)` / `cameraPosition` (atualizado quando o movimento para). Comando dado antes de o mapa
  carregar fica guardado e é aplicado ao carregar. Um ponto só vira "centralizar em `maxZoom`" (em
  vez do zoom máximo do SDK).
- Toque em grupo sem `onClusterClick`: aproxima até separar; se todos estão no MESMO ponto (vários
  itens no mesmo endereço), abre o mais grave via `onItemClick`.
- Posição do usuário: só com permissão já concedida (reconferida no `ON_RESUME` no Android). O mapa
  **não pede** permissão — use `createLocationProvider()`.
- Puras e testadas (21 casos): `limitGlyph`, `clusterRepresentative`, `clusterCountLabel`,
  `longitudeSpanForZoom`/`zoomForLongitudeSpan`/`latitudeSpanFor` (ponte zoom ↔ `MKCoordinateSpan`).

### 2. iOS do `MapView` deixou de ser placeholder
Sem `IosMapBridge.factory` registrada, o `MapView` (e `MarkersMap`/`SinglePinMap`/`PickerMap`)
agora desenha **MapKit** — antes mostrava o texto "Mapa indisponível". Com a ponte registrada, segue
exatamente como era (Google Maps via Swift).

### 3. Escala de status com cor arbitrária (`kmplib-ui`, GAP-ER-07)
- `StatusLevel(key, label, color, icon, severity)` + `StatusScale(levels)` (`level`, `require`,
  `mostSevere`, `bySeverityDescending`) — o semáforo é do app, não da lib.
- `rememberStatusRamp(count = 5)` (tema: `success` → `warning` → `error`) e `rampColors(stops, count)`.
- `StatusChip(label, color | tone | level, icon, style = TINTED|SOLID)` — ícone + rótulo + cor com
  **contraste AA garantido**; `StatusBadge(text, color)` (overload novo, cor arbitrária).
- `ColorContrast.adjustForContrast(foreground, background, minRatio)`.

### 4. `ProportionalBar` e `AppStepper` (`kmplib-ui`, GAP-ER-08)
- `ProportionalBar(segments: List<ProportionalSegment>, …)` — uma barra dividida na proporção (legenda
  com percentuais que somam 100: `proportionalPercents`, maior resto).
- `AppStepper(value, onValueChange, min, max, step, label, …)` — `[−] n [+]`, alvos de 48 dp,
  descrições nos 4 idiomas (`kmplib_stepper_decrease`/`_increase`), valor como região viva;
  `stepperNextValue` (preso aos limites, sem estourar `Int`).

### 5. Conferido, sem mudança (GAP-ER-06)
`signature` já expõe o composable `SignaturePad(state, …)` + `SignaturePadState.toPngBytes()`;
`OsPdfData` já aceita logo (`OsPdfCompany.logoBytes`) e marca d'água (`watermark`/`watermarkText`)
nos dois sistemas. Assinatura em PDF: `ReciboPdf` (`assinaturaEmitenteBytes`/`assinaturaPagadorBytes`) e `InspectionPdf` (`signatures`).

**Consumo:** `api(libs.kmplib.map)` (ou o umbrella). Android: a chave do Maps no manifesto do app
(`com.google.android.geo.API_KEY`), como antes. iOS: nada a instalar; para o ponto azul,
`NSLocationWhenInUseUsageDescription` no `Info.plist`.

## 2.219.0 — app GLOBAL: telas da lib nos 4 idiomas da fábrica, idioma/região/formatos do aparelho, `Accept-Language` + `X-Time-Zone`, telefone internacional (E.164), `locale` no cadastro

Minor, **aditiva**. Em aparelho brasileiro (pt-BR, região BR) **nada muda na tela** — os textos pt-BR
dos recursos são os mesmos dos defaults de antes (travado por teste de paridade). Primeiro consumidor:
QueiMap, que tinha contornado tudo isto no app (`LibTexts.kt`, `PlatformLocale.*`, `DateField`,
`HttpClientFactory`). Regra da casa mantida: **o app segue o idioma do DISPOSITIVO**, pelos Compose
Resources oficiais — sem seletor, sem `LocalComposeEnvironment`.

### 1. Telas e componentes da lib no idioma do aparelho (pt-BR · en · es · pt-PT)
196 strings novas em `kmplib-ui/composeResources` (`values`, `values-en`, `values-es`,
`values-pt-rPT`), prefixo `kmplib_`. Onde o app **não** passa texto, a lib agora lê o recurso:
- **Login/Cadastro**: os defaults das lambdas de `LoginTexts`/`RegisterTexts` viraram
  `stringResource` — `LoginTexts(title = { … })` mantém o resto traduzido. `LoginTexts.andText`
  (novo; era `" e "` fixo). Placeholder de telefone do cadastro continua o formato BR (a máscara
  default é a BR).
- **Primeiro acesso**: `ForcePasswordChangeDialog` — título/descrição/botão com default do recurso
  e `texts: ForcePasswordChangeTexts = rememberForcePasswordChangeTexts()` (rótulos, saudação, erros
  de campo; eram pt fixos).
- **Feedback / Desenvolvido por / Contato**: `rememberFeedbackTexts()`, `rememberDeveloperTexts()`,
  `rememberContactTexts()`; `texts` das três telas virou `FeedbackTexts? = null` (etc.) — passar o
  objeto continua igual.
- **Paywall**: `rememberPaywallTexts()` (disclosure legal traduzido nos 4), data de renovação no
  formato da região, `UsageMeter`/`UsageBadge` ("Ilimitado", "3 de 10"); nomes de plano
  `PaywallPlanLabels` + `loadPaywallPlanLabels()`/`rememberPaywallPlanLabels()` (passe
  `rotulos::planName`/`rotulos::durationLabel` aos mapeadores).
- **PDF**: `rememberPdfViewerTexts()` (default de `rememberPdfViewerState`; lembrado pelas frases —
  objeto novo a cada quadro recarregaria o documento). `kmplib-pdf` passou a depender de `kmplib-ui`
  (`implementation`).
- **Componentes**: `rememberErrorStateTexts()`, `rememberSearchTopBarTexts()` (+ `backDescription`/
  `menuDescription`, eram "Voltar"/"Menu" fixos), `rememberConnectivityTexts()`,
  `rememberMaintenanceTexts()`, `rememberAppLockTexts()`; `AppPickerField` (`searchPlaceholder` +
  `noResultsText` novo), `AppDatePicker`/`AppDatePickerDialog`, `ConfirmationDialog`, `AppAlertDialog`,
  `AppInputDialog`, `NoInternetDialog`, `OfflineBanner`, `PhotoStrip`; contentDescriptions de
  `AppTextField` (mostrar/ocultar senha), `FullScreenImageViewer`, `ImageGallery`, `NotificationBadge`,
  `FilterIconButton`.
- **Fora da composição** (camada de dados): `loadOwnAuthTexts()`, `loadDomainApiTexts()`,
  `loadPurchaseErrorTexts()`/`rememberPurchaseErrorTexts()`. Leitura que falha (teste de JVM) devolve
  os defaults pt-BR — mensagem de erro nunca vira outro erro.
- **own-auth traduzido por default**: `OwnAuthConfig(texts = null)` (default) lê as mensagens da lib
  no idioma da tela **a cada erro**; passando `texts`, vale o do app, como antes.
  `OwnAuthConfig.texts` continua não-nulo. `EmailPasswordAuthRepository(texts: OwnAuthTexts? = null)`.
- **`DomainApiClient(…, textsProvider = { loadDomainApiTexts() })`** — parâmetro novo; sem ele, os
  textos fixos de sempre (o `core` não carrega Compose). Provedor que falha cai nos fixos.
- **`AppDatePicker`**: sem `placeholder`/`formatDate`, a data sai no formato da **região**
  (`27/09/2026` BR, `09/27/2026` US) e o placeholder mostra esse formato (`dd/mm/aaaa`,
  `mm/dd/yyyy`). Quem quer o `dd/MM/yyyy` fixo passa `formatDate = ::formatDateBr`.

### 2. `locale` no cadastro (par da backlib 0.134.0)
`OwnAuthApi.register(…, locale: String? = null)` e `OwnAuthService.register(…, phone, locale)` (overload
novo, com implementação default que ignora o idioma — dublê de teste não quebra). `null` não manda o
campo: servidor anterior à 0.134.0 recebe o corpo de sempre. Passe `appLanguageTag()`.

### 3. Idioma, região e formatos (`kmplib-core`, pacote `core.locale`) — APIs oficiais de cada plataforma
- `FactoryLocales` (`PT_BR`/`EN`/`ES`/`PT_PT`/`ALL`, **`match(tag)`** = a mesma escolha de pasta do
  compose-resources), `deviceLanguageTag()`, `deviceLanguage()`, **`appLanguageTag()`** (idioma da
  tela), `splitLanguageTag`. No `kmplib-ui`: **`uiLanguageTag()`** (lê o recurso da pasta escolhida).
- **`RegionalFormat`**: `decimalSeparator`, `groupingSeparator`, `formatNumber`, `formatInteger`,
  `formatPercent`, `formatDate(LocalDate)`, `formatDateTime`, `formatTime`, `datePattern`,
  `datePlaceholder`; `DateSkeletons` (esqueletos CLDR); `placeholderFromDatePattern`. Android:
  `java.text` + `DateFormat.getBestDateTimePattern`; iOS: `NSNumberFormatter` +
  `NSDateFormatter.setLocalizedDateFormatFromTemplate`. HALF_UP nas duas.
- **`Countries`**: `all(languageTag)` (ISO 3166-1, nome traduzido pelo CLDR do sistema, ordem
  alfabética do idioma), `name(code)`, `search(query)`; `Country(code, name).flag`,
  `countryFlagEmoji`, `isIsoCountryCode`. A região do aparelho continua em `deviceRegion()`
  (`kmplib-platform`).
- **`DeviceLocaleHeaders`** (plugin Ktor): `Accept-Language` = idioma da tela, `X-Time-Zone` = fuso
  IANA, lidos **a cada requisição**, sem sobrescrever o que a chamada já definiu; `onlyHosts` para não
  contar o fuso a terceiros. **`HttpClientOptions(sendLocaleHeaders = true)`** — opt-in (default
  `false`, para não mudar a resposta de servidor existente). A casca já nasce ligada.

### 4. Telefone internacional (E.164), com o BR intacto
- `kmplib-core`: **`InternationalPhone`** — `callingCodeFor(region)`, `regionsFor(ddi)`,
  `filterInput`, `parse(input, defaultRegion): PhoneNumberParts?`, `toE164`, `isValid`, `format`,
  `formatAsYouType`. Plano de numeração (DDI + comprimento do número nacional + prefixo de tronco)
  de 245 regiões **gerado dos metadados do libphonenumber** (Google, Apache 2.0) —
  `core/tools/GeneratePhoneRegionData.java`. `+55` usa a regra completa do `PhoneValidator`.
  Não valida o plano fino de cada país (isso exigiria o libphonenumber inteiro no app; é do servidor).
- `kmplib-mask`: **`PhoneInputFormat.forRegion(region)`** (BR/nulo/desconhecida → o caminho BR de
  sempre; demais → E.164): `filter`, `visualTransformation`, `isValid`, `toSubmitValue`,
  `fromStoredValue`; `InternationalPhoneVisualTransformation`, `filterInternationalPhoneInput`.
- `kmplib-ui`: `rememberDevicePhoneInputFormat()`, `rememberPhonePlaceholder(format)`.
- **`FeedbackScreen`/`ContactScreen`/`DeveloperScreen`**: `phoneFormat` novo (default: região do
  aparelho). **Conserto:** fora do Brasil o feedback exigia "WhatsApp com 11 dígitos" e **não havia
  como enviar**; agora vale o número do país e ele sobe em E.164. No Brasil, igual.

### Testes
+67 testes (suíte do Android host: 2.874 testes, 15 ignorados, 0 falha): `AppLocaleTest`, `RegionalFormatTest`,
`CountriesTest`, `InternationalPhoneTest`, `DeviceLocaleHeadersTest`, `DomainApiClientTextsTest`
(core); `PhoneInputFormatTest` (mask); `LibStringResourcesParityTest` (as 4 pastas com as mesmas
chaves e os mesmos argumentos), `UiPtResourceParityTest`, `UiLocaleTest`, `LoaderFallbackTest` (ui);
`OwnAuthLocaleTest`, `OwnAuthPtResourceParityTest` (auth); `PaywallPlanLabelsTest`,
`MonetizationPtResourceParityTest` (monetization); `WhatsappCompletenessTest` (central).
`compileKotlinIosArm64` e `compileDebugKotlinAndroid` dos 24 módulos compilados no Linux (nenhum
`SKIPPED`). Em teste de JVM os recursos não carregam (`Resources.getSystem()` nulo), por isso o
conteúdo das traduções é travado lendo os XML (`*ParityTest`) e os leitores são testados no fallback.

### Migração (opcional — nada quebra)
- App que montava `*Texts` só para traduzir: pode apagar e deixar o default; para trocar uma frase,
  `remember…Texts().copy(…)`.
- App global: `createHttpClient(HttpClientOptions(sendLocaleHeaders = true))`,
  `DomainApiClient(…, textsProvider = { loadDomainApiTexts() })`, `register(…, locale = appLanguageTag())`.

## 2.218.2 — `formatAsCurrency` arredonda o centavo e põe o sinal antes do "R$"

Patch, sem mudança de API. **Correção de valor em dinheiro exibido** (com aviso).

- **`Double.formatAsCurrency`** (`kmplib-mask`) **truncava** em vez de arredondar:
  `(this * 100).toLong()` fazia `1234567.89` (= `123456788.99999…` centavos em ponto flutuante)
  sair **"R$ 1.234.567,88"**, e `19.99`/`0.1 + 0.2` perdiam um centavo pelo mesmo motivo. Agora
  arredonda ao centavo mais próximo, meio centavo para longe do zero (mesma regra do
  `formatCurrencyBRL` do core).
- **Negativos**: saía "R$ -1.000,00" e, abaixo de 1 real, texto quebrado ("R$ 0,-50"). Agora
  **"-R$ 1.000,00"** / **"-R$ 0,50"** — sinal antes do prefixo. Valor que arredonda para zero não
  leva sinal; `NaN`/infinito desenham zero.
- **`CurrencyVisualTransformation`**: a parte inteira é agrupada como texto — acima de 19 dígitos a
  conversão para `Long` estourava e o campo desenhava "R$ 0,xx".
- `currencyToDouble`/`filterCurrencyInput` conferidos: sem o mesmo defeito (divisão exata de `Long`
  por 100, entrada só de dígitos).
- Testes: `CurrencyMaskTest` (arredondamento, negativos, zero, milhares, prefixo vazio, >19 dígitos).

## 2.218.1 — re-review de segurança de 2.218.0: originais de câmera e cache de fotos privadas saem em todo caminho

Patch, sem mudança de API. Achados de severidade **baixa** (sem aviso).

- **`AccountDeletionService`**: sem `localData`, ou com o espelho sem titular (limpeza local
  recusada), a exclusão de conta não apagava os **originais de câmera** — só o purger fazia isso.
  Agora o serviço chama `clearCameraCaptureFiles(0L)` nesses dois caminhos. O cache de memória das
  fotos privadas passou a sair **sempre**, com ou sem `clearSharedFiles`.
- **`SyncAccountDataPurger`**: `clearSharedFiles = false` pulava também os originais de câmera e o
  cache de fotos privadas. A flag volta a controlar **só** as cópias de compartilhamento; as outras
  duas etapas rodam sempre (e falha nelas conta no relatório).
- **`FileBlobStore` (Android)**: `adopted` confirmado `@Volatile` (leitura fora do `Mutex` na via
  rápida de `adoptLegacyOnce`).
- Testes: `DeletionLocalFileStepsTest` (auth) e `PurgeLocalFileStepsTest` (sync).

## 2.218.0 — correções de segurança do review de 2.216.0/2.217.0: limpeza local com conta NOMEADA · sync segurado na exclusão de conta · 5xx sem mensagem do servidor

**Correção de segurança/privacidade.** Muda o jeito de **chamar** a limpeza local (2.217.0): a conta
passa a ser nomeada. Quem só usa `AccountDeletionService(localData = …)` não muda nada no código.

### Bloqueantes (2.217.0)
- **A limpeza apagava o bucket errado e dizia que deu certo.** `purgeAccount()`/`purgeOnSignOut()`
  liam o escopo do espelho *no instante da limpeza* — e o app que liga o escopo à sessão já o tinha
  trocado para "sem conta" no `signOut`. Resultado: `failures = 0` com o dado clínico intacto.
  - `AccountLocalDataPurger`: **`activeAccountId(): String?`** (novo, abstrato),
    **`purgeAccount(accountId: String)`**, **`purgeOnSignOut(accountId: String, pending)`** e
    **`suspend fun <T> withSyncPaused(block: suspend () -> T): T`** (default roda direto). As
    variantes sem conta ficaram `@Deprecated(level = ERROR)` — não compilam.
  - `SyncAccountDataPurger`: `purgeAccount(id)` apaga a conta nomeada **mesmo com o escopo já
    trocado** (espelho por `deleteAccountData(id)`, binários da fila por `getRowsAcrossAccounts`).
    Conta em branco/`NO_ACCOUNT` = **recusa** (`failures = 1`, nada apagado). `Keep` com o escopo já
    em outra conta também recusa (a triagem de pendência lê o bucket corrente).
  - `RestUploadOutbox.purgeAccount(accountId)` (novo) — `purgeCurrentAccount()` `@Deprecated`.
    A fila (com `formFields` no `payload_json`, 2.216.0) sai inteira na exclusão e no `Discard`.
  - `AccountDeletionService` captura `activeAccountId()` **antes** do wipe e o passa explicitamente.
- **O sync podia recriar no servidor a conta recém-apagada.** Entre o `DELETE` e a limpeza local, um
  ciclo subia a outbox (o access token ainda vale). Agora o serviço roda do `DELETE` ao fim da
  limpeza dentro de `withSyncPaused` — que no `SyncAccountDataPurger` segura o motor
  (`RestCrudSyncEngine.runExclusive`) **e** a drenagem de cada fila (`RestUploadOutbox.withDrainPaused`,
  novo — o `drainNow()` da tela não passa pelo motor). Wipe que falha solta sem apagar nada.
  `runExclusive` e a trava da fila ficaram **reentrantes na mesma corrotina** (senão a limpeza feita
  de dentro da pausa esperaria a si mesma).

### Médio/baixo (2.217.0)
- `BlobStore` Android: a adoção do diretório antigo devolvia "concluída" com arquivo **não movido** e
  nunca mais tentava. Agora tenta de novo, e enquanto isso `read`/`exists`/`delete`/`ids` enxergam
  o diretório antigo — a limpeza da conta e a varredura de órfãos alcançam a cópia que ficou.
- Originais da câmera (`cacheDir/photos/camera_*`, EXIF/GPS): `clearCameraCaptureFiles(olderThanMillis)`
  (kmplib-platform, novo) roda no `initKmpLibPlatform`/`KmpLib.init` (folga de 10 min, fora da main
  thread) e sem folga na limpeza da conta.
- `SyncStore.deleteAccountData` default deixou de ser no-op silencioso: loga e lança
  `UnsupportedOperationException` — a etapa conta como falha.

### Baixos (2.216.0)
- `OwnAuthApi`: em **5xx** a mensagem vem sempre de `texts.server(status)` — o corpo de uma falha
  interna (exceção, SQL) não chega mais à tela. 4xx segue com a frase do servidor.
- `changeOwnPassword`: rede/5xx depois de o servidor possivelmente ter aplicado a troca →
  `accessToken(forceRefresh = true)`: rotacionou = troca não aconteceu (erro original); refresh
  recusado = troca aplicada (entra de novo com a senha nova); sem resposta = `clear()` +
  `SignInRequired`.
- `PhotoSource`: `authenticated(api, path, key, accountId = null)` — **passe o id da sessão**: contas
  diferentes deixam de dividir a entrada do cache de memória. Fontes privadas entram com o prefixo
  `PRIVATE_PHOTO_MEMORY_KEY_PREFIX`, e **`clearPrivatePhotoMemoryCache()`** (ou com `ImageLoader`)
  tira só elas — o `SyncAccountDataPurger` e o `AccountDeletionService` já chamam.

### Migração
- **App que implementa `AccountLocalDataPurger` à mão:** implementar `activeAccountId()` e trocar as
  assinaturas para as com `accountId`.
- **App que chama a limpeza no logout** (ordem nova, vale para quem liga o escopo à sessão):
  ```kotlin
  val conta = purger.activeAccountId() ?: return     // ANTES do signOut
  purger.withSyncPaused {
      auth.signOut()
      purger.purgeOnSignOut(conta, policy)
      engine.setAccountScope(null)
  }
  ```
- **App com foto privada:** `PhotoSource.authenticated(api, path, accountId = sessao.userId)`; sem
  sync, chamar `clearPrivatePhotoMemoryCache()` no logout.
- Só `AccountDeletionService(localData = SyncAccountDataPurger(...))`: nada a mudar — basta subir.

## 2.217.0 — privacidade do dado local: fotos fora do backup · exclusão de conta limpa o aparelho · logout com pendências · original da câmera apagado

**Correção de segurança/privacidade** (apontada no security-review do QueiMap, app clínico). Sem
quebra de fonte; uma mudança de **comportamento** de default (`createBlobStore`), explicada abaixo.

### `kmplib-core` — `BlobStore` fora do backup por padrão
- `createBlobStore(directoryName, excludeFromBackup = true)`. **O default mudou:** até a 2.216.0 os
  binários (fotos da fila de upload) entravam no backup do iCloud e no Auto Backup/transferência do
  Android — mesmo quando o banco do sync estava fora. Fila é transitória; a cópia na nuvem, não.
- **Android:** `noBackupFilesDir/<dir>` (o diretório que a plataforma exclui do backup e da
  transferência entre aparelhos). O que estava em `filesDir/<dir>` é **movido** na primeira operação —
  nada pendente se perde. `false` faz o caminho inverso.
- **iOS:** mesmo `Application Support/<dir>`, com `NSURLIsExcludedFromBackupKey` no diretório,
  aplicado também ao diretório que já existia.
- **Efeito colateral aceito:** app com `createSyncDatabase(excludeFromBackup = false)` que restaurar
  um backup num aparelho novo recebe a linha da fila **sem** o binário — o upload aparece recusado
  ("O arquivo não está mais no dispositivo"), com opção de descartar. Antes a foto viajava na nuvem.
- Contrato novo `AccountLocalDataPurger` (+ `LocalPendingChanges`, `LocalPurgeReport`,
  `SignOutPendingPolicy`) — mora no core porque quem chama (auth) e quem apaga (sync) não se enxergam.

### `kmplib-sync` — limpar o que a conta deixou no aparelho
- **`SyncAccountDataPurger(store, uploadOutboxes, clearSharedFiles = true, engine = null, extraCleanup)`**:
  - `purgeAccount()` — espelho + outbox + cursores + remap da conta corrente, fila de upload com os
    binários, varredura de órfãos, cópias do `ShareHandler` e o `extraCleanup` do app.
  - `pendingChanges()` — `records` e `uploads` ainda não enviados, para o app **avisar antes do logout**.
  - `purgeOnSignOut(Keep | Discard)` — `Keep` apaga só o que já está no servidor (e os cursores, para
    o próximo pull devolver tudo), preservando outbox, fotos pendentes e os registros donos delas;
    `Discard` = `purgeAccount()`.
  - Nunca lança: etapa que falha é logada (sem PII) e contada em `failures`, sem impedir as outras.
- `SyncStore.deleteSyncedRows(keep)` (default no-op para fakes; nova consulta `selectCleanInAccount`,
  **sem mudança de schema**).
- `RestUploadOutbox.purgeCurrentAccount()` — apaga a fila da conta corrente inclusive linha de
  payload ilegível (que `discardAll()` não enxerga), esperando a drenagem em curso.
- `RestCrudSyncEngine.runExclusive { }` — roda um bloco com o ciclo de sync parado por dentro.

### `kmplib-auth` — `AccountDeletionService`
- Parâmetros novos `localData: AccountLocalDataPurger? = null` e `clearSharedFiles: Boolean = true`.
  Depois do wipe no servidor **e** do passo da credencial (qualquer resultado), o serviço apaga as
  cópias de compartilhamento e chama `purgeAccount()`. Wipe que falha não toca no aparelho. Falha
  local não muda o resultado (fica no log). A ordem do passo 2 foi preservada.

### `kmplib-ui` — original da câmera (Android)
- `rememberImagePickerLauncher`: o JPEG cru da câmera (`cache/photos/camera_*.jpg`, com EXIF/GPS e
  resolução cheia) **é apagado** depois de processado — e também quando a captura é cancelada. Originais
  com mais de 10 min deixados por versões anteriores são varridos a cada nova captura. O caminho do
  arquivo passou a `rememberSaveable` (Activity recriada pelo app de câmera não perde mais a foto).
  iOS não grava temporário (a imagem chega em memória).

### Casca / app — Android 12+
- `allowBackup="false"` **não** cobre a transferência aparelho-a-aparelho do Android 12+ (targetSdk
  31+). A `casca-mobile` passa a declarar `android:dataExtractionRules="@xml/data_extraction_rules"`
  excluindo todos os domínios em `cloud-backup` e `device-transfer`. Snippet em
  `kmplib-catalog/references/sync.md`.

### Migração
- **App com sync offline-first:** registrar `SyncAccountDataPurger` e passá-lo como `localData` ao
  `AccountDeletionService`; no logout, `pendingChanges()` → perguntar → `auth.signOut()` →
  `purgeOnSignOut(policy)` → `engine.setAccountScope(null)`.
- **Todo app Android com dado pessoal:** acrescentar `data_extraction_rules.xml` (ver casca).
- Nada a fazer para o novo default do `BlobStore` — a migração de diretório é automática.

## 2.216.0 — multipart com campos de texto · erro por campo no `DomainResult` · troca de senha own-auth · foto privada no `PhotoStrip`

Aditivo (sem quebra de fonte). Demandado pelo QueiMap (app clínico), genérico para todo app com
backend da fábrica.

### `kmplib-core` — `DomainApiClient`/`DomainResult`
- **Campos de texto no multipart:** sobrecargas `postMultipartParts(path, parts, formFields)`,
  `putMultipartParts(path, parts, formFields)` e `postMultipart(path, bytes, fileName, mimeType,
  fieldName, formFields)`. Os campos vão ANTES das partes binárias; nome em branco é ignorado. As
  assinaturas antigas continuam iguais (delegam com mapa vazio).
- **`DomainResult.Error` com o envelope inteiro:** `serverMessage` (a `message` do corpo) e
  `details: Map<String,String>` (erros por campo de `ValidationException.forField`/`FieldErrors`),
  mais `fieldError(field)`, `fieldErrors`, `hasFieldErrors` e `userMessage` (frase do servidor em
  4xx exceto 401/429; texto local em 5xx). `message` segue sendo o texto local de sempre.
- `parseServerErrorEnvelope(body): ServerErrorEnvelope?` público (nunca lança; aceita
  `{"error":{…}}` aninhado; `details` só com valores primitivos).

### `kmplib-sync` — `RestUploadOutbox`
- `enqueue(…, formFields = emptyMap())` e `enqueueParts(…, formFields)` — **persistidos** na linha da
  outbox (`PendingUpload.formFields`, linha antiga desserializa vazia) e enviados no mesmo multipart.
  Nome em branco ou igual a `fieldName` de uma parte → `Rejected(InvalidRequest)` ao enfileirar.
- Recusa terminal grava a frase do servidor (`userMessage`) em vez de "Erro do servidor (4xx)".

### `kmplib-auth` — troca de senha
- `OwnAuthService.changeOwnPassword(current, new): Result<PasswordChangeOutcome>`
  (`SessionRenewed(user)` | `SignInRequired`) contra `POST {authBasePath}/password/change`. O servidor
  revoga todas as sessões; a lib entra de novo com a senha nova e adota a sessão fresca. Senha
  temporária desvia para o primeiro acesso.
- `OwnAuthService.completeFirstAccess(new): Result<User>` — chama `password/first-access` com token
  válido e adota os tokens novos preservando nome/identificador/`providerId`.
- `EmailPasswordAuthRepository.changePassword` (contrato `IAuthRepository`) **deixou de falhar como
  "não suportado"** — delega ao `changeOwnPassword`.
- `OwnAuthApi.changePassword(current, new, accessToken)`; 401 dessa rota vira
  `InvalidCredentials(OwnAuthTexts.currentPasswordIncorrect)` ("Senha atual incorreta.").
- Os métodos novos da interface têm default (`Unsupported`): implementações externas compilam.

### `kmplib-ui` — `PhotoStrip` com fonte autenticada
- `PhotoSource` = `Url(url, headers)` · `Bytes(key, bytes)` · `Loader(key, load)` ·
  `PhotoSource.authenticated(api: DomainApiClient, path)`. `PhotoStripItem(…, source, uploadingOverride)`
  + `imageSource`/`ready`. `Loader` é um `Fetcher` do Coil por requisição; foto privada fica fora do
  cache de disco (só memória). `rememberPhotoSourceRequest`/`photoSourceRequest` para outros
  componentes. Falha de carregamento mostra a marca de falha.
- **Documentado (sem mudança de código):** o seletor de imagem (galeria, câmera, múltipla) sempre
  recodifica em JPEG — o HEIC da câmera do iPhone nunca sai dele, e o EXIF/GPS não viaja. O
  `FilePicker` não converte.

### Testes
`DomainApiClientTest` (+5), `RestUploadOutboxTest` (+3), `OwnAuthPasswordChangeTest` (8),
`PhotoSourceTest` (5). Android + `compileKotlinIosArm64` (25 tarefas, nenhuma SKIPPED).

## 2.215.0 — campo de HORA DO DIA com limite (`AppTimeField`)

Aditivo. Fecha o GAP-QUEIMAP-02 (QueiMap AP10, "hora da queimadura" — não pode ser depois de agora).

### Novo (`kmplib-ui`, `ui.components`)
- `AppTimeField(selectedTime: LocalTime?, onTimeSelected, label, modifier, isEnabled, minTime,
  maxTime, helperText, errorMessage, texts)` — par do `AppDatePicker`: campo somente-leitura que
  abre o seletor ao toque (interactionSource + overlay do iOS), 24h `HH:mm`, erro NO campo
  (`isError` + `supportingText` + `error()` na semântica), `helperText` neutro.
- `AppTimePickerDialog(selectedTime, onTimeSelected, onDismiss, minTime, maxTime, texts,
  initialDisplayMode)` — só o modal, sobre o **`TimePickerDialog` oficial do Material 3**, com
  título e alternância relógio ⇄ teclado do próprio M3.
- Limites inclusivos: o M3 não tem faixa permitida, então o seletor abre já dentro dela e, fora
  dela, o **OK fica desligado** com a frase do limite (live region). Valor já escolhido que sai da
  faixa depois (trocou a data) vira erro automático no campo; `errorMessage` do app vence.
- Puros e testados: `timeLimitViolation`/`TimeLimitViolation`, **`maxTimeNotAfterNow(date, now)`**
  (hoje → agora truncado no minuto; dia anterior → sem limite; futuro → 00:00), `formatTimeHm`,
  `initialPickerTime`, `timeLimitMessage`.
- Textos em Compose Resources nas 4 línguas (`kmplib_time_*`): `AppTimeFieldTexts` +
  `rememberAppTimeFieldTexts()`.
- O antigo `AppTimePicker(hour, minute, …)` segue intacto.
- Testes: `AppTimeFieldTest` (13).

## 2.214.0 — placeholder sem dado real · paywall acentuado · Keychain preso ao aparelho

Achados ao montar o Palpite Certo (24/set/2026). Sem mudança de assinatura; apps que passam os
próprios textos não mudam nada.

### Placeholders: instrução ou formato, nunca dado real
- Novo `FormPlaceholders` (`kmplib-ui`, `ui.components`): os defaults num lugar só — `NAME`,
  `EMAIL`, `EMAIL_OR_USERNAME`, `USERNAME`, `PASSWORD`, `CONFIRM_PASSWORD`, `PHONE`
  (`(00) 00000-0000`, formato) e `ADDRESS_NUMBER`.
- Saíram "João Silva", "seu@email.com", "seu@email.com ou seu.usuario", "seu.usuario",
  "(11) 98765-4321", "voce@email.com", "(11) 91234-5678", "email@exemplo.com", "123" (número do
  endereço) e "••••••••" (senha — é o desenho de uma senha já digitada). Onde: `EmailField`,
  `NameField`, `PhoneField`, `PasswordField`, `LoginTexts`, `RegisterTexts`, `ContactTexts`,
  `FeedbackTexts`, `AppReviewDialog`, `AddressFields`.
- Motivo: regra "PLACEHOLDER NUNCA É DADO REAL" (15/set/2026) — valor plausível no campo é lido
  como campo preenchido, e a pessoa envia achando que informou.
- Os recursos traduzidos da lib (`values`, `-en`, `-es`, `-pt-rPT`) não tinham placeholder de dado
  real; nada a mudar lá.
- Testes: `FormPlaceholdersTest`, `CentralPlaceholdersTest`.

### Paywall em pt-BR acentuado
- `PaywallTexts`: "disponível", "Você", "Informações legais", "até 24 horas antes do fim do
  período", "será cobrado… confirmação", "configurações", "Política de Privacidade", "dúvidas",
  "Restaurando…". `defaultDurationLabel(1)` = "1 mês". Os textos de erro de compra
  (`PurchaseErrorTexts`) já estavam certos. O paywall só tem defaults em pt-BR (outros idiomas vêm do
  app). Teste: `PaywallTextsTest`.

### iOS: refresh token preso ao aparelho (Keychain)
- `SecureTokenStorage` no iOS grava com `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` (era
  `kSecAttrAccessibleAfterFirstUnlock`). O item não vai no backup para ser restaurado em outro
  aparelho; continua legível em background depois do primeiro desbloqueio, para o refresh proativo.
- Migração: na primeira operação do cofre, `SecItemUpdate` troca o atributo dos itens já gravados
  daquele `serviceName` **sem apagar nem deslogar**. Se falhar (ex.: app acordado em background antes
  do primeiro desbloqueio), tenta de novo na próxima operação. Compilado com
  `compileKotlinIosArm64 -Pkmplib.forceAppleTargets=true`; o comportamento em device é do Mac.
- Efeito esperado: quem restaurar um backup num iPhone NOVO entra de novo no app (antes, a sessão ia
  junto).

## 2.213.0 — `AppBottomNavBar`: rótulo em UMA linha · `RegisterScreen`: fenda `trailingFields`

### `RegisterScreen(trailingFields = { … })`
- Fenda nova para campos **opcionais** do produto, depois da confirmação de senha e antes do aceite
  dos termos. `extraFields` (entre telefone e senha) continua igual. Motivo: um bloco opcional ali
  fazia a tela alternar opcional → obrigatório ("Endereço (opcional)" e, logo abaixo, a senha, que
  não é), e a pessoa não sabia o que podia pular. Aditiva; default `null`.

### `AppBottomNavBar`

- O rótulo de cada item passa a ter `maxLines = 1` + `TextOverflow.Ellipsis`. Antes, rótulo de duas
  palavras ("Minha avaliação") quebrava em telas de 360dp e só aquele item ficava mais alto,
  desalinhando ícone e texto do resto da barra (visto no NeuroCoreX, 23/set/2026).
- Aditiva e sem mudança de API. Rótulo que não cabe vira reticência — a correção de fundo continua
  sendo o app escolher rótulo curto, como o Material pede.

## 2.212.0 — `kmplib-video` sem foreground service: o download virou módulo próprio

⚠️ **Mudança crítica para quem só TOCA vídeo** e **quebra de dependência para quem BAIXA**.
Descoberto publicando o Mirassol Conectado na Play (21/set/2026): o upload travou no formulário
obrigatório **"Permissões de serviço em primeiro plano"**, exigindo justificar
`FOREGROUND_SERVICE_DATA_SYNC` — num app que não baixa vídeo nenhum. A permissão entrava pelo
manifest merger, vinda do `kmplib-video`, que empacotava player e download no mesmo módulo.

### Novo artefato `br.com.codecacto:kmplib-video-download` (opt-in)
- Leva o pacote `br.com.codecacto.kmplib.video.download` inteiro (`MediaDownloadManager`,
  `createMediaDownloadManager`, `MediaDownloadRequest`, `MediaDownloadStore`…), o
  `KmplibDownloadService`, o JobService do `PlatformScheduler`, as permissões `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_DATA_SYNC` e `RECEIVE_BOOT_COMPLETED`, e as strings do canal de notificação.
- **Os pacotes não mudaram**: quem importava `video.download` só acrescenta a dependência.
- Depende de `kmplib-video` (`api`) — mão única, nunca o contrário.
- **O umbrella `br.com.codecacto:kmplib` NÃO o inclui**, de propósito: o umbrella entra em ~25 apps
  que não baixam vídeo, e todos teriam o bundle barrado.

### `kmplib-video` (o player)
- **Sem serviço, sem permissão de primeiro plano.** No manifesto fica só `ACCESS_NETWORK_STATE`
  (pré-carregamento do feed, nível normal).
- O recorte foi **dentro** do antigo `Media3Downloads`: a parte de cache (diretório, banco,
  `SimpleCache`, fábrica de leitura do player) virou **`Media3Cache`** e ficou aqui, porque o player
  lê a cópia baixada por ela e o cache do feed reaproveita o banco. `DownloadManager`, notificação e
  requisitos de rede foram para o módulo novo, que constrói em cima.
- `setCacheWriteDataSinkFactory(null)` preservado na fábrica de leitura — sem ele, o streaming
  gravaria num cache que nunca esvazia.
- A pasta no disco (`filesDir/kmplib_media_downloads`) **não mudou**: aula baixada numa versão
  anterior continua sendo achada.
- `Media3Cache` e `VideoPlayerHolder.getContext()` ficaram públicos **sob opt-in**
  (`@KmpLibVideoInternalApi`, nível ERROR) — só para a ponte entre os dois módulos; não é API de app.

### Migração
- **App que só toca vídeo** (feed, `VideoPlayer`): subir para 2.212.0 e **apagar** qualquer
  `tools:node="remove"` que tenha posto no manifesto para esconder o serviço/permissões.
  `RECEIVE_BOOT_COMPLETED` **não** some: ela também vem do `kmplib-platform` (lembrete pós-reboot).
- **App que baixa** (`video.download`): acrescentar `kmplib-video-download` ao lado de
  `kmplib-video` (e, no `settings.gradle.kts` com `includeBuild`, o `substitute` correspondente).
  Sem ele o build falha em `unresolved reference: MediaDownloadManager` — não em runtime.
- `initKmpLibVideo(context)` continua sendo o único init: o download usa o mesmo registro.

## 2.211.0 — O modal do seletor de data, sem o campo do Material

Da revisão do Mirassol Conectado (fundador, 20/set/2026): *"está feio esse layout aqui, que a data
está maior que a hora"*. Aditivo — nenhuma assinatura existente muda.

### `AppDatePickerDialog(...)` (kmplib-ui)
- **Só o modal**: o calendário do Material, sem `OutlinedTextField` nenhum em volta.
- Existe porque **projeto com protótipo tem campo próprio**. O `AppDatePicker` traz o campo
  embutido, e num formulário desenhado (caixa lisa de 50dp) ele nasce mais alto que o campo ao
  lado — o desenho aprovado tem os dois do mesmo tamanho.
- A alternativa era o projeto montar o `DatePickerDialog` na mão, e com ele **a conversão de
  fuso**, que é a armadilha: o `DatePickerState` trabalha em UTC, e converter no fuso do aparelho
  abre o calendário no dia ANTERIOR para quem está a oeste de Greenwich.
- `dataParaMillisDoCalendario` / `millisDoCalendarioParaData` deixaram de ser `internal` pelo mesmo
  motivo — quem desenha o próprio campo precisa da ida e da volta.
- Quem quer o campo pronto continua no `AppDatePicker`, que agora chama este modal por dentro.

## 2.210.0 — O player inline pode nascer MONTADO

Um item em `kmplib-ui`, da revisão do Mirassol Conectado (fundador, 19/set/2026): no detalhe de uma
matéria de vídeo, *"aparece um botão feio de play — o player já tem que estar ali, grande, já
carregado"*. Assinatura compatível: o parâmetro novo tem default.

### `VideoPlayerInline(montarDeSaida = true)` (kmplib-ui)
- **O default não muda.** `false` continua desenhando a capa e montando a view só depois do play —
  é o que protege a LISTA, onde os três defeitos conhecidos nasceram (piscar, ficar preto, áudio
  tocando por baixo depois de sair da tela).
- **`true` inverte a conta numa tela de DETALHE**: quem abriu uma matéria de vídeo quer o vídeo, e
  a capa com um play desenhado por nós é um degrau a mais — ainda por cima com aspecto de "imagem
  com um botão colado por cima". Montado, quem aparece é o player real, com o botão do **próprio
  YouTube**.
- ⚠️ **Não é autoplay.** A view é criada; o vídeo não começa sozinho. Autoplay com som é bloqueado
  pelo YouTube e seria indesejado: a pessoa pode ter aberto para ler.
- `VideoSource.External` nunca monta — ela é aberta fora, e um WebView com link de fora é
  justamente o que a lib evita.

## 2.209.0 — A queda de rede espera 2s antes de virar aviso

Um item em `kmplib-core`, nascido da revisão do Mirassol Conectado (fundador, 19/set/2026):
*"está aparecendo um card de sem conexão com o servidor quando eu coloco em segundo plano e volto,
na tela de detalhes de um parceiro — isso somente no iPhone"*. Nenhuma assinatura quebra; o
comportamento muda **para todos os apps** (é correção).

### `ConnectivityObserver` amortece a QUEDA, nunca a volta (kmplib-core)
- **O defeito.** No iOS, o app que vai para o segundo plano perde o direito de usar a rede e o
  `NWPathMonitor` empurra `unsatisfied` — não porque o Wi-Fi caiu, mas porque o app saiu de cena.
  Ao voltar, o último estado empurrado ainda é esse, e o `ConnectivityGate` aparecia na frente da
  tela em que a pessoa estava até chegar o próximo update. O mesmo acontece **nas duas
  plataformas** na troca de Wi-Fi para dados móveis: há um intervalo de nenhuma das duas.
- **A regra.** `isOnline` só vira `false` depois de `QUEDA_CONFIRMADA_APOS` (**2 s**) de rede fora
  **contínua**; voltar a ficar online é **imediato**. Implementado com `collectLatest` + `delay`: a
  volta cancela a espera da queda, então a oscilação nunca chega à UI.
- **Nada fica preso atrás do atraso**: ele vale só para a má notícia. O pior caso é a tela de "sem
  internet" demorar 2 s em quem está mesmo sem rede — e essa pessoa já não carregaria nada nesse
  tempo. `refresh()` (o botão "Tentar novamente") continua **direto**, sem amortecimento: quem
  tocou está olhando a tela de offline e espera resposta agora.
- **Construtor com dois parâmetros novos, ambos com default** (`quedaConfirmadaApos`, `scope`) —
  `ConnectivityObserver()` continua compilando em todo consumidor. `Duration.ZERO` desliga o
  atraso.

Testes: `AmortecimentoDaQuedaTest` (+3) — queda que se desfaz antes do prazo não chega à tela,
queda de verdade chega depois dele, e a volta é imediata.

## 2.208.0 — Toast abaixo da câmera e legível; moeda em reais inteiros e campo vazio de verdade

Dois itens nascidos da revisão do Mirassol Conectado (fundador, 16/set/2026). Nenhuma assinatura
quebra; o toast muda de posição e de cor **para todos os apps** (é correção).

### 1. `ToastHost` respeita a barra de status e tem contraste (kmplib-ui)
- **Posição:** o host aplicava só `topPadding` (16dp) a partir do topo da janela. Montado na raiz —
  onde ele deve morar —, o toast nascia **por cima da câmera / Dynamic Island do iPhone**. Agora
  desconta `WindowInsets.safeDrawing` (topo + laterais) antes do `topPadding`. Parâmetro novo
  `respeitarBarraDeStatus: Boolean = true`; `false` só para host montado dentro de conteúdo que já
  descontou a barra.
- **Cor:** a pílula usava os tons 500 (`#10B981`, `#EF4444`, `#F59E0B`, `#3B82F6`) com texto branco —
  contraste entre 2:1 e 3:1, abaixo do WCAG AA. Agora tons 700 (`#047857`, `#B91C1C`, `#B45309`,
  `#1D4ED8`), ≥ 4,5:1. Texto 15sp semibold, largura máxima de 560dp e sombra.

### 2. `CurrencyVisualTransformation` — reais inteiros e vazio vazio (kmplib-mask)
- `decimalPlaces = 0` desenha **"R$ 35.000"**. Antes desenhava "R$ 35.000," com a vírgula pendurada.
- `showZeroWhenEmpty = false` deixa o campo **vazio** quando não há dígito. O default (`true`)
  mantém o "R$ 0,00" de sempre. Com `false`, o mapeamento de cursor devolve 0 para o texto vazio (o
  `prefixLength` de antes estouraria o limite e derrubaria o campo).
- Por quê: preço de veículo e de imóvel é em reais inteiros no mercado inteiro, e um "0,00"
  desenhado num campo em branco é lido como valor já informado — e esconde o placeholder.

Testes: `CurrencyMaskTest` (+3).

## 2.207.0 — Seleção MÚLTIPLA de fotos, a faixa com o "+" em primeiro, e o botão do diálogo que quebrava no meio da palavra

Três itens em `kmplib-ui`, nascidos da revisão do Mirassol Conectado (fundador, 15/set/2026). Os dois
primeiros são **aditivos** — nada que existe muda de comportamento. O terceiro é correção.

### 1. `rememberMultiImagePickerLauncher` — várias fotos numa passada só

```kotlin
val seletor = rememberMultiImagePickerLauncher(
    selectionLimit = 20,
    onImagesPicked = { fotos -> fotos.forEach(::enviar) },
    onError = { avisar(it) },
)
```

Android via `PickMultipleVisualMedia`, iOS via `PHPickerConfiguration.selectionLimit`. Devolve
`List<PickedImage>` — já reduzidas, **giradas pelo EXIF** e medidas, na ordem em que foram
escolhidas. **Não tem câmera**, de propósito: ela produz uma foto por vez, e oferecê-la num seletor
múltiplo prometeria o que o sistema não faz. Para uma foto só (avatar, capa, documento) continue em
`rememberImagePickerLauncher`.

Uma imagem ilegível no meio da seleção **não derruba as outras**: ela é descartada, `onError` é
chamado UMA vez, e o que deu certo é entregue. Fechar a galeria sem escolher não chama nenhum dos
dois. No Android a decodificação sai da thread de UI — vinte JPEGs na main thread congelam a tela.

*Por quê:* quem monta um anúncio de carro ou de imóvel já fotografou tudo antes de abrir o app.
Escolher uma, esperar o upload e repetir oito vezes era o atrito que fazia o anúncio nascer com duas
fotos.

### 2. `PhotoStrip` + `PhotoStripItem` — a faixa de fotos de formulário

```kotlin
PhotoStrip(
    items = state.fotos,                      // PhotoStripItem(id, url?, failed)
    onAdd = { seletor.launch() },
    onRemove = { vm.remover(it.id) },
    onMakeCover = { vm.usarComoCapa(it.id) },
    onRetry = { vm.tentarDeNovo(it.id) },
)
```

Rolagem horizontal com o **quadradinho de adicionar em PRIMEIRO**, sempre visível — com ele no fim,
quem já subiu oito fotos precisa rolar até o fim para subir a nona, e quem não rola conclui que não
dá para adicionar mais. **Não há botão "Adicionar foto" embaixo**: o quadradinho é o botão, e tem a
medida da miniatura.

A miniatura **nasce antes da URL** (`url = null` desenha o indicador girando): quem escolhe cinco
fotos volta da galeria e vê cinco quadradinhos, na ordem em que escolheu — sem isso a tela fica
idêntica à de antes por vários segundos e a pessoa reescolhe tudo. A que falhou fica na faixa com a
marca de recarregar, e o "x" remove.

### 3. `ConfirmationDialog` — os botões empilham quando os rótulos não cabem

**O defeito (todas as versões):** as duas ações dividiam a linha ao meio com `weight(1f)`, mesmo
quando o texto não cabia na metade. Num celular de 360dp sobram ~88dp para o rótulo depois do padding
do botão — e "Denunciar" quebrava no meio da palavra, deixando um **"r" sozinho na linha de baixo**,
num diálogo de moderação.

**Agora:** a largura dos rótulos é **medida antes de desenhar** (`TextMeasurer`); não cabendo lado a
lado, os botões empilham em largura cheia, com o confirmar em cima. É o mesmo comportamento do
`AlertDialog` do Material. Vale para qualquer rótulo e qualquer escala de fonte do aparelho — nada de
reticências, que num botão de ação irreversível é pior que a segunda linha.

## 2.206.0 — Apple é sempre nativa, também no modo `BACKEND`

Correção (`kmplib-auth`, `commonMain`). Nenhuma API muda; o app não precisa de código.

**O defeito (2.160.0–2.205.0):** com `SocialLoginMode.BACKEND`, o `SocialSignIn` mandava **todos**
os provedores para o navegador (`/social/start`), inclusive a Apple. O backend registra no fluxo pelo
navegador só o Google, e o botão "Continuar com Apple" no iOS respondia **"Provedor social não
habilitado"** (Backhand, 15/set/2026).

**Agora:** a Apple vai sempre pelo `AuthenticationServices` + `POST /auth/social` com o `idToken`,
nos dois modos. O modo decide só o caminho do Google. A regra mora em `caminhoDoLogin` (internal,
testada).

**O app iOS precisa do entitlement `com.apple.developer.applesignin`** (`iosApp.entitlements` +
`CODE_SIGN_ENTITLEMENTS` no `.xcconfig`). Sem ele a folha da Apple falha com
`ASAuthorizationError 1000`. A `casca-mobile` passa a trazê-lo.

## 2.205.0 — iOS: a sessão não sobrevive mais à desinstalação do app

Correção (`kmplib-auth`, só `iosMain`). Nenhuma API muda; o app não precisa de código.

**O defeito (todas as versões com own-auth no iOS):** o iOS apaga o `UserDefaults` ao desinstalar o
app, mas **não** o Keychain. Quem desinstalava e reinstalava abria o app com o `refreshToken` antigo:
pulava onboarding e login e caía direto numa tela de dentro (no Cidade Conectada, a escolha do bairro
do cadastro). O Android não tem o problema — o `EncryptedSharedPreferences` sai junto com o app.

**Agora:** o `IosSecureTokenStorage`, antes da primeira operação do processo, confere uma marca no
`UserDefaults` (`br.com.codecacto.kmplib.auth.instalacao.<serviceName>`). Sem a marca, apaga os itens
do Keychain **daquele `serviceName`** e grava a marca. Sob `NSLock`: nenhuma leitura concorrente vê o
token antigo, e não depende da ordem entre `AppDelegate` e Kotlin.

⚠️ **Na primeira versão do app com a 2.205.0, quem só ATUALIZA também é deslogado uma vez** (a marca
ainda não existe). O `UserDefaults` não distingue reinstalação de atualização com segurança — SDKs
gravam nele antes da primeira leitura da sessão.

**Quem tinha contornado no `AppDelegate`** (`SecItemDelete` de todo `kSecClassGenericPassword` com
flag `app_installed`): remover — apagava o Keychain inteiro do app, e não só a sessão.

## 2.204.0 — `AppServiceGate` não desmonta mais o app ao reconsultar (e reconsulta ao voltar do 2º plano)

Correção de comportamento + um parâmetro novo (aditivo, default que mantém o comportamento).

### `kmplib-platform` — reconsultar o `AppServiceGate` destruía a navegação (2.115.0–2.203.0)

O gate guardava status e "já dispensou" em `remember(key)`, e chamava `content()` em ramos
diferentes do `when` (`Soft` e `None` — cada ramo é um grupo de composição próprio). Consequências:

- **Trocar o `key` zerava tudo.** Com atualização OPCIONAL pendente, o status voltava a `None`
  enquanto a nova consulta não respondia, o `content()` mudava de ramo e **a pilha de navegação
  inteira era descartada** — o app voltava à Splash. O diálogo já dispensado reaparecia.
- **Mesmo sem trocar o `key`**, a chegada de um `Soft` na abertura já remontava o app uma vez.
- Trocar o `key` com uma consulta em voo a cancelava e abria outra (consulta dupla na abertura, no
  padrão `LifecycleResumeEffect { key++ }`).

Agora:

- O estado vive o tempo do gate (`remember` sem chave). O `key` só re-dispara a consulta; **a última
  resposta vale até a nova chegar**.
- O `content()` fica num **lugar fixo** da árvore. Manutenção e atualização obrigatória entram **por
  cima** (opacas, sem toque, sem voltar — `BackHandler` —, fora da acessibilidade), como o
  `ConnectivityGate`/`AppLockGate`. A pilha sobrevive inclusive a uma janela de manutenção aberta e
  fechada com o app em uso. ⚠️ Sob o bloqueio o app segue composto e `RESUMED`.
- A dispensa do opcional vale **por versão** (`latestVersionName`; sem ele, o próprio aviso): versão
  nova recomendada reabre o diálogo.
- Pedido que chega com consulta em voo é atendido por ela — nunca duas simultâneas.
- Exceção lançada pelo `check` **libera** (status vazio) em vez de derrubar a raiz do app.

API nova: **`AppServiceGate(…, recheckOnForeground: Boolean = false, content)`** — reconsulta ao
voltar do segundo plano (`ON_STOP` → `ON_START`; não na abertura, não na volta de diálogo do
sistema). Substitui o truque de trocar o `key` num `LifecycleResumeEffect`.

`AppUpdateGate` (admin-api central) passa pela mesma casca: `Hard` cobre o conteúdo em vez de
desmontá-lo, `Soft` não remonta o app, dispensa por versão. Assinatura inalterada.

Dependências novas no `kmplib-platform` (`implementation`): `lifecycle-runtime-compose` e
`compose ui-backhandler` — as mesmas que o `kmplib-ui` já trazia.

Testes: `AppServiceGateStateTest` (12) — dispensa sobrevive à reconsulta, versão nova reabre,
reconsulta em voo mantém o último status, pedido durante consulta em voo não abre outra, falha
libera, cancelamento solta a trava, primeiro `ON_START` não reconsulta. `compileKotlinIosArm64` do
`kmplib-platform` executado (não `SKIPPED`).

**Migração:** quem troca o `key` para reconsultar ao voltar ao app deve passar a
`recheckOnForeground = true` e remover o contador. Consumidor no monorepo: só o NeuroCoreX.

## 2.203.0 — Correções de revisão: "sem internet" preso com 4G, sessão derrubada por corrida, link pré-instalação perdido

Correções (quatro de comportamento e uma de API aditiva). Nenhuma API pública é removida;
`readInstallReferrerLinkOnce` passa a `@Deprecated` com o mesmo comportamento.

### `kmplib-core` — `ConnectivityObserver` no Android preso em offline (desde a 2.67.0; visível em tela cheia desde a 2.200.0)

O monitor registrava `registerNetworkCallback(request)`, que recebe eventos de **todas** as redes que
casam com o pedido. No aparelho com Wi-Fi e 4G ao mesmo tempo, o `onLost` do Wi-Fi gravava `false`
com o 4G funcionando — e, como nenhuma rede nova "aparecia" depois, nada voltava a `true`. Com o
`ConnectivityGate` em `FullScreen` (default da casca desde a 2.200.0), o app ficava **travado na tela
"sem internet" estando online**; o "Tentar novamente" destravava (ele relê o estado), mas o sintoma
voltava na próxima troca de rede.

Agora: `registerDefaultNetworkCallback` (rede padrão, API 24 = minSdk) e, em `onAvailable`/`onLost`,
o estado é **relido** do `ConnectivityManager` (`currentStatus()`), nunca gravado às cegas. iOS
conferido por leitura: `NWPathMonitor` sem interface específica já reporta o caminho geral
(`satisfied` enquanto houver rota), sem o defeito.

### `kmplib-ui` — `ConnectivityGate` `FullScreen` e `MaintenanceGate` engoliam um "voltar"

O `BackHandler` que bloqueia o voltar ficava **dentro** do `AnimatedVisibility` e seguia composto
durante o fade-out: a rede voltava, o gate já tinha liberado, e o primeiro voltar não fazia nada.
Agora fica fora da animação, com `enabled = active`. Correção no `BlockingOverlay`, comum aos dois.

### `kmplib-video` — posição `-1` desligava o pré-carregamento do feed (2.199.0–2.202.0)

`FeedPreloadPositions` começava no zero, e rolar para cima da primeira janela dava a posição `-1` —
o `C_INDICE_NENHUM` do `DefaultPreloadManager.setCurrentPlayingIndex`. Com o vídeo da vez nela, o
manager entendia "ninguém tocando". Agora o espaço começa em `FEED_PRELOAD_POSITION_ORIGIN =
Int.MAX_VALUE / 2` e as posições nunca ficam negativas. Teste novo
`rolarParaCimaDaPrimeiraJanelaNuncaDaMenosUm`.

### `kmplib-auth` — `restore()` em paralelo a um refresh derrubava a sessão

`OwnAuthTokenManager.restore()` lia o cofre e publicava **fora** da trava da renovação. Na ordem
"restore lê r1 → refresh troca r1 por r2 → restore publica r1", a sessão velha voltava à memória; o
refresh seguinte mandava r1, já rotacionado — **reuso**, a família de tokens é revogada e o usuário é
deslogado. Fica provável quando o app chama `restore()` na raiz **e** na Splash (o que a correção do
deep link recomenda) com uma requisição saindo na abertura.

Agora `restore()`, `adopt()`, `clear()` e a semeadura dentro de `accessToken()` rodam sob o **mesmo
`Mutex`** do refresh. `restore()` ficou idempotente: com sessão em memória, devolve-a sem reler o cofre
(a memória nunca está atrás do cofre; o contrário acontece quando uma gravação falha). Teste de
concorrência novo — falha contra a 2.202.0 com `expected r2 but was r1`.

### `kmplib-platform` — link pré-instalação pendente até o app confirmar

`readInstallReferrerLinkOnce` marcava a leitura como feita **antes** de o link ser usado: se o destino
pedia login e o processo morria na folha do Google, o link se perdia. API nova:

- **`peekInstallReferrerLink(key, maxAgeSeconds): String?`** — consulta a Play uma vez por instalação,
  **grava** o link e o devolve em toda abertura até ser confirmado. A validade (24 h) vale também para
  o pendente; sem instante da Play, conta da primeira leitura.
- **`markInstallReferrerLinkConsumed()`** — chamar quando o destino **abriu** (não na entrega).
- `readInstallReferrerLinkOnce` = `peek` + `mark` (comportamento antigo), `@Deprecated`.

```kotlin
LaunchedEffect(Unit) { peekInstallReferrerLink()?.let(IncomingLinks::deliver) }
// na tela de destino, quando ela abriu:
LaunchedEffect(Unit) { markInstallReferrerLinkConsumed() }
```

A chave `lido` da 2.201.0 foi mantida: quem já consultou não consulta de novo. iOS segue `null`.
Máquina de estado em `commonMain` (`peekInstallReferrerLinkWith`, interna) com 6 testes novos.

## 2.202.0 — Modo manutenção: a mesma tela cheia do "sem internet", ligada pelo app

Aditiva. Módulo `kmplib-ui`, pacote `ui.components`. Nenhuma API pública existente muda.

Pedido do fundador (12/set/2026, Cidade Conectada): ligado no admin, o modo manutenção trava portais
e app numa tela de manutenção. O backend responde `503` com `code: "MAINTENANCE"` e o config remoto
traz `maintenance: { enabled, message? }` — **quem decide ligar a tela é o app**; a lib não consulta
nada, só desenha e bloqueia.

### `MaintenanceGate` / `MaintenanceScreen` / `MaintenanceTexts`

- **`MaintenanceGate(active, message, onRetry, modifier, texts) { content }`** — sobrepõe a tela ao
  app com **exatamente** a mecânica do `ConnectivityGate` em `FullScreen`: o conteúdo segue composto
  no mesmo nó (quando a manutenção acaba, a pessoa está onde parou, sem o `NavHost` remontar), a
  semântica de acessibilidade dele some, o teclado fecha, o voltar do sistema é bloqueado, fade.
- **`MaintenanceScreen(message, onRetry, modifier, texts, icon = Icons.Filled.Build)`** — a mesma
  casca visual da `NoInternetScreen`. `onRetry = null` = **sem botão** (manutenção sem previsão não
  tem o que "tentar").
- **`resolveMaintenanceMessage(message, texts)`** — a mensagem do painel vence quando não é branca
  (aparada); vazia não pode deixar a tela sem explicação.
- **`MaintenanceTexts`** — defaults pt-BR: "Estamos em manutenção", "Estamos fazendo melhorias no
  aplicativo. Volte daqui a pouco.", "Tentar novamente", "Verificando…".

### Sem duplicar a tela cheia

A casca (ilustração + título + mensagem + botão com retorno de "verificando") virou
`FullScreenNotice` e a sobreposição bloqueante (nó único + semântica + teclado + `BackHandler` +
`AnimatedVisibility`) virou `BlockingOverlay`, ambos `internal` em `FullScreenNotice.kt`. A
`NoInternetScreen` e o `ConnectivityGate` `FullScreen` passaram a usá-los — dois avisos
bloqueantes com a mecânica copiada divergiriam na primeira correção (o teclado, o voltar) feita só
num deles.

Testes: `MaintenanceGateTest` (5); `ConnectivityGateTest` (6) sem alteração.

## 2.201.0 — Link de fora entra por UMA porta, e o link aberto antes de instalar chega ao app (Android)

Aditiva. Módulo `kmplib-platform`, pacote `platform.links`. Dependência nova no Android:
`com.android.installreferrer:installreferrer:2.2`.

Pedido do fundador (12/set/2026, Mirassol Conectado): quem recebe o link de uma empresa e tem o app
abre direto no perfil, e **voltar cai na tela inicial**; quem não tem vai à loja, instala, e na
primeira abertura é levado à mesma empresa.

### `IncomingLinks` — a porta única

`deliver(url)` (plataforma entrega) · `pending: StateFlow<String?>` · `consume(url)` · `belongsTo(url,
hosts)`. É o padrão da JetBrains para deep link em Compose Multiplatform: a plataforma entrega, o app
navega com `navController.navigate(NavUri(url))` **quando está pronto**.

Por que não deixar o `NavHost` tratar o `Intent` sozinho, que é o que os apps faziam: ele abre o
destino **por cima da abertura** (Splash). Voltar dali mostra a abertura de novo, e com o app em
segundo plano o `onNewIntent` não navegava para lugar nenhum — o link só trazia o app para a frente.
No Android, depois de entregar, **apague `intent.data`**, senão o `NavHost` abre também.

Aceita só `http(s)` absoluta com host: a volta do login do Google (esquema `com.googleusercontent…`)
é recusada, e a plataforma a entrega ao SDK. `belongsTo` compara host sem caixa, com `www.` equivalente
— e recusa `dominio.com.br.outro.com`.

### `readInstallReferrerLinkOnce()` — deferred deep link

- **Android:** Play Install Referrer API, a forma oficial. O site põe `&referrer=cc_link%3D<url>` no
  link da Play; a primeira abertura lê, uma vez por instalação. Resposta definitiva (ok, ou aparelho
  sem o serviço) marca como lida; falha passageira tenta na abertura seguinte. **Clique com mais de
  24 h é ignorado**: quem instalou por um link há meses e só agora recebeu esta versão não é levado
  àquela empresa.
- **iOS: `null`, sempre.** A Apple não atravessa a instalação com dado nenhum, e casar clique com
  instalação por impressão digital do aparelho é vedado pelas diretrizes da App Store. No iOS o link
  abre o app quando ele já está instalado (Universal Link) — e só.

Puras e testadas: `parseInstallReferrerLink`, `isInstallReferrerFresh`. Testes: `IncomingLinksTest`
(5) e `InstallReferrerLinkTest` (5).

## 2.200.0 — "Sem internet" ganha uma TELA CHEIA, e o app continua onde estava quando a rede volta

Aditiva. Nenhuma API existente muda; o default do `ConnectivityGate` continua `Modal`.

### `ConnectivityStyle.FullScreen` + `NoInternetScreen`

Pedido do fundador (12/set/2026, Mirassol Conectado): quando a internet cai, aparecer uma tela de
verdade — ícone, mensagem, "Tentar novamente" — em qualquer ponto do app, e não o diálogo pequeno
por cima do conteúdo.

- **`NoInternetScreen(onRetry, modifier, texts, icon)`** — ilustração (ícone em três círculos
  concêntricos na cor primária do tema), título, mensagem e botão de largura cheia (teto 320dp).
  Respeita `safeDrawing` e rola em tela pequena ou fonte grande. Tokens do `MaterialTheme`, nada
  hardcoded.
- **Depois do toque, o botão mostra "Verificando conexão…" por 1,2 s.** Sem esse retorno, tocar com
  a rede ainda fora não muda nada na tela, e o botão parece quebrado.
- **`ConnectivityGate(style = FullScreen)`** sobrepõe a tela ao app. Três decisões que valem ler
  antes de mexer:
  1. **Sobrepõe, não substitui.** O conteúdo continua composto no MESMO nó (só o modifier muda).
     Trocar o conteúdo pela tela desmontaria o `NavHost`: quando a rede voltasse, a pessoa estaria
     de volta ao início, sem a tela e sem o formulário em que estava.
  2. **Bloqueia de verdade.** O `Surface` consome o toque; o `BackHandler` segura o voltar do
     sistema (sem ele o gesto desempilharia a navegação escondida); o teclado é fechado; e a árvore
     de acessibilidade do app por baixo é limpa (`clearAndSetSemantics`), senão o leitor de tela
     continuaria navegando por botões invisíveis. O título é `liveRegion`, então o TalkBack/VoiceOver
     anuncia a queda.
  3. **`BackHandler` é o de `org.jetbrains.compose.ui:ui-backhandler`** (dependência nova do
     `kmplib-ui`). Ele está depreciado em favor do `NavigationEventHandler`, mas o
     `navigationevent-compose` **não publica variante Kotlin/Native** e derruba o link do iOS — o
     `@Suppress` fica isolado num wrapper privado.
- **`ConnectivityTexts`** ganha `screenTitle`, `screenMessage` e `checkingButton`, **no fim e com
  default**: o construtor posicional de 4 textos continua compilando (há teste travando).

**Recomendado para app online-por-padrão.** A `casca-mobile` passa a nascer com `FullScreen` em
`DataMode.ONLINE_REST`. Apps existentes não mudam sozinhos — o default do gate é o mesmo.

## 2.199.0 — O pré-carregamento do feed mirava o vídeo errado; e o delegate que uma chamada roubava da outra

Correção de duas coisas entregues nas duas versões anteriores. Nenhuma API pública muda.

### 1. `video.feed`: a escada de pré-carregamento respondia por um vídeo em nome de outro

**Afeta quem está na 2.197.0 ou 2.198.0 e usa `FeedVideo`/`FeedVideoHost`.** Sintoma: o próximo
vídeo do feed às vezes abre **sem nada pré-carregado** (primeiro quadro lento, que é justamente o
que a feature existe para evitar), enquanto um vídeo **já passado** consome os 3 s de dado. Não
trava, não erra em tela, não aparece em log — só gasta o plano de dados de quem usa no vizinho
errado e entrega menos do que promete.

A causa, confirmada com `javap` no `media3-exoplayer-1.11.1` (não por leitura de documentação):

- `BasePreloadManager$MediaSourceHolder.rankingData` é **`public final`** — fixado na construção do
  holder. Quem responde "quanto pré-carregar deste item?" recebe o valor **do dia em que o item
  entrou**.
- O valor que estávamos passando era o índice **na janela composta**, que muda a cada rolagem. O
  item somado na posição 3 continuava perguntando por 3 para sempre, e a posição 3 já era de outro
  vídeo.
- O mapa `posição → URL` que traduzia isso também **nunca era podado** (só no `reset()`): numa
  lista de 200 posts ele terminava com 200 entradas para uma janela de 4.

A correção é dar a cada URL uma **posição estável** enquanto ela estiver no manager
(`FeedPreloadPositions`, em `commonMain` e coberta por teste), contígua e na ordem da tela. Com
isso o `rankingData` congelado continua verdadeiro e a distância vira **subtração** — o mapa
auxiliar deixou de existir, e com ele a possibilidade de envelhecer.

⚠️ **As duas saídas "óbvias" estão erradas, e o artefato mostra por quê** — fica registrado para
quem for mexer aqui:

| Saída | Por que não |
|---|---|
| Re-`add` a cada `update` | `add` cria `MediaSource` + `PreloadMediaSource` + holder novos e grava com `HashMap.put`, que **substitui sem liberar** o holder anterior (não há `release()` no caminho). Vazaria um por item por rolagem, jogando fora a fonte já preparada para prepará-la de novo. |
| Chave estável que não seja posição | `SimpleRankingDataComparator` ordena por `abs(rankingData − currentPlayingIndex)`: o campo **é lido como posição**. Um id qualquer acertaria o alvo e embaralharia a **ordem** em que os vizinhos são adiantados. |

Trocar a posição de um item (reordenação, feed novo) é `remove` + `add` — aí sim o holder antigo é
liberado. Rolar não reordena, então esse caminho é raro.

### 2. iOS: duas chamadas simultâneas derrubavam o delegate uma da outra

A 2.198.0 prendeu os delegates de `CLLocationManager` a referências fortes, mas em **um lugar só**:
um campo do provider (`LocationProvider.ios.kt`) e um `var` de módulo
(`PermissionManager.ios.kt`), limpos incondicionalmente ao terminar. Isso está certo **enquanto
houver um pedido por vez** — e nada impunha isso. Com duas chamadas em curso, a primeira a terminar
anulava a referência da **outra**: o ARC podia liberar o delegate de quem ainda esperava, e voltava
o defeito da 2.198.0 (localização que devolve `null` no fim de 10 s; permissão concedida que nunca
chega à tela).

A referência agora é **por requisição**, amarrada ao quadro da corrotina (`finally`) ou à closure do
`awaitClose` — não há mais estado compartilhado, nem contador de referências, nem a disputa entre
threads que um `var` global de Kotlin/Native traria. O KDoc dos dois arquivos diz qual foi a escolha
e por quê.

### ⚠️ Duas coisas que mudaram de comportamento SEM opt-in (2.196.0–2.198.0, registradas aqui)

Nenhuma é regressão, mas quem só sobe a versão precisa saber:

- **O feed liga cache de disco e pré-carregamento por default.** `FeedVideoConfig.preloadEnabled =
  true` e `diskCacheBytes = 128 MB`: subir para 2.197.0+ passa a instanciar um `DefaultPreloadManager`
  e um `SimpleCache` de até 128 MB no `cacheDir`. É proposital (o feed em laço rebaixava da rede a
  cada volta), e é mitigado — rede medida/Data Saver **não** pré-carregam por default
  (`preloadOnMeteredNetwork = false`), e `cacheDir` é recuperável pelo sistema. Para desligar:
  `FeedVideoConfig(preloadEnabled = false)`; para outro teto, `diskCacheBytes`.
- **A sobrecarga `@Deprecated` `rememberVideoPickerLauncher(onVideoSelected)` virou assíncrona e
  engole erro.** Desde a 2.197.0 ela é uma ponte sobre o seletor novo: os bytes chegam num
  `launch`, e falha de leitura vira `onError = {}` — silêncio. Nenhum app do portfólio a usa hoje,
  mas é mudança de contrato numa API que ainda existe. Migre para
  `rememberVideoPickerLauncher(source, onVideoPicked, onError)`.

## 2.198.0 — O seletor de foto devolve a MEDIDA; e a varredura dos delegates que o iOS solta

Três frentes. A primeira é um bug ao vivo; a segunda é o mesmo defeito da 2.197.0 encontrado em mais
quatro lugares; a terceira aposenta um plano B que todo app com vídeo estava repetindo.

### 1. `rememberImagePickerLauncher` devolve `PickedImage` — bytes **e** medida

Até aqui o seletor devolvia só `ByteArray`. Sem largura e altura, o app publica a foto sem
`mediaWidth`/`mediaHeight`, o feed cai no padrão (4:5 com corte central) e **a mesma foto sai inteira
pelo site e cortada pelo aplicativo** — porque o navegador lê a medida sozinho, num `<canvas>`, e o
app não tinha de onde. Confirmado no Cidade Conectada (11/set/2026).

- **`PickedImage(bytes, widthPx, heightPx, mimeType)`** — a medida é a **da imagem que sai** (JPEG
  reduzido a `PICKED_IMAGE_MAX_DIMENSION` = 1024 px no maior lado, qualidade 85), não a do original.
  Devolver a medida do arquivo escolhido erraria a proporção de outro jeito.
- **Orientação aplicada nos dois lados, e gravada nos bytes.** Foto de celular chega deitada com a
  rotação num campo à parte; quem lê a medida crua conclui que **todo retrato é paisagem**. No
  Android o bitmap é girado pelo EXIF e recodificado (o `Bitmap.compress` não escreve tag de
  orientação); no iOS a imagem é **sempre redesenhada** antes de codificar, porque
  `UIImageJPEGRepresentation` grava os pixels crus **mais** a tag — e aí a medida devolvida poderia
  não bater com a que o backend lê. Depois disso, a medida daqui é a mesma que qualquer
  decodificador encontra nos bytes.
- **A conta da redução virou pura e comum** (`scaledImageSize`, testada): era a mesma fórmula escrita
  duas vezes, e duas escritas é como Android e iOS passam a divergir na proporção publicada.
- **`ImagePickerSource.GALLERY_ONLY`** — abre a galeria direto, sem folha e sem exigir
  `android.permission.CAMERA` (publicar foto ≠ tirar foto), igual ao que a 2.197.0 fez no vídeo. Ele
  é também o que **mantém compatível** a assinatura antiga: como o primeiro parâmetro da nova não é
  uma lambda, nenhuma chamada existente fica ambígua.
- **Aditivo.** As duas sobrecargas de `ByteArray` seguem funcionando, implementadas sobre a nova, e
  agora `@Deprecated` com o motivo.

### 2. Delegate de UIKit é `weak` — e havia mais QUATRO lugares soltando o objeto

A 2.197.0 corrigiu isto no `VideoPicker.ios.kt`. O mesmo erro estava em mais quatro pontos, e todos
falham do mesmo jeito: **compilam, não lançam, não logam — a ação simplesmente não acontece.**

| Onde | O que a pessoa vê |
|---|---|
| `ImagePicker.ios.kt` (`cameraDelegate`/`galleryDelegate`) | Escolhe a foto e **nada volta para a tela** |
| `LocationProvider.ios.kt` | "Não consegui achar sua localização" no fim de 10 s — o manager e o delegate eram locais que **não sobrevivem ao `await`** (a corrotina só preserva o que é usado depois da suspensão) |
| `PermissionManager.ios.kt` (LOCATION) | Concede a permissão e **a tela continua esperando** — o `awaitClose` retinha o *manager*, nunca o delegate |
| `NotificationActionBridge.installNotificationActionDelegate()` | `center.delegate = KmpLibNotificationDelegate()` sem dono: tocar na notificação para de abrir a tela, e ela nem aparece com o app aberto |

Em todos, a correção é a mesma: referência **forte** enquanto a operação existe, mais um KDoc
dizendo **por que** ela não pode ser "simplificada" de volta para uma variável local.

Auditados e **corretos** (não mexidos): `AppleAuthProvider`, `SocialBrowserLogin`, `FilePicker`,
`HtmlDocumentView`, `AudioPlayer`, `TtsController`, `CameraView`, `BarcodeCameraPreview`,
`MediaDownloadManager`.

### 3. `PickedVideo.captureFrame(atMillis = 500)` — a capa sai do vídeo

Sem isto, todo produto com post de vídeo repete o plano B: uma tela a mais pedindo que a pessoa
escolha uma foto de capa **para algo que o vídeo já tem**. `MediaMetadataRetriever` no Android,
`AVAssetImageGenerator` (com `appliesPreferredTrackTransform`) no iOS. Devolve `null` em vez de
lançar — capa é acessório e não pode derrubar a publicação. O default de 500 ms evita a capa preta
do instante `0` (fade de entrada, autofoco). No Android a rotação é **conferida**, não presumida: o
`getFrameAtTime` já devolve o quadro girado na maioria dos aparelhos, e um `postRotate` cego giraria
duas vezes.

## 2.197.0 — Vídeo de feed: cache de disco e pré-carregamento medido; seletor de vídeo por REFERÊNCIA

Duas frentes, as duas no vídeo. A primeira fecha o `GAP-CC-M-06`; a segunda conserta um seletor que
existia, funcionava e não servia para o caso de uso que chegou (publicar vídeo de até 100 MB).

### 1. O feed deixa de rebaixar o mesmo vídeo a cada volta do laço

O vídeo de feed toca em **laço** com buffer curto (15 s). Sem cache, um vídeo de 60 s **rebaixava da
rede a cada volta**, para sempre, enquanto o post estivesse na tela. Não era preparação para nada:
era plano de dados e bateria indo embora agora.

- **`FeedVideoCache` (Android)** — `SimpleCache` **singleton** (duas instâncias no mesmo diretório
  lançam: o cache tranca a pasta) em `cacheDir/kmplib_feed_video`, com
  `LeastRecentlyUsedCacheEvictor` e teto em **`FeedVideoConfig.diskCacheBytes`** (default 128 MB).
  **Não é o cache do `video.download`, e os dois são opostos de propósito:** feed = `cacheDir` +
  LRU + escrita ligada; download = `filesDir` + `NoOpCacheEvictor` + somente leitura. Juntá-los
  faria o evictor apagar a aula que o aluno baixou para o voo. Dividem só o `DatabaseProvider`.
- **A chave do cache é a URL SEM a query** (`feedVideoCacheKey`, pura e testada). A URL de feed é
  assinada e muda a cada abertura da tela: endereçada pela URL inteira, cada token viraria uma cópia
  nova no disco e nenhuma seria reaproveitada.
- **`Media3FeedPreloader` (Android)** — `DefaultPreloadManager`, o caminho oficial da Media3 para
  feed de vídeo curto. Escada (pura, em `feedPreloadTargetFor`): **3 s** no próximo · **1 s** no 2º e
  3º · **5 s só em disco** até o 5º · nada além disso.
- **Só para frente, e cancelado quando o vídeo da vez passa fome** (`shouldCancelFeedPreload`:
  < 5 s de buffer à frente, ou `Buffering`). Adiantar o post de baixo enquanto o de cima trava é o
  pior negócio possível — é o consenso entre a documentação da ByteDance e o `invalidate()` da
  Media3. Pré-carregar para trás foi recusado: dobraria o gasto para cobrir o caso raro.
- **`bufferForPlaybackMs` 1000 → 500** — é o teto direto do tempo até o primeiro quadro, e é o valor
  do demo oficial de vídeo curto do Google.
- **`ExoPlayer.setPriority`** (`PRIORITY_PLAYBACK` × `PRIORITY_PLAYBACK_PRELOAD`) separa quem toca de
  quem adianta. O CDD do Android 16 garante **6** decodificadores SDR concorrentes, e há aparelho
  relatando **1**: sem prioridade, quem o sistema derruba pode ser o vídeo que está na tela.
- **Rede medida e Data Saver cortam o PRÉ-CARREGAMENTO, nunca a reprodução**
  (`isActiveNetworkMetered` + `getRestrictBackgroundStatus`; chave de escape em
  `FeedVideoConfig.preloadOnMeteredNetwork`). ⚠️ **Não** foi implementado "autoplay só no Wi-Fi":
  é padrão legado, e nem Instagram nem TikTok o têm.

**iOS — três decisões, e a primeira custa zero e vale muito:**

- **A `AVPlayerLayer` passa a ser anexada ANTES de o item virar `currentItem`.** É a recomendação da
  Apple (WWDC16-503): item que vira `currentItem` sem camada faz a AVFoundation montar o pipeline
  **só de áudio**, para reconfigurar depois. Acontecia em **todo** item do feed — o `load()` do
  controller roda num `SideEffect`, antes de a superfície compor. A camada agora nasce dentro do
  engine, e a superfície só a **adota**.
- **`AVPlayerLooper` saiu; o laço é manual** (`AVPlayerItemDidPlayToEndTime` + `seek(.zero)` com
  `actionAtItemEnd = .none`). Motivo: o looper **ignora** `preferredForwardBufferDuration` e
  `preferredMaximumResolution` (ele gerencia réplicas do item por dentro), e tem bug conhecido de
  seek em HLS. Como o pré-carregamento do iOS **é** o `preferredForwardBufferDuration`, mantê-lo
  seria manter um parâmetro público que não faz nada — e o feed vai para HLS na migração ao Bunny
  Stream. Feito **antes** dela, de propósito.
- **`preferredForwardBufferDuration` = 1 s em quem não tem a vez**, automático em quem toca; e o
  asset de quem está adiantando nasce com `AVURLAssetAllowsExpensiveNetworkAccessKey` /
  `…ConstrainedNetworkAccessKey` em `false` (o Modo Dados Reduzidos da Apple). Ao ganhar a vez, um
  item barrado é refeito sem a restrição — senão ficaria esperando para sempre um Wi-Fi que não vem.

**Media3 1.6.1 → 1.11.1.** Exigido pelo `specifiedRangeCached` (pré-carregar para o disco, 1.9.0+) e
pelo `setPriority`. ⚠️ **Um breaking interno veio junto:** `DownloadHelper.Callback.onPrepared`
ganhou um segundo parâmetro (`hasPreparedTracks`) — corrigido no `video.download`; nenhum app é
afetado.

**Sobre o `PlayerPool` do Google** (novo em `media3-common-ktx` 1.11.0, com `rememberPooledPlayer` em
`media3-ui-compose`): **avaliado e recusado**, com motivo. O `acquire` dele é **suspenso** e o
`rememberPooledPlayer` amarra a posse do player à composição de cada item; o nosso pool decide de
forma **síncrona e por prioridade** (`assignFeedVideoSlots`), e sabe preferir o player que **já está
com a mesma fonte carregada** — é o que faz o vídeo que saiu da vez e voltou retomar sem reabrir o
manifesto. Também precisamos emprestar player a item **fora** da vez e trocar de dono sem passar
pela composição. O do Google resolve "N players para M itens"; o nosso resolve "quem toca, quem
adianta e quem espera". Registrado no KDoc do `FeedVideoController`.

### 2. `VideoPicker` — referência ao arquivo, não os bytes; e galeria sem câmera

O seletor existia e tinha dois defeitos sérios para publicar vídeo:

- **Carregava o arquivo inteiro num `ByteArray`** (`openInputStream().readBytes()` no Android,
  `NSData.dataWithContentsOfURL` no iOS). Com o limite de 100 MB da casa, é `OutOfMemoryError` em
  aparelho de entrada — e `OutOfMemoryError` **não é `Exception`**, então o `try/catch` em volta nem
  o pegava. Agora devolve **`PickedVideo`**: referência (`content://` no Android, caminho de uma
  cópia nossa no iOS) + nome + mime + tamanho, e os bytes saem do disco em pedaços por
  **`PickedVideo.readChunks`** (256 KB), pronto para `PUT` em fluxo no provedor de vídeo.
- **Sempre oferecia "Gravar vídeo".** Decisão do fundador (11/set/2026): *"a gente não vai ter
  câmeras para publicar; esses vídeos vão ser mais trabalhados, muitas vezes editados"*. Entrou
  **`VideoPickerSource.GALLERY_ONLY`**, que abre o seletor do sistema **direto**, sem folha de
  escolha (uma folha com uma opção só é um toque a mais para nada) e sem exigir a permissão de
  câmera. O default é `GALLERY_AND_CAMERA` — o comportamento de sempre.

De quebra, e nas duas plataformas: **duração e medida** vêm junto (`durationMillis`, `widthPx`,
`heightPx`), **com a rotação já aplicada** — vídeo de celular gravado em pé chega com medida de
deitado + matriz de 90°, e quem lê a medida crua conclui que todo vídeo de celular é horizontal. Com
a duração em mãos, o app recusa o vídeo de 90 s **antes** de subir 100 MB para o backend devolver
erro (o teto é 61 s). Android: `MediaMetadataRetriever`; iOS: `AVURLAsset`.

Também novo: **`VideoPickerError`** (`UNREADABLE`, `CAMERA_PERMISSION_DENIED`, `CAMERA_UNAVAILABLE`).
Antes, câmera negada era um `if (granted)` **sem `else`** e falha de leitura era `printStackTrace()`:
o toque no botão não produzia efeito nenhum, e a leitura de quem usa é "está quebrado". É a mesma
correção que o `ImagePicker` recebeu na 2.131.0.

⚠️ **Corrigido de passagem, e era grave:** no iOS os delegates do seletor eram criados **dentro** do
bloco de lançamento, e `PHPickerViewController.delegate` é **weak** — o delegate podia ser liberado
antes de a pessoa escolher, e aí escolher o vídeo não fazia nada, sem erro. Agora há referência
forte de módulo. **O `ImagePicker.ios.kt` tem o mesmo defeito** e **não** foi tocado (fora do escopo);
está registrado em `docs/backlog.md`.

`SelectedVideo` e a sobrecarga antiga continuam existindo, **`@Deprecated`**, implementados sobre a
API nova — nada deixa de compilar, e o caminho de migração está na mensagem. Nenhum projeto do
monorepo usava o `VideoPicker` (conferido arquivo por arquivo).

### De passagem

- **O `VideoPicker` e o `ImagePicker` NÃO estavam na skill-catálogo** — nem no índice, nem em
  `references/ui.md`. Falha do catálogo, não da lib: o fundador quase escreveu um seletor de vídeo do
  zero por não encontrar o que já existia. Corrigido no mesmo commit.

Testes: `FeedVideoPreloadTest` (17 — a escada, o cancelamento por fome, a chave de cache) e
`FeedVideoControllerTest` (+11 — modo de adiantamento por player, ordem da tela entregue ao
pré-carregador, fome suspendendo, `poll` que não reenvia a mesma ordem). Compilado no servidor:
`compileDebugKotlinAndroid` e `compileKotlinIosArm64` de `kmplib-video` e `kmplib-ui` (conferido:
executados, não `SKIPPED`). **Não validado em aparelho** — cache, consumo de dados e o seletor de
vídeo são do fundador (backlog `GAP-CC-M-07`).

Aditiva. Nenhum app precisa mudar.

## 2.196.0 — Vídeo de FEED: toca mudo ao aparecer, um por vez, em laço, com som global

O `VideoPlayer` (2.191.0) é de aula: controles, velocidade, legenda, retomada. Faltava o vídeo **de
feed** — o post de vídeo no meio de uma lista, estilo Instagram. Primeiro consumidor: a Início do
Mirassol Conectado (`docs/feed-moderno-spec.md` do projeto). Pacote novo
`br.com.codecacto.kmplib.video.feed`, no mesmo artefato `kmplib-video`.

- **`FeedVideoHost(modifier, controller) { LazyColumn(…) }`** — a área do feed. Provê o controller
  (`LocalFeedVideoController`) e marca o retângulo contra o qual a visibilidade é medida
  (`Modifier.layoutBounds`, Compose 1.9+).
- **`FeedVideo(url, posterUrl, aspectRatio, …, onClick, onRenewUrl, error)`** — o item. Mede a
  própria visibilidade com **`onLayoutRectChanged`** (a API oficial de visibilidade do Compose, com
  throttle de 100 ms e debounce de 64 ms), mostra a **capa** (Coil) até o primeiro quadro e, em erro,
  mantém a capa com um aviso discreto (slot `error`). `onClick` abre a publicação; o botão de som
  **não** propaga o toque. `onRenewUrl` renova URL assinada vencida (teto `MAX_RENOVACOES_DE_URL`).
  Fora de um host, **falha alto** (`checkNotNull`): sem coordenador, todos tocariam juntos.
- **`FeedVideoController`** (`rememberFeedVideoController(config, sound)`): quem toca é o **mais
  visível acima de 60%**, com margem de troca de 10% (sem ela, dois vídeos se cruzando na rolagem
  trocariam de dono a cada quadro); empate sem ninguém tocando → o de cima. **Pool** de no máximo
  `maxPlayers` players nativos (default 2: o da vez + o próximo, já no primeiro quadro), criados sob
  demanda e **reciclados** entre itens; item que sai da composição devolve o player esvaziado;
  **`ON_STOP` destrói todos** (recria no `ON_START`); `setPlaybackEnabled(false)` pausa o feed sem
  sair da tela (sheet por cima, aba escondida).
- **`FeedVideoSoundState`** — o som do feed, um estado só. Default `FeedVideoSoundState.Shared` (o
  processo inteiro: Início e detalhe concordam; app morto volta mudo). Nasce **mudo**.
- **`FeedVideoSoundButton`** — alto-falante / alto-falante riscado, alvo de **48 dp** com desenho de
  32 dp, cores em `FeedVideoColors`, descrição e estado para leitor de tela em `FeedVideoTexts`.
- Funções puras: `pickFeedVideoToPlay`, `feedVideoVisibleFraction` (por ÁREA), `feedMediaAspectRatio`
  (a régua 4:5…1,91:1 do Instagram, 4:5 sem medida), `FeedVideoCandidate`, constantes
  `FEED_VIDEO_PLAY_THRESHOLD`, `FEED_VIDEO_SWITCH_MARGIN`, `FEED_MEDIA_MIN/MAX_ASPECT_RATIO`.

### Decisões de plataforma (padrão-ouro)

- **Android — Media3 com a integração Compose oficial** (`media3-ui-compose`, dependência nova):
  `PlayerSurface` em **`TextureView`** (o `SurfaceView` não é recortado pelo clip do Compose, e no
  modo Crop a superfície é maior que a caixa — sairia por cima do post vizinho),
  `rememberPresentationState` (a capa sai no primeiro quadro) e `resizeWithContentScale`. Laço por
  `REPEAT_MODE_ONE`; buffer curto (máx. 15 s — com 2 players, o default de 50 s seguraria o vídeo
  inteiro por um post que a pessoa pula).
- **Som × foco de áudio (Android):** mudo **não pede foco** (`handleAudioFocus = false`, volume 0) —
  a música de outro app continua. Com som, pede (`USAGE_MEDIA`); perda definitiva do foco ou fone
  desconectado **não param o feed**: ele volta a mudo e segue.
- **iOS — `AVQueuePlayer` + `AVPlayerLooper`** (o laço sem emenda que a Apple indica) e
  `AVPlayerLayer` num `UIKitView` **não interativo** (o toque segue para o Compose). Primeiro quadro
  = `readyForDisplay` da camada. Troca de frame da camada sem animação implícita.
- **Sessão de áudio (iOS):** mudo → **`.ambient`** (mistura: a música de fundo continua; o
  `.soloAmbient` default a interromperia no primeiro vídeo, mesmo mudo). Com som → **`.playback`,
  modo `moviePlayback`** — foi um toque explícito, então o som sai mesmo com o interruptor de
  silencioso ligado; o feed continua **nascendo** mudo, que é como o interruptor é respeitado. A
  categoria anterior do app é restaurada quando o último feed sai. Interrupção (ligação/Siri) e rota
  perdida (fone) com som → o feed volta a mudo e segue.
- **Tela acesa só com som**, nas duas plataformas (`KeepScreenOn` do `kmplib-platform`): mudo, vale o
  tempo de tela do sistema — vídeo de feed é laço e, com a tela presa, tocaria para sempre num
  celular largado. No iOS isso exige `preventsDisplaySleepDuringVideoPlayback = false` (o default do
  AVPlayer prende a tela até com o vídeo mudo).

### De passagem

- `PlaybackException.paraVideoErrorKind()` (androidMain) passou de `private` a `internal`, para o feed
  usar a mesma tradução de erro do player de aula.
- Os testes do módulo **não compilavam para iOS**: 15 nomes de teste entre crases tinham vírgula, que
  o Kotlin/Native recusa (`Name contains illegal characters: ","`). Trocadas por travessão; nenhum
  teste mudou de conteúdo. `compileTestKotlinIosArm64` agora passa.

Testes: `FeedVideoPolicyTest` (30), `FeedVideoControllerTest` (22, com player falso: um de cada vez,
teto do pool, reciclagem, som global, perda de áudio, `ON_STOP`/`ON_START`, pausa do feed) e
`FeedVideoSoundStateTest` (4). Compilado no servidor: `compileDebugKotlinAndroid`,
`compileKotlinIosArm64` e `compileTestKotlinIosArm64` do `kmplib-video` (conferido: executados, não
`SKIPPED`). **Não validado em aparelho** — a parte visual e de áudio é do fundador (backlog
`GAP-CC-M-07`).

Aditiva. Nenhum app precisa mudar; o `kmplib-video` ganhou `media3-ui-compose` e Coil como
dependências de implementação.

## 2.195.0 — "Compartilhar app" com link rastreável (UTM), e o share sheet do iOS corrigido

Compartilhar é o único canal de aquisição que cresce com o uso — e até aqui cada app que o tinha
mandava a URL crua: a visita chegava ao site como "direto", sem dizer que veio de dentro do app. A
regra do ecossistema é que todo canal tem UTM (`docs/30`, invariante 6).

- **Novo `AppShareLink(landingUrl, campaign)`** (`kmplib-platform`, pacote `platform`): `url(content)`
  e `urlFor(pageUrl, content)` devolvem o link do site do produto com `utm_source=app`,
  `utm_medium=share`, `utm_campaign=<slug do projeto>` e `utm_content` opcional (a tela). Função pura
  `appendShareUtm(url, campaign, content, source, medium)`, com o `URLBuilder` do Ktor: preserva
  caminho, query e fragmento; **substitui** `utm_*` que o link já tinha (reenviar um link recebido
  não carrega a origem de quem mandou antes); normaliza campaign/content (minúsculas, espaço → `-`,
  porque o analytics diferencia caixa); recusa URL que não seja `http(s)` absoluta e campaign vazio.
- **Novo `ShareHandler.shareLink(url, message, title)`** — corpo default (quem implementa a interface
  não quebra). Mensagem + link num **texto único** (`composeShareText`): com itens separados, há app
  de destino que aproveita só um dos dois. Android: `ACTION_SEND` com `EXTRA_TEXT`, `EXTRA_TITLE`
  (a prévia da folha do Android 10+) e `EXTRA_SUBJECT` (e-mail).
- **Novo `ShareAppMenuItem(appName, link, …)`** (`kmplib-ui`, `ui/share`) — a entrada padrão
  "Compartilhar app" para o menu, com ícone e rótulo **no idioma do aparelho** (Compose Resources:
  pt-BR, pt-PT, en, es); `utm_content` default `menu` (`SHARE_APP_CONTENT_MENU`). Para outro gatilho,
  `rememberShareApp(link, texts, content, onError): () -> Unit`; textos em `ShareAppTexts` /
  `rememberShareAppTexts(appName)`; regra testável `ShareHandler.shareApp(link, texts, content)`. O
  `ShareHandler` é resolvido **no toque**: init ausente vira `onError`, não a tela inteira caindo.

### Correções no `ShareHandler` que valem para todo compartilhamento

- **iOS — iPad.** Lá o `UIActivityViewController` é apresentado como popover, e o UIKit exige
  `sourceView` (ou `barButtonItem`) — sem ele, **lança exceção na apresentação**. Nenhum dos métodos
  ancorava. Agora a folha ancora no centro da tela, sem seta. Afeta app **universal** (iPhone+iPad);
  app só-iPhone rodando no iPad em modo de compatibilidade não era atingido.
- **iOS — apresentação.** A janela saía de `UIApplication.windows.first` (obsoleto desde o iOS 15) e
  a folha era apresentada no `rootViewController`: com uma sheet ou diálogo já aberto por cima, o
  UIKit recusava ("already presenting") e o toque não fazia nada. Agora: cena ativa das
  `connectedScenes` → `keyWindow` → controlador do topo. Sem janela, **lança** (antes engolia e
  parecia sucesso — o contrato do `ShareHandler` desde 2.31.0 é propagar).
- **iOS — `shareImage`** com bytes que não decodificam como imagem passa a lançar (antes voltava
  calado).
- **Android — o chooser abre na tarefa do app.** `kmpLibPlatformOnResume`/`OnPause` passam a entregar
  a `Activity` ao `ShareHandlerHolder`; com ela o chooser sai da Activity (como a documentação manda),
  e só sem ela cai no `applicationContext` + `FLAG_ACTIVITY_NEW_TASK` de antes.

Testes: `AppShareLinkTest` (14) e `ShareAppTest` (4). Compilado `compileDebugKotlinAndroid` e
`compileKotlinIosArm64` (platform e ui) no servidor.

Aditiva na API. Nenhum app precisa mudar; quem tem app universal e compartilha algo ganha o
conserto do iPad ao subir.

## 2.194.0 — `Modifier.dismissKeyboardOnTapOutside()`: tocar fora fecha o teclado

No iPhone o teclado **não tem** botão de fechar: o que sobe para um comentário só desce quando a
pessoa sai da tela. Cada app resolvia tela a tela com `clickable { focusManager.clearFocus() }` (o
Super 8 fez assim), e a tela que esquecia ficava sem.

- **Novo `Modifier.dismissKeyboardOnTapOutside()`** (`ui/components`). Aplicado **uma vez, na raiz**
  do app, vale para todas as telas. Observa o gesto no passe `Final` — depois de todos os filhos — e
  **não consome nada**. Fecha o teclado quando o toque é **livre**: um dedo, sem passar do *touch
  slop*, e que **nenhum filho consumiu**. Isso separa sozinho, sem lista de exceções: o próprio campo
  (o `BasicTextField` consome o toque — conferido no fonte do Compose 1.10.3, Android
  `detectTapAndPress` e iOS `cupertinoTextFieldPointer`), botões/`clickable` (o "Enviar" não derruba
  o teclado de quem vai continuar escrevendo) e rolagem (passa do slop). `Modifier.Node`, não
  `composed`.
- **`FormContainer` troca o `clickable { clearFocus() }` por ele.** O `clickable` consumia o toque e
  publicava um nó de semântica clicável do tamanho do formulário — o leitor de tela anunciava a tela
  inteira como um botão.
- **`AppDialog` (e por ele `AppInputDialog`) e `AppBottomSheet` já trazem o modifier**: diálogo e
  folha são outra janela, e a raiz do app não os alcança.
- Regra do gesto pura e testada (`KeyboardDismissTap`, `KeyboardDismissTapTest`, 8 casos).

Aditiva. Quem não aplica na raiz não muda de comportamento, exceto o `FormContainer` (mesmo efeito,
sem o nó de semântica) e diálogo/folha da lib, que passam a fechar o teclado ao tocar num espaço
vazio deles.

## 2.193.0 — o `iosMain` volta a compilar, e passa a ser COMPILÁVEL AQUI

A 2.192.0 **não compilava para iOS**. Quatro linhas, quatro suposições sobre como o cinterop expõe a
API da Apple — nenhuma delas conferida, porque a casa acreditava que alvo Apple só compila no Mac.
O erro apareceu no Xcode, na véspera de subir para a loja.

### A correção que importa: o alvo iOS COMPILA no servidor Linux

```bash
./gradlew :kmplib-<módulo>:compileKotlinIosArm64 \
    -Pkmplib.forceAppleTargets=true \
    -Pkotlin.native.enableKlibsCrossCompilation=true
```

O que exige Xcode é o **link** do framework, não a compilação do Kotlin. **Sem a segunda flag a
tarefa sai `SKIPPED` e o Gradle imprime `BUILD SUCCESSFUL` sem compilar nada** — o mesmo verde falso
do `compileKotlinMetadata`; confira o `SKIPPED` no log. Toda a classe de erro abaixo é pega aqui, em
minutos, antes de alguém abrir o Xcode. A regra está no `CLAUDE.md` da lib e o detalhe em
`references/ios-cinterop.md` (skill `kmplib-catalog`).

### Os quatro pontos

| Onde | Estava | Está |
|---|---|---|
| `PdfViewer.ios` | `withContext(Dispatchers.Default)` | `Dispatchers.IO` + **`import kotlinx.coroutines.IO`** — no Native `IO` é propriedade de EXTENSÃO, não membro; `Default` é pool de CPU e ler disco ali bloqueia thread de cálculo |
| `PdfViewer.ios` | `setDisplayMode(1L)` / `setDisplayDirection(0L)` | `kPDFDisplaySinglePageContinuous` / `kPDFDisplayDirectionVertical` — `PDFDisplayMode` é `typealias` de `NSInteger` com **constantes de topo** (e o nome não tem "Mode" no meio) |
| `PdfViewer.ios` | `UIColor.grayColor` | `UIColor.Companion.systemGray5Color()` — cinza CLARO do sistema atrás da página; `grayColor` é 50% e escurece a leitura |
| `PrivacyScreen.ios` | `4L as UIBlurEffectStyle` | `UIBlurEffectStyle.UIBlurEffectStyleSystemMaterial` |

### ⚠️ O cast era um crash, não um estilo feio

`UIBlurEffectStyle` **é enum class** (`CEnum`) no Kotlin/Native. `4L as UIBlurEffectStyle` compila —
o compilador só avisa `this cast can never succeed` — e lança **`ClassCastException` na primeira
execução**. `IosPrivacyScreen.cover()` roda em `UIApplicationWillResignActiveNotification`: o app
quebraria na primeira vez que perdesse o foco. **Nenhum app da casa liga o `PrivacyScreen` hoje**
(varrido no monorepo), então não chegou a ninguém — mas era o primeiro a ligar que descobriria.

O `@Suppress("UNCHECKED_CAST")` ali calava o aviso ERRADO e deixava passar o certo. No `iosMain`,
`@Suppress` + número mágico + cast forçado são sintoma de que ninguém foi conferir como a API chega.

### E a primeira varredura já achou mais dois — no `kmplib-video`

Com o alvo iOS compilando, rodou-se `compileKotlinIosArm64` na **lib inteira**. O módulo de vídeo
(2.190.0/2.191.0), o mais novo, **não compilava para iOS** — ninguém saberia até o primeiro app de
curso abrir o Xcode:

| Arquivo | Estava | Está |
|---|---|---|
| `VideoPlayerState.ios` (5×) | `MPRemoteCommandHandlerStatus.MPRemoteCommandHandlerStatusSuccess` | `MPRemoteCommandHandlerStatusSuccess` — mesma armadilha do PDFKit: constante de topo qualificada por um `typealias` |
| `MediaDownloadManager.ios` | `assetDownloadTaskWithURLAsset(uRLAsset = …)` | `URLAsset = …` — o cinterop preserva a SIGLA maiúscula neste selector (o construtor `AVURLAsset(uRL = …)` minuscula, e é essa assimetria que engana) |

### A linha que sustentou a crença por meses

`kotlin.native.enableKlibsCrossCompilation` estava **`false`** no `gradle.properties` da lib. Era
essa linha — não uma limitação do compilador — que fazia todo alvo Apple sair `SKIPPED` no servidor
e alimentava o *"iOS só compila no Mac"*. Agora está `true` aqui, na `casca-mobile` e nos apps: o
comando ficou **uma flag só** (`-Pkmplib.forceAppleTargets=true` / `-Papp.forceAppleTargets=true`).
No Mac nada muda — lá todo alvo Apple já é nativo.

### E o `:kmplib-testing`, que falhava só em ÁRVORE LIMPA

`-friend-modules` do Kotlin/Native procurava o KLIB amigo com `listFiles()`. Com a **cache de
configuração** ligada (default aqui), esse provider é avaliado ao **gravar a entrada da cache** —
antes de qualquer tarefa rodar. Em árvore limpa a pasta ainda não existe, a amizade não é passada, e
o erro que sobra é `it is internal in PurchaseManager`, que manda investigar visibilidade. Na
segunda execução o KLIB já está lá do build anterior e tudo passa: **o defeito só aparece em CI, em
clone novo e depois de `clean`** — exatamente onde ninguém está olhando.

Agora o caminho é **calculado** (`…/klib/kmplib-monetization`), não procurado: quando o compilador
lê o argumento, a tarefa amiga já rodou pela dependência de projeto.

**Estado agora: os 23 alvos `iosArm64` da lib compilam, zero `SKIPPED`, em árvore limpa e com
`--no-build-cache`** — `:kmplib-testing` incluído. O app do Mirassol também
(`:composeApp:compileKotlinIosArm64`).

## 2.192.0 — vender ITEM, não assinatura: compra única e restauração de N itens

`GAP-RA-M-05` do `docs/backlog.md`, o quinto item do desenho do **Raquete Alta**. O módulo de compra
era **inteiro orientado a assinatura** — `Offerings` → `Package` → `PaywallScreen` → entitlement
`premium` —, e o produto vende **curso avulso, compra única, acesso vitalício**: na loja isso é um
**não-consumível** (App Store) / **in-app product** (Google Play), que não cabe naquele desenho sem
gambiarra.

E há um ponto que muda o desenho, não só a assinatura de um método: **restaurar precisa devolver N
itens**. `RestoreResult` responde *"tem assinatura ativa: sim/não"*, e essa pergunta não serve ao
aluno que comprou seis cursos e trocou de celular.

Tudo **aditivo**: os quatro métodos novos têm implementação default na interface, e nada do caminho
de assinatura mudou. Quem vende plano não sente nada.

### O que entrou

```kotlin
// catálogo — por id de produto, que é o caminho oficial do fornecedor para não-assinatura
when (val c = monetization.getStoreItems(cursos.map { it.productId })) {
    is StoreItemsOutcome.Available -> {
        ui.mostrar(c.items)                       // preço JÁ formatado pela loja
        if (c.incident) alertas.report(PaymentAlertKind.ItemIndisponivelNaLoja,
            detalhe = "faltando=${c.missingProductIds.size}")
    }
    is StoreItemsOutcome.Empty -> alertas.report(PaymentAlertKind.ItemIndisponivelNaLoja)
    is StoreItemsOutcome.Failed -> ui.erro(c.code.userMessage())
    StoreItemsOutcome.Unavailable -> ui.mostrar("em breve")   // build sem billing
}

// compra
when (val r = monetization.purchaseItem(curso.productId)) {
    is ItemPurchaseResult.Success     -> api.conciliar(r.claim)     // o SERVIDOR concede
    is ItemPurchaseResult.Pending     -> ui.aviso(textos.paymentPending)  // NÃO liberar
    is ItemPurchaseResult.AlreadyOwned-> api.conciliar(monetization.restoreItems())
    ItemPurchaseResult.Cancelled      -> Unit
    is ItemPurchaseResult.Failed      -> ui.erro(r.code.userMessage())
}

// celular novo — a LISTA, com o recibo de cada item
val r = monetization.restoreItems()   // só a partir de um toque do usuário
```

`StoreItem` · `PurchaseStore` · `StoreItemsOutcome` (`Available`/`Empty`/`Failed`/`Unavailable`,
`missingProductIds`, `incident`, `from`) · `OwnedStoreItem` · `StoreVerification` ·
`StorePurchaseClaim` (`isAnonymousAppUser`, `productIds`, `isEmpty`) · `ItemPurchaseResult`
(`Success`/`Pending`/`AlreadyOwned`/`Cancelled`/`Failed` + `fromFailure`) · `ItemRestoreResult`
(`Restored`/`NothingToRestore`/`Failed`) · em `PurchaseRepository`, `PurchaseManager` e
`MonetizationManager`: `getStoreItems`, `purchaseItem`, `restoreItems`, `ownedItems`. Mais dois
`PaymentAlertKind`: `ItemIndisponivelNaLoja` e `VerificacaoDeCompraFalhou`.

### Sete decisões que valem registro

1. **A lib NÃO concede acesso, e a API foi desenhada para não conseguir.** Não existe
   `temAcesso(item)` em lugar nenhum destes tipos — de propósito. `StorePurchaseClaim` é uma
   **alegação de cliente**, e cliente é o aparelho de quem pode ter interesse em mentir. Quem
   concede é o servidor (`backlib-entitlement` ≥ 0.111.0), avisado pelo **webhook** do fornecedor e,
   como rede de segurança, por este claim — que o backend confere consultando o fornecedor pelo
   `appUserId` antes de conceder. Um booleano aqui seria a porta que todo app acabaria usando, e ela
   abre com um APK modificado.
2. **O `transactionId` é a chave de idempotência da concessão**, e é por isso que ele viaja. Casa
   direto com `EntitlementService.grant(source = IAP_APPLE|IAP_GOOGLE, sourceRef = transactionId)`:
   o mesmo recibo entregue duas vezes concede uma vez. É também por isso que a compra devolve o
   claim **inteiro** (tudo o que a loja diz que a pessoa possui) e não só o item novo — reenviar o
   que já foi concedido não custa nada e **conserta de graça** a compra antiga cujo webhook se
   perdeu.
3. **Ler por id de produto, não por Offering.** É o caminho oficial do fornecedor para
   não-assinatura, e aqui é o único que serve: num catálogo de cursos os produtos nascem junto com o
   conteúdo, são dezenas e mudam toda semana. A documentação do RevenueCat admite atrelar
   não-consumível a **um entitlement por item** ("um por região do mapa"), e é o que a lib **não**
   faz: seria editar o painel do fornecedor a cada curso publicado. Os itens saem de
   `customerInfo.nonSubscriptionTransactions`, que é onde ele guarda toda compra única.
4. **Id que a loja não conhece é campo de primeira classe, não linha de log.** A loja **não erra**
   quando um id não existe no catálogo dela: ela **omite** o produto e responde 200. O app pede 12
   cursos, recebe 11, e a tela fica plausível — um curso a menos não parece defeito de ninguém.
   `StoreItemsOutcome.missingProductIds` existe para isso, e `incident` já diz se aquilo merece
   alerta. Foi a mesma lição da 2.141.0 (`OfferingsOutcome`), num lugar onde ela dói mais: no
   paywall a tela vazia é visível, aqui a falta é invisível.
5. **Dois desfechos que chegam do SDK como erro e não são.** `PAYMENT_PENDING` (aprovação parental,
   boleto — a cobrança está em andamento) vira `Pending`, e `ALREADY_OWNED` vira `AlreadyOwned`.
   Tratá-los como falha faz o app dizer *"não foi possível concluir a compra"* a quem acabou de
   pagar — e, no segundo caso, a quem **já pagou antes** e continua sem acesso. `AlreadyOwned` pede
   conciliação, não uma segunda cobrança. Já `ALREADY_OWNED_BY_OTHER_USER` **continua sendo falha**:
   a compra é de outra conta de loja, restaurar não resolve, e sugerir "restaurar compras" ali só
   produz a segunda frustração.
6. **Produto de ASSINATURA passado para a venda avulsa é descartado, não vendido.** `getProducts`
   devolve os dois tipos na mesma lista; comprar uma assinatura por este caminho criaria **cobrança
   recorrente** enquanto o app acha que vendeu acesso vitalício. O filtro é
   `ProductCategory.NON_SUBSCRIPTION`, e o produto descartado reaparece em `missingProductIds` — que
   é onde a fábrica enxerga catálogo mal configurado, em vez de descobrir pelo extrato do cliente.
7. **`restoreItems()` traz também a assinatura, numa chamada só.** A loja abre um diálogo do sistema
   a cada restauração; chamar `restoreItems()` e `restorePurchases()` em sequência pede a senha da
   Apple duas vezes. E ele é **só a partir de um toque do usuário** — a documentação do fornecedor é
   explícita. Para conciliar sozinho na abertura do app existe `ownedItems()`, que lê o
   `customerInfo` em cache e não incomoda ninguém.

### O que a compra avulsa exige do app, e a lib avisa em vez de calar

**Identifique o comprador antes de vender** (`identify(appUserId)`). Compra única feita com app user
**anônimo** não volta: a documentação do fornecedor diz, com todas as letras, que consumível e
não-renovável só se restauram com App User ID próprio. `purchaseItem` loga aviso alto quando o
sujeito é anônimo (não recusa a venda — recusar seria pior), e `StorePurchaseClaim.isAnonymousAppUser`
carrega o sinal até o servidor. Id em branco conta como anônimo: as duas situações têm exatamente a
mesma consequência, e distingui-las só produziria um claim que **parece** conciliável e não é.

### O que FICOU, e está aqui em vez de escondido

- **Google Play Billing 8 não consulta mais compra CONSUMIDA** (vale de `purchases-kmp` 2.0.0 em
  diante, que é a nossa). Não afeta o não-consumível desta versão — ele nunca é consumido. Sobre o
  `purchaseConsumable` legado (pay-per-action, hoje só o Meu Advogado, que roda com app user
  anônimo) o impacto foi **conferido no código e é menor do que parece**: ali o que a compra libera
  é gravado no **nosso** backend, atrelado à conta do usuário, e não há restauração de consumível no
  fluxo. Registrado como `GAP-RA-M-10` (P2), com a mitigação que o fornecedor recomenda.
- **`syncPurchases` não foi exposto.** Ele serve para migrar recibos anteriores à integração com o
  fornecedor, e traz risco documentado de *aliasing* de usuário anônimo. Nenhum produto nosso precisa
  disso hoje; `ownedItems()` cobre a conciliação silenciosa. Registrado como `GAP-RA-M-11`.
- **`entitlementVerificationMode` não é configurável pela lib.** O SDK recente já vem com *Trusted
  Entitlements* ligado em modo informativo, que é o que queremos: a lib **expõe** o resultado
  (`StoreVerification`) e o alerta (`VerificacaoDeCompraFalhou`); expor o modo só permitiria
  desligá-lo. Registrado como `GAP-RA-M-12`.
- **Os alvos iOS não compilam em Linux** (guarda de host, 2.69.0). Todo o código desta versão é
  `commonMain` — não há uma linha de `iosMain` nova —, e os símbolos usados (`ProductCategory`,
  `VerificationResult`, `nonSubscriptionTransactions`) são do `purchases-kmp-models`, multiplataforma.
  Revisado, não compilado, como nas rodadas anteriores.
- **`PaymentAlertKind` ganhou duas entradas.** Auditado: os 11 consumidores no monorepo apenas
  **constroem** o enum (nenhum `when` exaustivo sobre ele), então ninguém quebra.

### Testado

Suíte inteira: **2.534 testes, 0 falhas** (`testDebugUnitTest` em todos os módulos; XML conferido, não
só o "BUILD SUCCESSFUL"). Desses, **37 são novos**: `StoreItemsOutcomeTest` (8 — inclusive a ordem do
nosso catálogo vencendo a da loja, e `Available` vazio proibido pelo tipo), `ItemPurchaseResultTest`
(6 — com guarda de exaustividade sobre `PurchaseErrorCode`, para código novo no enum não cair em
`Failed` sem decisão consciente), `StorePurchaseClaimTest` (6), `ItemSaleApiTest` (5 — coexistência:
repositório só de assinatura responde "não vendo avulso" sem fingir sucesso),
`ItemSaleAlertKindTest` (1) e `FakePurchaseRepositoryItemTest` (11 — inclusive o celular novo com
seis cursos). `assembleDebug` verde em todos os módulos.

### Dublê de loja (`kmplib-testing`)

`FakePurchaseRepository` ganhou os cenários da venda avulsa, com o mesmo critério dos de assinatura
(construtor nomeado que diz **qual estado do mundo** ele reproduz, não flag booleana):
`compraDeItemQueDaCerto`, `compraDeItemQueTermina` (para `Pending` e `Failed`), `itensJaComprados`
(o celular novo), `catalogoDeItensQueFalha`, mais `itemDaLoja`/`itemComprado` e o registro
`itensComprados`. **Dublê de assinatura não passa a vender avulso sozinho**: sem catálogo de itens
ele responde `Unavailable`, o mesmo que um build sem billing.

## 2.191.0 — a aula se BAIXA para assistir sem internet (`video.download`) e o `ProgressRing`

Os dois itens seguintes do desenho do **Raquete Alta**, `GAP-RA-M-03` e `GAP-RA-M-04` do
`docs/backlog.md`. O primeiro é fundação pesada: download offline é **item obrigatório** desse
mercado (Hotmart, Kiwify, Teachable e o concorrente direto têm), e não havia nada na lib — o
`BlobStore` guarda bytes, não gerencia fila, retomada nem HLS.

### `video.download` — `MediaDownloadManager`

Mora **dentro do `kmplib-video`**, e não num módulo novo, por uma razão técnica e não de
arrumação: **tocar o que foi baixado exige o mesmo cache de quem baixou**. No Android o player lê
os segmentos pelo `CacheDataSource` sobre o `SimpleCache` que o `DownloadManager` escreveu; separar
os dois em artefatos diferentes obrigaria um a expor o cache do outro, ou faria o app baixar num
lugar e o player procurar noutro.

```kotlin
val downloads = createMediaDownloadManager(onRenewUrl = { vm.novaUrlAssinada(it.id) })

downloads.enqueue(
    MediaDownloadRequest(
        id = aula.id,                 // a CHAVE — nunca a URL, que é assinada e muda
        url = aula.hlsUrl,
        title = aula.titulo,
        groupId = curso.id,           // removeGroup(curso.id) quando o direito cai
        estimatedBytes = aula.bytes,  // confere o espaço ANTES de começar
        expiresAtMillis = acesso.venceEm,
    ),
)

// Tocar o baixado é o MESMO VideoPlayer, mudando só a fonte:
val media = downloads.offlineMediaFor(aula.id) ?: VideoMedia(url = aula.hlsUrl)
val player = rememberVideoPlayerState(media)
```

**Android: `DownloadManager` + `DownloadService` + `DownloadHelper` do Media3. iOS:
`AVAssetDownloadTask` (HLS) e `NSURLSessionDownloadTask` de fundo (progressivo).** É o caminho
oficial das duas plataformas, e o único que baixa **HLS como HLS** — o `.m3u8` é um índice de
centenas de segmentos, e guardá-lo como arquivo solto produz 2 KB de texto que não tocam em lugar
nenhum.

O que entrou: enfileirar, pausar, retomar, cancelar · **retomada que sobrevive ao app ser fechado**
· progresso por item (bytes, percentual, estado) · escolha de faixa de qualidade
(`MediaDownloadQuality`) · restrição a Wi-Fi · conferência de espaço **antes** de começar · apagar
item a item, por curso e tudo · espaço ocupado (`storageUsage`) · expiração do direito de acesso ·
renovação silenciosa da URL assinada.

**Seis decisões que valem registro:**

1. **A chave é o `id` do app, nunca a URL.** Aula de curso vem por URL assinada de curta duração, e
   ela muda a cada abertura. No Android isso vira o `customCacheKey` do `DownloadRequest`: **sem
   ele, renovar o token faz o download recomeçar do zero e ocupar o dobro do disco**, porque os
   segmentos já baixados ficam endereçados por uma URI que ninguém mais pede. Com a chave fixa,
   trocar a URL é trocar só *por onde buscar o que falta*.
2. **URL vencida no meio da transferência é caso NORMAL, não erro.** Um download de aula leva
   minutos ou dezenas de minutos; um token de 15 minutos vence no meio, sempre. `onRenewUrl` é
   chamado quando a falha é `Expired` (401/403/410) e o item **retoma de onde parou** — mesmo
   padrão que o player adotou na 2.190.0, com o mesmo teto de duas renovações por item (sem teto,
   servidor que devolve sempre a mesma URL vencida põe a fila num laço que **não trava nada** e por
   isso ninguém percebe até a conta do CDN chegar).
3. **Reserva de 300 MB que a lib nunca ocupa.** `checkMediaDownloadSpace` não pergunta "cabe?", e
   sim "cabe sobrando a reserva?". Abaixo de ~300 MB o Android dispara `DEVICE_STORAGE_LOW`, recusa
   instalar app e começa a apagar o `cacheDir` de todo mundo: encher o celular do aluno com aulas e
   deixá-lo nesse estado é pior do que não baixar a aula. Tamanho desconhecido **não** recusa o
   download (recusar impediria de baixar de qualquer servidor sem `Content-Length`) — falha no meio
   com `NoSpace`, e é por isso que mandar `estimatedBytes` vale.
4. **`NoOpCacheEvictor`: nada é despejado por conta própria.** Um evictor por tamanho apagaria a
   aula que o aluno baixou para o voo porque outra entrou depois — e a tela de Downloads continuaria
   listando as duas. Quem apaga é o usuário ou a regra do app.
5. **Na reprodução, o cache é SOMENTE LEITURA.** `setCacheWriteDataSinkFactory(null)`. Sem isso,
   tudo o que o aluno assiste **em streaming** é gravado no cache de downloads — que nunca esvazia
   (decisão 4). O app ocuparia 12 GB enquanto a tela de Downloads mostra "1,4 GB", e ninguém
   entenderia por quê.
6. **Expiração do direito não é DRM, e a lib não finge que é.** `expiresAtMillis` +
   `purgeExpired()` + `removeGroup(cursoId)` são o mecanismo; a decisão é do app. O relógio é o do
   aparelho, então atrasar a data do celular passa — está escrito no KDoc. A trava de verdade é o
   servidor não renovar a URL; esta é a metade que funciona **sem rede**, que é justamente quando o
   servidor não pode ser consultado. Item expirado vira estado próprio (`Expired`, que vence até
   `Completed`) em vez de sumir da lista: quem perdeu o acesso precisa ver por que a aula não abre.

**O manifesto vem no módulo.** `kmplib-video` passou a ter `AndroidManifest.xml` com o `<service>`
do download, o `JobService` do `PlatformScheduler` (que o AAR da Media3 **não** declara) e as
permissões de primeiro plano. É a regra da 2.175.0 aplicada de novo: sem isso, quem declara
`kmplib-video` direto teria um download que enfileira e **para de andar** assim que o usuário troca
de tela — sem erro nenhum no build.

**O que FICOU, e está aqui em vez de escondido:**
- **iOS — renovar a URL de um HLS parcial pode recomeçar o item.** A retomada de HLS na Apple é
  abrir o pacote `.movpkg` parcial como `AVURLAsset` local, e a URL remota vive **dentro** dele: não
  há API pública para substituí-la. Enquanto o token valer, a retomada é literal; quando ele vencer
  e o pacote parcial não puder mais ser usado, o item recomeça. A saída correta é do lado do
  servidor — assinar a URL de **download** com validade longa (é o que as plataformas do mercado
  fazem). Registrado como `GAP-RA-M-09`.
- **iOS — "aguardando Wi-Fi" não existe na tela.** O `NSURLSessionTask` fica parado sem dizer que
  está esperando rede; `waitingForNetwork` é sempre `false` lá. Inventar a frase seria pior que
  omiti-la.
- **iOS — trocar "só no Wi-Fi" vale do próximo item em diante.** `allowsCellularAccess` é fixado na
  criação da `NSURLSession`, e recriá-la derruba o que está baixando.
- Os alvos **iOS não compilam em Linux** (guarda de host, 2.69.0): o código iOS está revisado, não
  compilado — mesma regra dos 9 geradores de PDF e do player da 2.190.0.

### `ProgressRing` — o anel de progresso (`kmplib-ui`)

44dp, traço de 4, começa às 12h, sentido horário, número no meio e ✓ ao chegar a 100%. É o par
circular do `AppProgressBar`, e lê a **mesma** grandeza `0f..1f` pela **mesma** `normalizeProgress`
— senão a barra e o anel da mesma tela desenhariam a mesma fração de formas diferentes.

Três detalhes que só aparecem em uso:

- **O rótulo não mente.** Arredonda para **baixo**: 99,6% mostra `99`, e não `100` com uma aula
  faltando — a reclamação clássica de plataforma de curso, a que faz o aluno procurar um
  certificado que não existe. E fração maior que zero nunca mostra `0`.
- **Quem começou aparece.** 1% daria 3,6° — menos que a ponta arredondada do traço, e o anel
  ficaria idêntico ao de quem não começou. Há um piso de 6°.
- **A animação de entrada é OPT-IN** (`animateOnAppear = false`). Animar de zero na primeira
  composição é bonito num cabeçalho e péssimo numa `LazyColumn`: o item recomposto ao voltar
  rolando reanima, e a lista inteira "respira" a cada rolagem.

O tamanho do número sai do diâmetro **em densidade, não em `sp`**: o anel é fixo, e um número que
cresce com a escala de fonte do aparelho transborda o círculo em vez de ajudar — quem usa leitor de
tela recebe o percentual pela semântica (`ProgressBarRangeInfo`), que é o canal certo.

⚠️ **Não é o `CircularProgressIndicator`** (indicador de atividade) **nem o `ScoreRing` da weblib**
(gráfico de pontuação, outra forma). O KDoc traz a tabela de qual usar quando — é o caso clássico de
escolher componente pelo **papel** e herdar uma **forma** que ninguém aprovou.

### Cobertura
40 testes novos: 31 no `kmplib-video` (a máquina de estados da fila com a ordem de precedência,
percentual que nunca chega a 100 antes da hora, seleção de faixa por qualidade, reserva de espaço,
expiração no instante exato, o que volta baixando depois de o app fechar, teto de renovação, o
registro durável e o JSON ilegível que não derruba o app) e 9 no `kmplib-ui` (arco, rótulo, marco de
100% e a normalização compartilhada com a barra). Suíte inteira: **2.497 testes, 0 falhas.**

## 2.190.0 — a lib passa a TOCAR vídeo e a LER PDF (`kmplib-video`, `pdf.viewer`)

Dois buracos de fundação que apareceram juntos no desenho do **Raquete Alta** (plataforma de cursos
em vídeo) e que estavam registrados como `GAP-RA-M-01` e `GAP-RA-M-02` no `docs/backlog.md`. Nenhum
dos dois tinha "quase isso" na lib: de vídeo não havia nada além do embed de YouTube, e de PDF havia
só **geração**.

### `kmplib-video` — módulo novo, o 22º artefato

**Media3/ExoPlayer no Android, AVPlayer/AVFoundation no iOS.** Não é escolha de conveniência: o
`VideoView`+`MediaPlayer` da plataforma (que é o que o `KmplibVideoActivity` do `kmplib-ui` usa para
"abrir um mp4 solto") **não toca HLS de forma confiável, não tem velocidade de reprodução, não
seleciona faixa de legenda e não tem `MediaSession`**. Os quatro são requisito do produto.

```kotlin
val player = rememberVideoPlayerState(
    media = VideoMedia(url = aula.hlsUrl, title = aula.titulo, startPositionMillis = aula.retomarEm),
    onPosition = { pos, _ -> vm.salvarProgresso(pos) },
    onRenewUrl = { vm.novaUrlAssinada(aula.id) },   // o token venceu na pausa
)

VideoPlayer(player, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
    Text(aluno.email, color = Color.White.copy(alpha = .35f), modifier = Modifier.align(Alignment.TopEnd))
}
```

O que entrou: HLS (`.m3u8`) e MP4 progressivo · play/pause · barra arrastável · tempo decorrido/total
· **as seis velocidades** (0,5× … 2×) · ±10 s · tela cheia (a **decisão** é do app — a lib não gira a
Activity de quem a embutiu) · legenda ligando/desligando · **retomada** e **callback de posição** ·
seis estados exclusivos (`Idle`/`Loading`/`Buffering`/`Playing`/`Paused`/`Ended`/`Error`) · pausa ao
sair do primeiro plano, liberação ao sair da tela e tela acesa só enquanto toca.

**Cinco decisões que valem registro:**

1. **Módulo separado, e não dentro do `kmplib-ui`.** O Media3 é dependência pesada; no `kmplib-ui`
   ela cairia nos ~25 apps do portfólio, inclusive nos que nunca tocam um vídeo — exatamente o que a
   modularização da 2.163.0 existe para evitar. O embed de YouTube (`ui.components.video`:
   `VideoLauncher`, `VideoPlayerInline`) **fica onde está e não muda**: são coisas diferentes, e a
   do YouTube tem de ser WebView por exigência do próprio Google (IFrame Player API).
2. **Controles em Compose, superfície nativa.** A `PlayerView` entra com `useController = false` e a
   AVKit fica de fora (usamos `AVPlayerLayer`). Os controles nativos são views/UIKit e ficariam **por
   cima de qualquer sobreposição do app** — inclusive da marca d'água. E os dois não têm os mesmos
   botões: um curso que parece um app diferente em cada aparelho é o que isto evita.
3. **A marca d'água é um SLOT, não um parâmetro.** `VideoPlayer(…) { … }` recebe um `BoxScope` que
   fica entre o vídeo e os controles, e não recebe toque. Antipirataria é regra de produto: nenhum
   produto quer a moldura que a lib escolheria.
4. **Legenda EXTERNA é interpretada em `commonMain`.** A embutida no HLS é da plataforma (ExoPlayer e
   AVPlayer a acham e a desenham). Para arquivo `.vtt`/`.srt` lateral as duas divergem: o ExoPlayer
   aceita, e o iOS **não tem API pública** para acrescentar faixa a um HLS remoto (o caminho oficial
   é um `AVAssetResourceLoaderDelegate` reescrevendo o manifesto — um subsistema, não um parâmetro).
   Deixar cada plataforma no seu caminho daria legenda no Android e nenhuma no iOS, **com build verde
   nos dois**. Agora o arquivo é lido uma vez (`parseSubtitles`, WebVTT e SRT) e a fala é desenhada
   sobre o vídeo nas duas plataformas — idêntico, determinístico e coberto por teste.
5. **Sem `headers` na API.** Cabeçalho HTTP customizado numa `AVURLAsset` remota não tem API pública
   no iOS. Aceitá-lo faria a lib prometer no Android o que ignoraria em silêncio no iOS — o mesmo que
   o `SoundEffectPlayer` recusou fazer com "volume por disparo". **Assine a URL**, que é como o
   produto protege o vídeo de qualquer forma.

**A renovação da URL assinada é da lib, não do app.** `onRenewUrl` é chamado quando o erro é
`VideoErrorKind.Expired` (401/403/410) e o player recarrega **na posição em que estava** — o aluno
que pausou, atendeu o telefone e voltou vinte minutos depois vê um instante de espera, não uma tela
de erro. Teto de duas tentativas por mídia: sem ele, servidor que devolve sempre a mesma URL vencida
põe o player num laço de recarga que **não trava a tela** e por isso ninguém percebe.

**O que FICOU, e está aqui em vez de escondido:**
- **Android — notificação de mídia persistente e reprodução com o app fechado.** A `MediaSession` do
  Media3 está criada e ativa (metadados na central de mídia, comandos de fone/Bluetooth/tela de
  bloqueio, `handleAudioBecomingNoisy`). O que falta é a **notificação persistente** e o segundo
  plano de verdade, que exigem um `MediaSessionService` em primeiro plano com o player morando no
  serviço e a tela falando por `MediaController` — outra arquitetura, não um parâmetro. A API pública
  já está desenhada para recebê-la sem quebrar ninguém (`VideoBackgroundBehavior.ContinueAudio`).
- **iOS — completo:** `MPNowPlayingInfoCenter` + `MPRemoteCommandCenter` (play/pause/±10 s) e
  `AVAudioSession` em `.playback`, que é o que faz o áudio sair com o silencioso ligado.
- **Picture-in-Picture** não entrou em nenhuma das duas. Registrado no `docs/backlog.md`.
- Os alvos **iOS não compilam em Linux** (guarda de host, 2.69.0): o código iOS está revisado, não
  compilado. Vale a mesma regra dos 9 geradores de PDF.

### `pdf.viewer` — o módulo `pdf` passa a LER, não só a gerar

`PdfViewer` + `rememberPdfViewerState` + `PdfSource` (`Bytes`/`LocalFile`/`Url`). **Android:
`android.graphics.pdf.PdfRenderer`** (API da plataforma, sem dependência de terceiro — a mesma que o
`PdfRasterizer` já usava); **iOS: PDFKit (`PDFView`)**. Rolagem contínua, pinça, "página X de Y",
estados de carregando e erro, e `onPageChange` para o app guardar onde o aluno parou.

**Três decisões:**

1. **O zoom NÃO é um `graphicsLayer` por cima da lista.** O `ZoomableBox` do `kmplib-ui` é o
   componente certo para uma FOTO e, por isso mesmo, **consome o arrasto de um dedo quando
   ampliado** — dentro de um documento isso prenderia o leitor na página em que ele ampliou. Aqui a
   pinça muda a **escala de rasterização** (texto continua nítido, em vez de virar imagem esticada),
   a vertical continua sendo da lista e a horizontal é do `horizontalScroll`. A pinça só captura
   gesto de dois dedos.
2. **A chave do cache ignora a QUERY.** Material de curso vem por URL assinada
   (`…/apostila.pdf?token=…`), e o token muda a cada abertura: com a URL inteira na chave o cache
   erraria **sempre**, e sem sinal o material simplesmente não abriria. `pdfCacheIdFor` usa o
   caminho.
3. **Resposta que não começa com `%PDF-` NÃO entra no cache.** Um download que falha nem sempre
   falha: o CDN devolve `200` com página de erro, o portal cativo do wi-fi de hotel responde no lugar
   do arquivo, o token recusado vira JSON. Guardar isso com o nome do arquivo faria o documento **não
   abrir nunca mais**, nem com rede boa — um bug de cache que só some desinstalando o app.

O download reusa o `createHttpClient` do `kmplib-core` (log de requisição e gzip por default) e o
armazenamento é o `BlobStore`, em diretório próprio (`kmplib_pdf_cache`). Ele é **durável**, não
purgável: material de curso é o que o aluno quer achar sem sinal, e o `cacheDir` do Android é apagado
justamente quando o aparelho está cheio. A contrapartida é que a faxina é do app —
`createPdfCache().ids()/delete()/totalBytes()`.

⚠️ **O módulo `kmplib-pdf` passou a aplicar o Compose** (`kmplib.module.compose`). Aditivo para quem
o consome; quem só gera PDF não precisa mudar nada.

### Cobertura
48 testes novos no `kmplib-video` (relógio, as seis velocidades, classificação de HLS com URL
assinada, máquina de estados, mapeamento de HTTP para tipo de falha, leitor de WebVTT/SRT com busca
binária, mescla e preferência de faixa) e 20 no `kmplib-pdf` (chave de cache, assinatura de arquivo,
caminho remoto com `MockEngine`: cache-primeiro, 404, 5xx e HTML com 200). Suíte inteira: **2.457
testes, 0 falhas**.

## 2.189.0 — o recuo do `AppCheckbox` era 10dp, não 14dp

Correção do número que a 2.188.0 introduziu. A caixa ficou **acima** do começo do rótulo, e o motivo
é uma confusão que vale registrar: o `Checkbox` do Material desenha o seu *state layer* em **40dp**
(`CheckboxTokens.StateLayerSize`), mas o `minimumInteractiveComponentSize()` reserva **48dp** de
**alvo de toque**. Eu usei os 48 como se fossem a altura do desenho, e por isso empurrei o texto 4dp
a mais do que devia.

Quem manda no alinhamento óptico é o tamanho **desenhado**: quadrado no centro dos 40dp = 20dp do
topo, primeira linha de `bodyMedium` com o centro em ~10dp, recuo = 10dp.

Vale para qualquer alinhamento de ícone com texto: o alvo tocável de um componente Material costuma
ser maior que ele, e alinhar pelo alvo desloca o desenho.

## 2.188.0 — a caixa do consentimento alinha pelo começo do texto, e chip que não cabe DESCE

Duas correções de formulário, as duas no componente — que é onde elas somem de vez, em vez de serem
lembradas tela a tela.

### `AppCheckbox` alinha pela PRIMEIRA LINHA do rótulo

Era `Alignment.CenterVertically`. Com rótulo de uma linha ninguém nota; com o texto de um
consentimento LGPD — que tem parágrafo, não três palavras — a caixa descia para o **meio** do bloco,
longe do começo da frase que ela governa, parecendo pertencer à linha que estava ao lado dela.

Agora é `Alignment.Top` com **14dp** de folga no texto, e o número não é chute: o `Checkbox` do
Material ocupa 48dp de alvo com o quadrado desenhado no centro (24dp), e a primeira linha de
`bodyMedium` (lineHeight 20sp) tem o centro em ~10dp. Os 14dp fazem os dois centros coincidirem —
então **rótulo de uma linha continua exatamente como estava**, e o de várias passa a começar junto
da caixa. Nenhum consumidor precisa mudar nada.

### `ChoiceChipGroup` — escolha única que quebra linha (componente novo)

Faltava na lib o terceiro comportamento possível quando a largura acaba. Os dois que existiam
falham de jeitos diferentes, e nenhum dos dois serve para um grupo de 4 opções com rótulo de duas
palavras:

| Componente | O que faz quando não cabe |
|---|---|
| `SegmentedControl` | **Comprime** — linha única, os rótulos se espremem até cortar. |
| `FilterChipRow` | **Esconde** — é `LazyRow`; o que passou da borda só aparece rolando, e ninguém rola o que não sabe que existe. |
| **`ChoiceChipGroup`** | **Desce** — `FlowRow`, as opções continuam todas visíveis, em duas linhas. |

Cada chip tem no mínimo 48dp de altura (o `FilterChip` puro nasce com 32dp) e o estado vai na
semântica, nunca só na cor.

O caso: "melhor horário para falar com você" no formulário de pedido do Crédito na Mão — quatro
opções, uma delas "Qualquer horário". Num telefone de 320dp o segmentado espremia os quatro rótulos
até cortar. Continua valendo `SegmentedControl` para 2–3 rótulos curtos (a linha única é mais
legível) e `AppPickerField` quando a lista passa de umas dezenas, porque aí a tela é de **procurar**,
não de comparar.

## 2.187.0 — o mesmo alerta para de virar DUAS issues (`fingerprint`)

`CrashReporter.captureMessage` ganha o parâmetro **`fingerprint: List<String>`** (aditivo, default
vazio = comportamento de antes), e o `PaymentAlertReporter` passa a mandar
`listOf("pagamento", kind.slug)` em todo alerta.

**O que estava acontecendo.** No Super 8, "PAGAMENTO: loja sem pacotes de assinatura" virou DUAS
issues no GlitchTip — #303 e #304 —, iguais em mensagem, tags, `tipo`, device e release, as duas
nascidas 19:05 de 21/ago/2026. Uma condição, duas issues: quem abre o painel conta errado e não
sabe se está olhando dois problemas ou um.

**Por que o cuidado que já existia não bastava.** O KDoc de `PaymentAlertKind` manda manter o
`titulo` FIXO justamente para não criar issue nova a cada ocorrência — e isso está certo, mas
resolve só o texto variável. Alerta é `captureMessage`: chega **sem stacktrace**, então o servidor
tem pouco com que agrupar, e dois eventos idênticos no mesmo instante criam dois grupos. O
fingerprint torna o agrupamento determinístico e a corrida deixa de importar.

**Como foi implementado, e por que não é contorno.** O `Scope` do `sentry-kotlin-multiplatform`
(0.13.0 — a ÚLTIMA publicada; conferido no Maven Central) expõe tag, contexto, user, level e
breadcrumb, e **não** expõe fingerprint. Quem expõe é o `SentryEvent`, e o único ponto comum a
Android e iOS em que se alcança o evento antes do envio é o **`beforeSend`** — a via oficial do
próprio Sentry para agrupamento. O valor viaja do `Scope` até lá numa tag reservada
(`_fingerprint`), que o `beforeSend` traduz e remove.

Guardar o valor numa variável do reporter seria mais direto e **errado**: o `beforeSend` roda fora
da chamada, e dois alertas simultâneos leriam o valor um do outro. Na tag, o dado viaja DENTRO do
evento e não há corrida possível.

O fingerprint **não** carrega `projeto` nem `detalhe`: cada app tem o seu DSN (a issue já nasce
separada por projeto) e `detalhe` muda a cada ocorrência — incluí-lo devolveria o problema que este
fingerprint existe para matar. Três testes travam isso.

Aditivo: quem não passa `fingerprint` continua exatamente como estava.

## 2.186.0 — o PDF de orçamento imprime quantidade fracionária

`kmplib-pdf` · aditivo — `OsPdfItem.quantityLabel: String? = null`.

`OsPdfItem.quantity` é `Int`, e `OsPdfData` é o modelo de **ordem de serviço, orçamento e recibo**.
Em orçamento de prestador a quantidade é fracionária o tempo todo — "2,5 h de mão de obra", "1,5 m
de cabo" —, e o consumidor não tinha para onde ir: arredondava, e o PDF saía com uma linha que
**não fecha**, `2 × R$ 100,00 = R$ 250,00`, num documento que vai para o cliente do prestador. O
total nunca esteve errado (ele vem de `subtotal`/`total`, em string); quem mentia era a coluna da
quantidade.

Com `quantityLabel`, quem tem quantidade decimal manda o rótulo pronto e o render usa
`quantityLabel ?: quantity.toString()` — Android e iOS, mesmo layout.

**É o ÚLTIMO parâmetro da `data class`, de propósito:** posto no meio, ele quebraria toda chamada
posicional já existente — inclusive a dos próprios testes da lib, que foi como o erro apareceu.

Quem não passa nada vê exatamente o mesmo PDF de antes. Origem: orçamento no app do Mirassol
Conectado (06/set/2026).

## 2.185.0 — `getJson` aceita cabeçalho por chamada

`kmplib-core` · aditivo — `DomainApiClient.getJson(path, headers = emptyMap())`.

Existe para a **capacidade de leitura sobre UM recurso**: o token que um produto sem conta entrega
ao criar um registro e que autoriza consultar aquele registro depois (Crédito na Mão, 06/set/2026).

Sem isso, o app só tinha a URL — e credencial em query entra no log de acesso de todo intermediário
(Cloudflare, Traefik), no histórico do navegador e no `Referer` de qualquer link que a tela abra. O
`Authorization` também não serve: não há sessão nem portador de identidade ali, e usá-lo faria
clientes e proxies tratarem o valor como credencial de sessão (cache, refresh, redirecionamento
entre hosts).

## 2.184.0 — onboarding: o slide não vaza pela borda, e o texto não salta entre páginas

`kmplib-ui` · **mudança de comportamento visual** do `OnboardingPager` (nenhuma assinatura mudou).

Os dois defeitos foram vistos no app rodando (Crédito na Mão, 06/set/2026) e são do componente —
não do app. Os dois passam por build verde e só aparecem para quem arrasta a tela.

### 1. `edgeToEdge` passa a ser `true` por default

O `contentPadding` do pager (24.dp compacto / 64.dp expandido) deixava um pedaço do slide seguinte à
mostra na borda, como dica de "arrasta para o lado". **Ninguém lê aquilo como dica**: com ilustração,
ícone grande ou cor de fundo — que é todo onboarding real —, a fatia vizinha aparece como um retalho
colado no canto, e a primeira tela do app parece quebrada. Quem quiser a dica de volta passa
`edgeToEdge = false` de propósito.

O único consumidor que já tinha visto o problema (Cidade Conectada) passava `true` explícito; o
argumento continua válido e nada muda para ele.

### 2. A arte e o título ficam no MESMO lugar em todos os slides

`OnboardingSlide` usava `Arrangement.Center`: a altura do bloco depende do comprimento do texto
daquela página, então o slide com descrição de 3 linhas desenha o ícone e o título mais acima que o
de 2. Ao arrastar de um para o outro, os dois deslizam na horizontal **e saltam na vertical**.

A disposição passa a ser por fração da altura — a arte centrada num bloco superior de 42%, o texto
começando sempre no mesmo Y e crescendo para baixo. O bloco de texto agora **rola**
(`verticalScroll`), que é o que faltava para a página mais longa não ter o fim cortado com fonte
grande do sistema em tela baixa.

## 2.183.0 — `AppTimeGridScheduler` aceita clique no BLOQUEIO: a ausência se desfaz na própria grade

`kmplib-ui` · aditivo — `onBlockClick: ((ScheduleBlock) -> Unit)? = null`.

Com o handler, a faixa hachurada vira alvo de toque (`clickable` + `Role.Button` na semântica, para
o leitor de tela parar de anunciá-la como decoração). Sem o handler, nada muda.

Par mobile do `onBlockClick` da weblib 0.173.0, e pelo mesmo motivo de produto: a grade já é onde se
LANÇA a indisponibilidade, mas a faixa resultante não respondia a nada — quem errava o dia só
desfazia numa tela de configuração que o profissional às vezes nem alcança (no Meu Barbeiro,
Horários exige `BUSINESS_HOURS_MANAGE`), e concluía que não dava para remover.

O `clickable` entra ANTES do `clearAndSetSemantics` de propósito: ao contrário, o `clear` apagaria o
papel de botão que ele anuncia.

## 2.182.0 — `DELETE` com corpo, para o segredo não viajar na URL

`kmplib-core` · aditivo, nenhuma assinatura existente mudou.

### `DomainApiClient.deleteJson(path, body)`

O cliente tinha `delete(path)` (descarta a resposta) e `deleteJson(path)` (devolve o corpo), e
**nenhum dos dois envia corpo**. A rota que apareceu no Tá Feito precisa disso: desregistrar o
aparelho no logout é `DELETE /dispositivos` com `{"token": "..."}`.

O token de push **é** a identidade do aparelho, e pôr um valor desses num segmento de caminho o
entrega ao log de acesso de todo intermediário — a regra da casa é que **toda requisição registra
método e URL** (`CLAUDE.md`, "todo projeto loga requisição"). O corpo não vai para o log; o caminho,
vai.

Sem esta variante o projeto escreve a chamada com o `HttpClient` cru e perde, de uma vez, as três
coisas que justificam o `DomainApiClient` existir: **401 → refresh + um retry**, **402 →
`DomainResult.Quota`** e **transporte que nunca lança**. Uma chamada fora do cliente é uma sessão
que expira sem renovar.

`DELETE` com corpo é permitido pela RFC 9110 §9.3.5 (sem semântica definida) — e é por isso que ele
mora numa **variante explícita**, não no `delete` de sempre.

Teste `DomainApiClientTest` (+1: corpo enviado, `Content-Type: application/json`, 204 vazio = sucesso).


## 2.181.0 — o erro de campo volta para dentro do campo (e mais seis buracos de tela)

Sete lacunas levantadas por um app real (Tá Feito) contra a 2.179.0, escritas na hora em que
apareceram. **Três faziam a própria lib violar uma regra inegociável da casa** — por isso o conserto
é aqui, não nas telas. Tudo **aditivo**: nenhum parâmetro novo é obrigatório e nenhum comportamento
atual mudou.

### 1. `AppDatePicker` ganhou `errorMessage` — `kmplib-ui`

"Erro de campo fica NO campo" é regra da constituição (19/ago/2026), e o `AppTextField`, o
`AppTextArea`, o `NumberField` e o `AppPickerField` já a cumpriam. **Só o campo de data ficava de
fora** — e "a data de fim não pode ser antes da de início" é erro de campo como qualquer outro. O
app teve de escrever um `CampoDeData.kt` só para isso.

Agora `errorMessage` faz o que faz nos irmãos: **borda e rótulo em `colorScheme.error`** (o `isError`
do Material), **a frase embaixo** (o mesmo `supportingText`) e **`error()` na semântica** — que é o
que o leitor de tela anuncia e o que dá ao `focusFirstInvalidField` da tela onde parar.

De quebra, as duas conversões de data do seletor viraram funções puras e testadas —
`dataParaMillisDoCalendario` / `millisDoCalendarioParaData`, ambas em **UTC de propósito**: o
`DatePickerState` do Material trabalha em UTC, e converter no fuso do aparelho é o que faz quem está
a oeste de Greenwich abrir o calendário no dia ANTERIOR ao que salvou.

### 2. `AppMultiSelect` ganhou `errorMessage` — `kmplib-ui`

Mesma regra, mesmo buraco: "marque ao menos um dia da semana" virava um `Text` vermelho solto
embaixo dos chips — sem nada ligando a frase ao grupo, sem o leitor de tela saber que o campo está
inválido, sem o foco ter onde parar depois de um envio recusado.

Com `errorMessage`: **rótulo em `colorScheme.error`**, **frase embaixo do grupo** e **`error()` na
semântica** do container. A "borda vermelha" aqui é uma **caixa em volta dos chips**, e **só existe
no estado de erro** — o Material não define estado de erro para `FilterChip`, e reservar o recuo da
caixa no estado normal empurraria o layout de todo formulário que já usa o componente.

### 3. `ImageGallery` ganhou `scrollable` — `kmplib-ui`

Ele era `LazyVerticalGrid` puro. Dentro de uma coluna com `verticalScroll` a grade lazy pede altura
infinita e **estoura em runtime** ("*Vertically scrollable component was measured with an infinity
maximum height constraints*"). **Não é preferência de layout, é queda de tela** — e aconteceu duas
vezes, em duas telas diferentes do mesmo app, cada uma contornando com `BoxWithConstraints` e uma
conta de célula × linhas na mão.

`scrollable = false` troca a grade lazy por `Column`/`Row`, que cresce até a altura do conteúdo e
deixa a rolagem com o pai. **O parâmetro é o mesmo do `TimelineList`, de propósito** — era o único
componente de lista da lib sem ele. A última linha incompleta mantém as células do tamanho das
demais (o vão vira `Spacer`, não célula esticada).

### 4. `TimelineItem` ganhou `badgeTone: StatusTone?` — `kmplib-ui`

`badgeColor` é um `Color`, e cor de tema só se lê dentro de um `@Composable`. Como quem monta a
lista é o **ViewModel**, o campo era **inutilizável exatamente onde a lista nasce**: "concluída **com
atraso**" não teve como ganhar tom próprio numa timeline cujo `status` é `Done` (verde), e o atraso
virou texto no subtítulo.

`badgeTone` diz o **significado** e a lib resolve a cor com `statusToneColor` — a mesma fonte do
`StatusBadge`. Tem **precedência** sobre `badgeColor`, que continua funcionando para quem já o usa
dentro da composição. O campo entrou no **fim** do construtor, para não trocar de lugar com
`indicatorColor` na cara de quem constrói o item por posição.

### 5. `formatTimeBrFromMillis(millis, timeZone)` — `kmplib-core`, `core/format`

A lib dava a data, a data com a hora e a hora a partir de hora+minuto — e **nada que desse só
"09:12" a partir de um instante**. Toda tela que escreve "concluído às" repetia as mesmas quatro
linhas. Mesma convenção dos irmãos do arquivo: `millis <= 0` devolve `"-"`, e **o fuso é decisão do
chamador** (quando a hora pertence a um lugar — a casa, a obra, a quadra —, passe o fuso de lá, senão
o app de quem viajou mostra a hora do destino para um fato que aconteceu em casa).

### 6. `AppButton` e `AppOutlinedButton` ganharam `icon: ImageVector?` — `kmplib-ui`

Ícone à esquerda do rótulo, decorativo (sem `contentDescription`: o rótulo do botão já diz a ação, e
repeti-la faz o leitor de tela anunciar duas vezes). É `ImageVector` porque é o tipo que a lib já usa
para ícone informado de fora (`leadingIcon` do `AppTextField`, `navigationIcon` do `AppTopBar`);
ícone de arquivo (`DrawableResource`) segue nos botões de marca fixa (`GoogleLoginButton`,
`AppleLoginButton`). O rótulo dos dois botões passou a sair de um único `AppButtonLabel` privado —
era a mesma decisão escrita duas vezes, e foi assim que o `textAlign` já divergiu uma vez.

### 7. `AppTopBar` ganhou `subtitle: String?` — `kmplib-ui`

Segunda linha da barra para o **contexto do que está aberto** (o período da viagem, o nome da casa, a
placa). Sem ela, esse dado descia para o topo do conteúdo — onde some ao rolar, justamente quando a
pessoa precisa lembrar de qual registro está vendo. Uma linha, elipse no fim; a cor sai do
`LocalContentColor` da própria barra com opacidade reduzida, e não de um token fixo que ficaria
ilegível numa barra colorida.

### De quebra: `ACCESS_NETWORK_STATE` passou a ser declarada pelo `kmplib-core`

O `PlatformConnectivityMonitor` chama `registerNetworkCallback`, `getActiveNetwork` e
`getNetworkCapabilities`, e o módulo **não tinha manifesto nenhum** — então quem consome
`kmplib-core` granularmente e não lembrou de declarar a permissão por conta própria levava
`SecurityException` em **runtime**, com build verde. É a mesma falha que a 2.175.0 consertou no
`kmplib-platform` (FileProvider, receivers), e a mesma regra: **elemento de manifesto mora no módulo
que faz a chamada**. Permissão de nível normal — sem prompt, sem declaração na Play. Era também o
que fazia o `lintDebug` do módulo reprovar com três `MissingPermission`.

### Não entrou (registrado no `docs/backlog.md`, não implementado)

`AppDateRangePicker`, lista reordenável com alça, `SectionedList`/`ListSectionHeader` e
`ImagePickerSource` no `rememberImagePickerLauncher` — falta 2º consumidor, e a régua da lib é essa.

### Consumidores

Nada a migrar: os sete são aditivos. Quem quiser tirar contorno do app: `AppDatePicker`/
`AppMultiSelect` (campo de erro escrito à mão), `ImageGallery` dentro de coluna rolável (a conta de
altura em `BoxWithConstraints` sai), `TimelineItem` (tom de badge vindo do ViewModel) e a hora
formatada à mão.


## 2.180.0 — modo discreto: o app some da multitarefa e volta pedindo a digital

Um app de gestão de terreiro no celular de um membro pode ser aberto por outra pessoa, aparecer no
seletor de apps recentes ou ser fotografado — e religião é dado pessoal **sensível** (LGPD art. 5º,
II) num país em que 76–80% dos terreiros sofreram racismo religioso nos últimos dois anos. O mesmo
vale, com outro nome, para o processo de um cliente, o prontuário de um paciente e o extrato de
alguém: **Meu Advogado, NeuroCoreX e Minha Arena têm a mesma necessidade**, e cada um ia errar de um
jeito diferente escrevendo `expect/actual` de janela dentro do app.

O modo discreto tem **duas metades independentes**, e as duas entram nesta versão.

**1. Sumir da multitarefa** — `kmplib-platform`, pacote `platform.privacy`:

- **`PrivacyScreen`** (`isSupported`, `isHidden`, `setHidden(Boolean)`) + **`getPrivacyScreen()`** —
  o caminho imperativo, para quem guarda a decisão numa preferência ("modo discreto" nos ajustes).
- **`HideFromRecents(enabled)`** — o composable, que **desliga sozinho** ao sair da composição.
  Aninhar é seguro: os pedidos são **contados por referência**, senão fechar a tela de detalhe
  desligaria a proteção que a lista, viva atrás dela, tinha pedido.
- **Android:** `FLAG_SECURE` na janela — a API oficial, e a única que o sistema respeita na hora de
  tirar a miniatura de recentes. ⚠️ **O mesmo flag bloqueia print e gravação de tela**; é efeito
  desejado aqui, mas o sistema não avisa o usuário (a captura só falha), então diga isso na sua tela
  de ajustes. O flag é **reaplicado no `kmpLibPlatformOnResume`**: sem isso, girar o aparelho
  derrubaria a proteção em silêncio, porque a janela nova nasce sem ele.
- **iOS:** desfoque (`UIVisualEffectView`) sobre as janelas em `applicationWillResignActive` — o
  instante exato em que o sistema tira a foto da multitarefa — removido em `didBecomeActive`. Não
  existe `FLAG_SECURE` no iOS, e o truque conhecido (embutir a tela na camada de um `UITextField`
  seguro) é uso indevido de detalhe interno do UIKit: fica de fora de propósito.

**2. Trancar ao voltar** — `kmplib-ui`, pacote `ui.security`:

- **`AppLockGate(enabled, graceMillis = 60_000, hideFromRecents, texts, mark, onUnlockFailed) { … }`**
  + **`AppLockTexts`**, mais um overload com `BiometricAuth` explícito (Koin, dublê de teste).
- **Nasce trancado** e tranca de novo quando o app volta do segundo plano depois da folga (default
  60 s — sair para copiar um código do SMS não pode pedir digital na volta). Usa **`ON_STOP`/
  `ON_START`**, e não `ON_PAUSE`/`ON_RESUME`: o próprio diálogo de biometria e a barra de
  notificações pausam a tela sem o app ter saído, e com `ON_PAUSE` o portão se trancaria por cima
  da própria digital.
- **Rotação não tranca; processo novo tranca.** O estado vive num objeto de processo, e é o único
  jeito de acertar os dois: `rememberSaveable` sobrevive à morte do processo (o app voltaria
  destravado depois de o sistema matá-lo) e `remember` puro morre na recriação da `Activity`
  (girar o aparelho trancaria na cara de quem não saiu de perto).
- **O conteúdo continua composto por baixo** de uma cobertura opaca que engole o toque no passe
  `Initial`. Tirar o conteúdo da composição perderia a pilha de navegação — a pessoa destravaria e
  cairia na tela inicial, como se o app tivesse esquecido onde ela estava.
- **A tela de bloqueio não explica nada:** só a marca (cadeado, ou a logo do app) e **Desbloquear**.
  Quem está com o aparelho na mão pode não ser o dono, e a frase "seus dados estão protegidos"
  entrega do que o app trata.

**`BiometricAuth` ganhou a trava de tela como alternativa** (aditivo, com corpo default na interface
— dublê de teste que já a implementa continua compilando):

- **`isDeviceSecured()`** — biometria cadastrada **ou** PIN/padrão/senha. `isAvailable()` responde só
  por biometria, e trancar um app por ela sozinha tranca para fora o dono de um aparelho sem digital.
- **`authenticate(title, subtitle, allowDeviceCredential, …)`** — Android:
  `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`, **exceto no Android 10 (API 29)**, onde essa combinação
  não é suportada pelo `androidx.biometric` e o `build()` lança — ali vai `BIOMETRIC_WEAK or
  DEVICE_CREDENTIAL`, aceitável porque não se assina nada com `CryptoObject`. E **sem**
  `setNegativeButtonText`, que com `DEVICE_CREDENTIAL` também faz o `build()` lançar. iOS:
  `LAPolicyDeviceOwnerAuthentication`, que é o que a Apple indica para trava de app.

Aparelho **sem biometria e sem trava de tela** abre: não há o que conferir, e insistir só entregaria
um app que não abre mais. A proteção da multitarefa continua valendo.

Origem: `GAP-PF-M-01` do design do **Ponto Firme** (P0 de fundação). Aditivo — nada a migrar.

## 2.179.0 — o ícone do WhatsApp mora aqui

Todo produto da fábrica tem botão de WhatsApp, e cada um resolvia o ícone do seu jeito: o Mirassol
Conectado desenhou o glifo oficial e deixou no próprio repositório, com um comentário dizendo
"isto deveria morar na kmplib"; o Backhand entregava um botão escrito **"Zap"**, que ninguém
reconhece de relance.

`Res.drawable.ic_whatsapp` — glifo monocromático, `viewBox` 24×24, **sem cor própria**: quem tinge é
o `Icon(tint = …)` da tela. Fica ao lado de `ic_google` e `ic_apple`, que já eram da lib pelo mesmo
motivo.

## 2.178.0 — o botão da Apple não aparece mais no Android

`Sign in with Apple` não existe no Android desde sempre (`AppleAuthProvider.android.kt` devolve erro
explícito), mas a regra de *não desenhar o botão* vivia só em KDoc: "o app esconde o botão no
Android". Pedir isso a cada tela de login do portfólio é pedir que 50 telas lembrem da mesma coisa —
e a primeira que esquecer entrega ao usuário de Android um botão que **só sabe dar erro**. Foi o que
aconteceu no Backhand, numa tela de login própria (protótipo): "Continuar com Apple" desenhado num
aparelho Android, com build verde, lint limpo e nenhum teste vermelho.

A regra passa a morar na lib, num lugar só:

- **`provedoresSociaisDaPlataforma`** — `{GOOGLE}` no Android, `{GOOGLE, APPLE}` no iOS.
- **`SocialProvider.disponivelNestaPlataforma`** — o atalho para a tela própria:
  `if (SocialProvider.APPLE.disponivelNestaPlataforma) { BotaoDaApple() }`.
- **`provedoresSociaisPara(plataforma)`** — a mesma regra como função pura, para o teste não
  depender do alvo em que roda.

**`LoginScreen` e `RegisterScreen` já aplicam sozinhas**: `AuthMethods(apple = true)` passou a
significar "ofereça a Apple **onde ela existe**", e não "desenhe o botão sempre". Quem usa as telas
prontas não precisa fazer nada — no Android o botão simplesmente deixa de ser desenhado, e no iOS
nada muda.

**Tela própria (projeto com protótipo) precisa consultar a constante** — é justamente ela que a lib
não alcança.

O modo `BACKEND` não abre exceção: negociar pelo navegador funcionaria no Android, mas o padrão de
mercado (e da fábrica) é não oferecer Apple ali, e um botão a mais custaria Services ID, domínio
verificado e chave `.p8` para atender ninguém. Projeto que precise pede nominalmente.

## 2.177.0 — dois arquivos com o mesmo nome no mesmo package, e o app não acha o componente

`RefreshableBox` saiu do `:kmplib-sync` para o `:kmplib-ui` na 2.176.0, mas o arquivo antigo ficou
lá com o **mesmo nome** e o **mesmo package** (`ui.components`), agora contendo `SyncRefreshBox`.

Kotlin gera a classe-fachada a partir do NOME DO ARQUIVO. Dois `RefreshableBox.kt` no mesmo package,
ainda que em módulos diferentes, produzem duas `br/com/codecacto/kmplib/ui/components/RefreshableBoxKt.class`
— e o umbrella traz os dois módulos juntos. No classpath do app uma esconde a outra, e o que aparece
é `Unresolved reference 'RefreshableBox'` **apontando para as telas do app**, sem uma palavra sobre
colisão. No NeuroCoreX foram 13 arquivos de uma vez, todos "errados" sem terem mudado.

O arquivo do `:kmplib-sync` passa a se chamar `SyncRefreshBox.kt`, como a função que ele declara.
Nada de API muda: `RefreshableBox` e `SyncRefreshBox` continuam onde estavam, com as mesmas
assinaturas. Quem consome não precisa fazer nada.

**Para quem for mexer:** enquanto os dois módulos compartilharem o package `ui.components`, o nome do
arquivo é parte do contrato. Renomear a função sem renomear o arquivo traz a colisão de volta.

## 2.176.0 — puxar para atualizar não devia custar o SQLDelight inteiro

`RefreshableBox` — o pull-to-refresh que **toda lista do ecossistema** usa — estava declarado no
package `br.com.codecacto.kmplib.ui.components`, mas o **arquivo** morava no módulo `kmplib-sync`.
O package dizia uma coisa, o Gradle dizia outra, e quem pagava era o app que não sincroniza: para
ter o gesto de puxar a lista, ele precisava declarar `kmplib-sync` e arrastar junto o SQLDelight, a
outbox e o motor de sincronização de que não usa nada.

Apareceu no bootstrap do **Backhand** (arquétipo B *online-only*, sem cache local — o dado de
negócio vive só no banco central). Ao declarar apenas os módulos que usa, o app deixou de compilar
por `Unresolved reference 'RefreshableBox'` — um componente de UI puro, sem uma linha de
sincronização dentro.

O arquivo foi para **`kmplib-ui`**, junto com `RefreshAction` e `resolveRefreshAction`.
`SyncRefreshBox` **fica no `sync`**, e ali é o lugar certo: ele depende do `RestCrudSyncEngine` para
drenar a outbox antes de reconciliar.

**Ninguém precisa mudar nada.** O package é o mesmo (nenhum import muda) e `kmplib-sync` já declara
`api(project(":kmplib-ui"))`, então quem hoje chega ao símbolo pelo módulo de sync continua
chegando. É aditivo para todos os consumidores — Minha Arena, Diária Certa e os demais seguem
compilando sem toque.

## 2.175.0 — o manifesto morava no umbrella, e quase ninguém consome o umbrella

Todo o `AndroidManifest.xml` da lib estava no módulo **umbrella** (`library`, a coordenada
`br.com.codecacto:kmplib`), que faz `api(project(...))` dos vinte módulos. Só que os apps **não
consomem o umbrella** — eles pedem os módulos granulares (`kmplib-ui`, `kmplib-core`,
`kmplib-push`). Para esses, o manifesto **nunca chegava**, e o preço eram três falhas de runtime com
build verde, lint limpo e nada no log de build:

- **sem o `<provider>`**, `FileProvider.getUriForFile` lança `IllegalArgumentException`: a câmera do
  `rememberImagePickerLauncher`, o `VideoPicker` e o `ShareHandler.shareFile` falham;
- **sem `NotificationReceiver`/`NotificationActionReceiver`**, o alarme dispara e a notificação
  nunca aparece — nem os botões de ação;
- **sem `BootCompletedReceiver`** (e a permissão `RECEIVE_BOOT_COMPLETED`), todo lembrete agendado
  morre no reboot, que é exatamente o que a 2.99.0 existia para consertar;
- **sem as duas Activities de vídeo**, o `VideoLauncher` devolve `ActivityNotFoundException`.

Cada elemento foi para o módulo que contém a **classe** ou faz a **chamada**:

| Elemento | Ia para o consumidor só via | Agora mora em |
|---|---|---|
| `<provider>` FileProvider + `res/xml/kmplib_file_paths.xml` | `kmplib` (umbrella) | **`kmplib-platform`** |
| `NotificationReceiver`, `NotificationActionReceiver`, `BootCompletedReceiver` | idem | **`kmplib-platform`** |
| `uses-permission RECEIVE_BOOT_COMPLETED` | idem | **`kmplib-platform`** |
| `KmplibVideoActivity`, `KmplibVideoCompactActivity` + `res/values/kmplib_video_styles.xml` | idem | **`kmplib-ui`** |

O umbrella continua entregando tudo, agora por herança das dependências — quem consome
`br.com.codecacto:kmplib` não perde nada e não precisa mudar linha nenhuma.

**A regra que fica:** elemento de manifesto mora no módulo dono da classe, **nunca** no umbrella.

### ⚠️ Se o app declara o PRÓPRIO FileProvider, o build passa a parar

Quem já declarava um `<provider>` na authority `${applicationId}.fileprovider` (era o jeito antigo,
antes de a lib passar a provê-lo) agora vê o manifest merger falhar:

```
Attribute meta-data#android.support.FILE_PROVIDER_PATHS@resource value=(@xml/file_paths)
  is also present at [:kmplib:kmplib-platform] value=(@xml/kmplib_file_paths).
  Suggestion: add tools:replace="android:resource" ...
```

**Não siga a sugestão do merger.** `tools:replace` faz o `file_paths.xml` do app **substituir** o da
lib, e aí câmera (`cache/photos`), vídeo (`cache/videos`) e compartilhamento (`cache/shared_files`)
passam a lançar `IllegalArgumentException` em runtime — o conflito some do build e reaparece na mão
do usuário.

O certo é o app **remover o provider dele** e gravar dentro de um dos caminhos que a lib já expõe
(`cacheDir/photos` para foto de câmera). Feito assim no FX Investimentos.

## 2.174.0 — o radar dizia a forma e escondia a grandeza

`RadarChart` ganhou `mostrarEscala` (default `false`): o valor de cada anel da grade, escrito subindo
do centro pelo eixo vertical.

Sem ele o polígono comunica **forma e proporção**, nunca **grandeza** — dois radares idênticos podem
ser 3,5 numa escala de 5 e 7 numa de 10, e nada na figura separa os dois. Fica no eixo de cima, e não
espalhado pelos anéis, porque o polígono cobre a área: uma coluna curta de números, sempre no mesmo
lugar, é lida sem competir com o desenho.

**O rótulo do anel virou função pura testada** (`rotuloDoAnel`), e ela tem uma decisão dentro:
escala longa (100) sai inteira, escala curta (5) ganha uma casa. Com 4 anéis numa escala de 5 cada
anel vale 1,25, e imprimir inteiro daria "1, 3, 4, 5" — uma progressão que mente sobre onde as
linhas da grade estão. **Meia casa arredonda para cima, explicitamente:** `kotlin.math.round` empata
para o par (1,25 → 1,2), que é o correto em estatística e o inesperado num rótulo de escala; e com
escala curta o empate não é caso de borda, é metade dos anéis.

Default `false` porque em miniatura os números competem com o desenho — quem sabe o tamanho da caixa
é o consumidor. Nenhum app existente muda.

Par do `showScale` da weblib 0.155.0. Pedido do parceiro do NeuroCoreX na auditoria do ICTC:
escala numérica no radar, no relatório **e** nas telas.

## 2.173.0 — "Feedback" e "Desenvolvido por" ganham rodapé, e com ele o banner

Duas telas que existem em praticamente **todo** app da fábrica (a constituição manda: "Desenvolvido
por CodeCacto" + Contato em todo produto) montavam `Scaffold` próprio e **não ofereciam onde pôr o
banner**. Num app cujo único modelo de receita é house ad, isso fechava **2 telas por app** — só na
categoria AdsOnly, ~112 telas sem anúncio por limitação da fundação, não por decisão de produto.

E não havia saída do lado do app: embrulhar a tela num `Scaffold` externo dá dois `Scaffold`
aninhados (o de dentro ignora o de fora e o conteúdo passa por baixo do rodapé), e copiar a tela
para o projeto joga fora a razão de ela morar na lib.

### O slot

`FeedbackScreen`, `DeveloperScreen` e `ContactScreen` passam a aceitar

```kotlin
bottomBar: @Composable () -> Unit = {}
```

repassado direto ao `Scaffold` interno:

```kotlin
FeedbackScreen(
    onBack = { navController.popBackStack() },
    bottomBar = { ManagedBannerAd(Modifier.fillMaxWidth(), size = BannerSize.STANDARD) },
)
```

**Aditivo, com default vazio, e o parâmetro entrou ANTES dos callbacks finais**
(`onFeedbackSent`/`onSent`) justamente para não roubar a posição de trailing lambda de quem já
chama. Os ~110 arquivos que hoje abrem essas telas no monorepo continuam compilando sem tocar em
nada.

### O `ContactScreen` recebe o rodapé da `DeveloperScreen`

O botão "Entrar em contato" **substitui** a tela inteira pelo formulário — sem repassar, o banner
sumia justamente ali. A `DeveloperScreen` repassa o seu `bottomBar` para ela.

### O conteúdo termina COLADO no topo do rodapé

O `Scaffold` desenha o `bottomBar` por cima da área de conteúdo: quem chama `fillMaxSize()` sem
consumir o `innerPadding` termina com o último item metade escondido atrás do banner. A altura da
barra **já vem** no `innerPadding`; a folga agora entra no **contêiner** das três telas (antes da
rolagem), e não como padding interno do scroll — a área rolável para no topo do banner em vez de
passar por baixo dele. A regra virou uma função só, `espacoAcimaDoRodape`, testada: ela **soma** com
o padding de leitura da tela, nunca substitui.

Dois defeitos menores caíram junto: a tela de sucesso do `ContactScreen` ("mensagem enviada") não
descontava rodapé nenhum — só o formulário descontava —, e o `DeveloperScreen` descontava por
dentro da rolagem.

## 2.172.0 — `BannerSize.SQUARE`, o terceiro tamanho, e a queda para o menor

### Banner quadrado (1:1)

Depois do grande, o pedido foi um **ainda maior**, para tela que sobra espaço — a "Sobre" de um app
deixa quase meia tela livre. No celular o banner ocupa a largura inteira, então "subir até ficar
quadrado" é literalmente **1:1**: numa tela de 360 dp, 360 dp de altura. Arte de **1440×1440**.

```kotlin
ManagedBannerAd(Modifier.fillMaxWidth(), size = BannerSize.SQUARE)
```

**Não** use numa tela de leitura contínua — ali ele come metade do conteúdo. O lugar dele é onde já
havia vazio: "Sobre", estado vazio, fim de fluxo.

### A queda agora é uma cadeia, e só desce

`SQUARE → LARGE → STANDARD`, `LARGE → STANDARD`. Descer é seguro porque a caixa segue a proporção da
arte que veio: sai um banner mais baixo, inteiro. **Subir seria pedir uma peça mais alta do que o
app reservou na tela** — por isso a cadeia nunca sobe.

Requer apps-api com `banner_square` (migração `V46__banner_quadrado.sql`) e o Nexus com os dois
slots novos.

## 2.171.0 — o banner sai de trás da barra de gestos, e para de sumir por falta de UMA variante

Dois acertos no house ad do rodapé, os dois vindos de encaixar a coisa de verdade no aparelho.

### A barra de navegação cobria o rodapé do criativo

Com `targetSdk` 35+ o Android desenha **edge-to-edge à força**, e o `bottomBar` do `Scaffold` **não
recebe inset sozinho** — quem está lá precisa consumir. O banner ficava por baixo da barra de
gestos: no criativo, some justamente a faixa de baixo, onde costuma estar o botão.

`CustomBannerAd` passa a aplicar `windowInsetsPadding(WindowInsets.navigationBars)`. O modificador
respeita o **consumo** de insets: no `bottomBar` ele aplica; no meio do conteúdo, onde o `Scaffold`
já consumiu, vira zero. O mesmo composable serve nos dois lugares, sem parâmetro novo.

### Pedir o grande não pode significar ficar sem anúncio

Até a 2.170.0, `size = BannerSize.LARGE` num pool sem arte 3:1 deixava o rodapé **vazio** — a
alternativa (esticar a 6:1) era pior. Agora a caixa segue a proporção **da arte que veio**
(`BannerSize.aspectRatioOf`), então cair para o banner comum não corta nada: dá só um banner mais
baixo. Um app cujo único ganho é o house ad não pode deixar de exibir por falta de uma variante.

`selectAd` continua **estrito** (quem pede `banner_large` recebe `banner_large` ou nada) — o
fallback é decisão explícita do composable, e é o que o mantém testável.

## 2.170.0 — a altura do banner sai da PROPORÇÃO da arte, e o criativo para de ser cortado

Achado ao encaixar a primeira arte 3:1 de verdade (1440×480, Piada Pronta): **altura fixa e arte de
proporção fixa brigam**, e quem perde é o criativo.

O `CustomBannerAd` desenha com `ContentScale.Crop` — ele **preenche** a caixa e corta o excedente.
Numa tela de 360 dp, a faixa 6:1 mede 60 dp de altura naturalmente; a 2.168.0 a forçou para 90 dp,
e o Crop passou a **cortar um terço da largura**. O que some é a borda da peça, justamente onde
costuma estar o nome do app. Com a 3:1 em 180 dp, o mesmo: 33% fora.

Agora a caixa acompanha a arte — `aspectRatio` em vez de `height`:

| | Arte | 360 dp de tela | 480 dp |
|---|---|---|---|
| `BannerSize.STANDARD` | 6:1 | 60 dp | 80 dp |
| `BannerSize.LARGE` | 3:1 | **120 dp** | **160 dp** |

O "dobro" que separa os dois passa a ser a **proporção**, não um número — e ele se mantém em telas
que não previmos, inclusive tablet e paisagem.

`height` / `customHeight` continuam existindo, agora anuláveis: informar uma volta ao comportamento
de altura fixa, para o layout que precisar disso. `AdDefaults.BANNER_HEIGHT` e
`BANNER_LARGE_HEIGHT` seguem publicados como esses valores fixos.

## 2.169.0 — banner GRANDE: um formato novo, com arte própria

### `BannerSize.LARGE` + `CustomAd.FORMAT_BANNER_LARGE`

A 2.168.0 subiu o banner de 60 para 90 dp e ele **continuou fino** — porque altura não era o
problema todo. A arte do banner é uma faixa **6:1** (1440×240): aumentar o espaço sem trocar a
imagem só a estica (`Fit` deixa sobra) ou come metade da mensagem (`Crop`). Um banner "mais grosso"
é **outro formato**, não um parâmetro de layout.

```kotlin
ManagedBannerAd(modifier = Modifier.fillMaxWidth(), size = BannerSize.LARGE)
```

- `BannerSize.STANDARD` — faixa 6:1, 90 dp (o de sempre; segue sendo o default).
- `BannerSize.LARGE` — faixa **3:1** (arte 1440×480), **180 dp**, o dobro.

Cada tamanho pede um **formato diferente** ao apps-api (`banner` / `banner_large`), e um anúncio só
entra no sorteio do grande se tiver a arte dele cadastrada no Nexus. **Sem arte grande, o app não
mostra banner** — em vez de cair na 6:1 deformada. É o comportamento que os testes travam.

A escolha é do app: tela de leitura contínua fica no padrão; app cujo house ad é a única receita
ganha visibilidade com o grande.

Requer **apps-api com o `banner_large`** (migração `V10__banner_grande.sql`, colunas
`banner_large_image_url`/`banner_large_web_image_url`) e o Nexus com os dois slots novos.

## 2.168.0 — o banner de rodapé cresceu, e a medida passou a morar num lugar só

### `AdDefaults.BANNER_HEIGHT` = **90 dp** (era 60)

O house ad usava 60 dp, o tamanho do banner clássico das redes de anúncio. Só que o criativo aqui é
**nosso** — não há formato imposto por ninguém —, e em 60 dp a arte chega inteira mas em miniatura:
o que deveria ser a chamada de um app da casa vira uma tarja no rodapé. Pedido do fundador, olhando
o Piada Pronta: *"a gente não consegue fazer um banner um pouco maior? Achei pequeno."*

90 dp é meio-termo: 50% mais alto, ainda **menor** que o *large banner* de 100 dp das redes, e sem
comer a tela num aparelho pequeno (num Android de 640 dp de altura, ocupa ~14%).

A medida saiu de dois defaults duplicados (`CustomBannerAd.height` e `ManagedBannerAd.customHeight`,
cada um com o seu `60.dp`) para **uma constante só**. Enquanto eram dois, mudar um e esquecer o
outro deixava o app com altura diferente conforme o composable escolhido — e os dois compilam.

**Todo app que usa o default cresce ao rebuildar**, sem tocar em código. Quem quiser outra altura
passa `customHeight`.

## 2.167.0 — o umbrella parou de gerar a classe `Res` que duplicava no dex do app

### `mergeLibDexDebug`: *"ActualResourceCollectorsKt is defined multiple times"*

Todo app que depende da kmplib deixou de montar APK. O erro não é de compilação — `compileKotlin`
passa —, e só aparece na `assembleDebug`/`assembleRelease`:

```
Type br.com.codecacto.kmplib.generated.resources.ActualResourceCollectorsKt$$…Lambda0
is defined multiple times: …/library/build/…, …/ui/build/…
```

Os recursos compartilhados (as 4 traduções, o logo, os ícones) moram no `:kmplib-ui`, que fixa o
pacote da classe gerada em `br.com.codecacto.kmplib.generated.resources`. Só que esse é
**exatamente** o pacote que o plugin `compose.resources` daria ao umbrella por conta própria
(group + nome do subprojeto `:kmplib`) — e o umbrella continuava gerando um `Res` **vazio** mais os
*ResourceCollectors* ao lado, porque no default `auto` basta aplicar o plugin do Compose e depender
de `components.resources`. Tirar o diretório `composeResources/` de lá, como foi feito quando os
recursos mudaram de módulo, não desligava nada.

Como o umbrella faz `api(project(":kmplib-ui"))`, os dois entram no classpath de todo consumidor e
o D8 acha a mesma classe duas vezes.

O umbrella passa a declarar `compose.resources { generateResClass = never }` — o mecanismo oficial
do plugin para um módulo sem recurso próprio. **Nada muda para o consumidor:** o `Res` do umbrella
era `internal` e vazio; quem usa recurso da lib já importa o do `:kmplib-ui`.

Provado com `:composeApp:assembleDebug` do Piadaria: falhava em `mergeLibDexDebug`, agora sobe.

## 2.166.0 — o "X" do intersticial ganhou id, e a captura de loja deixou de fotografar anúncio

### `AdsTestTags.BTN_FECHAR_INTERSTITIAL`

O intersticial de abertura dispara uma vez por sessão e cobre exatamente a primeira tela que a
vitrine precisa mostrar. E ele engana o flow de captura: a tela por baixo **continua na hierarquia**,
então o `extendedWaitUntil` passa e a foto sai com o anúncio na frente — print que a Apple recusa.

Fechar por texto ("Fechar", "X") não serve, porque a fábrica publica em quatro idiomas e o rótulo
muda. O `IconButton` de fechar do `CustomInterstitialAd` passa a expor
`ads-btn-fechar-interstitial`, e o mesmo flow de captura serve os 55 apps de publicidade.

Mudança **aditiva**: nenhum consumidor precisa mexer em nada, e quem não automatiza não percebe.
A tag vira `resource-id` pelo `testTagsAsResourceId` que o `AppTheme` já declara desde a 2.107.0.

## 2.165.0 — o `code` do erro chega na tela

### `DomainResult.Error` ganhou `serverCode`

O cliente de domínio classificava a resposta pelo **status** e jogava fora o corpo. O envelope de
erro da backlib (`{"message": …, "code": …, "traceId": …}`) nunca chegava ao app, e o `code` é
justamente o que distingue dois erros diferentes com o mesmo número.

O caso que forçou: no NeuroCoreX, `409` numa rota de resultado pode ser **"o resultado ainda está
sendo preparado por quem acompanha"** — que é um ESTADO, com cartão âmbar e sem botão de tentar de
novo — ou **"esta resposta já foi enviada"**, que é outra coisa inteira. Com o status sozinho, o app
ou adivinhava (acertando por acaso enquanto houvesse um 409 só naquela rota) ou refazia a chamada
para ler o corpo que este cliente tinha acabado de descartar.

```kotlin
when (val r = api.getJson("/v1/me/aplicacoes/$id/resumo")) {
    is DomainResult.Error -> when (r.serverCode) {
        "RESULTADO_EM_PREPARACAO" -> mostrarEmPreparacao()
        else -> mostrarErro(r.message)
    }
    …
}
```

**Aditivo e compatível:** `serverCode` tem default `null`, então `DomainResult.Error(code, message)`
continua compilando. É `null` quando o corpo não é JSON, não tem `code`, ou o erro é de transporte —
um proxy que devolve HTML em 502 não pode virar uma exceção dentro do tratamento do erro original.

Ler o corpo só acontece em resposta de ERRO; o `Success` continua entregando a resposta intacta.

## 2.164.0 — os campos que não abriam no iOS, e o DELETE que devolve o corpo

### ⚠️ `AppDropdownField`, `AppPickerField` e `AppDatePicker` estavam MORTOS ao toque no iOS

**No Compose Multiplatform iOS, `TextField(readOnly = true)` intercepta o toque** e o
`Box.clickable()` que envolve o campo nunca recebe o evento. Os três componentes são construídos
nesse desenho — campo somente-leitura servindo de vitrine, com o clique no container — então **o menu
não abria**, e só no iPhone: no Android o clique passava normalmente.

Corrigido com um `Box` transparente em `matchParentSize()` por cima do campo, capturando o toque.

**Quem está abaixo da 2.164.0 tem os três componentes mortos no iOS.** Todo app iOS que use spinner,
seletor ou calendário precisa subir — é o caso de qualquer formulário com escolha de item.

O sintoma engana e custa horas: o campo aparece desenhado normal, o toque não faz nada, e a
validação depois reclama do campo vazio. É **idêntico** a "a lista de opções está vazia", porque um
menu sem itens também abre com altura zero — foi por aí que a investigação no Cidade Conectada foi
parar no backend, no banco, no cache e na URL, todos certos o tempo todo. Duas provas separam os
casos em minutos: **funciona no Android e não no iOS ⇒ é a plataforma, não o dado**; e se um host de
configuração na raiz já montou a tela, a lista chegou.

Encontrado no cadastro de endereço do Mirassol Conectado (29/ago/2026).

### `DomainApiClient.deleteJson`: o DELETE que devolve o corpo

`delete(path)` descarta a resposta, e continua sendo o certo quando o servidor responde 204. Faltava a
variante para a API que responde **com o estado depois de apagar** — a lista já sem o item —, padrão
comum e o que evita uma segunda chamada só para a tela se atualizar. Sem ela, quem precisa do corpo
escreve o `execute` à mão dentro do projeto e perde o tratamento de token, quota e erro que mora no
cliente.

Aditivo: nada muda para quem já usa `delete`.

## 2.163.0 — a lib vira 21 módulos, e o umbrella segura os apps de pé (28/ago/2026)

**Reorganização estrutural. Nenhuma API mudou, nenhum pacote mudou de nome, nenhum app precisa ser
tocado.**

### O que motivou

O Cidade Conectada não conseguia fazer o `Archive` de iOS: `linkReleaseFrameworkIosArm64` morria com
`OutOfMemoryError` no `DevirtualizationAnalysis` num Mac de 16GB. A causa não era só o tamanho da
lib — era o `export(libs.kmplib)` no `build.gradle.kts` do app.

**Exportar uma dependência não é "deixá-la disponível": é declarar cada símbolo público dela no
header Obj-C e, com isso, torná-lo raiz do dead code elimination.** Nada abaixo de uma raiz pode ser
eliminado, e toda raiz entra no CallGraph que o `DevirtualizationAnalysis` percorre. Com a lib
inteira exportada, eram ~1.436 declarações de nível superior servindo de raiz — num app que fala com
a lib por **dois** objetos (`GoogleSignInBridge` e `ApplePushBridge`). Restringindo o `export` a
`kmplib-auth` e `kmplib-push`: ~96 raízes, **−93%**.

Modularizar é a outra metade: o app deixa de carregar o que não abre. No piloto, ficaram de fora
ads, astro, camera, pdf, media e voice — 112 arquivos, 16.187 linhas, −19%.

E há uma terceira peça, que não é da lib: **`org.gradle.jvmargs` não vale para o Kotlin/Native**. O
link do framework roda em processo próprio, com heap default, então os 8GB do Gradle daemon nunca
chegavam ao compilador que precisava deles. `kotlin.native.jvmArgs=-Xmx6g` no `gradle.properties` do
app.

### Os módulos

`kmplib-core` (core + validation) · `kmplib-mask` · `kmplib-ui` · `kmplib-platform` (+ permissions,
torch, signature, appupdate) · `kmplib-auth` (+ account) · `kmplib-firebase` · `kmplib-sync` ·
`kmplib-monetization` · `kmplib-central` (contact + developer + feedback) · `kmplib-observability` ·
`kmplib-push` · `kmplib-location` · `kmplib-map` · `kmplib-brdata` (+ pix) · `kmplib-qr` ·
`kmplib-camera` · `kmplib-pdf` · `kmplib-media` (+ voice) · `kmplib-ads` · `kmplib-astro`.

### O que NÃO mudou (de propósito)

- **`br.com.codecacto:kmplib` continua existindo**, agora como *umbrella*: dois arquivos e um
  `api()` para cada módulo. Quem tem `api(libs.kmplib)` no build não precisa fazer nada — nem hoje,
  nem depois. Não há fase de deprecação prevista.
- **Nenhum pacote foi renomeado.** `PaywallScreen` continua em
  `br.com.codecacto.kmplib.ui.screens.paywall` mesmo morando no módulo `monetization`; Kotlin não
  exige pasta igual a pacote. São 56 imports espalhados pelos apps que seguem válidos por causa
  disso.

### O que muda para quem adota os módulos

A inicialização deixa de ser uma função só. `KmpLib.init(context)` existe apenas no umbrella, porque
toca holders de todos os módulos — mantê-la disponível a um app modular o obrigaria a declarar a lib
inteira de volta só para compilar a própria `Application`. Cada módulo passa a ter o seu:
`initKmpLibCore`, `initKmpLibPlatform`, `initKmpLibAuth`, `initKmpLibSync`, `initKmpLibMedia`, mais
`kmpLibPlatformOnResume`/`OnPause` e `kmpLibAuthOnResume`/`OnPause` no lugar de
`KmpLib.setActivity`/`clearActivity`.

### Correções que a fronteira de módulo revelou

- **`currentPlatform` estava declarado duas vezes** — um `expect/actual` em `appupdate` e outro
  igual em `feedback`, cada um `internal` no seu pacote. Sobe para `core.util` como API pública.
- **Quatro contratos neutros desceram para o `kmplib-core`**: `IAuthRepository`, `User`,
  `QuotaExceeded`/`Entitlement` e `DomainApiClient`. Nenhum deles nomeia um tipo do Firebase, do
  RevenueCat ou do SQLDelight; estavam nos módulos das implementações por acidente de história, e
  faziam um app que só fazia login arrastar SQLDelight, Storage e o SDK de compras.
- **`FakeAppPreferences` e `FakeCrashReporter` viram `InMemoryAppPreferences` e
  `RecordingCrashReporter`**, em `commonMain`. Como dublês de `commonTest` eles só existiam para
  quem compilava o mesmo módulo, e já eram usados de fora; source set de teste não é publicado.
- **Cinco ciclos entre pacotes** que impediam a divisão: `UrlLauncherHolder` era o holder do
  `Context` (vira `core.context.AndroidAppContext`), `CentralServices` saiu de `core` para
  `central`, e `LatLng` desceu de `map` para `location` — `map.LatLng` segue como typealias.
- **Um teste flaky de verdade**: `NotificationActionTest` esperava em `runTest` (relógio virtual)
  por um evento entregue no `Dispatchers.Default` (thread real); o `withTimeout(5_000)` saltava os
  cinco segundos no mesmo instante. Falhava 1 em 4 execuções.

2.316 testes em 20 módulos, zero falhas.

## 2.162.0 — o cliente passa a PEDIR gzip (28/ago/2026)

**Correção de desempenho. Toca TODO app da fábrica** — o buraco era igual em todos, e ninguém o via.

O backend comprime; o app nunca pediu. Sem `Accept-Encoding` na requisição, o servidor responde em
texto cru — e o plugin que manda esse cabeçalho **não está no `ktor-client-core`**: mora no artefato
`ktor-client-encoding`, que nenhum projeto declarava. Ou seja, o portfólio inteiro baixava JSON sem
compressão porque a correção dependia de cada app lembrar de uma dependência a mais.

Medido no Cidade Conectada, em produção, rota a rota:

| rota | sem gzip | com gzip |
|---|---:|---:|
| `/v1/categories` | 26.847 B | 8.172 B |
| `/v1/feed?size=20` | 15.065 B | 4.815 B |
| `/v1/properties?size=6` | 9.308 B | 1.997 B |
| leque da tela Início (20 rotas) | ~108.000 B | ~34.000 B |

**−69% de todo o tráfego JSON do app**, sem tocar em uma linha de tela. Numa cidade em 4G isso é a
diferença entre a Início abrir e a Início "estar lenta".

`HttpClientOptions.installContentEncoding` (**default `true`**) instala `ContentEncoding` com `gzip()`
e `deflate()`. É default, e não opção, pelo mesmo motivo do log de requisição da 2.117.0: o que
depende de lembrar não acontece.

Detalhe que engana quem for medir: **OkHttp (Android) e NSURLSession (iOS) já pediam gzip sozinhos**
quando ninguém definia o cabeçalho, então parte do ganho pode já estar acontecendo nesses engines. O
plugin torna o comportamento **determinístico e independente do engine** (CIO/Js não fazem isso) e o
faz aparecer no log. Não há descompressão dupla: quando o cliente define o cabeçalho, as duas
plataformas param de descomprimir sozinhas; e quando descomprimem, removem o `Content-Encoding`, que
é justamente o que o plugin lê para decidir.

### Migração

Nenhuma. Quem consome por composite build já recebe; quem consome por artefato, no bump. App que
monta o próprio `HttpClient` em vez de usar o `createHttpClient` **não ganha nada** — é mais um
motivo para migrar para o factory.



## 2.161.0 — `isLoggedIn` para de emitir o mesmo `true` (28/ago/2026)

**Correção. Toca todo app que observa a sessão para recarregar tela** — e o efeito só aparece em uso
real, nunca no build.

`isLoggedIn` era `authStateChanged.map { it != null }` (e, no own-auth,
`tokenManager.session.map { it != null }`), **sem `distinctUntilChanged`**. A fonte emite a cada
mudança do usuário — renovação de token, atualização de perfil, releitura do estado — e o `map`
transforma vários desses eventos no MESMO `true`.

Quem observa esse flow para recarregar a tela, que é o uso natural dele, dispara **uma requisição
por emissão**. No Cidade Conectada isso deu **quatro chamadas da mesma rota em 300 ms** e **141
requisições em um minuto com UM único usuário navegando** — o suficiente para o app inteiro passar
a responder 429, em todas as famílias ao mesmo tempo, porque o balde de rate limit é por IP e não
por rota. Nada disso quebra o build, e o log do app não acusa: as chamadas são todas legítimas,
só repetidas.

`isLoggedIn` é **estado, não evento**: "está logado?" só interessa quando a resposta muda.

### Também

`defaultHttpErrorMessage(429)` deixou de dizer **"Muitas requisições. Aguarde um momento."** A frase
era lida como acusação por quem não fez nada de errado — o fundador do Cidade Conectada bateu no
limite navegando sozinho e respondeu: *"a mensagem não está certa, não só eu estou usando"*. Quem
estoura um teto de rate limit ou está num app que pede demais, ou num teto apertado; os dois são
problema nosso. Agora: **"O aplicativo está indo rápido demais. Tente de novo em instantes."**

### Migração

Nenhuma. Quem consome por composite build já recebe; quem consome por artefato, no bump.


## 2.160.0 — os DOIS modos de login social, declarados (ago/2026)

**`SocialLoginMode`** (`NATIVE` | `BACKEND`), a fachada **`SocialSignIn`** e
**`OwnAuthSocialService.signInWithSocialCode(code, codeVerifier)`**.

### O que muda de fato

A lib já tinha os dois caminhos desde a 2.143.0 — o nativo (`GoogleAuthProvider`/`AppleAuthProvider`)
e o do navegador (`SocialBrowserLogin`). O que não existia era **a escolha declarada**: cada app
reescrevia um dos dois roteiros na própria tela de login, e é no meio deles que moram os erros que
passam pelo build — pular o nonce do servidor, guardar o `verifier` no lugar errado, mandar o
`accessToken` no lugar do `idToken`, tratar cancelamento como falha.

```kotlin
val socialSignIn = SocialSignIn(
    mode = SocialLoginMode.NATIVE,          // ou BACKEND — é a única linha que muda
    api = ownAuth.api,
    social = ownAuth.social,
    nativeWebClientId = AppConfig.googleWebClientId,   // modo NATIVE
    // backendAppId = "mirassol", redirectScheme = "brcodecacto.mirassol",  // modo BACKEND
)

socialSignIn.signIn(SocialProvider.GOOGLE)
    .onSuccess { irParaHome() }
    .onFailure { if (!it.foiCancelado()) mostrarErro(it.message) }
```

A tela **não sabe qual modo o projeto usa**. Trocar de modo é trocar um argumento.

### A assimetria que isto fecha

No fluxo nativo a lib adotava a sessão; no fluxo pelo navegador o `socialExchange` devolvia tokens
crus e cada app tinha de adotá-los na mão. Um app que esquecesse esse passo terminava o login com o
usuário **ainda deslogado, sem erro nenhum**. Agora os dois caminhos terminam em `Result<User>` com
a sessão adotada.

`signInWithSocialCode` entra na interface com **implementação default que recusa** — os apps mantêm
fakes de `OwnAuthSocialService` em `commonTest`, e método abstrato novo quebraria todos de uma vez.

### Cancelamento não é erro

Nos dois modos, desistir chega como `SocialBrowserException(reason = "cancelado")`, e
`Throwable.foiCancelado()` responde isso sem a tela comparar strings de erro — era assim que
"cancelado" virava "falha no login" na primeira tradução que mudasse.

### Nenhum modo é "o certo"

`BACKEND` é o default de projeto interno da empresa: o provedor nunca vê o app, então **um cliente
OAuth serve o portfólio inteiro** e app novo entra só na allowlist do backend. `NATIVE` continua
sendo a experiência que a plataforma desenha, e é a escolha natural de projeto de **parceria ou de
cliente**, que publica na conta dele e traz o próprio projeto no Google Cloud — ali o teto de
clientes OAuth não é problema. **A escolha é do fundador, por projeto** (decisão de 27/ago/2026).

Aditivo: quem já chamava `GoogleAuthProvider`/`SocialBrowserLogin` direto continua compilando igual.

## 2.159.0 — a porta em que ninguém se cadastra (ago/2026)

`AuthMethods` ganhou **`showRegister`** (default `true`). Em `false`, a `LoginScreen` não desenha o
"Não tem uma conta? Cadastre-se".

Companheiro do `footerSlot` da 2.158.0: a segunda porta de entrada é para quem recebeu a conta de um
RH ou de um profissional, e ali o convite a se cadastrar manda a pessoa exatamente para onde ela não
deve ir. Pior: a conta que ela criar sozinha **não fica ligada** à empresa nem ao profissional, então
o resultado dela não aparece para quem a convidou — e o suporte recebe "respondi e sumiu".

```kotlin
LoginScreen(
    // …
    authMethods = AuthMethods(emailPassword = true, showRegister = false),
)
```

Aditivo: default `true`, quem não passa nada vê a tela como antes.


## 2.158.0 — a segunda porta de entrada do login (ago/2026)

`LoginScreen` ganhou **`footerSlot`**: conteúdo do app abaixo dos links legais, no fim do formulário.

### Para que serve

Produto com duas populações tem duas portas: quem comprou entra pelo login comum, e quem foi
convidado por uma empresa (ou por um profissional) entra por outra — "Entrar com login corporativo",
"sou colaborador", "acesso da clínica". A autenticação é a MESMA; o que muda é para onde a pessoa vai
depois e o que ela vê lá dentro. Isso é decisão do app, não da lib — mas até agora não havia onde
pendurar o botão, e o único caminho era o app reescrever a tela de login inteira, perdendo tema,
acessibilidade e o painel de tablet junto.

### Por que no FIM, e não acima do botão de entrar

Acima, ele disputa com a ação principal e confunde quem tem conta comum, que é a maioria. No fim,
quem procura acha e quem não procura não tropeça. Aparece nas duas formas (telefone e tablet com
painel de marca) porque é parte do formulário — ao contrário do `brandPanel`, que só existe em janela
expandida.

```kotlin
LoginScreen(
    state = state,
    onAction = viewModel::onAction,
    // …
    footerSlot = {
        TextButton(onClick = { navController.navigate(Route.LoginCorporativo) }) {
            Text("Entrar com login corporativo")
        }
    },
)
```

Aditivo: `footerSlot` é opcional e o default é `null`. Quem não passa nada vê a tela como antes.


## 2.157.0 — o giro que ficava POR CIMA do documento já carregado (ago/2026)

Correção no `HtmlDocumentView` (Android). O iOS não tem o defeito — lá o progresso é indeterminado.

### O sintoma, nas palavras de quem viu

"Cliquei em ver o relatório. Ele apareceu, eu consigo rolar o documento — e o carregando fica em
cima, eterno, não some por nada."

É a descrição exata do bug: o indicador é desenhado **por cima** do WebView, no mesmo `Box`. Com o
documento já renderizado embaixo, um estado `Loading` que não termina não parece um carregamento
travado — parece a tela pronta com um giro grudado nela.

### A corrida

`onPageFinished` e `onProgressChanged` **não têm ordem garantida**. Num documento grande — muitas
seções, fontes, imagens — o progresso oscila e chega a cair depois do `onPageFinished`. Como
qualquer valor de 0..99 emitia `Loading`, o estado voltava de `Ready` para `Loading` e ficava lá:
`onPageFinished` já tinha passado e não dispara de novo.

Quanto maior o documento, mais provável — por isso batia no relatório de 20 seções e não nas telas
curtas, e por isso parecia "só na primeira vez".

### O que mudou

- Uma trava por carga: depois de pronto, **progresso não regride o estado**. Ela é rearmada quando
  uma carga nova começa (fonte trocada ou recarga pedida).
- `onProgressChanged(100)` passou a **concluir** também. `onPageFinished` é o caminho normal, mas
  não é garantido em todo conteúdo servido por `loadDataWithBaseURL`; sem esse segundo caminho, um
  documento em que ele não dispare ficaria no indicador para sempre.

## 2.156.0 — a tela que girava para sempre: ler o corpo estava FORA do try (ago/2026)

Correção. Atinge **todo consumidor** de `DomainApiClient` da fábrica — `getJson`, `postJson`,
`putJson`, `patchJson`, os multipart e `getBytes`.

### O problema

O `try/catch` do `execute` cobre a requisição. Mas o corpo era lido **depois** que ele retornou:

```kotlin
suspend fun getJson(path: String): DomainResult<String> =
    execute(path) { … }.map { it.bodyAsText() }   // ← o download acontece AQUI, fora do catch
```

Uma conexão derrubada no meio da leitura — o caso comum num payload grande e demorado — lançava, a
exceção subia pelo repositório e caía no `launch` do `BaseViewModel`, **que não captura nada**. A
corrotina morria em silêncio: sem log, sem erro na tela, com o `carregando` aceso. A tela girava
para sempre, e nenhuma linha do app estava errada.

Diagnosticado no relatório de 10 páginas do NeuroCoreX, gerado sob demanda: o defeito só aparecia na
PRIMEIRA abertura, que é a mais lenta. Foi reportado como "abri e ficou carregando, não sumia".

### O que mudou

- `texto()` / `bytes()` leem o corpo sob a mesma proteção do `execute`, devolvendo o
  `DomainResult.Error` de sempre em vez de lançar. `Error` e `Quota` atravessam intactos — não têm
  corpo a ler, e reler o do 402 consumiria um corpo já consumido em `classify`.
- **`CancellationException` volta a propagar**, nos dois pontos. O `catch (Exception)` do `execute`
  a engolia: sair da tela no meio de uma chamada cancela o escopo, e isso virava
  `DomainResult.Error` — a tela seguinte podia nascer com "sem conexão" sem ter feito chamada
  nenhuma. Cancelamento não é falha de rede.

### Para quem consome

Nada muda na API. O que muda é que o erro passa a **chegar** ao `when` que o app já escreveu — os
ramos `else`/`is Error` que existiam e nunca eram alcançados agora executam.

## 2.155.0 — `platform/DeviceLocale`: a região do aparelho, que não é o idioma dele

Aditivo. Nada existente mudou.

**`deviceRegion(): String?`** devolve a região configurada no sistema em ISO 3166-1 alfa-2
(`"BR"`, `"US"`, `"PT"`), ou `null` quando a plataforma não sabe dizer.

**Por que não dá para usar o idioma no lugar disto.** Idioma responde "em que língua escrever";
região responde "que regras valem aqui". Um brasileiro morando em Portugal costuma ter o aparelho em
português **com região PT**; um americano estudando espanhol pode ter idioma `es` **com região US**.
Escolher conteúdo regional pelo idioma erra os dois. O caso que motivou: o **Decibelímetro Simples**
traduzido para quatro idiomas continuava exibindo NBR 10151 e NR-15 — normas brasileiras — para
quem abrisse o app em Nova York.

- **Android:** lê a configuração **da aplicação** antes do `Locale.getDefault()`, porque desde o
  Android 13 o usuário pode escolher idioma só para um app, e o default do processo continua sendo o
  do sistema. `DeviceLocaleHolder` entrou no `KmpLib.init` — nada a fazer no app.
- **iOS:** `NSLocale.currentLocale.countryCode`, que é a região de Ajustes → Idioma e Região —
  no iOS, uma configuração separada do idioma.
- **`null` é um caso real**, não teórico: emulador recém-criado, aparelho sem região definida, ou um
  valor que não é país. **Nunca assuma um país de default** — é assim que um app brasileiro passa a
  mostrar a CLT para um americano. Caia no conteúdo internacional.
- A normalização (`normalizeRegion`) recusa o que não for duas letras: o Android devolve **`"419"`**
  nessa posição para o "espanhol da América Latina", que é código de **área** (UN M.49), não de país.

**Não é geolocalização:** é configuração do sistema, sem permissão e sem rede. Quem viaja com o
aparelho no país de origem continua vendo as regras de lá — e, para escolher tabela de referência,
é o comportamento desejável. Quem precisa de posição real usa `location`.



## 2.155.0 — o arrasto que a galeria comia: deslizar entre fotos NÃO funcionava

Defeito da 2.153.0, reportado pelo fundador ao usar a galeria de espaço de festa:
*"quando clico em uma foto da galeria ela abre em tela cheia — quero arrastar para o lado para ir
vendo as próximas ou anteriores"*. E não ia: o dedo arrastava e não acontecia nada.

### A causa
`ZoomableBox` usava `detectTransformGestures`, que **consome todo arrasto** que passa do touch
slop — inclusive o de UM dedo, e inclusive com a imagem em escala 1. Num visualizador de imagem
sozinha isso não se nota (não há quem receberia o gesto). Dentro de um `HorizontalPager` é o
defeito inteiro: o dedo arrasta, a foto fica parada (escala 1, offset zerado) e o pager nunca vê o
evento.

O `userScrollEnabled = !ampliada` que a 2.153.0 tinha era correto e **inútil sozinho**: ele libera
o pager, mas o gesto já tinha sido comido antes de chegar lá.

### A correção
Laço de gesto próprio (`awaitEachGesture`) no lugar do `detectTransformGestures`, consumindo
**só o que é do zoom** — `gestoEDoZoom(dedos, escalaAtual)`:

- **dois dedos é sempre pinça** (ninguém usa dois dedos para virar página);
- **um dedo só é nosso com a imagem ampliada** — aí o arrasto move a imagem, que é a única coisa
  que faz sentido; em escala 1 não há para onde mover.

Fora disso os eventos passam adiante e o pager vira a página.

O corte é `1.01`, e não `1f` exato: a pinça deixa resíduo de ponto flutuante (1.0000001), e comparar
com igualdade faria a imagem "de volta ao normal" continuar capturando o arrasto para sempre.

### O que muda para quem já usava `ZoomableBox`
Nada visível. Com imagem única e escala 1, o arrasto deixa de ser consumido — mas não havia ninguém
para recebê-lo. Ampliada, continua panorâmica como antes.

Cinco testes novos (`ZoomableBoxGestoTest`), e o primeiro é exatamente o caso que estava quebrado.

## 2.154.0 — o microfone que diz onde é o silêncio, e a permissão que finalmente abre

Duas correções de comportamento no mesmo release. Nenhuma assinatura sumiu; as duas **mudam o que o
código faz**, e é por isso que estão descritas em detalhe.

### 1. `platform/audio`: piso de ruído medido, saturação por fração, e o fim de um grampo que mentia

- **`AudioLevel.noiseFloorDbfs` (novo)** — o **menor RMS observado na sessão**, que é o ruído próprio
  do conjunto microfone + pré-amplificador **daquele aparelho**. Existe para o app poder dizer
  "abaixo disto o seu aparelho não distingue" em vez de apresentar ruído próprio como medição — a
  reclamação nº 1 da categoria. Enquanto nada foi observado, vale `SILENCE_DBFS`.
- **`AudioLevel.clippedSampleRatio` (novo)** e **`isClipping` por fração** — satura a janela em que a
  fração de amostras no fundo de escala passa de `CLIPPING_RATIO_THRESHOLD` (0,1%). O critério
  anterior era um **limiar fixo em dB**, e limiar fixo é impossível de acertar: o teto de captação
  varia de **82 a 100 dB SPL** conforme o AGC que cada fabricante ajustou para voz. A fração é a
  mesma verdade em qualquer hardware.
- **`rmsDbfs` deixou de ser grampeado em `SILENCE_DBFS`.** O grampo era materialmente inócuo (nenhum
  microfone de celular chega perto de -120 dBFS) mas mentia no contrato: quem lia o código concluía
  que existia um mínimo de exibição. Agora o número é o do conversor — se o cálculo dá -97, sai -97.
  `SILENCE_DBFS` continua, e passou a ser **só o que sempre deveria ter sido**: a sentinela do
  silêncio digital (RMS zero), para `log10(0)` não virar `NaN` e sumir com o número da tela.

⚠️ **Um defeito de uma linha foi corrigido junto**, e ele anulava o piso de ruído inteiro: o
acumulador de mínimo nascia em `SILENCE_DBFS` e a condição era `rms < piso` — como nenhuma leitura
real fica abaixo de -120, **o piso nunca era observado**. É o clássico mínimo inicializado com o
menor valor possível em vez do maior. Hoje o acumulador nasce "não observado" (`null`), o que é
diferente de "é -120".

### 2. Permissão de runtime no Android: o diálogo que nunca abria

**`KmpLib.setActivity` não registrava o `PermissionHostHolder`.** Registrava os outros quatro
holders (biometria, brilho, notificação, Google Sign-In) e deixava a permissão de fora — então
`requestPermission` **não abria diálogo nenhum**: registrava um aviso no log e devolvia o status que
já tinha. O botão "Permitir" existia, era tocável, e não acontecia nada, com build verde.

Levantamento de 26/ago/2026 no portfólio: **26 apps pedem permissão de runtime e 9 já registravam o
holder por conta própria** — nove equipes descobriram o mesmo furo e escreveram o mesmo contorno.
Chamar `setActivity` duas vezes é inofensivo, então **quem contorna não precisa remover nada** para
subir de versão.

- **O `onRequestPermissionsResult` deixou de ser necessário.** O pedido passou a usar o
  `ActivityResultRegistry` — a via recomendada pelo AndroidX (`requestPermissions` +
  `onRequestPermissionsResult` está depreciado na `Activity`). Era a outra metade do furo: quem não
  sabia do repasse via o diálogo abrir e a resposta nunca chegar. `handlePermissionResult` continua
  existindo e virou no-op para quem ainda o chama.
- **`checkPermission` passou a distinguir `NOT_REQUESTED` de `DENIED` no Android.** O sistema não
  expõe esse bit — `shouldShowRequestPermissionRationale` é `false` nos dois extremos (antes do
  primeiro pedido e depois da negação definitiva) —, então a lib **lembra** que já pediu, num arquivo
  de preferências próprio. Sem isso, o app cai num de dois defeitos: ou nunca mostra a tela de
  contexto que as lojas exigem antes do diálogo, ou oferece para sempre um botão que não abre nada.
  A marca é gravada **antes** de abrir o diálogo, para sobreviver ao processo morrer com ele na tela.

⚠️ **Mudança de comportamento:** quem tratava `DENIED` como "primeira vez" passa a receber
`NOT_REQUESTED` nessa situação. É a semântica que o enum sempre prometeu e que o iOS já entregava.


## 2.153.0 — `FullScreenGallery`: a galeria em tela cheia que PASSA de foto

O `FullScreenImageViewer` abre **uma** foto. Quem tem doze precisa fechar, tocar na seguinte e abrir
de novo — doze vezes. Este é o irmão dele para quando há um conjunto: abre na foto tocada, desliza
para os lados, e cada uma continua com o pinch-to-zoom e o duplo toque do `ZoomableBox`.

```kotlin
FullScreenGallery(
    fotos = urls,
    indiceInicial = tocada,
    onDismiss = { aberta = false },
)
```

Veio do perfil de **espaço de festa** no Cidade Conectada (fundador, 26/ago/2026: *"queria uma aba,
talvez galeria, que aí você clicava, abria em tela cheia e podia passar"*) — mas não tem nada
daquele produto: é o gesto que qualquer galeria de app precisa.

### As duas coisas que a versão caseira sempre erra

**O zoom briga com o deslizar.** Com a foto ampliada, arrastar tem de mover a IMAGEM, não virar a
página — senão é impossível olhar o canto de uma foto: o primeiro arrasto some com ela. Por isso
`ZoomableBox` ganhou **`onScaleChange`** e o pager desliga o `userScrollEnabled` enquanto há zoom.
A escala é do PAGER, não de cada página, e zera ao trocar de foto: o zoom da anterior não vale para
a nova.

**O contador não é enfeite.** Sem "3 / 12" ninguém sabe quantas faltam, e a pessoa desliza até bater
na última para descobrir que acabou.

### E uma que derruba a tela

`rememberPagerState` **estoura** com `initialPage` fora da faixa — e o índice vem de uma lista que
pode ter encolhido entre o toque e a abertura (uma foto apagada, uma recarga do perfil). A conta é
`paginaInicialDaGaleria`, testada nos cinco casos, e ancora na primeira: abrir a galeria vale mais
que abrir na foto exata.

Fechar por toque no fundo está **desligado** de propósito: numa galeria o dedo passa o tempo todo
sobre a imagem, e o toque que "erra" a foto fecharia a tela no meio de quem só queria deslizar.

## 2.152.0 — o painel de marca não deixa a logo aparecer duas vezes (ago/2026)

Correção de comportamento no caminho que nasceu na 2.151.0. Afeta **só** quem passa `brandPanel` — a
API tem um consumidor, e nele o defeito era visível na primeira tela do app.

### O problema

`LoginScreen`/`RegisterScreen` recebem `logo` e `brandPanel` por parâmetros independentes, e quem
chama passa os dois **sem saber em que ramo vai cair**: o `logo` é desenhado no topo do formulário
em qualquer classe de janela, e o `brandPanel` só aparece em EXPANDIDA. Resultado num tablet em
paisagem: o painel navy com o lockup claro à esquerda e, imediatamente à direita, **o mesmo lockup
outra vez**, em tinta escura, no topo do formulário.

Não era erro de quem consumiu. A tela é que oferecia duas fontes de marca e não dizia que elas se
excluem — e a única forma de o app acertar seria escrever ele mesmo
`if (LocalWindowSizeClass.current == EXPANDIDA) null else painter`, isto é, recalcular por fora uma
regra que a tela já decide por dentro. É a mesma classe de defeito que o `GAP-NCX-T-04` documentou:
componente que sabe algo e não conta a quem chama.

### O que mudou

O lambda interno do formulário passa a receber `comPainelDeMarca: Boolean`, e o bloco do `logo` só
desenha quando ele é `false`. Onde o painel está em cena, a marca é o painel.

`logo` continua com o mesmo tipo e o mesmo default — nenhuma assinatura pública mudou, e quem não
usa `brandPanel` não vê diferença nenhuma.

## 2.151.0 — o tablet para de esticar formulário, texto e gráfico

**Aditivo**: nenhuma assinatura existente quebra, nenhuma chamada precisa mudar. Origem: a §E do
`docs/design/tablet-spec.md` do **NeuroCoreX**, que mediu quatro coisas que **nenhum app da fábrica
consegue corrigir de fora** — e as quatro atingem qualquer app num tablet, não só aquele produto.

### O que estava errado

- **`FormContainer` não tinha teto nenhum** (`fillMaxSize().padding(horizontal = 24.dp)`). Num
  tablet em paisagem (1280dp) o campo de e-mail do login nascia com **1184dp de largura**, em todo
  app da fábrica. E o app **não conseguia consertar de fora**: o `modifier` da `LoginScreen` vai
  para o `Surface` de FUNDO, então limitar por lá encolheria o fundo junto — a tela ganharia uma
  faixa de 480dp de cor no meio de um fundo de outra cor.
- **`EmptyState` esticava o texto.** Num painel de 888dp o título saía numa linha de ~820dp,
  centralizado. Cada tela *podia* passar `Modifier.widthIn(...)` — e é justamente por isso que
  estava errado em 100% das telas: correção que depende de lembrar, num componente usado dezenas de
  vezes por app.
- **`RadarChart` não cabia na própria tela.** O `Canvas` era `fillMaxWidth().aspectRatio(1f)`: num
  painel de 888dp, um quadrado de **888 × 888dp — mais alto que a tela de um tablet em paisagem
  (800dp)**. A legenda das séries nascia fora da tela.
- **`ListDetailScaffold` sabia que estava em painel único e não contava.** Cada consumidor
  recalculava a regra por fora para decidir a seta de voltar, e ela tem duas formas parecidas:
  `!classe.temDoisPaineis` (certa) e `classe == COMPACTA` (errada, esquece o tablet em RETRATO). O
  NeuroCoreX escreveu a segunda, e a pessoa ficava **presa no detalhe** ao girar o tablet para
  retrato. Todo app com mestre-detalhe reescreveria a mesma linha, com uma chance em duas de errar
  igual.

### O que entrou

- **`FormDefaults`** — regra pura, testável sem árvore de composição:
  `maxContentWidth(classe)` (`Dp.Unspecified` em COMPACTA, **480dp** em MEDIA e EXPANDIDA),
  `MaxContentWidth` e `BrandPanelFraction` (0.37f).
- **`FormContainer(..., maxContentWidth: Dp = FormDefaults.maxContentWidth(LocalWindowSizeClass.current))`**
  — o container **continua** `fillMaxSize` (o fundo é de borda a borda); quem ganha teto é só a
  `Column` interna, centrada. Todas as telas de formulário da lib herdam.
- **`LoginScreen(..., brandPanel: (@Composable () -> Unit)? = null)`** e o mesmo em
  **`RegisterScreen`** — quando não nulo **e** a janela é EXPANDIDA, a tela vira `Row` com o painel
  de marca em 37% e o formulário no resto. Em qualquer outra classe o painel é **ignorado**, não
  empilhado: num telefone ele empurraria os campos para fora da tela.
- **`EmptyState(..., maxTextWidth: Dp = 420.dp)`** (e o mesmo em `FullScreenEmptyState`) — a
  `Column` externa continua `fillMaxWidth` e continua centralizando; só o bloco
  título + descrição + ação ganha teto. Em telefone (coluna útil ~354dp) o valor nunca morde.
- **`RadarChart(..., tamanhoMaximo: Dp = 420.dp)`** — `widthIn` no `Canvas`, centrado na `Column`.
- **`ListDetailPaneScope`** com **`emPainelUnico`**, recebido pelos slots `lista`, `detalhe` e
  `vazio` do **`ListDetailScaffold`**.
- **`FeedbackScreen`** ganhou o mesmo teto de 480dp (formulário e tela de sucesso), centrado — ela
  **não** usa o `FormContainer`, então não herdaria a correção de graça. É uma tela que a fábrica
  entrega em todo app: sem isto, o formulário de feedback continuaria com 1280dp de largura.

### Por que o `emPainelUnico` chega por ESCOPO e não por parâmetro de lambda

A forma pedida no desenho — `detalhe: @Composable (emPainelUnico: Boolean) -> Unit` numa sobrecarga
nova, mantendo a antiga — foi escrita, compilada e **reprovada pelo compilador**:
`Overload resolution ambiguity`, porque uma chamada existente (`lista = { … }`, lambda sem parâmetro
declarado) casa com as **duas** assinaturas. Publicar assim quebraria a compilação de todo app que
já usa o componente.

O escopo de receptor entrega a mesma informação **sem tocar em nenhuma chamada existente** — e é a
forma que o próprio Compose usa para um container informar seus slots (`ColumnScope`,
`LazyItemScope`, `ThreePaneScaffoldScope` do AndroidX). O caso está travado em
`ListDetailScaffoldApiTest`, que chama a forma antiga: se um dia alguém reintroduzir a ambiguidade,
o arquivo de teste **não compila**.

### Teste

`FormDefaultsTest` prova a regra pura com **larguras de aparelho real** (360/393/412, 599/600,
744/820/839, 840/1180/1280/1440), não números redondos — é neles que um `<=` no lugar de um `<`
aparece. A comparação com `Dp.Unspecified` usa `isUnspecified`, e não `==`: `Dp.Unspecified` é
`NaN`, e `NaN == NaN` é falso. Dois testes de invariante fecham o resto: o teto de **formulário** é
mais estreito que o de **leitura** (`leituraMaxWidth`), e a fração do painel de marca deixa, na
menor janela EXPANDIDA (840dp), largura suficiente para os 480dp de conteúdo mais o padding.

Suíte: **2.279 testes, 0 falhas** (`:kmplib:testDebugUnitTest`), `koverVerify` verde.

## 2.150.0 — captura de microfone e nível sonoro (`platform/audio`)

**Aditivo**: nenhuma assinatura existente mudou, **nenhuma permissão nova no manifesto da lib**, e
`media`/`voice` ficaram intocados.

A lib não tinha **como ler o microfone**. `media/AudioPlayer` é reprodução; `voice/SpeechRecognizer`
é fala — e **nenhum dos dois expõe buffer de áudio nem nível**. Um decibelímetro ou um afinador
teriam de escrever `expect/actual` **dentro do projeto**, que é exatamente o que a lib existe para
concentrar. O gap bloqueava a Onda 1 inteira do **Decibelímetro Simples**.

**Dois consumidores desde o primeiro dia, e é o que decidiu a forma da API:** o Decibelímetro quer o
**nível em dB**; o **afinador por microfone do Tom Certo** (registrado no PRD dele como feature
futura) vai querer as **amostras cruas** para detecção de frequência. Por isso a API entrega
**nível E buffer** — entregar só o dB faria o segundo consumidor reimplementar a captura do zero, e
a promoção para a lib não teria valido nada.

### O que entrou

- **`AudioCapture`** — `isAvailable`, `state: StateFlow<AudioCaptureState>`,
  `levels: Flow<AudioLevel>`, `frames: Flow<AudioFrame>`, `start()`/`stop()`/`release()` e
  **`updateProcessing(weighting?, timeWeighting?, emitIntervalMillis?)`**, que troca o processamento
  **sem reiniciar a captura** (a tela de Configurações não pode fechar e reabrir o microfone a cada
  toque).
- **`createAudioCapture(config)`** (`expect`/`actual`) e **`rememberAudioCapture(enabled, config)`**,
  que libera o recurso no `onDispose`.
- **`AudioLevel`** (`rmsDbfs`, `peakDbfs`, `isClipping`, `sampleRate`, `weighting`,
  `timestampMillis`) e **`AudioFrame`** (PCM 16-bit mono cru, opt-in).
- **`AudioCaptureConfig`**, **`AudioWeighting`** (`Z`/`A`), **`AudioTimeWeighting`**
  (`FAST`/`SLOW`/`NONE`), **`AudioInputSource`**, **`AudioCaptureState`**, **`AudioCaptureError`**.
- **DSP puro e testável, em `commonMain`:** **`AudioLevelAnalyzer`**, **`AWeightingFilter`**,
  **`TimeWeightingIntegrator`** — os `actual` **só leem o hardware**; não há aritmética de dB dentro
  de nenhum deles, de propósito (se houvesse, o mesmo som daria números diferentes nas duas
  plataformas e ninguém descobriria sem dois aparelhos na mão).
- **`SplCalibration`** — `toSpl(dbfs)`/`toDbfs(spl)` e `DEFAULT_OFFSET_DB = 90.0`.

### Padrão-ouro, com o porquê

- **Android — `AudioRecord`** (PCM 16-bit mono, `AudioRecord.Builder`), **nunca
  `MediaRecorder.getMaxAmplitude()`**: o atalho **grava um arquivo em disco** só para devolver um
  inteiro de pico, com resolução grosseira, sem RMS, sem ponderação e **sem acesso ao buffer** — um
  medidor feito assim escreve áudio no armazenamento do usuário a cada leitura, e um afinador não
  teria como existir sobre ele.
- **Fonte de entrada: `UNPROCESSED` quando o aparelho declara suporte
  (`AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED`), senão `VOICE_RECOGNITION`.** O motivo é
  medição, não qualidade: ganho automático, supressão de ruído e cancelamento de eco **destroem a
  relação amplitude → SPL** — o AGC "conserta" exatamente aquilo que estamos medindo. `MIC` costuma
  vir com AGC/NS em OEM. A fonte efetiva vai em `Running.source`, porque **trocar de fonte invalida a
  calibração**.
- **iOS — `AVAudioEngine`** com tap no `inputNode` + `AVAudioSession` categoria `record` e **modo
  `measurement`**, que a Apple documenta como o que minimiza o processamento do sistema sobre a
  entrada: é o par do `UNPROCESSED` do Android. `AVAudioRecorder` foi descartado — grava arquivo e só
  expõe `averagePower`.
- **Thread dedicada com `THREAD_PRIORITY_URGENT_AUDIO`** e emissão `DROP_OLDEST`: o laço de áudio
  **nunca espera coletor**. Tela lenta atrasaria a leitura do driver e corromperia a medição.

### As decisões que o número da tela depende

- **A unidade é dBFS, não dB SPL.** dBFS é a verdade do que o hardware entregou; SPL depende da
  sensibilidade do microfone **daquele modelo**, que nenhuma API expõe. A conversão fica na lib como
  **função pura** (`SplCalibration`) porque a *constante de referência* é conhecimento compartilhado
  — deixar cada app inventar a sua é como a fórmula se perde. **Persistir o offset e desenhar a tela
  é do app.** O offset é **por aparelho E por fonte de entrada**, e **isto não é instrumento de
  medição**.
- **A curva A é DERIVADA da `sampleRate`, em runtime** (transformada bilinear sobre os polos da IEC
  61672-1, com normalização de 0 dB em 1 kHz calculada sobre a cascata digital). **Hardcodar
  coeficientes de 44.100 Hz é proibido:** o aparelho que abrir em 48 kHz — muito comum — mediria
  **errado em silêncio**, e nada no log denunciaria. Há **pré-warp seletivo** dos polos (só abaixo de
  `0,45·fs`): sem ele o erro em 10 kHz é de **-1,5 dB**; com ele, +0,7 dB. Aplicá-lo a polo acima de
  Nyquist é pior que não aplicar — medido: **-5,5 dB** de erro em 4 kHz numa captura de 16 kHz.
  Erro máximo contra a tabela da norma: **0,2 dB em 44,1 e 48 kHz**.
- **A integração temporal (Fast 125 ms / Slow 1 s) é sobre a POTÊNCIA, nunca sobre o dB.** Média de
  decibéis não é média de energia: suavizar em dB achata o transiente e mostra um estouro menor do
  que ele foi, no exato caso em que o número importa. Sem integração nenhuma, o valor pula a cada
  150 ms e a tela fica ilegível.
- **Piso de silêncio `SILENCE_DBFS = -120.0`.** Janela em silêncio digital tem potência zero, e
  `log10(0)` é `-Infinity`, que vira `NaN` na animação e **some com o número da tela** — falha muda,
  sem erro no log. Nenhum campo em dB emite `Infinity`/`NaN`, e há teste explícito para isso.
- **Saturação:** `isClipping` quando alguma amostra encosta no teto (`|sample| >= 32767`) **ou**
  `peakDbfs >= -0.1`. O pico é medido no sinal **cru** — ponderar antes de medir pico esconderia
  justamente a saturação que ele existe para denunciar.
- **Curva C ficou de fora**, e é decisão: ela só serve a pico impulsivo/ruído industrial e **não tem
  consumidor**. Módulo novo não nasce com opção sem dono. Pelo mesmo motivo **não há FFT nem detecção
  de pitch** (é do produto), nem estéreo, nem gravação.

### O que a lib NÃO faz (de propósito)

- **Não pede permissão** — só **confere**, e falha com `AudioCaptureError.PermissionDenied`. Pedir é
  do app, via `PermissionManager` + `AppPermission.MICROPHONE`, depois da tela que explica o porquê.
  A conferência existe porque **sem ela o `AudioRecord` abre normalmente e entrega silêncio
  digital**: o app mostraria -120 dB para sempre, sem exceção, sem log e sem nenhuma pista da causa.
- **Não declara `RECORD_AUDIO` no manifesto da lib** — mesma régua do `SCHEDULE_EXACT_ALARM`: é
  permissão perigosa, e impô-la a **todo** app da lib (inclusive os que nem gravam) transfere um
  risco de revisão de loja para quem não pediu. O app declara a sua, e o
  `NSMicrophoneUsageDescription` do `Info.plist`.
- **Não observa o ciclo de vida do app.** Quem pausa em segundo plano é o `enabled` do helper
  Compose (mesmo desenho do `rememberShakeDetector`). **Mas interrupção e mudança de rota no iOS são
  obrigação do `actual`**: ligação/Siri levam a `Interrupted` e a captura **volta sozinha** no
  `shouldResume`; fone plugado e `AVAudioEngineConfigurationChange` reinstalam o tap e **recalculam
  os coeficientes da curva A** (a taxa pode ter mudado junto). Sem isso, o app volta da ligação com a
  tela viva e o número congelado.
- **Não grava nada em disco**, em hipótese alguma. Só buffer em memória.

### Armadilha registrada no código (iOS)

O tap **tem de usar `inputNode.outputFormatForBus(0)`**. Instalar tap com formato diferente do
hardware **derruba o app em runtime** (`required condition is false:
format.sampleRate == hwFormat.sampleRate`) — não é erro de compilação e não aparece em teste de
unidade. Por isso `preferredSampleRate` é **ignorado no iOS**, e as amostras `Float32` são
convertidas no cálculo, nunca na instalação do tap.

### Testes

44 casos novos em `commonTest`, com **vetores conhecidos e sem hardware**: seno de amplitude plena =
**-3,01 dBFS**, contínua em fundo de escala = **0 dBFS**, silêncio = piso exato e **nunca
`NaN`/`Infinity`**, saturação em ±32767 vs. -6 dBFS, pico ≥ RMS, curva A contra a tabela da norma
**em 44.100 E 48.000 Hz** (é o teste que prova que os coeficientes são derivados da taxa), degrau
chegando a 63% em 1τ, Slow atrás do Fast, `NONE` sem suavizar, máquina de estados e `release`
idempotente. Suíte da lib: **2271 testes, 0 falhas**.

### Pendência

O `actual` iOS **não compila no servidor Linux** (alvos Apple só sob `HostManager.hostIsMac`).
Entra como código pronto e revisado — **não** como verificado. Compilação e teste em dispositivo
saem no Mac.

## 2.149.0 — efeito sonoro curto empacotado (`media/SoundEffect`)

**Aditivo**: nenhuma assinatura existente mudou, nenhuma permissão nova no manifesto, o
`media/AudioPlayer` ficou intocado.

O bipe de confirmação — o som curto disparado no instante do toque e repetido à vontade — **não
tinha caminho** na lib, e o contorno que os projetos imaginavam não existia:

- **`media/AudioPlayer` é mídia**, não efeito: `play(filePath)` exige caminho **absoluto** de
  arquivo local, o Android usa `MediaPlayer.setDataSource(String)` (que **não abre**
  `file:///android_asset/...` — asset pede `AssetFileDescriptor`), o preparo é assíncrono e a
  reprodução é **uma por vez**. Um bipe a cada 300 ms por ali recria o player a cada volta e **corta
  o som anterior**.
- **`core/storage/BlobStore` não devolve caminho de arquivo** ("não é um sistema de arquivos", diz o
  próprio KDoc): é chave → bytes. Então nem materializar o recurso para alimentar o `AudioPlayer`
  era possível.

Resultado prático: para tocar um bipe empacotado, um app teria de escrever `expect/actual` **dentro
do projeto** — exatamente o que a lib existe para concentrar. Primeiro consumidor: **Contador de
Voltas** (som + vibração + sinal visual como as três vias de confirmação da volta). Serve igual a
cronômetro, app de exercício, jogo e leitor de código de barras.

### O que entrou

- **`SoundEffectPlayer`** — `load(key, bytes)` (suspenso, volta só quando dá para tocar),
  `play(key)` (síncrono, não bloqueia, não corta o disparo anterior), `isLoaded`, `loadedKeys`,
  `unload`, `release`.
- **`createSoundEffectPlayer(maxStreams)`** (`expect`/`actual`) e
  **`rememberSoundEffectPlayer(maxStreams)`**, que faz o `release` no `onDispose`.
- **`SoundEffectOutcome`** (`Success`/`Failure`) + **`SoundEffectError`** (`NotInitialized`,
  `InvalidAudio`, `StorageFailure`, `NotLoaded`, `Released`, `InvalidKey`, `Unknown`).
- **`SoundEffectFormat`** + `detectSoundEffectFormat(bytes)` e **`SoundEffectDefaults`**
  (`MAX_STREAMS`, `RECOMMENDED_MAX_BYTES`, `fileNameFor`, `isValidKey`, `isOversized`).

O app entrega **bytes** (`Res.readBytes("files/beep.wav")`), nunca caminho de arquivo e nunca
`Context` — é o que o Compose Resources sabe dar em código comum. A lib materializa o áudio no
diretório temporário do próprio app (é o que as duas APIs nativas recebem) e apaga no
`unload`/`release`.

### Padrão-ouro, com o porquê

- **Android — `SoundPool`**, a API que a documentação do Android indica para efeitos curtos e
  repetidos: decodifica **uma vez** no `load` e mantém o PCM em memória, então cada disparo é só
  despachar para o mixer. `maxStreams = 4` (o default do `SoundPool` é **1**, que cortaria o bipe
  anterior a cada toque). `AudioAttributes` com `USAGE_ASSISTANCE_SONIFICATION` +
  `CONTENT_TYPE_SONIFICATION` — a classificação de som de interface, que evita o efeito se
  apresentar como mídia e **abaixar a música do usuário por *audio focus* a cada bipe**.
- **iOS — *System Sound Services*** (`AudioServicesCreateSystemSoundID` no `load`,
  `AudioServicesPlaySystemSound` no disparo), o caminho que a Apple documenta para *short sounds*
  (≤ 30 s). A razão que decidiu contra um pool de `AVAudioPlayer`: ele exigiria **ativar uma
  categoria de `AVAudioSession`** para o som sair de forma previsível, e ativar sessão de mídia por
  causa de um clique **interrompe a música que o usuário está ouvindo** — a cada volta contada.
  Além disso o `SystemSoundID` é pré-carregado de verdade (o disparo não decodifica nem prepara) e
  chamadas consecutivas são mixadas pelo sistema, enquanto `AVAudioPlayer.play()` sobre uma
  instância ainda tocando **reinicia** o som. O preço é não haver controle de volume nem laço — e a
  API comum **não promete** nenhum dos dois, de propósito, para não expor parâmetro que uma
  plataforma ignoraria em silêncio. Se um produto vier a precisar de volume por disparo, o
  padrão-ouro é `AVAudioEngine` + `AVAudioPlayerNode` com buffer PCM, troca interna sem quebrar a
  API.

### Formato e modo silencioso (o app precisa saber)

- **WAV PCM 16-bit, mono, 44.1 kHz** é o formato recomendado, e o **único multiplataforma**: o
  *System Sound Services* aceita só Linear PCM/IMA4 em `.wav`/`.caf`/`.aif`. **MP3, M4A e OGG
  carregam no Android e falham no iPhone** — daí o aviso no log já no `load`
  (`SoundEffectFormat.isCrossPlatform`).
- **Android:** com `USAGE_ASSISTANCE_SONIFICATION` o efeito segue o **stream de sistema** — no
  Silencioso/Vibrar e sob "Não perturbe" o som **não sai**, e o volume é o do toque.
- **iOS:** som de sistema segue o interruptor **Silencioso**. O módulo **não** reconfigura a
  `AVAudioSession` (ver acima); se o app já ativou `Playback` por outro motivo — o `AudioPlayer` da
  lib, por exemplo —, o efeito passa a seguir aquela categoria.
- Em nenhuma das duas o app controla o volume. **Nada disso é falha:** é a razão de o produto
  confirmar a ação também por vibração e por sinal visual.

### Degradar em silêncio é contrato, não descuido

Nenhuma operação lança. Bytes inválidos, disco cheio, chave desconhecida, `KmpLib.init` esquecido —
tudo volta como `SoundEffectOutcome.Failure` com log. O som é **uma** das vias de confirmação;
derrubar a contagem porque o áudio falhou seria pior do que ficar mudo.

### A invariante que a suíte protege

Recarregar uma chave já carregada **tem** de devolver o identificador nativo anterior para quem o
criou descarregá-lo (`sampleId` no Android, `SystemSoundID` no iOS). Perder essa devolução é
vazamento silencioso — o áudio continua tocando certo e a memória só aparece depois de N recargas.
Por isso o índice `chave → identificador` mora em `commonMain` (`SoundEffectRegistry`) e é testado
sem aparelho, junto com a detecção de formato e a derivação do nome de arquivo (`"a/b"` e `"a b"`
não podem colidir no mesmo arquivo de cache).

### Validação

`:kmplib:compileDebugKotlinAndroid` e `:kmplib:testDebugUnitTest` verdes no servidor Linux (29
testes novos; suíte com 2227 testes, 0 falhas). **Pendente de validação no Mac:** o `actual` iOS foi
escrito conforme as APIs oficiais, mas Kotlin/Native de iOS não compila fora do macOS (alvos Apple
sob `HostManager.hostIsMac`).

## 2.148.0 — brilho da tela do app (`platform/ScreenBrightness`)

**Aditivo**: nenhuma assinatura existente mudou, nenhuma permissão nova no manifesto.

A 2.147.0 trouxe o `KeepScreenOn`, e faltava a outra metade: **manter a tela acesa não é aumentar a
luz dela**. No modo "tela como luz", pintar a tela de branco no máximo só ilumina se o brilho
**físico** subir — sem isso a tela *parece* certa e não funciona (opacidade de pixel não gera lúmen).
Era o que bloqueava o Minha Lanterna, e serve a qualquer tela que precise subir o brilho por um
instante: QR code no caixa, cartão de embarque, leitura no escuro.

### O que entrou

- **`ScreenBrightness(level, enabled)`** — a forma recomendada, no espírito do `KeepScreenOn`: o
  brilho vale enquanto o componente estiver na composição e **o valor de antes volta no `onDispose`**
  (navegou, fechou o modo, o app foi para trás).
- **`ScreenBrightnessController`** (`current()`, `setBrightness()`, `restore()`, `release()`) +
  `createScreenBrightnessController()` / `rememberScreenBrightnessController()`, para quando o dono
  do brilho não é uma composição. Quem cria, libera.
- **`ScreenBrightnessState`** (`overrideLevel`, `systemLevel`, `isOverridden`, `effective`) e
  **`ScreenBrightnessLevel`** (`MIN`/`MAX`/`SYSTEM`/`UNKNOWN`, `clamp`, `isOverride`, `percent`).

### Padrão-ouro, com o porquê

- **Android — `WindowManager.LayoutParams.screenBrightness` na janela da Activity**, com
  `BRIGHTNESS_OVERRIDE_NONE` para devolver o comando ao sistema. O escopo é a **janela do app**: sai
  do app, acaba o efeito. **`Settings.System.SCREEN_BRIGHTNESS` é usado só para LER** — escrever ali
  mudaria o brilho do **aparelho inteiro**, exigiria `WRITE_SETTINGS` (uma tela de sistema, não um
  diálogo) e deixaria o aparelho alterado depois de o app fechar. Ler não pede permissão nenhuma, e é
  por isso que este módulo não acrescenta **uma linha** ao manifesto.
- **iOS — `UIScreen.brightness`**, com restauração **explícita**: o brilho é do aparelho e o iOS
  **não** devolve o valor anterior sozinho.

### A invariante que a suíte protege

O valor anterior é capturado **uma única vez**, na primeira aplicação. Recapturá-lo a cada `set`
faria o segundo movimento do slider guardar o brilho **já forçado** — e "restaurar" viraria "deixar
no talo". É esse o defeito que a pessoa sente na bateria e não consegue atribuir a nenhum app. A
regra inteira (captura, restauração por plataforma, faixa, ciclo de vida) mora em `commonMain`
(`ScreenBrightnessSession`) e tem **22 testes**; os `actual` só sabem ler e escrever o brilho.

Duas decisões de borda, também testadas: **`0f` é um override válido** ("no mínimo", para modo
noturno), não "sem override"; e número inválido (negativo/`NaN`) **devolve o controle ao sistema** em
vez de apagar a tela ou forçá-la ao máximo.

**PENDÊNCIA (host macOS):** o `actual` iOS não compila em Linux — ver
`GAP-KL-M-BRIGHTNESS-IOS-VALIDATE` em `docs/backlog.md`.


## 2.147.0 — a lanterna sai de dentro da câmera (módulo `torch`) + 4 peças de fundação

**Módulo novo `torch`**, e mais quatro itens de plataforma que o app de lanterna expôs. Tudo
**aditivo**: nenhuma assinatura existente mudou.

### `torch` — controle de hardware da lanterna, sem sessão de câmera

Até aqui o único flash da kmplib vivia **dentro** do `CameraView` (OCR de placa) e do
`BarcodeScannerView`: para acender uma luz, o app teria de montar um leitor de código de barras
inteiro — preview, permissão de câmera, analisador de frames. É o que travava o **Minha Lanterna**.

`createTorchController()` devolve um `TorchController` com:

- **`turnOn(level)` / `turnOff()` / `toggle()` / `setLevel()`** — síncronos de propósito: o tempo
  entre o toque e a luz é a métrica do produto.
- **`state: StateFlow<TorchState>` que reflete o HARDWARE**, não a última intenção do app. Quando o
  SO apaga a luz sozinho (outro app pegou a câmera, aparelho esquentou, Central de Controle do iOS),
  o estado muda sem ninguém pedir. Botão preso em "aceso" com o LED apagado é o defeito clássico dos
  apps de lanterna.
- **`TorchCapabilities` consultável ANTES de desenhar a tela**: tem flash? tem intensidade? quantos
  níveis? (`sliderSteps` já sai pronto para o `Slider`). Sem isso, o slider vira um controle que
  mexe e não muda nada.
- **`TorchOutcome` + `TorchError`** (`NoTorch`, `PermissionDenied`, `InUse`, `Unavailable`,
  `Unknown`) — nada de exceção crua chegando à UI. A lib **não** traz texto de usuário aqui: a frase
  é do produto, e o idioma vem do aparelho.
- **`release()`** apaga a luz e solta o recurso; `rememberTorchController()` faz isso no `onDispose`.

**Padrão-ouro, com o porquê:**
- **Android** — `CameraManager.setTorchMode` (não abre a câmera, não pede permissão `CAMERA`, não
  bloqueia outros apps) + **`registerTorchCallback`** para o estado real. Intensidade por
  `turnOnTorchWithStrengthLevel` a partir da **API 33**, com o teto lido de
  `FLASH_INFO_STRENGTH_MAXIMUM_LEVEL`.
- **iOS** — `AVCaptureDevice` com `lockForConfiguration`/`unlockForConfiguration`, `torchMode` e
  `setTorchModeOn(level:)` (faixa contínua 0..1), com **KVO de `torchActive`** para o estado real.

**A capacidade de intensidade é uma conjunção, não uma versão de SO.** No Android ela exige API 33+
**E** teto maior que 1 — aparelho com `FLASH_INFO_STRENGTH_MAXIMUM_LEVEL == 1` no Android 14 é
liga/desliga, e prometer slider ali é um controle morto. A decisão mora em `commonMain`
(`androidTorchCapabilities`) e é coberta por teste, sem aparelho.

**PWM é PROIBIDO.** Onde não há suporte nativo a nível de força, a capacidade é reportada
**ausente** e ponto — simular intensidade piscando o LED em alta frequência aquece e desgasta o
componente. Pelo mesmo motivo, **estroboscópio e Morse ficam FORA da lib**: a lib entrega o
hardware, o padrão de piscar é regra de produto.

### `ui/components` — `AppSlider`

Par do `AppSwitch`/`AppCheckbox` para grandeza contínua: cores do `AppTheme` (zero `Color(0x…)`),
cabeçalho **rótulo à esquerda / valor à direita**, ícones opcionais nas pontas e
`onValueChangeFinished` para persistir só ao soltar. **`AppSliderDefaults.stepsFor(range, increment)`**
fecha o off-by-one clássico: `steps` no Material 3 conta os pontos **intermediários** (0..10 de 1 em
1 são 11 posições e `steps = 9`), e errar isso faz o slider parar em valores que não existem.

### `platform` — `BatteryMonitor`

Nível + "está carregando", observável. Android via `ACTION_BATTERY_CHANGED` (sticky broadcast — só
registrável em runtime, nunca no manifesto); iOS via `UIDevice` com `isBatteryMonitoringEnabled` e as
notificações de nível/estado (o `release()` **desliga** a flag, que é global do processo).

A regra do corte crítico é comum e testada: `isCritical(threshold)` só é verdadeira **sem
carregador** — 2% no carregador não é emergência, e cortar a lanterna ali seria um defeito. Leitura
indisponível **nunca** é crítica: `fromLevelAndScale` usa a `scale` reportada (nem sempre 100) e
`fromIosLevel` trata o `-1` do iOS como *desconhecido*, não como bateria zerada.

### `platform` — `KeepScreenOn`

Composable que impede a tela de apagar enquanto estiver na composição. Android: `View.keepScreenOn`
(recomendado pelo próprio Android, preferível a um `PowerManager.WakeLock`, que exige permissão e é
a origem clássica do "a tela nunca mais apagou"). iOS: `isIdleTimerDisabled`. **Não existe versão
imperativa, de propósito** — `acquire()`/`release()` soltos são exatamente como se esquece a tela
acesa a viagem inteira; aqui a liberação é do `onDispose`.

### `platform` — `ShakeDetector`

Gesto de chacoalhar com sensibilidade ajustável (`ShakeSensitivity.fromFraction`, direção intuitiva:
1 = mais sensível). Android: `SensorManager` + `TYPE_ACCELEROMETER` a `SENSOR_DELAY_GAME`; iOS: Core
Motion (`CMMotionManager`) a 50 Hz — o *shake* de graça do UIKit não serve porque não tem
sensibilidade ajustável.

A decisão fica no **`ShakeAnalyzer`, em `commonMain`**: os `actual` só entregam amostras **já
normalizadas em g** (o Android divide por `GRAVITY_EARTH`, o iOS já reporta em g), então o mesmo
gesto dispara igual nas duas plataformas. O `cooldown` é o que impede um chacoalhão de virar 25
eventos — a 50 Hz, meio segundo de movimento passa do limiar o tempo todo. Aparelho sem
acelerômetro reporta `isAvailable = false`, e a opção some da tela em vez de virar um ajuste que
nunca dispara.

### Testes

67 novos em `commonTest` (2.176 no total, 0 falhas): capacidade por versão de SO e por teto de
níveis, clamp/alinhamento de intensidade ao degrau do hardware, máquina de estado (inclusive "o SO
apagou sozinho"), mapeamento de `CameraAccessException.reason` para erro tipado, escala de bateria,
regra do corte crítico, rajada do acelerômetro e `stepsFor`.

### Origem

Minha Lanterna (`1-Apps-Offline-Ads`): PRD + wireframes levantaram 6 gaps. Cinco eram fundação e
entraram aqui; o sexto (overlay "segurar para desbloquear") **fica no app** — é regra de produto,
não capacidade de plataforma. Registro em `docs/backlog.md`.

## 2.146.0 — o erro de rede que culpava a internet do usuário

`mapGenericNetworkMessage`: **"Unable to resolve host" não diz mais "Sem conexão com a internet."**
Agora diz **"Não foi possível encontrar o servidor. Verifique sua conexão e tente novamente."** O
`ConnectTimeoutException` também parou de mandar "Verifique sua internet".

Falha de DNS acontece nos **dois** casos — aparelho offline **e** endereço que não existe (host
errado no build, domínio novo que ainda não propagou, subdomínio de nível a mais que o curinga do
Cloudflare não cobre). A frase antiga escolhia um dos dois e mandava a pessoa conferir o wi-fi
enquanto o problema estava no app.

Motivo do release: o Mirassol Conectado trocou de domínio, o app foi compilado apontando para
`api.mirassolconectado.com.br` antes de a delegação propagar, e a tela disse **"sem conexão com a
internet"** — com o celular online, o backend `healthy` e respondendo 200 no host antigo. Mesmo
sintoma do NeuroCoreX (`api.neurocorex…` em vez de `api-neurocorex…`), que originou a regra de log
de requisição. O fundador leu a tela e perguntou se o servidor tinha caído.

Quem sabe de verdade se há internet é o `ConnectivityObserver`, e quem avisa é o `ConnectivityGate`
— que cobre a tela quando o aparelho está offline, e cujos textos continuam dizendo "Sem conexão com
a internet" porque ali é verdade. Se o gate não está aparecendo, contradizê-lo na mensagem de erro
era o furo.

Histórico de versões. Fonte de verdade viva da superfície de APIs = skill `kmplib-catalog`;
breaking curados = `BREAKING_CHANGES.md`; decisões = `docs/adr/`.

> Nota: este arquivo foi (re)criado na 2.78.0 (auditoria — não havia `CHANGELOG.md` de raiz; a
> história pré-2.78 está no catálogo por versão e no `docs/legacy/CHANGELOG_UI_COMPONENTS.md`).

## 2.145.0 — o dropdown deixa de parecer desabilitado (`readOnly` no AppTextField)

**`AppTextField` ganhou `readOnly: Boolean`** — campo só de leitura, mas com aparência de
HABILITADO. E o **`AppDropdownField`** passou a usá-lo no lugar de `enabled = false`.

**Por que existe.** O campo-vitrine do dropdown (o que mostra a seleção; o toque vai para o `Box`
que abre o menu) era um `AppTextField` com `enabled = false`. Isso o pintava com as cores de
DESABILITADO — texto e borda cinza —, e o dropdown inteiro parecia desligado. Reportado no cadastro
do Cidade Conectada (25/ago/2026): o spinner de bairro "parece estar desabilitado".

`enabled = false` bloqueia a edição PELAS cores de disabled; `readOnly = true` bloqueia a edição e o
teclado **mantendo as cores de campo ativo**. É a distinção certa para qualquer campo cuja escrita
acontece por outro caminho — um seletor, um mapa, um dropdown que o embrulha. Vale para as duas
variantes do dropdown (single e multi), que compartilham o mesmo `AncoraDeMenu`.

Aditivo: `readOnly` nasce `false`, ninguém que já usa `AppTextField` muda.

## 2.144.0 — notificação FIXA (a faixa do pedido em curso)

**`NotificationScheduler.showOngoingNotification(id, title, body, …)`** — a notificação que não sai
ao deslizar nem ao ser tocada, e só desaparece com `cancelNotification`. É a faixa que os apps de
entrega mantêm na bandeja enquanto o pedido está a caminho.

**Por que existe.** Pedida nominalmente no Cidade Conectada (*"eu queria que tivesse a notificação
fixa, igual tenho no iFood"*, 25/ago/2026). A lib tinha `showNotificationNow`, que é um AVISO:
aparece, a pessoa toca ou desliza, e acabou. A faixa do pedido é um ESTADO — enquanto durar, tem de
estar lá, inclusive depois de a pessoa ter tocado nela e voltado.

**Não é um booleano no `showNotificationNow`.** As duas têm ciclos de vida opostos, e um parâmetro
faria o chamador escolher entre dois comportamentos que não se parecem. No Android, o par é
`setOngoing(true)` **com** `setAutoCancel(false)`: o primeiro sozinho ainda some quando a pessoa toca
— e o sintoma seria a faixa desaparecer exatamente para quem a usou.

**Limites, declarados.** Ela não se atualiza sozinha: quem a mantém em dia é o app (a cada leitura
do estado) ou um push; sem nenhum dos dois ela congela no último texto — melhor que sumir, mas não é
acompanhamento em tempo real. No **iOS** não há equivalente na bandeja (o que se aproxima é a Live
Activity, outra API e outra entrega): lá o default do contrato exibe uma notificação comum.

Aditivo: implementação padrão no `interface`, então nenhum consumidor precisa mudar nada.

## 2.143.0 — login social pelo NAVEGADOR, contra o nosso backend

**`SocialBrowserLogin`** (Android + iOS), **`PkcePair`/`PkceCrypto`**, `OwnAuthApi.socialStartUrl()` e
`OwnAuthApi.socialExchange()`. Aditivo: `GoogleAuthProvider`/`AppleAuthProvider` (fluxo nativo)
continuam existindo, e um app pode manter o caminho antigo.

**Por que existe.** No fluxo nativo, o Google identifica o aplicativo pelo par *package + SHA-1* e
exige **um cliente OAuth para cada par**; o projeto do Google Cloud tem teto, e uma família de apps
sobre a mesma base de código bate nele. O sintoma é mudo: o console mostra a impressão digital
cadastrada, o `google-services.json` sai sem o cliente, e o botão do Google devolve
`DEVELOPER_ERROR` num app aparentemente configurado. Com a autorização acontecendo no backend
(backlib 0.84.0), o provedor conversa com **um cliente web só** e nunca vê o aplicativo. Desenho em
`docs/27` do studio.

**Como o app usa:**
```kotlin
val pkce = PkcePair.generate()
val url = api.socialStartUrl(SocialProvider.GOOGLE, appId = "inss-negou", codeChallenge = pkce.challenge)
val codigo = SocialBrowserLogin().authenticate(url, redirectScheme = "brcodecacto.inssnegou")
val tokens = api.socialExchange(codigo, pkce.verifier).getOrThrow()
```

**Navegador do sistema, nunca WebView** (RFC 8252): Custom Tabs no Android,
`ASWebAuthenticationSession` no iOS. WebView embutida enxerga o que a pessoa digita, não compartilha
a sessão do navegador — obrigando a digitar a senha do Google a cada login — e é recusada pelos
provedores.

**PKCE é obrigatório, e o motivo é concreto:** em Android e iOS um esquema de URL pode ser
reivindicado por **mais de um aplicativo instalado**. Sem o `code_verifier`, quem interceptasse o
*deep link* de volta trocaria o código pela sessão. O `verifier` tem 43 caracteres base64url (piso da
RFC 7636 §4.1), e o backend cobra o tamanho.

**Android exige uma Activity de callback** no aplicativo, com `intent-filter` do esquema, chamando
`SocialBrowserRedirect.handleRedirect(uri)` — passo a passo no KDoc de `SocialBrowserLogin.android`.
Ela precisa de `launchMode="singleTask"` e `noHistory="true"`: sem eles, o gesto de voltar joga a
pessoa de novo para dentro do login que ela acabou de concluir.

**iOS não foi validado em host macOS** — alvos Apple não compilam no servidor Linux. O código segue
as APIs oficiais e espelha o `AppleAuthProvider.ios.kt`, mas compilar e testar em aparelho é do Mac.

## 2.142.0 — o botão para de CORTAR o texto que não coube

**`AppButton`, `AppOutlinedButton`, `AppSecondaryButton`, `GoogleLoginButton` e `AppleLoginButton`
passam de altura FIXA para altura MÍNIMA** (`heightIn(min = height)` no lugar de `height(height)`),
e o rótulo ganha `textAlign = TextAlign.Center`.

Os cinco travavam a altura em 56.dp. Rótulo que ocupasse duas linhas era **cortado no meio da
segunda** — sem erro de build, sem aviso, sem log: o botão simplesmente aparecia com meia letra na
borda de baixo. E, quando quebrava, a segunda linha alinhava à **esquerda** dentro de um botão
simétrico, o que fazia o rótulo parecer torto mesmo onde cabia.

**Onde isso aparece, e por que não é caso raro:** dois botões dividindo uma `Row` com
`Modifier.weight(1f)` — o padrão de ações secundárias lado a lado. Com metade da largura, "Ver meu
último resultado" quebra em qualquer telefone. Foi assim que apareceu, no cartão de acesso do
NeuroCoreX (24/ago/2026).

O `height` continua existindo e continua sendo 56.dp: rótulo de uma linha desenha exatamente como
antes. A mudança é **aditiva** — o botão passa a poder crescer, e só cresce quem precisava.

⚠️ Num `Row` de botões lado a lado, um que cresça deixa o vizinho mais baixo. Quem quiser as duas
alturas iguais usa `Row(Modifier.height(IntrinsicSize.Min))` com `Modifier.fillMaxHeight()` nos
botões — ou, melhor, encurta o rótulo.

## 2.141.0 — "não falei com a loja" deixa de chegar como "a loja não tem plano"

**`EntitlementProvider.loadOfferings(): OfferingsOutcome`** — a leitura do catálogo passa a dizer o
que aconteceu, em vez de devolver sempre uma `List`.

Até aqui, `offerings()` fazia `repo.getOfferings().getOrDefault(emptyList())`: a falha do `Result`
era **engolida** e chegava ao app **idêntica** a um catálogo vazio. Quem lia a lista não tinha como
distinguir *"a loja respondeu e não há nada para vender"* de *"não consegui falar com a loja"* —
nem de *"este build não tem billing"* (repositório nulo também virava lista vazia).

**O que isso custava, medido (Torneio de Pênalti, 23/ago/2026):** abrir a tela de assinatura **sem
rede** — situação normal num app usado em campo — disparava `PaymentAlertKind.PaywallSemPlano`,
severidade **Fatal**, título "impossível vender". Pior que o ruído: o `PaymentAlertReporter` envia
**um alerta por tipo por sessão**, então o falso positivo **queimava o alerta verdadeiro** daquela
sessão. No dia em que a loja realmente devolvesse zero pacote depois de publicado, o canal poderia
estar mudo.

- **Novo:** `OfferingsOutcome` (pacote `monetization.entitlement`) — `Disponivel(pacotes)` ·
  `Vazio` · `Falha(mensagem, code)` · `Indisponivel`, com `pacotes`, `catalogoVazioConfirmado` e
  `Falha.incidente` (atalho de `PurchaseErrorCode.isPaymentIncident`).
- **Novo:** `EntitlementProvider.loadOfferings()`, sobrescrito pelos dois providers da lib.
  `RevenueCatEntitlementProvider` mapeia a falha do SDK com o **motivo tipado**; repositório ausente
  vira `Indisponivel`, não `Vazio`. `StubEntitlementProvider` devolve **sempre** `Indisponivel`.
- **Fábricas:** `OfferingsOutcome.dePacotes(lista)` (vazia ⇒ `Vazio`) e
  `OfferingsOutcome.deResultado(Result<List<PurchasePackage>>)` — use esta última também nos apps
  que falam com `PurchaseRepository.getOfferings()` direto, no lugar de `getOrDefault(emptyList())`.

**A régua do consumidor:** só **`Vazio`** autoriza alertar paywall sem plano. `Falha` alerta apenas
quando `code.isPaymentIncident` (`NETWORK_ERROR` **não** é); `Indisponivel` é defeito de build
(chave ausente), que se pega no release, não em alerta de runtime.

**Não é breaking.** `offerings(): List<PurchasePackage>` continua existindo, com o **mesmo
comportamento** — é o caminho certo para quem só desenha a lista. `loadOfferings()` tem
implementação default que delega a `offerings()` (vazia ⇒ `Vazio`), então provider próprio de app
segue compilando e mantendo o que já fazia; para ganhar a distinção, sobrescreva `loadOfferings()`
e deixe `offerings()` como `loadOfferings().pacotes`.

**Migração de quem alerta (5 linhas):** trocar `val pacotes = provider.offerings()` por
`when (val r = provider.loadOfferings())` e mover o alerta de `pacotes.isEmpty()` para o ramo
`OfferingsOutcome.Vazio`.

## 2.140.0 — impressão de anúncio passa a contar o que foi VISTO

**`CustomBannerAd` só registra impressão quando o anúncio fica ≥50% na tela por ≥1 segundo
contínuo** — o critério MRC/IAB para display, o mesmo que a weblib já usava desde a 0.93.0.

Até aqui o gatilho era `LaunchedEffect(ad.id, ad.imageUrl)`, ou seja, **toda entrada na
composição**: troca de tela, volta do background, rotação, retorno pela pilha de navegação. Sem
critério de visibilidade nem de tempo. Um app com banner em cinco telas contava cinco impressões
de uma navegação normal.

**O que isso custava, medido:** no Super 8, em 21/ago/2026, **164 impressões de banner em três
minutos** (16:47–16:50), espalhadas por 3 anúncios. Era uma sessão de teste sendo contada por
render. O número inflava sozinho e o CTR (cliques ÷ impressões) afundava junto — a métrica errava
na direção que faz decidir mal, e o app respondia por metade das impressões do portfólio inteiro.

- **Novo:** `rememberViewableImpressionModifier(key, enabled, onViewable)` (interno ao módulo
  `ads/custom`) e `visibleFractionOf(...)`, a aritmética de visibilidade coberta por teste.
- **Relógio contínuo:** sair da tela antes de completar 1 s zera a contagem — rolar rápido pelo
  anúncio não conta. Depois de contabilizado não dispara de novo enquanto o `ad.id` não mudar.
- **`CustomInterstitialAd` não muda:** ele só existe enquanto `show = true`, então já contava uma
  vez por exibição. Os dados batem — 315 impressões de banner contra 11 de interstitial no mesmo app.

**Não é breaking:** a API pública do `CustomBannerAd` é a mesma. O que muda é o *número* que chega
em `monitoramento.ad_stats` — ele cai, e passa a ser comparável com o do site.

⚠️ **Leitura de dados históricos:** impressão de app anterior a esta versão é **limite superior**,
não contagem. Ao comparar períodos, separar antes/depois.

## 2.138.0 — em own-auth, o wipe já leva a credencial

**`AccountDeletionService(credencialSaiNoWipe = true)`** — o segundo passo da exclusão deixa de
existir onde ele não faz sentido.

O serviço nasceu para app com **Firebase**: apaga os dados (`DELETE /v1/me/data`) e depois a conta no
IdP. Em projeto **own-auth** (`backlib-auth-local`) não há IdP externo: a senha mora na MESMA base
que o wipe apagou. O `deleteAccount()` do `EmailPasswordAuthRepository` responde
`UnsupportedOperation` — de propósito —, e o serviço traduzia essa recusa em
`DataWipedAccountPending`, fazendo o app dizer *"entre novamente para remover o login"* de um login
que **já não existe**. A conta era apagada corretamente e a pessoa saía achando que sobrou alguma
coisa.

Com a flag, o serviço encerra a sessão local (`auth.signOut()`) e devolve `Completed`. Default
`false` — ninguém que use Firebase muda de comportamento.

Achado ao dar ao MinhaFrota o botão de excluir a conta, que ele não tinha em superfície nenhuma
(auditoria de 22/ago/2026).

## 2.139.3 — o botão de tela cheia SAI da janela que já é tela cheia (`fs=0`)

A janela do vídeo já é a tela cheia — mas o YouTube não sabe disso: para ele o player está num
WebView grande, não em fullscreen. Então ele continuava oferecendo "expandir", e o toque **não mudava
o tamanho** (já era tudo): só trocava o desenho das setas. A saída aparecia no segundo toque, agora
com o ícone de "sair". Um passo a mais que não leva a lugar nenhum.

`fs=0` é o parâmetro oficial do IFrame Player API — *"Setting this parameter to 0 prevents the
fullscreen button from displaying in the player"* — e resolve pela raiz: o botão redundante deixa de
existir. A saída passa a ser o **X**, que desde a 2.139.2 vive na raiz e nunca some, e o gesto de
voltar do aparelho.

No modo **compacto** o botão FICA (o default `fs=1`): lá o player é um cartão no meio da tela, e
expandir tem para onde ir.

## 2.139.2 — na tela cheia, "minimizar" agora FECHA (e o X nunca some)

Dois pontos da mesma armadilha: dentro da janela do vídeo, o player já ocupa tudo.

**Sair do fullscreen do embed não fazia nada de visível.** Ao tocar em expandir, o player entra no
seu próprio fullscreen e o tamanho não muda — era tudo, continua tudo; só o ícone vira "minimizar".
Tocar nele devolvia ao container de baixo, do mesmo tamanho: nada acontecia, e a pessoa ficava
presa procurando a saída. Agora `onHideCustomView` **fecha a tela** no modo cheio — quem pede para
minimizar uma tela que É o vídeo está pedindo para sair do vídeo. No modo **compacto** o
comportamento antigo continua: lá o player é um cartão, e sair do fullscreen tem para onde voltar.

**O X passou para a RAIZ.** Ele era filho do container de conteúdo, e o `onShowCustomView` esconde
esse container inteiro: dentro do fullscreen do player, a única saída visível era o controle do
próprio embed. Se ele falhasse — ou se a pessoa não o encontrasse — não sobrava nada. Na raiz, o
fechar está sempre lá.

## 2.139.1 — o botão de tela cheia não fazia NADA: faltava o `onHideCustomView`

O WebView só considera que a página sabe fazer tela cheia quando o `WebChromeClient` implementa
**`onShowCustomView` E `onHideCustomView`**. Com um só — que foi o que a 2.139.0 entregou — o
controle de expandir aparece no player e **o toque não produz efeito nenhum**: nem callback, nem
erro, nem uma linha de log. A presença do segundo método é o que habilita o botão, não o corpo dele;
aqui ele é `= Unit`, porque nenhuma view chega a ser promovida.

Junto, a ordem dentro do `onShowCustomView` foi invertida: **abre a outra tela primeiro, desliga o
embed depois**. O contrário parecia mais limpo (parar o áudio antes de sair), mas colocava uma
recomposição — que destrói o próprio WebView de onde o callback está sendo chamado — entre a decisão
e a abertura.

## 2.139.0 — a tela cheia do player inline é OUTRA TELA (e as 2.138.x foram um beco)

O botão de expandir do `VideoPlayerInline` passa a abrir a **janela do sistema** do `VideoLauncher`.
O player de dentro da página não muda em nada — ele continua onde está, do jeito que está.

### Por que as duas tentativas anteriores estavam erradas

A 2.138.0 promoveu a custom view para o `decorView`; a 2.138.1 mudou para `android.R.id.content` e
escondeu os irmãos. As duas partiam da ideia de expandir o vídeo **dentro da janela do app**, e as
duas quebraram — a segunda com `NullPointerException` em `FrameLayout.onMeasure`, um filho `null` no
`content`.

**A causa não é o fullscreen: é o app ser RESPONSIVO.** Telefone em paisagem passa de `COMPACTA`
para `MEDIA`, e um chassi que troca de composição por classe de janela **reconstrói a árvore inteira**
ao girar. O `AndroidView` do WebView é descartado (o `onRelease` destrói justamente a view que
alimenta o vídeo), o composable sai da composição — e a custom view, pendurada fora da árvore, fica
órfã no meio de um layout pass. O rastro no logcat entrega a sequência: `sairDoImersivo` aparece
**ao ENTRAR** em tela cheia, porque o `onDispose` disparou.

Ou seja: promover tela cheia dentro da árvore do Compose só seria seguro num app que não muda de
layout com a largura — o oposto do que a fábrica faz.

### O vídeo recomeça do zero

Decidido com o fundador: *"se não tiver como continuar o vídeo de onde parou, pode colocar do zero,
não tem problema"*. Retomar a posição exigiria conversar com o player pelo IFrame API e devolver o
instante à outra janela — custo alto para um segundo de diferença.

Ao pedir a tela cheia, o player de dentro da página é **desligado antes** de a outra abrir: sem
isso, os dois áudios tocam juntos.

## 2.138.1 — a tela cheia ficava PRETA (com áudio): faltava esconder o conteúdo de baixo

A 2.138.0 fez o botão de expandir responder, mas o resultado era **vídeo tocando com a tela preta**.
A custom view era empilhada sobre o `decorView` e o `WebView` de 16:9 **continuava vivo e visível
por baixo** — duas superfícies de vídeo disputando a mesma composição de hardware, e quem ganha é a
de baixo, que está recortada no retângulo original.

O conserto é o desenho que a `KmplibVideoActivity` usa há meses: um container preto ocupando tudo, a
custom view dentro dele, e **os irmãos escondidos** enquanto durar. E ele vai para
`android.R.id.content`, não para o decor: é ali que os irmãos são a tela do app — no decor eles são
as barras do sistema.

A lista de escondidos é guardada, e não um "escondi tudo": ao sair, só volta a aparecer o que
estava visível antes de expandir.

Junto veio um `DisposableEffect` que encerra a tela cheia ao sair da tela — sem ele, um gesto do
sistema no meio do vídeo deixava o container pendurado no `content` e o aparelho travado em paisagem.

**E a orientação volta de verdade ao sair.** Restaurar o `requestedOrientation` cru só funciona
quando a Activity tinha orientação FIXA (um app retrato-só volta ao retrato). Quando ela era
`UNSPECIFIED` — o caso de quem não declara `screenOrientation` no manifest, que é a maioria —
devolver `UNSPECIFIED` logo depois de um `SENSOR_LANDSCAPE` forçado deixa a decisão num estado
indefinido, e o aparelho continua deitado. Agora `UNSPECIFIED` vira `SCREEN_ORIENTATION_USER`, que
diz explicitamente "quem manda daqui em diante é o usuário e o sensor".

## 2.138.0 — o botão de tela cheia do player inline passa a FUNCIONAR

`VideoPlayerInline` no Android agora atende `onShowCustomView`/`onHideCustomView`. Antes o
`WebChromeClient` era vazio: o controle de expandir aparecia e **não fazia nada**, porque o pedido
do player caía no chão.

A view vai para o **decorView da Activity**, e não para a árvore do Compose — aqui dentro o player
está confinado ao retângulo de 16:9, que é justamente de onde ele quer sair. No decor ela fica por
cima de tudo, sem disputar camada com composição nenhuma (o mesmo motivo de a tela cheia do
`VideoLauncher` ser uma Activity). Junto vão as três coisas que a tela cheia exige e que ninguém
lembra na primeira versão: **paisagem**, **modo imersivo** e a **orientação anterior guardada** para
ser restaurada na saída — sem ela a página volta deitada depois do vídeo.

**Voltar sai da tela cheia, não da tela**: `BackHandler` ativo só enquanto expandido. Sem ele o
gesto navegaria para trás com o player ainda por cima do decor, e a pessoa sairia da página
continuando a ver o vídeo.

`Context.activity()` percorre a cadeia de `ContextWrapper`: o `LocalContext` do Compose quase nunca
é a Activity direto, e um `as? Activity` seco devolveria `null` — a tela cheia simplesmente não
abriria, de novo em silêncio.

**iOS não mudou, e é de propósito:** o WebKit atende o botão sozinho, promovendo o vídeo ao player
nativo em tela cheia.

## 2.137.0 — o player DENTRO da página, e o toast com cara de banner

**`VideoPlayerInline`** — o vídeo toca no lugar onde ele está, rolando junto com o texto. A capa
vira player no primeiro toque, no mesmo retângulo; a tela cheia continua sendo o botão do próprio
player.

⚠️ **Isto já falhou duas vezes** (`VideoPlayer`/`VideoPlayerDialog`, removidos: piscava, ficava
preto, o áudio tocava por baixo). Três coisas eram a causa, e as três estão resolvidas: a view nasce
**só depois do play** (antes disso é uma imagem, sem processo de renderização nenhum); é memoizada e
**liberada explicitamente** no `onRelease`, que é o que interrompe o áudio ao sair; e **não pode ir
dentro de item de `LazyColumn`** — lista preguiçosa recicla, e o vídeo recomeçaria ao rolar. Quem
usa numa tela rolável usa `Column` + `verticalScroll`.

O `VideoLauncher` continua para quando **assistir é a tarefa** (um curso, uma aula) — e o modo
compacto da 2.136.0 segue valendo.

**`ToastHost(style = ToastStyle.BANNER)`** — o toast com o layout do `AppBanner`, em vez da pílula
sólida. Nasceu de um pedido que se repete: *"quero um toast, mas com o layout daquele cartãozinho"*.
Sem a opção, a saída era um banner fixo no meio da coluna — espaço permanente para uma confirmação
de dois segundos — ou uma pílula que não se parece com nada mais no produto. `ToastData` ganhou
`title`, que só o estilo BANNER desenha.

⚠️ **iOS não compilado**: `compileKotlinIosSimulatorArm64` roda SKIPPED em Linux. O código das duas
plataformas é simétrico e o Android está verde; a validação Apple é do Mac.

## 2.136.0 — o vídeo abre PEQUENO: `play(source, compact = true)`

O `VideoLauncher` ganhou um segundo tamanho. `compact = true` abre a **mesma janela do sistema**,
agora translúcida: o player fica em **16:9 no meio da tela**, o conteúdo de onde a pessoa veio
continua visível por trás, tocar fora fecha, e nada de paisagem forçada nem modo imersivo. O botão
de expandir do próprio player continua ali — quem quiser o modo cheio pede.

**Por que existe** (pedido do fundador no NeuroCoreX, 22/ago/2026): o vídeo de apresentação do
protocolo tem dois minutos e explica a tela em que a pessoa está. Abrir isso em tela cheia, virando o
aparelho e engolindo as barras do sistema, tira a pessoa do lugar onde ela estava lendo. *"Queria que
ele abrisse pequeno... pode abrir até uma tela nova, só que pequena. Se a pessoa escolher deixar em
tela cheia, deixa."*

⚠️ **Isto NÃO é o player embutido na composição** — aquilo continua não funcionando, e é por isso que
o modo compacto é uma janela do sistema e não um `Box` na tela. View nativa de vídeo dentro de uma
árvore Compose (coluna rolável ou `Dialog`) pisca, fica preta e toca áudio por baixo, com os
controles inalcançáveis. O que muda aqui é o **tamanho e a transparência da janela**, nunca o lugar
onde o vídeo vive.

**Duas Activities no Android, de propósito.** `KmplibVideoCompactActivity` existe só para carregar um
`android:theme` translúcido no manifest: translucidez é resolvida quando o sistema cria a janela,
antes do `onCreate` — ligá-la por um extra do Intent daria uma janela opaca com layout compacto, ou
seja, moldura preta em volta do player. No iOS o equivalente é `UIModalPresentationOverFullScreen`
(e não `FullScreen`, que remove a view de baixo da hierarquia e faria o fundo translúcido mostrar
preto).

Aditivo: `compact` tem default `false`, e quem já chamava `play(source)` não muda de comportamento.

## 2.135.0 — "Perto de mim" era um botão mudo: `AppPermission.LOCATION`

**`AppPermission.LOCATION`** (Android `ACCESS_COARSE_LOCATION` · iOS `CLLocationManager` when-in-use)
e o **`LocationProvider` do Android consertado em dois pontos**.

⚠️ **Eram dois defeitos somados, e o sintoma dos dois é o mesmo: nada acontece.**

1. `AndroidLocationProvider.hasPermissionSync()` conferia **só `ACCESS_FINE_LOCATION`**. Um app que
   declara apenas `ACCESS_COARSE_LOCATION` no manifesto — o que a fábrica recomenda para ordenar por
   distância — nunca satisfazia a conferência: a pessoa **permitia**, e `getCurrentLocation()`
   devolvia `null` assim mesmo. A tela então dizia "não foi possível obter sua localização" logo
   depois de o usuário ter concedido. Agora **COARSE ou FINE serve**.
2. `createLocationProvider()` — o que o Koin resolve — nascia **sem `PermissionRequester`**, e só o
   helper Compose `rememberLocationProvider()` tinha um. Provider injetado, portanto, **nunca abria
   o diálogo**: dependia de outra tela já ter pedido. Agora ele pede pelo `PermissionManager` da
   própria lib.

Junto veio o pedido em si como permissão de primeira classe: `rememberPermissionState(
AppPermission.LOCATION)` funciona nas duas plataformas, com negação permanente → `openAppSettings()`,
como as outras quatro. E o `CurrentLocationRequest` passou a `PRIORITY_BALANCED_POWER_ACCURACY` — com
COARSE o Fused não entrega precisão de GPS de qualquer forma, e pedir alta precisão só gastava
bateria e alongava o fix.

**O app continua responsável pela declaração**: `ACCESS_COARSE_LOCATION` no manifesto (Android) e
`NSLocationWhenInUseUsageDescription` no `Info.plist` (iOS). Sem ela o sistema nega **sem mostrar
diálogo** — o mesmo modo de falhar da `CAMERA`.

## 2.134.0 — o app parava de mentir sobre o que o campo de login aceita

**`AuthIdentifierMode`**, **`OwnAuthIdentifierConfig`**, **`OwnAuthApi.identifierConfig()`** e
`LoginState.identifierMode`/`identifierLabel`.

⚠️ **Era um defeito real, não uma capacidade que faltava.** O `LoginScreen` tinha rótulo e teclado
**fixos em e-mail**, e a lib não lia o `GET {authBasePath}/config`. No Meu Barbeiro o portal já dizia
"E-mail ou usuário" e o app dizia "E-mail", com teclado de e-mail, para quem precisava digitar
`joao.silva`. O login **funcionava** (a API aceita os dois) — a TELA é que estava errada, e é a tela
que a pessoa vê.

Agora o campo obedece ao modo: rótulo, exemplo, ícone e `keyboardType`. O **rótulo do servidor
vence** o texto local (sistema configurado como "Matrícula" mostra "Matrícula"), e `LoginTexts` ganhou
`identifierLabel`/`identifierPlaceholder`/`usernameLabel`/`usernamePlaceholder` para o app traduzir.

`identifierConfig()` **nunca lança**: rede fora, backend anterior à 0.80.0 (404) ou corpo inesperado
caem em `EMAIL` — uma tela de login que não abre porque o endpoint do *rótulo* caiu seria trocar um
inconveniente por uma porta trancada. Pelo mesmo motivo o default de `LoginState.identifierMode` é
`EMAIL`: app que não consulta o servidor não muda de aparência sozinho.

É o que mantém **app e portal em sincronia sem republicar nada na loja**: os dois leem a mesma
configuração do mesmo backend.

## 2.133.0 — a senha temporária vira constante exportada

**`TEMPORARY_PASSWORD`** (`br.com.codecacto.kmplib.auth`) — o `"123456"` que só existia como default
de parâmetro do `ForcePasswordChangeDialog` agora é público, e o componente passa a lê-lo de lá.

Existe porque a tela de **quem cadastra** precisa dizer qual é a senha ("passe isto para a pessoa"),
e sem a constante cada app escreve o literal na mão — foi o que começou a acontecer em dois projetos
da rodada do primeiro acesso, cada um com o seu `private const val`. A weblib já exportava o
equivalente desde a 0.139.0; a kmplib estava atrás.

⚠️ Não é segredo: o que sustenta a senha pública é a trava do SERVIDOR, nunca o sigilo dela.

Aditivo.

## 2.132.0 — o primeiro acesso obrigatório, e o login que aceita usuário

Metade mobile da decisão do fundador (21/ago/2026; backend em `backlib-auth-local` 0.80.0): conta
criada pelo painel nasce com a senha temporária da fábrica e o titular define a dele antes de usar o
app.

**`ForcePasswordChangeDialog`** — o diálogo que não fecha. `dismissOnClickOutside` e
`dismissOnBackPress` em `false`, e `onDismiss` vazio: as duas saídas vêm **abertas por default**, e
qualquer uma delas transformaria "obrigatório" em sugestão — a pessoa ficaria num app cujas telas
todas respondem 403, sem nada explicando o motivo. Campo + confirmação (com o olho, que o
`AppTextField` já traz em `isPassword`), erro do servidor **junto do botão**, e vermelho só depois do
primeiro toque em salvar.

**`OwnAuthApi.firstAccessPasswordChange(newPassword, accessToken)`** — troca a temporária pela senha
do titular. Responde com **tokens novos e plenos**: a troca revoga todas as sessões, então quem chama
é obrigado a substituir o par no `AuthSessionStore`. Sem isso, a pessoa define a senha e cai na tela
de login no toque seguinte, o que lê exatamente como falha.

**`OwnAuthTokens.passwordChangeRequired`** — `Boolean` com default `false`, **nunca nulável**: campo
ausente na resposta de um backend anterior desserializa como `false`, que é o correto. Nulável
convidaria ao `!= null` no ViewModel, que devolve `true` para "não veio".

**`OwnAuthSession.passwordChangeRequired`** — a marca chega até a sessão persistida, que é o que o
app observa para abrir o diálogo. Default `false` e não-nulável: sessão gravada por uma versão
anterior desserializa como `false`, que é o correto — quem já usava o app tem senha própria.

**`OwnAuthApi.login(identifier, password)`** — o parâmetro deixa de se chamar `email` e o corpo manda
`identifier` **e** `email` juntos quando o valor tem `@`. Os dois de propósito: um app atualizado
contra um backend ainda não bumpado receberia "usuário ou senha inválidos" para credencial correta.
Chamada posicional não sente a renomeação.

⚠️ **A trava é do servidor, não do diálogo.** O access token da sessão restrita carrega uma claim e o
backend recusa toda rota do produto com `403 PASSWORD_CHANGE_REQUIRED`. O diálogo existe para a
pessoa entender o que fazer.

## 2.131.0 — vídeo que toca DENTRO do app, endereço que pergunta o estado antes da cidade, e o fim de três silêncios

Rodada de correções vinda da leitura do app do NeuroCoreX pelo fundador em 21/ago/2026. O fio comum
de metade delas: **a lib falhava calada** e o app parecia quebrado.

### `VideoPlayer` — o vídeo para de jogar a pessoa para fora do app

Novo, em `ui.components.video`. O que os apps faziam era `UrlLauncher.openUrl(url)`: o toque no vídeo
mandava a pessoa ao navegador ou ao app do YouTube, fora do produto — num app cujo vídeo **é** a peça
que explica o instrumento, sair para assistir é perder a pessoa no meio da explicação.

- `videoSourceOf(url)` classifica em `YouTube` · `File` · `External` (função pura, 6 testes): as
  quatro formas de link do YouTube (watch, youtu.be, embed, shorts), arquivo nosso (`.mp4`/`.m3u8`) e
  o que não sabemos tocar — que continua abrindo fora, de propósito.
- **YouTube toca no IFrame Player API oficial**, em `WebView`/`WKWebView`. Não é atalho: a *YouTube
  Android Player API* foi descontinuada pelo Google e o IFrame API é o caminho que ele mantém —
  extrair a URL da mídia para um player nativo viola os Termos e quebra a cada mudança deles.
- **Tela cheia funciona.** No Android é preciso atender `WebChromeClient.onShowCustomView`: sem isso o
  botão de expandir aparece, a pessoa toca e nada acontece. A orientação volta ao que era no
  `onHideCustomView` — é o detalhe que costuma ficar preso e deixa o app deitado depois do vídeo.
- **Para no descarte** nas duas plataformas: WebView solto continua **tocando** depois de a tela sair.
- Arquivo nosso usa o player da plataforma (`VideoView` / `AVPlayerViewController`) — sem trazer o
  Media3 inteiro para dentro de todo app da fábrica.

### `VideoLauncher` — o vídeo ganha a PRÓPRIA janela do sistema (segunda correção do mesmo dia)

O diálogo não bastou. O relato voltou igual — *"ainda está bugado"* —, e a razão é que um `Dialog`
do Compose **continua sendo uma janela com árvore de composição**: a view nativa de vídeo disputa
camada com ela do mesmo jeito.

O que funciona está em produção no app de Roteiros desde antes disto existir, e agora é da lib:
**`VideoLauncher` + `KmplibVideoActivity`** — uma Activity sem Compose nenhum dentro (no iOS, um
`UIViewController` modal). Lá o `WebChromeClient` consegue entregar tela cheia de verdade, a
orientação vira paisagem, o botão de fechar é view nativa que sempre responde, e o áudio para no
`onDestroy`.

Três detalhes decidem se o embed carrega, e faltavam nas tentativas anteriores: **`origin=` na URL**,
**base igual ao pacote do app** (não ao domínio do YouTube) e **`referrerpolicy`**. Sem eles o IFrame
API recusa a origem e o player abre preto, sem erro nenhum.

A Activity é declarada **no manifest da lib**: uma que o consumidor tivesse de lembrar de declarar é
uma que alguém vai esquecer, com falha só em runtime.

`VideoPlayer` e `VideoPlayerDialog` **saíram** — API que não funciona é armadilha, não legado.
`videoSourceOf`/`youTubeIdOf` ficam, com os 6 testes.

### ~~`VideoPlayerDialog`~~ — a tentativa anterior, mantida aqui como registro do modo de falhar

O `VideoPlayer` embutido no meio de uma tela **não funciona**, e o modo de falhar é específico:
dentro de uma coluna com `verticalScroll`, a view nativa divide a árvore de composição com o
conteúdo que rola por cima. O relato do fundador, horas depois do release: *"pisca, aparece uma tela
toda preta, parece que dá play por baixo, e não dá para parar"* — os controles do player estão
dentro do retângulo que não está sendo desenhado.

`VideoPlayerDialog(source, onDismiss)`: fundo preto, vídeo centrado em 16:9 e um **X** que garante a
saída mesmo que o player não desenhe nada. O gatilho continua sendo um cartão com a capa — que não
custa um processo de renderização a quem talvez nem vá assistir.

Junto, o WebView do YouTube deixou de pedir composição por camada (`setBackgroundColor` preto em vez
de transparente) e perdeu as barras de rolagem próprias: as duas coisas contribuíam para o quadro
sumir dentro de um container rolável do Compose.

### `AddressFields` — estado antes de cidade, cidade com busca, bloco com folga

Três correções no mesmo componente:

- **A ordem inverteu:** o **Estado** vem primeiro. A cidade depende dele, então perguntá-la antes era
  pedir a resposta antes da pergunta — com o agravante de ser campo de texto livre.
- **A cidade virou escolha com BUSCA**, dos municípios do IBGE daquela UF (`BrazilianCities`, que a
  lib já tinha). Digitar livre trazia de volta o que o picker da UF existe para impedir: "Sao Paulo",
  "S. Paulo" e "sao paulo" no mesmo campo, para o mesmo lugar. Sem UF escolhida, o campo fica
  desabilitado e o placeholder diz por quê.
- **8dp de folga** em cima e embaixo: entre os sete campos internos há 12dp, o mesmo respiro que o
  formulário usa entre um campo e outro, então o bloco não terminava em lugar nenhum e o campo logo
  abaixo de "Estado / Cidade" parecia a última linha do endereço. O par web saiu na weblib 0.134.0.

### `AppPickerField` — `searchable`

Campo de busca no topo do sheet, filtrando por rótulo, **ignorando acento e caixa** ("sao" acha "São
Paulo"). Ligue quando a lista passar de umas três dezenas: rolar 853 municípios atrás de um nome é
conferência, não escolha. Reabrir limpa o filtro — filtro preso é o que faz a pessoa concluir que a
cidade dela "não está aí". Lista vazia depois de filtrar diz isso, em vez de um vão branco.

### `rememberImagePickerLauncher` — `onError`, porque câmera negada era SILÊNCIO

Novo parâmetro `onError: (ImagePickerError) -> Unit` (com sobrecarga de um parâmetro só, para quem já
chama). Antes, permissão negada caía num `if (granted)` **sem `else`** e falha de câmera num
`printStackTrace()`: o toque em "Tirar foto" não produzia efeito nenhum na tela. Foi o que aconteceu
no NeuroCoreX, onde o app não declarava `android.permission.CAMERA` — permissão não declarada é
negada pelo sistema na hora, sem nem mostrar o diálogo.

Três motivos tipados: `CAMERA_PERMISSION_DENIED`, `CAMERA_UNAVAILABLE`, `IMAGE_UNREADABLE`. Desistir
(fechar a galeria, cancelar a câmera) **não** é erro e não chama o callback.

⚠️ **Requisito de manifest** que o KDoc agora declara: a opção "Tirar foto" exige
`<uses-permission android:name="android.permission.CAMERA" />` **no app**. O `FileProvider` já vem da
lib — não redeclarar, dois `FILE_PROVIDER_PATHS` na mesma authority param o merge do manifest.

## 2.130.0 — o spinner que faltava, e o fim da parede de chips

`AppDropdownField` e `AppMultiDropdownField` (`ui.components`): menu suspenso **ancorado no campo**,
com a largura medida dele, escolha única ou múltipla.

A lib tinha duas formas de escolher e nenhuma servia a uma lista média e **já ordenada**:
`AppPickerField` abre um sheet que cobre o formulário — certo para as 27 UFs, exagero para os
bairros de uma cidade —, e `AppMultiSelect` desenha um chip por opção, o que em vinte opções vira um
bloco alto onde a ordem alfabética se perde no empacotamento das linhas. Foi o que o fundador
apontou na tela de endereço do Cidade Conectada, em 20/ago/2026: *"aqui poderia ser um spinner"* e
*"esse chip aqui ficou muito feio"*.

Quando usar cada um está na tabela do KDoc de `AppDropdownField`: lista curta ou média e em ordem
conhecida → spinner; lista longa, em que a pessoa **procura** → sheet.

Detalhes que o componente resolve e que cada app resolveria de um jeito: o menu nasce com a largura
do campo (medida em runtime — o `DropdownMenu` do M3 se dimensiona pelo conteúdo e abriria estreito
e deslocado); no múltiplo, o menu **não fecha** a cada marcação; `lockedValues` mantém marcado e sem
alvo de toque o item que a regra do produto já inclui (o bairro onde a pessoa mora, entre os que ela
acompanha) — some da lista quem não existe, não quem já está garantido. O resumo do campo fechado
("Centro, Jardim Aurora +2") é `dropdownFieldSummary`, função pura e testada.

## 2.129.0 — bloco de endereço, seletor de lista longa e a dica que não é erro

Três lacunas que apareceram juntas quando o NeuroCoreX pôs endereço no cadastro do app: a kmplib
tinha as **peças** (`CepVisualTransformation`, `BrazilianStates`, `BrazilianCities`, `AppTextField`)
e não a montagem, e o produto teve de compor um bloco próprio. Isso é o começo de sete campos
divergindo em N apps.

**`AddressFields`** (`ui.components`) — CEP, logradouro, número, complemento, bairro, cidade e UF,
com máscara, autopreenchimento e a lista de estados. Par do `AddressFields` da weblib 0.133.0, campo
a campo: app e portal do mesmo produto falam com a MESMA rota, e um campo com nome diferente grava
`null` sem nenhum erro de compilação denunciando.

- **`Address`** (`brdata`) com `completo`, `temAlgumCampo`, `normalized()` e `cepDigits`.
  `complemento` fica fora do `completo` — é o único campo que um endereço válido pode não ter.
- **O autopreenchimento é conveniência e nunca trava.** `onCepLookup` é **opcional e o transporte é
  do consumidor**: a lib não escolhe o serviço nem embute cliente HTTP, porque um fornecedor embutido
  só se troca publicando na loja — e loja leva semanas. Falha, demora e "não achei" deixam os campos
  editáveis. Exceção lançada no callback é engolida de propósito.
- **`Address.mergedWith(lookup)`** é função pura, fora do composable, e é o que os testes cobrem
  (a fábrica não escreve teste de UI em KMP): **o que já está preenchido vence** — quem corrigiu o
  nome da rua não vê a correção sumir porque o CEP genérico do bairro devolveu outro — e valor vazio
  no resultado **não apaga** o que estava lá. O número nunca vem da busca; por isso ele ganha o foco
  quando ela volta.
- **UF é picker, e trocar de estado limpa a cidade** — senão "Santos/BA" existe sem ninguém notar.

**`AppPickerField`** (`ui.components`) — escolha única em lista **longa**, em bottom sheet. A lib
tinha três formas de escolher e nenhuma servia a 27 itens num formulário: `FilterChipRow` é `LazyRow`
e **rola de lado**, escondendo o que não coube (a pessoa escolhe entre as três que enxerga sem saber
que havia outras), e `AppMultiSelect`/`MultiSelectList` são múltipla escolha. O campo é
somente-leitura — deixar digitar traria de volta o valor livre que o servidor recusa.

**`AppTextField(helperText = …)`** — dica **neutra** sob o campo. Só havia `errorMessage`, então quem
precisava mostrar "Buscando endereço…" usava o campo de erro e **pintava o controle de vermelho**
durante uma operação normal. Erro vence dica, que vence contador: um por vez.

- 15 testes novos (2.067 na suíte). Tudo aditivo — nenhum consumidor precisa mudar.

## 2.128.0 — `OnboardingPager`: slide full-bleed e bullets no slide

Duas coisas que a abertura do Cidade Conectada pediu, e que o componente não tinha:

- **`OnboardingPager(edgeToEdge: Boolean = false)`** — `true` faz o slide ocupar a largura toda, sem
  a fatia do vizinho aparecendo nas bordas, e move o respiro lateral do pager para dentro do slide.
  O default preserva o recuo de sempre, em que o pedaço do próximo slide é dica de "arrasta". A dica
  só funciona com slide de texto sobre o fundo: com ilustração ou cor, a fatia vira retalho no canto.
- **`OnboardingPage(bullets: List<String> = emptyList())`** — 2 ou 3 linhas de detalhe com marca de
  conferido, abaixo da descrição, alinhadas à esquerda (lista centralizada obriga o olho a procurar
  onde cada linha começa). Vazio = slide como sempre foi.

**Supersede o `contentPadding: PaddingValues?` que a 2.127.0 introduziu**, e que viveu uma hora: era
o knob cru (qualquer recuo horizontal reintroduz o peek, então só o valor zero fazia sentido) e não
resolvia o respiro lateral que precisa existir junto. `edgeToEdge` diz a intenção e faz as duas
coisas. Nenhum consumidor havia adotado o parâmetro removido.

Os dois são aditivos: sem passá-los, nada muda.

## 2.127.0 — `OnboardingPager`: o slide pode ocupar a largura toda

`contentPadding: PaddingValues? = null` no `OnboardingPager`. `null` mantém o que sempre foi
(24.dp compacto / 64.dp expandido, com uma fatia do slide vizinho aparecendo nas bordas — a dica de
"arrasta para o lado"); `PaddingValues(0.dp)` faz o slide ocupar a largura inteira.

O recuo era fixo, e para abertura com ilustração ou cartão de fundo ele não é dica: a fatia vizinha
vira um retalho colorido no canto da tela, e o efeito é de tela quebrada. Quem reclamou foi o
fundador, olhando a abertura do Cidade Conectada num aparelho.

Aditivo: nenhum consumidor muda de comportamento sem passar o parâmetro novo.

## 2.126.0 — copiar texto para a área de transferência (`Clipboard`)

`getClipboard().copy(text, label = "Texto")` — Android (`ClipboardManager` + `ClipData`) e iOS
(`UIPasteboard.generalPasteboard`). Só **copiar**: ler a área de transferência é o caminho por onde
um app lê o que a pessoa copiou de outro (uma senha, um código de banco), e nenhum produto da fábrica
precisa disso — quando algum precisar, entra com o motivo declarado.

Faltava porque parecia resolvido: o Compose tem `LocalClipboardManager.setText(...)`. Só que ele está
**depreciado**, e o substituto (`LocalClipboard` + `setClipEntry`) recebe um `ClipEntry` que é
**específico de plataforma** (`ClipData` no Android, `UIPasteboard` no iOS) e **não tem construtor em
`commonMain`**. Ou seja: a alternativa "oficial" não existe em código compartilhado, e cada app
terminaria escrevendo o próprio `expect/actual` — ou ficando na API depreciada, que some no próximo
bump do Compose. 1º consumidor: Cidade Conectada, botão "copiar a chave Pix da loja" no
acompanhamento do pedido de delivery (Onda 7).

No Android reusa o contexto do `UrlLauncherHolder` — é o mesmo `Application` context, e um segundo
holder seria mais um passo de inicialização para o app esquecer e descobrir em produção.

## 2.125.0 — lembrete local que se repete TODA SEMANA (e no fuso do LUGAR, não do aparelho)

`NotificationScheduler.scheduleWeeklyNotification(id, title, body, weekday, hour, minute,
timeZoneId, data, channelId, isCritical, actions)` — `weekday` em **ISO-8601 (1 = segunda … 7 =
domingo)**, o mesmo de `kotlinx.datetime`. Cancela com `cancelNotification(id)`, como os demais.

Faltava o caso mais comum de lembrete que não é dose de remédio: **o compromisso semanal**. Com o
que existia, um "culto de domingo às 18:30" só tinha dois caminhos, ambos errados —
`scheduleDailyNotification` avisaria a pessoa **seis vezes por semana fora de hora**, e
`scheduleNotification` (disparo único) valeria **uma vez**: na semana seguinte o lembrete
simplesmente não vem, sem erro nenhum, e só volta se o app for aberto. Foi o que travou o RF-056 do
Cidade Conectada, onde a preferência "me avise 30 min antes" era gravada e **nunca disparava**.

**O fuso é do LUGAR quando o compromisso é de um lugar.** `timeZoneId` (IANA, ex.:
`"America/Cuiaba"`; `null` = aparelho) existe porque o culto de domingo às 19:00 acontece às 19:00 na
cidade da igreja — quem viajou continua querendo o aviso a tempo de assistir, não uma hora fora. O
lembrete **diário** é o caso oposto (a dose acompanha a pessoa) e por isso segue sem o parâmetro.
Fuso que a plataforma não conhece **cai no do aparelho com log** — errar o horário é ruim, não
agendar é pior.

- **Android:** `AlarmManager` + reagendamento da semana seguinte dentro do `NotificationReceiver`,
  com o agendamento persistido (o `BootCompletedReceiver` restaura depois do reboot/atualização,
  como no diário).
- **iOS:** `UNCalendarNotificationTrigger(weekday/hour/minute, repeats = true)` — quem repete é o
  sistema, com o app fechado. **`NSDateComponents.weekday` conta 1 = domingo**, não ISO: a conversão
  é da lib (passar o número ISO cru desloca todo lembrete em um dia e joga o de domingo no sábado).
  O fuso vai **dentro** dos componentes.
- **Regras puras** (`NotificationRescheduling`, testadas em `commonTest`):
  `nextWeeklyTriggerMillis(weekday, hour, minute, nowMillis, timeZone)`,
  `nextRecurringTriggerMillis(item, nowMillis, fallbackTimeZone)` — fonte única do "quando é o
  próximo", usada ao agendar, ao reagendar depois do disparo e ao restaurar pós-boot — e
  `zoneOf(id, fallback)`. A semana avança em **dias de calendário**, nunca em 7 × 24 h: na semana da
  virada do horário de verão a aritmética de instante desloca o culto em uma hora.
- **Modelo:** `NotificationScheduleKind.WEEKLY` + `ScheduledNotification.weekday`/`timeZoneId`
  (campos **com default** ⇒ registro gravado por versão anterior segue legível) e
  `isWeekly`/`isRecurring`. `plan()` trata recorrente (diário **ou** semanal) por um caminho só e
  `selectWindow()` dá a ambos a mesma prioridade no teto de 64 pendentes do iOS.
- **API com corpo default** na interface ⇒ `NotificationScheduler` escrito à mão (fake de teste,
  decorator) continua compilando.

14 testes novos (`NotificationWeeklyTest`). **O caminho iOS não compila em Linux** (alvos Apple só em
macOS, `HostManager.hostIsMac`) — revisado por inspeção, como o restante do `iosMain`.

## 2.124.0 — `RegisterScreen` ganhou fenda para os campos que só aquele produto pede

`extraFields: (@Composable ColumnScope.() -> Unit)?` — renderizado **entre o telefone e a senha**,
porque o que se pede sobre a pessoa vem antes do que protege a conta.

Nasceu do NeuroCoreX, que precisou pedir data de nascimento, gênero, estado civil e profissão **no
cadastro**: eram os campos de uma tela bloqueante atravessada no caminho de quem ia responder à
avaliação, e o fundador mandou eliminá-la. Sem a fenda, a única saída era o app abandonar a tela da
lib e reescrever o cadastro inteiro — perdendo validação, máscara de telefone, medidor de força de
senha, confirmação e aceite dos termos, para acrescentar quatro campos.

**Não é lugar para regra de auth.** O estado dos campos extras é do ViewModel do produto, e o
`RegisterAction.Submit` continua levando só o que a lib conhece; o que o produto pediu a mais ele
grava depois, com a sessão já aberta. Misturar os dois faria a lib validar campo que ela não define.

Recebe `ColumnScope` para o produto herdar o mesmo espaçamento vertical dos campos da lib.

## 2.123.0 — o telefone que a tela de cadastro pede finalmente sai do app

`RegisterFields.showPhoneField` nasce `true` desde sempre: a `RegisterScreen` mostra o campo, com
máscara e teclado numérico, e o `RegisterViewModel` guarda o valor. O `RegisterBody` do own-auth não
tinha a chave — então o número era coletado e **descartado**. Campo que se pede e se joga fora é pior
que campo ausente: quem preenche acredita que a empresa tem como retornar.

- `OwnAuthService.register(..., phone: String? = null)` — default para não quebrar quem implementa a
  porta.
- `OwnAuthApi.register(..., phone)` e `RegisterBody.phone: String?` — a chave é **omitida** do JSON
  quando não há telefone, para o corpo não dizer "informei nada".
- Vai **como a pessoa digitou**, com máscara. Normalizar na lib decidiria formato de telefone por
  todos os produtos, e a máscara de digitação já é a decisão da fábrica.

Par de servidor: backlib **0.68.0** (`AuthLocalRegisterRequest.phone`). Sem ela o campo viaja e o
backend ignora — nada quebra, mas nada chega. Descoberto no NeuroCoreX, ao igualar o cadastro do
portal web ao do app.

### E o `FeedbackScreen` não preenchia nada, apesar do KDoc

`defaultName`/`defaultEmail`/`defaultWhatsapp` eram lidos só dentro de `remember { }`. Quem chama a
tela lê o perfil de forma assíncrona (`produceState`, `collectAsState`), então na primeira composição
os três são `null`, o `remember` captura string vazia — e o valor que chega depois **nunca entra**. O
"nome e e-mail já vêm preenchidos" era promessa de documentação: na tela, a pessoa redigitava o que o
app já sabia.

Agora a semeadura acontece num `LaunchedEffect`, **uma vez por campo, quando o valor aparece**, e só
se o campo ainda estiver vazio — reaplicar a cada recomposição voltaria por cima do que ela acabou de
escrever. Mesma correção que a weblib 0.131.0 fez no `FeedbackForm`.

## 2.122.0 — `StepTimeline`: o "ANDAMENTO" que três projetos estavam desenhando à mão

Linha do tempo **vertical de andamento**: marcadores circulares ligados por um fio, cada etapa com
título, legenda de horário e estado (**concluída / atual / pendente / cancelada**). É o bloco
"ANDAMENTO" do chamado à prefeitura e o "Status do pedido" do delivery — o mesmo desenho que
Cardápio Digital e Minha Arena já montavam etapa a etapa dentro da tela.

```kotlin
StepTimeline(
    steps = listOf(
        TimelineStep("pub", "Publicado pelo morador", timeLabel = "hoje 09:12", state = StepState.Done),
        TimelineStep("vis", "Prefeitura visualizou", timeLabel = "hoje 10:05", state = StepState.Current),
        TimelineStep("fim", "Prefeitura fecha o caso", timeLabel = "aguardando"),
    ),
)
```

**Não substitui o `TimelineList`** — os dois respondem a perguntas diferentes. `StepTimeline` é
*processo que caminha*: poucas etapas, conhecidas de antemão, incluindo as que ainda não
aconteceram ("previsto 10:20"); interessa **em que ponto estamos**. `TimelineList` é *histórico*:
marcos já ocorridos, coluna de data à esquerda e selo de status; interessa **o que aconteceu e
quando**. Ambos estão agora documentados no catálogo (o `TimelineList` existe desde a 2.33.0 e nunca
teve linha própria no índice — foi por isso que ele quase virou um terceiro componente copiado).

**Decisões que valem a pena saber:**
- **O estado nunca fica só na cor** (WCAG 1.4.1): preenchido × vazado, "✓" × "✕", risco no título da
  cancelada, ênfase de peso na atual. O ícone dentro do marcador cheio é escolhido por **contraste
  WCAG** (`ColorContrast.pickOnColor`) contra a cor do próprio marcador — um app de paleta clara
  não recebe "branco sobre amarelo".
- **Tom por `statusToneColor`**, a mesma fonte única de `StatusBadge`/`ChecklistItem`/`AppBanner`:
  concluída = `SUCCESS`, atual = `WARNING`, pendente = `NEUTRAL`, cancelada = `DANGER`. Zero hex.
- **Etapa clicável tem 48dp** de alvo (`Role.Button`) e o item inteiro é **um nó semântico**, com
  `stateDescription` do estado (`StepTimelineTexts`, i18n).
- A lib **não formata data**: `timeLabel` chega pronto do app, que é quem sabe fuso e idioma.

**Correção no `TimelineList` (mesma rodada).** O fio era feito de dois `Box` de **altura fixa
(40dp)**, que não acompanhavam a altura real do marco: item com título de duas ou três linhas
deixava um **buraco visível** no meio da linha do tempo. Agora os dois componentes pintam o fio no
`drawBehind` do próprio item (`timelineConnector`, interno), então ele acompanha qualquer conteúdo.
O marco também ganhou altura mínima de 48dp (a lista é clicável). **Sem mudança de API.**

## 2.121.0 — a bottom nav ganhou item em destaque (e item desligado)

`AppBottomNavBar` só sabia desenhar cinco ícones iguais. "Ação principal em destaque no centro da
barra" (criar, publicar, anunciar) é padrão recorrente de app — quem precisava dele copiava um
`NavigationBar` inteiro no projeto, e junto vinha o resto: a semântica de aba, o alvo de toque, o
estado desabilitado. Agora é da lib, e é **aditivo**: quem já consome não muda nada.

**O que entrou (tudo com default que preserva o comportamento anterior):**

- `BottomNavItem.emphasis: BottomNavEmphasis?` — `null` (padrão) é o item comum. Preenchido, o ícone
  passa a ser desenhado dentro de uma **pill preenchida inline na barra** (44×34, raio 14 por
  padrão), com o label embaixo como em qualquer outro item. **Não é FAB flutuante** — o realce ocupa
  a mesma célula, então herda o alvo de toque dela; para FAB sobreposto continua valendo
  `Scaffold(floatingActionButton = ...)`.
- `BottomNavItem.enabled: Boolean = true` — item desligado não clica, esmaece (alphas de
  desabilitado do Material 3: 0.38 conteúdo / 0.12 contêiner) e é anunciado como desabilitado pelo
  leitor de tela. Serve para feature que ainda vai ligar numa próxima onda.
- `BottomNavItem.contentDescription: String? = null` — `null` cai no `label` (era o comportamento
  fixo anterior).
- `BottomNavItemState` + `bottomNavItemState(item, selectedRoute)` — a regra de estado exposta e
  testada: **desabilitado vence selecionado** (item desligado não parece ativo só porque a rota
  bateu, o que acontece em navegação de volta ou quando a feature cai por flag).
- `BottomNavDefaults` — tokens de forma (largura/altura/raio/ícone da pill), alvo de toque mínimo
  (48dp) e os alphas de desabilitado.
- Parâmetros novos de `AppBottomNavBar`, todos ao final da lista (compatibilidade posicional
  preservada): `disabledContentColor`, `emphasisContainerColor` (padrão `primaryContainer`),
  `emphasisContentColor` (padrão `onPrimaryContainer`).

**Cor vem do tema, não de hex.** A pill sem cor própria usa `primaryContainer`/`onPrimaryContainer`;
um app com cor de marca própria passa `containerColor = AppColors.current.warning` (ou outro token do
tema) no `BottomNavEmphasis`. O indicador do Material é suprimido **só** no item com realce, para não
empilhar dois fundos no mesmo ícone.

Primeiro consumidor: Cidade Conectada / Mirassol Conectado (Início · Buscar · **Publicar** · Cidade ·
Perfil, com "Publicar" em dourado de marca e desabilitado até a Onda 2).

## 2.120.0 — o modal de "esqueci minha senha" que piscava, e o topo do cadastro alinhado ao login

Três correções da mesma tela, achadas usando o app do NeuroCoreX.

**1. `LoginEffect.Navigate.ToForgotPassword` (novo).** O caminho padrão de "esqueci minha senha" é o
`InputDialog` embutido, ligado por `LoginState.showForgotPasswordDialog`. Mas um app cujo fluxo
continua em outra tela (digitar o código, definir a nova senha) não cabe num diálogo — e, sem um
destino no contrato, a única saída era **usar o flag do diálogo como sinal de navegação**: o
ViewModel ligava, a `Route` observava e navegava.

Isso **pisca na cara do usuário**, e não é sutil: o flag é estado de UI, então o Compose recompõe e
desenha o diálogo no mesmo frame; só depois o `LaunchedEffect` da Route roda, limpa e navega. A
pessoa vê um modal aparecer e sumir sozinho antes da tela certa. Agora o app emite uma **navegação**
— que é o que ele quer dizer — e nenhum diálogo chega a existir. **Quem usa o diálogo da lib não
muda nada.**

**2. O topo da `RegisterScreen` passou de 32dp para 64dp**, o mesmo da `LoginScreen`. Eram
diferentes por descuido, e nas duas telas do mesmo fluxo isso aparece: quem toca em "criar conta" vê
a marca pular para cima.

**3. Do título ao primeiro campo, 16dp em vez de 24dp** (eram dois espaçadores em sequência, 8 + 16).
O de 8 sobrou de quando havia subtítulo entre eles; sem ele, o cadastro abria com um vazio que
nenhuma outra tela de formulário tem.

## 2.119.0 — `ScoreBarRow`: o par mobile da linha "domínio → barra → valor"

Fecha o segundo gap do espelhamento app ↔ portal do NeuroCoreX. A weblib tem o `ScoreBarRow` desde a
0.100.x; no app, cada tela que mostrasse domínios teria de montar a linha à mão — e são quatro (Meu
ICTC, evolução, resultado e o portal do profissional adiante).

O cabeçalho é um **`FlowRow`**, e isso é medido: "Flexibilidade Comportamental" com o rótulo
qualitativo ao lado não cabe em 360 dp, e sem quebra o nome do domínio é espremido até virar **uma
letra por linha** — o defeito real que na versão web levou a página a 17.000 px de altura. O rótulo
desce em vez de estrangular o nome; o nome para em duas linhas para não criar item de altura
imprevisível numa lista de sete.

A barra reusa o `AppProgressBar` (não redesenha trilho nem cor). Fração clampada em `0..max`: valor
acima do máximo satura, negativo vira zero, `max = 0` não divide por zero. 4 testes.

## 2.118.0 — `RadarChart`: o par mobile do radar da weblib

Par de `DomainRadarChart` (weblib 0.118.x). Existe porque a invariante do NeuroCoreX é que **app e
portal do cliente são espelho**, e o radar dos 7 domínios do ICTC nasceu só no web — o app não
desenha gráfico nenhum hoje.

`RadarChart(eixos, series, maximo)` em `ui/components`, commonMain puro, sem lib de gráficos:
Canvas + `TextMeasurer`. Uma ou **duas** séries (T0 × T1 de uma reavaliação sobre a mesma teia);
a partir da terceira, ignora — três polígonos numa teia de sete pontas não se distinguem em tela de
celular, e histórico maior que isso é lista.

**A cor é da SÉRIE, nunca do eixo** — o erro clássico do radar. O polígono é um objeto só; sete cores
nele não codificam nada e destroem a comparação entre duas avaliações, que é o motivo do desenho.
Preenchimento translúcido (22%) para que a série de cima não apague a de baixo. Grade **poligonal**,
não circular: círculo sugere continuidade entre eixos que não existe. Legenda obrigatória com duas
séries, e `contentDescription` no Canvas, que leitor de tela não lê.

A geometria mora em **`RadarChartGeometry.kt`**, provada em 18 testes sem tela — porque o que dá
errado num radar é aritmética: valor acima do máximo **satura** em vez de estourar a caixa, negativo
vira 0 (ponta para dentro leria como o oposto), o primeiro vértice fica no **topo** e o rótulo longo
quebra em duas linhas no espaço mais próximo do meio, ancorado pelo lado que aponta para fora —
"Flexibilidade Comportamental" numa linha só é mais largo que o gráfico inteiro no celular.

## 2.117.0 — log de requisição LIGADO por padrão (regra da fábrica)

Muda o default de `HttpClientOptions`: `enableLogging = true` e `logLevel = INFO`. Quem já passava
as opções explicitamente não muda em nada.

### O caso que originou a regra

O app do NeuroCoreX apontava para `https://api.neurocorex…` quando o host real é
`https://api-neurocorex…` — um hífen. O login ficava girando até o timeout e terminava em "erro de
conexão", e o **logcat não mostrava uma linha sequer**. De fora, "o servidor caiu", "a senha está
errada" e "está batendo num host que não existe" são o mesmo sintoma; sem log de rede, a
investigação recomeça do zero toda vez.

O fundador fechou a regra: **todo projeto da fábrica loga requisição, por padrão.**

### Por que `INFO`, e não `HEADERS`

Porque o default não pode ser o nível que vaza credencial:

- `HEADERS` imprime o `Authorization` — o token de acesso inteiro no logcat;
- `BODY` imprime o corpo do `POST /auth/login`, ou seja, **a senha em claro** (e, num produto de
  saúde, as respostas da avaliação).

`INFO` dá método, URL, status e tempo — o que a investigação precisa, e nada que não deveria estar
ali. Quem quiser mais em depuração local sobe para `BODY` de propósito, sabendo o que imprime.

**Migração:** nenhuma. Apps que montam o próprio `HttpClient` em vez de usar `createHttpClient` não
ganham o log — e é o caso de vários; migrá-los é o passo seguinte, projeto por projeto.

## 2.116.0 — cadastro: o link da política abria os TERMOS, e a logo saía menor que a do login (ago/2026)

Correção de defeito + parâmetro aditivo, os dois na `RegisterScreen`. Nenhuma assinatura quebra:
`logoModifier` tem default igual ao comportamento anterior.

### O link errado (defeito real, afeta todo app que usa a tela)

O texto do aceite marcava os trechos com `pushStringAnnotation` — que só delimita o intervalo — e o
clique morava num `Modifier.clickable` no `Text` **inteiro**, disparando sempre
`RegisterAction.Click.Terms`. Efeito para quem usa: tocar em "Política de Privacidade" abre os
**Termos de Uso**; tocar em qualquer palavra do meio da frase abre os Termos também. A tela monta, o
link pinta de azul e um documento abre — só que o errado, e nenhum build, lint ou teste acusa.

Agora cada trecho carrega o próprio clique (`LinkAnnotation.Clickable` + `withLink`, a API oficial
do Compose para link dentro de texto), e não há mais clique no bloco. A `LoginScreen` nunca teve o
problema porque lá cada link é um `Text` separado com o seu `clickable`.

Reportado pelo fundador em dois produtos independentes (NeuroCoreX e Minha Arena) no mesmo dia — o
que se espera de um defeito que mora na fundação.

### A logo de 120dp

`RegisterScreen` fixava `Modifier.size(120.dp)` enquanto a `LoginScreen` já expunha `logoModifier`.
O app que passa a MESMA logo nas duas telas via a marca encolher ao trocar de tela: um lockup
horizontal cabe inteiro no login (`fillMaxWidth(0.82f)`) e é espremido no quadrado do cadastro.
`logoModifier` agora existe nas duas, com o mesmo nome e o mesmo default.

**Migração:** nenhuma. Quem usa logo-ícone não muda nada; quem usa lockup horizontal passa o mesmo
`logoModifier` que já passa no login.

## 2.115.0 — `AppServiceGate`: manutenção programada e force update contra backend PRÓPRIO (ago/2026)

Aditiva. Nada do `appupdate` existente muda: `AppUpdateGate`, `AppUpdateService` e
`AppUpdateConfig` seguem falando com o admin-api central, com o mesmo contrato.

### O problema

Duas lacunas, e as duas apareceram no mesmo lugar (NeuroCoreX, Onda 10):

1. **Não havia tela de manutenção.** A lib tinha `ConnectivityGate` ("sem internet") e `ErrorState`
   ("deu erro, tente de novo"), mas nada para o estado que o *operador declara*: "o serviço está fora
   de propósito, volta às 8h". Sem isso, uma janela de manutenção chega ao usuário como erro de rede
   genérico — indistinguível de defeito, e sem previsão de retorno.
2. **O force update só sabia falar com o admin-api da fábrica.** `AppUpdateConfig` embute
   `{adminApiBaseUrl}/public/app-version?project=…`. Projeto de **parceria** (NeuroCoreX, Clinnota,
   StatusHub) tem backend e admin próprios: o estado mora lá, e apontar o app para o catálogo central
   significaria manter a mesma configuração em dois lugares — sendo que um deles não é dono do
   produto. Sem alternativa na lib, cada parceria reimplementaria a política E a UI.

### O que entrou

`appupdate/AppServiceGate.kt`:

- **`AppServiceGate(check, key, texts, updateTexts, formatUntil, content)`** — mesma política do
  `AppUpdateGate` (hard bloqueia, soft é dispensável), mas a consulta é do app: `check` é um
  `suspend () -> AppServiceStatus` contra o backend que o projeto quiser. `key` permite refazer a
  consulta.
- **`AppServiceStatus(update, maintenance)`** e **`MaintenanceNotice(message, untilEpochMillis)`**.
- **`MaintenanceScreen`** — tela cheia, com botão de **tentar de novo** (a `HardUpdateScreen` não tem,
  de propósito: da atualização obrigatória só se sai atualizando; a manutenção acaba sozinha).
- **`AppServiceTexts`** — defaults pt-BR; mensagem do servidor tem prioridade.

Duas decisões que valem registrar:

- **Manutenção vence atualização.** Mandar a pessoa à loja durante a janela produz um app novo que
  também não funciona, agora sem explicação nenhuma.
- **Falha na consulta LIBERA.** `check` é best-effort e deve devolver `AppServiceStatus()` vazio
  quando não conseguir perguntar. Um gate que bloqueia por não conseguir consultar transforma
  qualquer soluço de rede numa manutenção fantasma — que ninguém desliga, porque desligá-la exige a
  mesma rede.

### Consumidor

NeuroCoreX (APP-42), contra `GET /public/app?versao=` do backend do projeto.

## 2.114.0 — classe de janela e chassi adaptativo: tablet deixa de ser telefone esticado (ago/2026)

Aditiva. `LocalIsCompact` continua existindo e passa a **derivar** da nova classe — nenhum consumidor
atual muda.

### O problema

A lib só oferecia `LocalIsCompact`: um booleano com corte em 600dp. Com ele, a única coisa que um app
consegue fazer num tablet é **a mesma árvore de composição, mais larga** — trocar `padding` e número
de colunas de uma grade. É a ferramenta do responsivo mal feito.

Três coisas faltavam para um layout de tablet de verdade:

1. **Três classes de janela**, não duas. Um tablet em retrato (~800dp) e um em paisagem (~1280dp)
   não querem o mesmo desenho, e no booleano os dois caem no mesmo `false`.
2. **Navegação lateral** substituindo a barra inferior. Bottom bar em tablet é o sintoma mais visível
   de app esticado: o alvo de toque fica a 25 cm do polegar.
3. **Mestre-detalhe** com os dois painéis compartilhando o mesmo estado — para a rotação
   retrato↔paisagem preservar a seleção em vez de voltar para a lista.

### O que entrou

- `WindowSizeClass` (COMPACTA/MEDIA/EXPANDIDA) + `LocalWindowSizeClass` + `ProvideWindowSizeClass`.
  Limiares 600/840dp — os mesmos do Material 3, **sem** a dependência `material3-window-size-class`,
  que é Android-only: aqui é `BoxWithConstraints` puro, e funciona em Android, iOS e Desktop.
- `windowSizeClassFor`, `gridColumnsFor` e `leituraMaxWidth`: regras PURAS, testáveis sem árvore de
  composição. Os casos do teste são larguras de aparelho real, não números redondos — é nelas que um
  `<=` no lugar de `<` aparece (um telefone de 600dp virando tablet).
- `AdaptiveScaffold`: barra inferior em compacta, navigation rail em média, rail **largo com rótulo
  ao lado do ícone** em expandida. Não é a mesma barra com outro padding: a barra inferior deixa de
  existir e o conteúdo passa a dividir a tela na horizontal.
- `ListDetailScaffold`: painel único em compacta/média, dois painéis em expandida — com o MESMO
  estado de seleção nos dois casos. É isso que faz a rotação preservar o item escolhido.

Tablet em **retrato** não ganha dois painéis de propósito: caberiam, mas cada um sairia com menos de
400dp — duas colunas espremidas, que é pior que uma boa.

### Origem

Pedido do fundador em 16/ago/2026, literal: *"não só expandir e deixar responsivo… eu quero um layout
próprio pra tablet e pra celulares"*. O que ele está recusando tem nome: esticar a tela do telefone.

## 2.113.0 — paywall com slots: teste grátis e Pix do portal cabem na tela canônica (ago/2026)

Aditiva: dois parâmetros opcionais em `PaywallScreen`/`PaywallContent`, ambos `null` por default.
Nenhum consumidor muda.

### O problema

O paywall canônico cobre a loja e só a loja. Dois pedaços do padrão da fábrica não cabiam nele:

- **Teste grátis de 7 dias** (RF72 do Diária Certa, e a regra geral da casa) — não é produto de
  loja: quem concede é o admin-api central, um por conta, para sempre. A lib não tem como conhecer
  esse endpoint.
- **"Assinar por Pix"** nos produtos **own-auth**, em que o pagamento web passa pelo portal e pelo
  Asaas. É o caminho **principal** de cobrança em vários projetos BR — e no app ele não tinha onde
  aparecer.

Sem slot, o caminho que sobrava era o app **reimplementar a tela inteira** para acrescentar um
botão. E a primeira coisa que se perde numa cópia dessas é o `LegalDisclosureSection` — o texto de
renovação automática que a Apple e o Google **exigem** para aprovar o app.

### O que entrou

`beforePlansContent` e `afterPlansContent`, ambos `(@Composable () -> Unit)?`. Renderizam **só no
estado não-premium** (oferecer teste grátis a quem já paga é ruído), em volta do `PlansSection`:

```kotlin
PaywallScreen(
    state = state,
    onAction = viewModel::onAction,
    beforePlansContent = { TesteGratisCard(...) },   // antes dos preços, de propósito
    afterPlansContent = { AssinarPorPixCard(...) },  // depois dos cards, antes do bloco legal
)
```

**A ordem é a decisão, não o acaso.** O teste grátis vem ANTES dos preços: depois deles, quem
decidiu não pagar hoje não rola mais até lá. O Pix vem DEPOIS dos cards, porque no app a loja é o
caminho principal — e ACIMA do bloco legal, para não ficar embaixo do texto de renovação
automática, que ninguém lê.

## 2.112.0 — gerar BR Code Pix: o módulo `pix` passa a cobrar, não só a ler (ago/2026)

Aditiva, um arquivo novo em `pix/`, nenhum símbolo alterado. Fecha o `GAP-DC-M-01` (P0 do **Diária
Certa**, Onda 3) e tem par na weblib (`GAP-DC-W-02`).

### O que faltava

O módulo `pix` sabia **ler** plaquinha — validar CRC, comparar recebedor, desconfiar de um QR
trocado. Não sabia **emitir**. Todo app que precisa cobrar (a diarista mandando o QR da diária, o
prestador anexando o Pix ao orçamento) montaria a string EMV na mão, e é o tipo de código em que um
detalhe errado produz um QR que **abre no app do banco e falha na confirmação** — o pior desfecho,
porque parece que funcionou.

### O que entrou

- **`buildPixBrCode(PixCharge): PixBrCodeResult`** — BR Code **estático** com chave, nome, cidade,
  valor opcional, `txid` e descrição. Resultado tipado (`Ok`/`Invalid` com motivo), na mesma
  disciplina do `parseBrCode`: a tela precisa dizer *qual* recusa aconteceu, não "erro ao gerar".
- **`PixCharge`**, **`PixBrCodeError`**, **`PixBrCodeResult`**.

O caso **dinâmico** fica de fora de propósito: exige um PSP emitindo a cobrança e devolvendo a URL
do payload; oferecer a API sugeriria que o cliente monta isso sozinho.

### As decisões que fazem o QR funcionar no banco de verdade

- **Nome e cidade são normalizados para ASCII maiúsculo.** Não é estética: o tamanho do TLV é
  contado em **caracteres** e o CRC é calculado sobre **bytes UTF-8**. "Rosângela" tem 9 caracteres
  e 10 bytes, e emissores divergem sobre qual das contas escrever — normalizar tira a questão da
  mesa. A tabela de acentos é explícita porque `commonMain` não tem normalização Unicode e
  `java.text` mataria o iOS.
- **Valor com separador único seguido de 3 dígitos é RECUSADO, não adivinhado.** `"1.234"` pode ser
  mil duzentos e trinta e quatro ou um valor de 3 casas; chutar erra por **mil vezes** em alguma
  direção. Um leitor de moeda de tela pode arriscar — aqui se emite instrumento de pagamento, e a
  recusa alta tem conserto ("escreva 1234,00") enquanto a cobrança errada não tem. `"1.234,50"` (os
  dois separadores) e `"1.234.567"` (o mesmo repetido) são inequívocos e passam.
- **Valor zero é recusado.** `54 = "0.00"` não é "sem valor": é uma cobrança de zero real, que o
  banco recusa na confirmação. QR sem valor se pede com `amount = null`.
- **A descrição encolhe para caber nos 99 caracteres do template**, em vez de derrubar a geração:
  EMV MPM não tem tamanho estendido, e recusar o pagamento por causa de um texto decorativo que
  metade dos leitores nem exibe seria trocar um problema cosmético por um pagamento que não acontece.
- **`txid` fora do alfabeto (A–Za–z0–9) ou acima de 25 recusa.** Um traço vindo de "PEDIDO-42" passa
  pelo parser da lib e quebra no PSP.
- A assinatura sai do `PixCrc.sign` — nunca concatenada à mão. O CRC cobre `"6304"`, e errar isso é
  o modo clássico de "todo QR dá inválido".

### Cobertura

22 casos, e a prova principal é o **ida-e-volta**: o payload gerado é relido pelo `parseBrCode` da
própria lib (que já é ancorado no *check value* publicado do CRC-16/CCITT-FALSE). Tamanho errado,
campo fora de ordem ou CRC que não fecha reprovam ali. Comparar com string colada provaria só que
ninguém mexeu no arquivo. Suíte completa da lib: **1964 testes, 0 falhas**.

## 2.111.0 — questionário e documento: os dois componentes que faltavam para o app não redesenhar nada (ago/2026)

Aditiva, dois módulos novos, nenhum símbolo alterado. `GAP-NCX-M-01` e `GAP-NCX-M-02` (P0 do
**NeuroCoreX**), ambos com par na weblib (`GAP-NCX-W-01` e o `<iframe sandbox>` do portal).

### `LikertScaleField` — a escala de N pontos com âncoras (`ui/components`)

Um instrumento repete esta fileira **dezenas de vezes na mesma sessão**, e é a repetição que decide
se a pessoa termina de responder. O que existia na lib era o `SegmentedControl`, e ele é o componente
errado de quatro formas ao mesmo tempo: visualmente **unido** (parece seletor de modo, não régua de
intensidade), sem **âncoras**, sem estado **"não respondida"** (`selectedIndex: Int` obriga a inventar
um selecionado) e com semântica de **botão** — o leitor de tela anuncia *"botão 3"*, que não
significa nada para quem não vê a régua.

Novidade: `LikertScaleField` + a lógica pura `likertPoints` / `likertColumnCount` / `likertRowRanges`
/ `likertSlotMinSize` / `likertOptionState` / `likertOptionBorderWidth` / `likertOptionBold` /
`likertOptionLabel` / `likertOptionDescription`, mais `LikertScaleDefaults`, `LikertScaleTexts`,
`LikertOptionState` e `LikertScaleTestTags`.

Quatro decisões que valem mais que a lista de símbolos:

· **A escala é parametrizada, e nada é 1..5.** `min`/`max`/`optionLabels`/`startAnchor`/`endAnchor`
  vêm do cadastro do instrumento. `1..5`, `1..7`, `0..10` (NPS/EVA) e `-2..2` (neutro em zero) são
  todos reais; literal `"nunca"`/`"sempre"` no código quebra no primeiro protocolo diferente.
· **O alvo NUNCA encolhe — a regra de quebra está escrita, não implícita.** Numa linha única com peso
  igual, cinco alvos numa tela de 320dp viram cinco alvos de 20dp: passa em build, passa em review, e
  só falha no dedo de quem responde. O componente mede a largura e decide quantas opções cabem por
  linha preservando os 48dp, quebrando em linhas **equilibradas** (10 pontos viram 5 + 5, não 6 + 4).
  No pior caso empilha uma por linha. Os números não são abreviados: são a resposta que vai para o
  instrumento. O espaço do **anel de foco** entra na conta (`likertSlotMinSize`) — medir só o alvo
  devolve uma coluna a mais do que cabe e o alvo encolhe em silêncio.
· **Acessibilidade é o componente, não um adorno.** `selectableGroup()` + `Role.RadioButton` com
  `selected` real; cada alvo anuncia *"Às vezes, opção 3 de 5"*; o grupo anuncia **"Não respondida"**
  enquanto `value` for `null` (num formulário de 28 perguntas é a única forma de saber onde se parou);
  estado nunca só por cor (borda do **dobro** da espessura + número em **negrito**); altura mínima,
  nunca fixa, então o texto acompanha o `AppTheme(fontScale = ...)`.
· **Escala impossível não some da tela.** `min >= max`, um ponto só ou mais de 15 pontos viram a
  mensagem "Escala inválida" com aviso no log. Renderizar nada faria o defeito parecer "a pergunta não
  carregou", e ninguém descobriria que o cadastro é que está errado.

O **card** em volta continua sendo do app (uma tela põe a pergunta num `Card`, outra numa lista);
`isError` sinaliza o campo, não a moldura. E os ids de automação são **por pergunta**
(`testTag = "q12"` ⇒ `q12-opcao-3`): id fixo apareceria 28 vezes na mesma rolagem e o teste
responderia a pergunta errada, ficando verde.

### `HtmlDocumentView` — documento HTML do backend, na tela (`ui/components/html`)

Para exibir dentro do app um documento cujo layout precisa ser **idêntico** ao do PDF (laudo,
relatório, contrato, fatura). Quando o requisito é fidelidade, reimplementar as seções em Compose é
justamente o que produz a divergência: o PDF nasce do mesmo HTML no servidor, e duas implementações
do mesmo documento sempre acabam diferentes — primeiro num detalhe, depois num número.

Novidade: `HtmlDocumentView` (duas sobrecargas) + `HtmlDocumentSource` (`Html` / `Url`),
`HtmlDocumentState`, `HtmlDocumentError`, `HtmlDocumentTexts`, `HtmlLinkDecision`, `HtmlBlockReason`
e as puras `htmlLinkDecision` / `htmlIsSameDocument` / `htmlUrlScheme` / `clampHtmlDocumentZoom` /
`htmlDocumentZoomPercent`.

**Padrão-ouro:** `android.webkit.WebView` e `WKWebView` — o componente nativo de cada plataforma,
nenhum renderizador de HTML próprio. E as travas que separam um **visualizador de documento** de um
navegador embutido vêm ligadas:

· **JavaScript desligado** por padrão. Documento é conteúdo; conteúdo que executa código dentro do
  app é superfície de ataque, e um relatório não precisa de script.
· **Navegação externa interceptada:** link para fora é devolvido ao app e aberto no **navegador do
  sistema**, com barra de endereço e botão de voltar. **Âncoras internas continuam funcionando** —
  sem essa distinção, o índice de seções do documento (que é como se navega um relatório de 20
  seções) pararia de funcionar, que é o defeito mais provável de um visualizador que "bloqueia links".
· **Esquemas perigosos recusados sempre** (`javascript:`, `file:`, `content:`, `data:`, `blob:`),
  inclusive com a navegação externa liberada.
· **Sem rastro em disco:** `WKWebsiteDataStore` não persistente no iOS; sem storage e sem cookie de
  terceiro no Android.
· **Ciclo de vida:** o componente nativo é liberado com a tela (carregamento parado, delegates soltos,
  `destroy()`). `WebView` esquecido segura o documento — que é dado pessoal — em memória.

**Autenticação nas duas formas**, porque o documento é dado sensível e não vai ser público:
`HtmlDocumentSource.Url(url, headers)` para URL assinada de curta duração, e
`HtmlDocumentSource.Html(html, baseUrl)` — **preferível** — para o HTML buscado com o
`DomainApiClient`, que é o que dá renovação de token, tratamento de 402 e cache local para releitura
offline. A limitação de cabeçalho em subrecursos (nenhum dos dois WebViews o propaga) está declarada
no KDoc em vez de virar surpresa.

**Zoom acompanha o `fontScale` do app**, com a diferença de plataforma declarada: Android tem
`textZoom` e amplia só o texto; o `WKWebView` não tem equivalente sem executar JavaScript, então o
iOS usa a API oficial `pageZoom` e amplia a página inteira. Nenhuma das duas exige JS ligado.

A regra de navegação é **uma função pura consultada pelos dois `actual`** — é o que impede Android e
iOS de divergirem justamente na parte de segurança, onde a divergência não aparece em teste de tela.

`LikertScaleTest` (23) + `HtmlDocumentTest` (19); suíte 1943/0. Controle negativo: removendo a regra
de âncora do `htmlLinkDecision`, 3 testes falham.

**Pendente de macOS:** o `actual` iOS do `HtmlDocumentView` foi escrito conforme as APIs oficiais mas
**não compila em Linux** (`GAP-KL-M-HTMLDOC-IOS-VALIDATE`).

## 2.110.0 — a grade passa a dizer o que a faixa É, não só que ela está bloqueada (ago/2026)

Aditiva. `GAP-MA-M-01` (P0 do **Minha Arena**), par exato do `GAP-MA-W-01` da weblib.

O `AppTimeGridScheduler` tinha **uma** camada de fundo, e ela só sabia negar: `ScheduleBlockVariant`
é `{ OffHours, Block }` — duas variantes da mesma frase ("aqui não pode"), sem rótulo próprio e sem
legenda. Faltava a outra metade, que é o conceito estruturante de qualquer agenda com propósito por
horário: **o que aquela faixa daquela coluna É** (Aluguel · Clubinho · Social · Aula · Bloqueado). Sem
ela o app só consegue dizer "indisponível", e a pergunta que o operador faz o dia inteiro — *"o que
acontece nesta quadra às 19h?"* — não tem resposta na tela.

**Novidade:** `layers: List<ScheduleLayer>` + `layerLegend: ScheduleLayerLegend` no scheduler (últimos
parâmetros, com default — nada muda para quem já consome), o componente `ScheduleLegend` e a lógica
pura `resolveLayerStyle` / `layerLegendEntries` / `layerAtMinute` / `flattenLayers` / `layerRange` /
`indistinguishableLayerKinds`, mais os tipos `LayerTone`, `LayerPattern`, `ScheduleLayerStyle` e
`ResolvedLayerStyle`.

**A API foi acordada com o `lib-web` na mesma rodada** (weblib `GAP-MA-W-01`, `src/calendar/layers.ts`):
nomes, semântica, escada de resolução, regra de sobreposição, ordem da legenda e as opacidades de
preenchimento/textura são os mesmos nas duas plataformas. Divergir aqui condenaria o produto a duas
grades que se comportam diferente no app e no portal.

Quatro decisões que valem mais que a lista de símbolos:

· **Destinação NÃO é variante de bloqueio.** `ScheduleLayer` é tipo próprio, com `kind` **aberto**
  (o domínio declara quantos propósitos quiser) e rótulo. Modelá-la como um terceiro
  `ScheduleBlockVariant` teria custado uma linha e devolvido o consumidor ao ponto de partida: a
  grade voltaria a saber apenas que a faixa está indisponível. As duas camadas convivem — destinação
  é a regra da semana (fundo), bloqueio é a exceção pontual por cima.
· **Textura, não só cor.** `LayerPattern { Solid, Dots, Stripes, Hatch }` desenhado sobre o
  preenchimento. Não é enfeite: a paleta é do cliente, e numa arena de marca vermelha "Aluguel" e
  "Bloqueado" seriam o mesmo retângulo se a única diferença fosse o tom (WCAG 1.4.1). E `LayerTone`
  é enum **próprio**, não o `StatusTone` dos selos: os cinco tons semânticos resolvem para tokens
  semânticos do tema (nunca para a cor de marca, senão "Aluguel" numa arena vermelha voltaria a
  colidir com "Bloqueado"), e `Primary`/`Accent` existem justamente para a destinação que **quer** a
  cor do produto.
· **Sobreposição é resolvida, não empilhada.** Em `flattenLayers`, **a última faixa vence** no trecho
  comum: o app empilha *padrão semanal* e depois *exceção do dia* na mesma lista e obtém "terça é
  Aluguel, mas nesta terça das 14h às 16h é Bloqueado — chuva", sem recortar faixas na mão. Um minuto
  tem, portanto, **uma** destinação. (No web o empilhamento do DOM basta; no Compose duas superfícies
  translúcidas **somam** opacidade e o trecho comum sairia manchado — achatar é o que mantém as duas
  plataformas visualmente iguais, com a MESMA regra.)
· **`layerAtMinute` responde pela MESMA regra que está desenhada.** É com ele que o consumidor recusa
  uma reserva numa faixa que não é de aluguel. Com duas resoluções, o que a pessoa vê e o que o app
  decide divergiriam exatamente na sobreposição — onde alguém já pensou no assunto e escreveu a
  exceção.

Sem legenda declarada, toda destinação sai neutra e lisa — e é **de propósito** que não há um default
"esperto" de textura: `indistinguishableLayerKinds` acusa os `kind` que compartilham tom **e** textura
(são o mesmo retângulo, com a legenda mentindo) e o scheduler **avisa alto** no log. Melhor o defeito
aparecer do que a grade parecer decorada.

Outra decisão que só aparece no uso: **destinação NÃO estica a janela da grade**, ao contrário de um
evento. Evento expande a janela porque nada pode sumir da agenda; destinação é fundo, e uma arena que
declara "Social das 00:00 às 24:00" transformaria a grade em 24 horas e destruiria a leitura das horas
em que algo de fato acontece. Ela é **recortada** à janela (`clipToWindow`, novo em `CalendarLayout`) —
sem isso, faixa que começa antes nasceria como uma tira grudada no topo e faixa que termina depois
desenharia para fora da coluna.

Correção de vizinho na mesma rodada: `HatchedBlock` (bloqueio) desenhava as diagonais **sem recorte**;
elas são traçadas de propósito para fora dos limites e vazavam sobre a faixa vizinha.

`CalendarLayersTest` (**33**). Controle negativo: trocando "a última vence" por "a primeira vence" em
`flattenLayers` e `layerAtMinute`, **3** falham. Suíte da lib: 1901 testes, verde.

## 2.109.0 — login também vira fluxo automatizável (ago/2026)

Aditiva. Fecha o outro lado do par: pagamento já era automatizável desde a 2.108, e login — o outro
fluxo em que uma quebra silenciosa custa cliente — só dava para testar procurando TEXTO na tela
("Entrar", "E-mail"), o que quebra a cada ajuste de copy e em cada idioma, fazendo o teste "achar"
um defeito que não existe.

`LoginTestTags` (mesmo desenho de `PaywallTestTags`) e as tags plantadas na `LoginScreen`:
`login-input-email`, `login-input-senha`, `login-btn-entrar`, `login-btn-esqueci-senha`,
`login-btn-cadastrar`, `login-btn-google`, `login-btn-apple` e `login-erro`.

Duas decisões que vêm da experiência do paywall:

· **um id por provedor social**, nunca um compartilhado — id único faria o teste tocar no primeiro
  botão da tela e passar verde tendo exercitado o provedor errado, porque "entrou" é verdade nos dois
  casos;
· **o erro tem id próprio**, e é ele que distingue "a tela não abriu" de "a tela abriu e recusou a
  senha". Sem isso, um teste de credencial inválida não consegue afirmar que o app AVISOU — e login
  que falha em silêncio é o defeito que ninguém percebe até o cliente reclamar.

O vocabulário é o mesmo do lado web (prefixo `login-`, minúsculo, com hífen), para um flow servir app
e portal do mesmo produto sem tradução de seletor. Um teste trava a convenção e a unicidade.

Nada muda para quem já usa a `LoginScreen`: só entram `Modifier.testTag`, que o `AppTheme` já expõe
como `resource-id` desde a 2.107.0.

### Corrigido no caminho: a lib não compilava para iOS desde a 2.105.0

Três erros, invisíveis porque nada compila iOS no servidor e ninguém compilou iOS desde então —
achados pela primeira build Apple de verdade (a captura do print de review):

· `NSNumber.numberWithBool(...)` não existe no Kotlin/Native (o que existe é o inicializador);
· as constantes `VNBarcodeSymbology*`/`AVMetadataObjectType*` chegam como `String?`, e `listOf`
  produzia `List<String?>`;
· campo em `companion object` de subclasse de tipo Obj-C é proibido (`FilePicker`).

### kmplib-testing: o gancho de loja passa a existir no iOS

`PurchaseTestHooks` ganhou par em `iosMain`, com a amizade de compilador estendida às compilações
nativas — `-friend-modules <path>` (a forma do Kotlin/Native; a `-Xfriend-modules` da JVM é aceita
calada e não faz nada) apontando para o KLIB, que é um **diretório**, não um arquivo `.klib`. A
visibilidade do `initializeWith` continua `internal`.

## 2.108.1 — as constantes que faltavam para o id de teste ser usável (ago/2026)

Aditiva, e é a segunda metade da 2.108.0. Aquela versão plantou as tags e expôs `plano(plan)` /
`botaoAssinar(plan)` — mas **do lado do teste não existe um `PaywallPlan` para passar**: o teste não
constrói o estado da tela, ele lê a tela. A alternativa real era redigitar
`"paywall-btn-assinar-mensal"` dentro do `@Test`, que é exatamente o acoplamento por string que o
objeto existe para evitar.

`PaywallTestTags` ganhou os seis ids canônicos prontos: `PLANO_MENSAL`/`_SEMESTRAL`/`_ANUAL` e
`BOTAO_ASSINAR_MENSAL`/`_SEMESTRAL`/`_ANUAL`. Um teste novo trava as constantes contra o que a função
gera — se as duas fontes divergirem, o teste do app passa a procurar um id que a tela não emite, e o
vermelho diz "elemento não encontrado", mandando investigar a tela em vez do id.

## 2.108.0 — o paywall canônico ganhou os ids de teste (ago/2026)

**Aditiva.** Nenhuma assinatura mudou; só `Modifier.testTag` a mais na árvore.

### O gap que a 2.107.0 deixou

A 2.107.0 fez as `testTag` virarem `resource-id` — mas **o paywall canônico da lib não tinha tag
nenhuma**. Como é a `PaywallScreen` quem renderiza o card e o CTA, o app não tinha como plantá-las: o
teste sobrava selecionar pelo texto do rótulo, e o rótulo do CTA é **o mesmo nos três planos**. Na
prática isso significa `onAllNodesWithText("Assinar")[0]` — tocar no primeiro da tela, que é
**comprar o plano errado** com o teste passando.

### `PaywallTestTags` (público) + tags plantadas

| Id | Onde |
|---|---|
| `paywall-plano-<sufixo>` | card de cada plano |
| `paywall-btn-assinar-<sufixo>` | CTA de cada plano |
| `paywall-assinatura-ativa` | bloco "assinatura ativa" |
| `paywall-btn-gerenciar-assinatura` | CTA de gerenciar |
| `paywall-btn-restaurar` | restaurar compras |
| `paywall-sem-planos` | **paywall vazio** (a suíte precisa distinguir isto de "a tela não abriu") |
| `paywall-erro` | card de erro |

**O sufixo vem da DURAÇÃO** (`mensal`/`semestral`/`anual`), não do `PaywallPlan.id` — o `id` é o
`packageId` da loja (`$rc_monthly`) ou um id interno que varia por projeto, e id de teste com `$` e
nome diferente em cada app não é vocabulário comum. Duração não-canônica (um `lifetime`, o
`$rc_three_month` residual do Super 8) cai no próprio id **sanitizado**: a verdade honesta, em vez de
um sufixo inventado que colidiria com outro plano na mesma tela.

São os **mesmos nomes** que a `PricingTable` da weblib emite (0.106.0): app e portal do mesmo produto
se automatizam com um vocabulário só. A constante é pública de propósito — teste que redigita a string
do id quebra em silêncio no dia em que a lib mudar de nome.

5 testes cobrem o contrato, inclusive "três CTAs, três ids" e "dois planos não-canônicos não colidem".

## 2.107.0 — a automação de UI enxerga as `testTag`, e o teste consegue simular compra (ago/2026)

**Aditiva** — nenhuma assinatura mudou, nenhum consumidor precisa tocar em código para continuar
funcionando. Duas coisas que a plataforma de automação de QA (`AUTOMACAO-QA-PLANO-EMPRESA.md`)
esperava da lib e que não existiam.

### 1. `testTagsAsResourceId` na raiz da hierarquia (`AppTheme`)

`Modifier.testTag("paywall-btn-assinar")` **não virava `resource-id`**: no Android a tag só aparece
na árvore de acessibilidade — que é o que o `uiautomator` lê — se algum ancestral declarar
`testTagsAsResourceId = true` na sua `semantics`. Sem isso, Maestro e Appium **não veem tag nenhuma**,
e o flow só consegue se ancorar em **texto de tela** — que quebra no dia em que alguém melhora o copy.

Agora o [`AppTheme`] embrulha o `content` com `WithTestTagsAsResourceId` (`expect/actual`; no Android
um `Box` com a `semantics`, no iOS **no-op**, porque lá o Compose já publica a tag como
`accessibilityIdentifier`). Uma linha na lib em vez de 28 cópias nos apps — e as tags que o Influencer
já tinha plantadas, inertes desde então, passam a valer sem o app ser tocado.

**Ligada sempre, não só em debug**, de propósito: condicionar a `BuildInfo.isDebug` faria o seletor
por id funcionar no emulador e falhar no build da **faixa alpha** — que é justamente o build que a
suíte de pagamento é obrigada a usar, porque o Play Billing não inicializa em app instalado de lado.
O que se expõe são nomes de elemento de UI, não segredo; a árvore de acessibilidade do Compose já é
legível por qualquer serviço de acessibilidade, com ou sem a flag.

**Como conferir:** `maestro studio` (ou `adb shell uiautomator dump`) passa a mostrar `resource-id`
com as tags plantadas.

**Armadilha que vale para TODO paywall do portfólio:** `paywall-btn-assinar` é uma string só, mas o
paywall tem **um botão desses por plano**. Selecionar pelo id pegaria o primeiro da tela e
**compraria o plano errado sem o teste perceber**. Ancore no card (`childOf: paywall-plano-mensal`)
ou dê sufixo de plano ao botão (`paywall-btn-assinar-mensal`), que é o que a `PricingTable` da weblib
passou a emitir na 0.106.0.

### 2. `br.com.codecacto:kmplib-testing` — artefato novo, só de teste

Nenhum teste instrumentado do portfólio conseguia **simular uma compra** sem tocar na loja: o app lê
`PurchaseManager.repository`, cujo campo é privado e só é escrito por um `initialize` que configura o
SDK nativo. Era por isso que o `FakePurchaseRepository` do Super 8 existia **sem ser referenciado em
lugar nenhum** e que a suíte `pagamento-e2e` só chegava a "o paywall abriu".

O artefato novo traz:

| O que | Onde | Para quê |
|---|---|---|
| `PurchaseTestHooks.instalar/limpar` | `androidMain` | instala a loja de teste (e zera no `@After` — `PurchaseManager` é `object`, o estado vaza entre testes) |
| `FakePurchaseRepository` | `commonMain` | dublê com **cenários nomeados**: `comOfertas`, `compraQueDaCerto`, `compraCancelada`, `compraQueFalha(codigo)`, `jaAssinante`, `semOfertas` (paywall vazio), `ofertasQueFalham` |

Consumo:

```kotlin
// composeApp/build.gradle.kts
androidTestImplementation("br.com.codecacto:kmplib-testing:2.107.0")

// TestApplication.onCreate() / @Before — antes de a tela ser composta
PurchaseTestHooks.instalar(FakePurchaseRepository.compraQueDaCerto())
```

**Por que artefato separado, e não uma API nova na kmplib.** O gancho troca a implementação que
decide **se alguém é assinante**. Publicado na lib de produção, seria um caminho para injetar "é
premium para todo mundo" alcançável em build de release, por qualquer código do app ou por uma
dependência dele. Como artefato separado, declarado só em `androidTestImplementation`, ele **não
existe no APK/AAB de release** — e isso é verificável:

```bash
unzip -p app-release.aab "base/dex/classes.dex" | strings | grep -c PurchaseTestHooks   # 0
```

**A visibilidade da kmplib NÃO foi afrouxada.** `PurchaseManager.initializeWith` segue `internal`; o
módulo de teste o alcança por **friend modules** (`-Xfriend-paths`, o mecanismo oficial do compilador
para dar acesso a `internal` sem torná-lo público). O caminho amigo não é escrito à mão: é **filtrado
do próprio classpath** de compilação ("amigo é toda entrada que vem da pasta de build da `:kmplib`"),
o que o torna imune a mudança de layout interno do AGP.

A amizade é aplicada **só às compilações Kotlin/Android**, de propósito — é onde o `androidMain` (o
único código que usa `internal`) é compilado, e assim a release oficial, que sai do Mac com os alvos
Apple, não depende desse ajuste. O `FakePurchaseRepository` é `commonMain` e não precisa de amizade
nenhuma: implementa a interface pública `PurchaseRepository`.

**Dívida conhecida:** o gancho é Android-only. Quando existir suíte iOS que precise instalar o dublê,
entra o `actual` de lá junto com a amizade para as compilações nativas.

15 testes novos cobrem os cenários do dublê — inclusive `pacotesComprados`, que é o assert que pega o
pior erro silencioso da automação de paywall (clicar no card errado e comprar outro plano, com tudo
ficando verde).

## 2.106.0 — o 402 do backend próprio volta a abrir o paywall (ago/2026)

**Aditiva** (nenhuma quebra de assinatura, nenhum formato deixou de ser aceito). `GAP-KM-QUOTA-PARSE-01`.

### O defeito

`parseQuotaExceeded` cobria dois formatos de corpo — o envelope canônico do admin-api
(`{ ok, error: { details } }`) e o payload direto (o objeto raiz **é** o `details`) — e **não** o
terceiro, que é o do **`ErrorResponse` da backlib**: `details` no **topo** do corpo.

```json
{ "message": "Limite do plano gratuito atingido", "code": "QUOTA_EXCEEDED", "traceId": "…",
  "details": { "feature": "items", "limite": "50", "contagem": "50", "upgradeUrl": "…" } }
```

Esse é o formato que **todo backend próprio do ecossistema** responde: o `ErrorHandlingPlugin` da
backlib serializa `AppException.details` nesse campo. O caminho retrocompat (objeto raiz como
`details`) procurava `feature` na raiz, não achava e devolvia `null` — o 402 virava
`DomainResult.Error(402)` em vez de `DomainResult.Quota`.

**O estrago não é bloquear demais, é deixar de vender.** O item continua barrado (isso vem do próprio
código 402), mas o payload de paywall (`feature`/`limite`/`contagem`/`upgradeUrl`) se perde: o app diz
"não pode" e não diz "assine para poder". Quem expôs: o backend novo do **Acervo**, que responde
exatamente nesse formato no 402 de cota.

### A correção

Os três formatos passaram a ser candidatos avaliados **nesta ordem de precedência**, documentada no
KDoc, e o **primeiro completo vence** — o envelope canônico continua ganhando:

1. `error.details` (envelope canônico do admin-api);
2. **`details` no topo** (`ErrorResponse` da backlib) — o caso novo;
3. o objeto raiz como `details` (retrocompat).

Um candidato presente porém **incompleto** (ex.: `error.details` sem `feature`) não impede os
seguintes de responderem: descartar o corpo inteiro por causa de um envelope pela metade custaria o
CTA de assinatura, e nenhum corpo real tem os dois preenchidos com conteúdo diferente. `error` como
**string** (e não objeto) já era tolerado e segue sendo — agora sem atrapalhar a leitura do `details`
do topo.

Continuam valendo: `limite`/`contagem` como **string ou número** (o `details` da backlib é
`Map<String, String>`), corpo ausente/ilegível ⇒ `null`, nunca lança.

### Testes

`EntitlementModelTest` foi de 17 para **21** casos, cobrindo os três formatos e o negativo:
`ErrorResponse` da backlib com números em string; `details` de topo com números e `error` string;
**precedência** (os dois presentes ⇒ vence o envelope); e 402 **sem** payload de paywall (`code`
apenas, `details` sem `feature`, `error.details` incompleto, JSON que não é objeto) ⇒ `null`.

**Controle negativo:** revertendo a lista de candidatos para o `details ?: obj` anterior, **2 dos 4**
testes novos falham.

### Ação para quem consome

Nenhuma mudança de código. Apps cujo backend responde 402 no formato da backlib passam a receber
`DomainResult.Quota` (e o paywall com contexto) só bumpando. **Não** virou aviso no Monitoramento:
é aditivo e nenhum app em produção depende hoje do formato novo.

---

## 2.105.0 — o dado local não vaza para a nuvem nem sobra no disco (ago/2026)

**Aditiva** (nenhuma quebra de assinatura; nenhuma migração de schema). Três correções vindas de um
security-review que auditou o **manifesto mesclado de um build real** — todas na fundação, todas
caminhos pelos quais dado do usuário sai do sandbox sem ninguém pedir.

O produto que as expôs: **Confere QR**, app 100% offline cujo argumento de venda, publicado na landing
**e na Política de Privacidade**, é *"o cofre nunca sai do seu aparelho"* — e cujo cofre guarda
**chaves Pix** (CPF, e-mail ou telefone; PII, inclusive de terceiros). Não era hipótese de segurança
abstrata: era uma frase publicada que a fundação tornava falsa.

### 1. [CRÍTICO] iOS: o banco de sync ia para o backup do iCloud

`SyncDatabaseFactory.ios.kt` criava o `NativeSqliteDriver` só com `schema` e `name` — sem `basePath` —,
então o arquivo caía num diretório persistente do container. **No iOS tudo no container, exceto `tmp/`
e `Library/Caches/`, entra no backup do iCloud e do Finder** a menos que o arquivo (ou o diretório que
o contém) esteja marcado com `NSURLIsExcludedFromBackupKey`. `grep -rn "isExcludedFromBackup"` na lib
inteira dava **zero**.

**`createSyncDatabase(name, excludeFromBackup: Boolean = false)`**:

- **iOS** — o banco passou a viver em `Library/Application Support/kmplib_databases/<name>/`, **um
  diretório por banco**, e é o **diretório** que recebe a marcação. Diretório e não arquivo porque o
  SQLite em WAL mantém `-wal`/`-shm` ao lado do `.db`: marcar só o `.db` deixaria as escritas mais
  recentes viajando para a nuvem — a forma clássica de "excluí do backup" que não exclui nada. Como o
  caminho **não** depende do flag, ligar/desligar numa versão futura do app não perde o banco.
- **Application Support** também porque é o lugar que a Apple define para arquivo de apoio do app
  (`Documents` é do usuário; `Caches` é purgável) — e é onde o `BlobStore` (2.104.0) já grava.
- **Base já instalada**: se houver banco no local antigo (o default do SQLiter) e o novo ainda não
  existir, o arquivo é **adotado** (movido, com `-wal`/`-shm`). Se o movimento falhar, a lib abre **no
  local antigo** em vez de começar do zero — perder o espelho e a outbox do usuário seria pior que
  ficar no diretório errado — e registra que a exclusão **não** está ativa.
- **O default segue `false`, e isso é decisão de produto, não timidez.** Super 8, Lua Certa, Hora do
  Remédio **querem** o dado de volta no aparelho novo; virar `true` de cima para baixo tiraria isso de
  todos em silêncio. É **opt-in por app**, com o KDoc dizendo quem quer o quê e por quê.

**Android — o review dizia "correto", e está meio correto.** O arquivo nasce em
`/data/data/<pkg>/databases/`, privado: nenhum outro app lê. Mas **privado não é fora da nuvem**: o
**Auto Backup** inclui `databases/` por padrão, e sair dele só se declara **no manifesto**
(`dataExtractionRules` API 31+, `fullBackupContent` 23–30, ou `allowBackup="false"`) — a lib não pode
impor isso a todo consumidor. Então, com `excludeFromBackup = true`, a lib **confere o que dá para
conferir em runtime** (`FLAG_ALLOW_BACKUP`) e **avisa alto no log** quando o manifesto ainda permite
backup, em vez de deixar a promessa falhar calada. O snippet pronto do XML está no KDoc.

### 2. [MÉDIO] O arquivo compartilhado ficava no disco para sempre

O destino do `ShareHandler` estava certo (diretório privado + `FileProvider` `exported="false"`, nada
de Downloads). O problema era o que **sobrava**: o arquivo exportado — no Confere QR, o cofre inteiro
em texto claro — **nunca era apagado**, nem depois do share, nem no boot seguinte. O usuário exporta
hoje, amanhã apaga a plaquinha "para apagar o dado" (que é o que a Política manda fazer) e a chave Pix
continua no armazenamento do app, numa cópia que nenhuma tela mostra e que nenhuma ação do app remove.

**A semântica escolhida (o ponto técnico):** apagar logo após disparar o chooser **quebraria o
share** — o `ACTION_SEND` é assíncrono e o app receptor lê a URI depois, às vezes minutos depois, com
o nosso processo já em background ou morto. Então:

- **Android** — purga por **idade** (`DEFAULT_SHARED_FILE_TTL_MILLIS`, 1 h) **antes** de gravar o
  arquivo novo: o do share em curso é sempre o mais novo, logo nunca é vítima da própria limpeza.
- **iOS** — além da purga, usa o `completionWithItemsHandler` do `UIActivityViewController`, que é o
  sinal **oficial** de que a folha terminou (inclusive no cancelamento), e apaga ali. É o único lugar
  onde a plataforma permite ser preciso — e "o sistema limpa o `NSTemporaryDirectory()`
  eventualmente" não é coisa que se escreva numa política de privacidade.
- **`ShareHandler.clearSharedFiles(olderThanMillis = 1 h): Int`** (corpo default ⇒ fakes de app seguem
  compilando) para o app chamar no bootstrap; `0` apaga tudo (ação explícita de "limpar dados").
- Nome de arquivo agora é **sanitizado** (`sanitizeSharedFileName`): vem do chamador, às vezes de dado
  do usuário, e um separador escreveria fora do diretório de compartilhamento.

### 3. [BAIXO] `FilePicker` lia o arquivo inteiro antes de qualquer teto

`FilePicker.android.kt` fazia `readBytes()` sem limite dentro de um `catch (Exception)` que **não pega
`OutOfMemoryError`**: escolher um arquivo de centenas de MB **derrubava o app** antes de o consumidor
ver um byte (e o `FileData.size` existia sem ser consultado).

**`rememberFilePicker(mimeTypes, maxBytes, onResult: (FilePickResult) -> Unit)`** — desfecho **tipado**
(`Picked` / `Cancelled` / `TooLarge` / `Failed`) em vez de `FileData?`, que juntava "desistiu", "não
cabia" e "não deu para ler" num `null` só. Duas barreiras: o tamanho declarado pelo provedor (recusa
**sem abrir** o arquivo) e, quando o provedor não informa, leitura **com teto**
(`BoundedByteAccumulator`) abortada ao estourar — em nenhum caminho a lib materializa o arquivo para
descobrir depois que ele não cabia. `OutOfMemoryError` é capturado como última linha e vira
`Failed(OutOfMemory)`. **"Sem limite" não é opção oferecida** (`maxBytes <= 0` cai no default de
25 MiB) — era exatamente o comportamento que derrubava o app. As sobrecargas antigas continuam
existindo e agora recusam o arquivo grande (chega `null`) em vez de crashar.

### Testes

+30 casos em `commonTest`, todos sobre a **lógica pura** (o desfecho é decidido em commonMain e os
`actual` só ligam o SO): `SyncDatabaseDirectoryTest` (7 — nome do app não vira caminho),
`SharedFileCleanupTest` (11 — o share em curso não é apagado, o resíduo é, janela `0` apaga tudo, data
desconhecida/no futuro, sanitização) e `FilePickLimitTest` (12 — teto inválido cai no default, tamanho
desconhecido não é recusado de antemão, a leitura para de copiar ao estourar, desfechos
distinguíveis). Suíte: **1854 testes, 0 falhas**.

**Dois testes falharam na primeira execução e mudaram o código** (não o teste): `"///"` como nome de
arquivo virava `"___"` (agora cai no fallback legível).

### Pendente de macOS

O item 1 é `iosMain`, e o item 2/3 também têm `actual` iOS. **Alvos Apple não compilam em Linux** — o
código segue as APIs oficiais (`onConfiguration`/`extendedConfig.basePath` do SQLDelight,
`NSURLIsExcludedFromBackupKey`, `completionWithItemsHandler`), mas **não foi compilado nem validado**.
A validação é no Mac do fundador, junto da `assembleDebug`.

### Migrar

- **Confere QR** — `createSyncDatabase(excludeFromBackup = true)` no `offlineDataModule` +
  `getShareHandler().clearSharedFiles()` no bootstrap + `android:allowBackup="false"` (ou
  `dataExtractionRules` excluindo `domain="database"`). Sem a primeira linha, a frase publicada
  continua falsa.
- Qualquer app com dado sensível local no iOS: mesma linha.

## 2.104.0 — fila de upload DURÁVEL: `RestUploadOutbox` + `core/storage/BlobStore` (ago/2026)

**Aditiva** (uma depreciação, nenhuma quebra; **sem migração de schema**). Corrige perda silenciosa de
dado do usuário. (GAP-AC-M-PHOTOOUTBOX-01.)

### O defeito

`sync/rest/RestUploadQueue` e `firebase/storage/UploadQueue` guardavam a fila num
`MutableStateFlow<List<UploadItem>>` e os bytes num `mutableMapOf<String, UploadRequest>` — **memória
pura**. A tabela `synced_entity` só guarda `payload_json`; nenhum binário. E o `RestCrudSyncEngine`
não conhecia uploads: seus participantes só expõem `drainOutbox`/`refresh`.

Consequência real, verificada: **o usuário cadastra um item com foto offline, fecha o app, e a foto
se perde em silêncio** — o item sincroniza depois, sem ela. Atinge qualquer app da fábrica com anexo
offline. O caso que expôs: **Acervo** (`moedas`), onde anverso e reverso são obrigatórios e o uso
típico é numa feira sem sinal — item de coleção sem foto é registro sem valor.

### A decisão de fundo: estender a outbox que existe, não somar uma segunda fila

A fila persistida é o **mesmo** espelho `synced_entity`, gravado pelo **mesmo** `RestEntityMirror`,
sob um nome de entidade próprio (`kmplib_upload`). Isso não é economia de código — é o que faz a fila
de fotos herdar, **sem código novo e sem poder divergir**:

- **escopo de conta** (2.91.0) — a foto de um usuário não sobe na conta de outro no aparelho
  compartilhado, e trocar de conta e voltar preserva a fila de cada um;
- **histórico de entrega** (2.94.0) — uma recusa do servidor não é apagada pelo toque seguinte;
- **drenável × recusada** (2.91.0) — 4xx sai da fila e só volta por retry explícito;
- o vocabulário de estado `RestRowState`, que a UI já sabe ler.

**Nenhuma coluna foi acrescentada à tabela** — só uma consulta nova (`selectEntityAllAccounts`).
Apps em produção migram apenas bumpando.

### Os bytes: `core/storage/BlobStore` (expect/actual)

Novo `BlobStore` (chave→bytes, `suspend`, nada lança) + `createBlobStore(diretório)`:
**Android = `filesDir`** (interno privado; **não** `cacheDir`, que o sistema apaga sob pressão de
espaço — seria a foto sumindo pelo mesmo motivo de sempre) e **iOS = `Application Support`** (**não**
`Caches`, purgável; **não** `Documents`, que é do usuário). Escrita **atômica** (temporário +
`rename` / `writeToFile(atomically:)`) nos dois. Id de blob é **recusado** quando inválido, nunca
"sanitizado": sanitizar faria dois ids diferentes virarem o mesmo arquivo e uma foto sobrescrever a
outra. `KmpLib.init(context)`/`initSync(context)` já registram o holder.

### A amarração de ordem (a lição do ADR-0006 do "Todos a Bordo")

A foto só sobe depois que o item dono migrou do id local para o id do servidor. O upload declara o
dono (`ownerEntity`/`ownerHandle`) e o caminho usa `{owner}`; a cada ciclo a lib resolve o id do
servidor pelo remap do ciclo, pelo espelho do dono e pelo **remap durável** (2.93.0) — o que faz a
foto ainda achar o item quando o app foi fechado **entre** o `POST` do item e o da foto. Enquanto o
dono não migrou, o upload **espera** (`UploadTarget.WaitingOwner`): não conta tentativa, não vira
erro na tela. Enviar antes significaria `POST /v1/items/local-…/photos` → recusa por `FOREIGN KEY`
→ **terminal** → foto perdida para sempre.

### Retentativa, estado e limpeza

- **Recuo exponencial** determinístico (`UploadRetryPolicy`, 30 s → 30 min, teto de 8 tentativas);
  esgotada a política vira erro **visível** com o binário preservado, em vez de girar para sempre.
- **Estado observável**: `observeAll()`/`observePendingCount()` ("3 fotos aguardando envio") e
  `observeForOwner(handle)` — este correlacionado por **conjunto de handles**, nunca por igualdade
  de id (senão as fotos do item recém-sincronizado somem da tela).
- **Limpeza**: sucesso apaga a linha e **depois** o arquivo (nessa ordem: arquivo sem linha é órfão
  recolhível; linha sem arquivo seria erro na cara do usuário de uma foto que já subiu).
  `sweepOrphanBlobs()` recolhe resíduo de processo morto no meio — e olha as linhas de **todas as
  contas**, porque varrer só a conta corrente apagaria as fotos de quem trocou de usuário.

### O que entrou

- **`core/storage`**: `BlobStore`, `createBlobStore` (expect/actual Android/iOS), `InMemoryBlobStore`,
  `isValidBlobId`, `BlobStoreHolder` (Android).
- **`sync/rest`**: `RestUploadOutbox` (participante do `RestCrudSyncEngine`), `PendingUpload`/
  `PendingUploadPart`/`UploadMethod`/`UploadContent`, `UploadRetryPolicy`/`uploadRetryDelayMillis`,
  `UploadTarget`, `UploadEnqueueResult`/`UploadRejectReason`, `UploadDrainSummary`,
  `resolveUploadPath`/`uploadPathRequiresOwner`/`uploadBlobId`/`isValidUploadId`,
  `RestRow<PendingUpload>.toUploadItem()` (os composables `UploadQueueView`/`UploadProgressItem`
  renderizam a fila nova sem mudança).
- **`sync`**: `SyncStore.getRowsAcrossAccounts(entity)` (default `null` = "não sei responder ⇒ não
  varra"; fake de app segue compilando) + query `selectEntityAllAccounts`.

### Compatibilidade

- **`RestUploadQueue` está `@Deprecated` (WARNING)**, com o caminho de migração no KDoc. Continua
  funcionando; MinhasHoras não quebra. A fila em memória não precisa ser convertida — o que estiver
  nela já é volátil por construção.
- `firebase/storage/UploadQueue` **não** foi depreciada (fala com o Firebase Storage, não há
  substituto equivalente), mas a limitação passou a estar escrita no KDoc.

### Testes

`RestUploadOutboxTest` (**23**): sobrevivência ao processo morrer (outbox recriada sobre o mesmo
espelho e o mesmo disco, com verificação dos bytes no corpo multipart), FIFO, espera do dono, remap
durável entre ciclos, dono inexistente, correlação por handles, recuo/adiamento/esgotamento, pausa
sem rede, 4xx e 402, histórico de recusa preservado no requeue, escopo de conta, varredura de órfãos
(inclusive o store que não sabe responder), descarte, binário sumido, recusas de enfileiramento,
multipart de 2 partes, `onUploaded`, contagem pendente e as funções puras. **Controle negativo:**
trocando a espera do dono por "envia com o id local", 1 teste falha — o que exatamente pega o defeito
do ADR-0006.

## 2.103.0 — módulo `qr`: GERADOR de QR Code (ISO/IEC 18004) (ago/2026)

**Aditiva.** Pacote novo `br.com.codecacto.kmplib.qr` + dois arquivos em `ui/components`,
**`commonMain` puro**: zero `expect/actual` novo, **zero dependência nova**, nenhum arquivo existente
alterado. (GAP-KL-M-QRGEN-01.)

### O gap

A lib **lia** QR (`camera/barcode`, 2.97.0) e não sabia **gerar**. A weblib tem o par (`ui/QRCode`);
no mobile não havia. O Confere QR precisa disso na tela "Exportar Cofre": o dono exporta e o
funcionário importa **sem conta e sem nuvem**, por arquivo (sempre funciona) ou por **QR de
transferência** (o caminho prático para quem não sabe mandar arquivo). Segundo consumidor já na fila:
o projeto `gerador-de-qr-code`.

### Decisão de abordagem: encoder próprio em `commonMain`

Mantida a orientação do CTO, e por três razões que se sustentam sozinhas:

1. **Não existe "API oficial de cada plataforma" para GERAR.** A Apple tem `CIQRCodeGenerator`
   (CoreImage); o **Android não tem nada** — nem no framework, nem no Play Services (ML Kit só *lê*).
   O padrão-ouro "use a API do fornecedor" não tem o que aplicar em metade dos alvos.
2. **Duas implementações produziriam duas saídas.** Um `expect/actual` com CoreImage no iOS e algo
   escrito à mão no Android daria símbolos diferentes para o mesmo payload, com o lado iOS **sem poder
   ser compilado nem testado em Linux** — dívida iOS a mais, na direção oposta ao que a lib vem
   pagando (2.77/2.78).
3. **É matemática determinística, e a lib já faz isso** (`Gtin`, `BarcodeScanDebouncer`,
   `MoonCalculator`): um código só, testável sem device, igual nos dois alvos.

**Sobre o risco levantado ("encoder à mão não se verifica sem device"):** legítimo, e foi endereçado
com verificação **externa**, não com confiança:

- **Matriz comparada bit a bit com uma implementação independente** — `node-qrcode` 1.5.4 — em 8
  vetores (v1 a v24, os 4 níveis, os 3 modos, incluindo informação de versão e multi-bloco). Bate
  **100%**.
- **O que geramos foi DECODIFICADO por um leitor independente** (`jsQR`), renderizando a matriz como
  imagem 4px/módulo: **27/27** payloads voltaram idênticos — com acento, emoji, 1200 caracteres, os 4
  níveis e a **máscara escolhida pela nossa heurística** (não forçada). É a prova mais próxima de
  leitura real que existe sem câmera.
- **Vetores do próprio padrão** onde eles existem: gerador Reed-Solomon (expoentes publicados de α),
  os 32 valores da Tabela C.1 (informação de formato), Tabela D.1 (informação de versão, `0x07C94` /
  `0x28C69`) e o exemplo clássico `"01234567"` em 1-M (bitstream **e** os 10 *codewords* de EC).

Uma dependência KMP de terceiro (QRose/qr-kit) resolveria o mesmo problema, mas: nenhuma delas usa
API de plataforma (todas são encoders em Kotlin, ou seja, **a mesma classe de código** que este),
adicionaria uma dependência transitiva ao artefato de **todo** app do ecossistema, e nos deixaria sem
controle sobre a quiet zone e o nível de correção que este produto precisa expor. Com a verificação
acima no lugar, não há ganho de risco em terceirizar.

### O que entrou

- **`encodeQr(text, errorCorrection, quietZone, minVersion, maxVersion, forcedMask): QrEncodeResult`**
  — ponto de entrada. Modo (numérico → alfanumérico → bytes UTF-8) e versão (a menor que couber)
  escolhidos automaticamente; máscara pela menor penalidade. **Conteúdo grande é
  `QrEncodeResult.TooLong` com os números**, não exceção: "não cabe" é estado de produto (o cofre
  grande precisa do fallback de arquivo), enquanto argumento inválido de programação lança.
- **`QrCode` — matriz SEPARADA da renderização.** É dado puro (`size`, `isDark(x, y)`, `toMatrix()`,
  `toDebugString()`), alimentando tanto o `@Composable` quanto o bitmap. Uma composable não devolve
  bytes, e o app precisa de PNG para anexar/compartilhar.
- **`qrCodeFits(...): QrCapacityCheck`** (+ `qrCodeFitsPayload` booleano e
  `qrByteCapacity(version, level)`) — **decide QR × arquivo ANTES de tentar**, com
  `requiredVersion`/`remainingBytes`/`usedFraction`, não só um sim/não. Teste garante que a capacidade
  declarada é **exata** (o encoder aceita o payload no limite e recusa 1 byte além), nos 4 níveis e em
  6 versões: helper conservador mandaria o usuário para o arquivo sem necessidade, otimista faria a
  tela prometer o que o encoder não entrega.
- **`QrCodeView(qrCode | value, size, foregroundColor, backgroundColor, texts, onTooLong)`** —
  Canvas, cores por token do tema, `contentDescription` (QR é imagem muda para leitor de tela). Módulo
  em **pixel inteiro** com desenho centralizado, e módulos escuros vizinhos agrupados numa faixa (sem
  costura clara de anti-aliasing entre retângulos).
- **`renderQrCodeToPng(...)` / `renderQrCodeToPngOrNull(...)`** — off-screen, mesmo padrão do
  `renderShareCardToPng` (`ImageBitmap` + `CanvasDrawScope` + `encodeBitmapToPng`), sem
  `expect/actual` próprio e sem depender de fontes. O tamanho é ajustado **para baixo** ao múltiplo do
  número de módulos (720px pedidos em 29 módulos ⇒ 696px): módulo fracionário é a causa clássica de
  "o PNG não lê, mas na tela lia".
- **Quiet zone de 4 módulos embutida na matriz e CLAMPADA nesse mínimo.** É exigência do padrão e o
  esquecimento clássico: sem ela muitos leitores não decodificam, e o defeito aparece como "não lê no
  celular do funcionário", não como erro. `quietZone = 0` é elevado a 4, com teste.
- **Níveis L/M/Q/H expostos, com o trade-off no KDoc** em vez de um nível fixo escondido: para QR
  lido de **tela para tela** (o caso deste produto) `L` é a escolha tecnicamente correta e a que mais
  cabe; `H` é para impresso pequeno ou com logo sobreposto.

### Onde encoders caseiros erram — coberto

**Máscara**: as 8 expressões da Tabela 10 (com teste específico nas máscaras 1, 2 e 4, as únicas que
revelam a troca de linha/coluna) e as 4 penalidades **isoladas**, cada uma com matriz cujo valor
esperado se calcula no papel — incluindo os dois pontos em que implementações erram: blocos 2×2 são
contados **sobrepostos** (3×3 uniforme = 4 blocos, não 1) e a regra 3 casa a janela de 11 bits nos
**dois** arranjos. Mais: informação de formato (BCH 15,5) e de versão (BCH 18,6), intercalamento com
blocos curtos **e** longos, padding `0xEC`/`0x11` **depois** do terminador, Reed-Solomon em GF(256)
com `0x11D`, e os padrões funcionais (localização, separadores, sincronismo, alinhamento — inclusive o
**passo excepcional da versão 32** — e o módulo escuro fixo).

**Um defeito real foi pego pelo teste de referência durante o desenvolvimento:** a reserva da área de
informação de formato apagava o módulo de **sincronismo** em (8, 6) e (6, 8). O símbolo saía com 2
módulos errados — invisível a olho nu, e reprovado por qualquer leitor de referência. Corrigido com
**fonte única** de posições (`forEachFormatPosition`), usada pela reserva e pelo desenho: quando as
duas listas viviam separadas, uma incluía o sincronismo e a outra não.

### Testes

**64 casos novos**, suíte **1799/0** (15 skipped pré-existentes): `QrReferenceMatrixTest` (3, sobre 8
vetores externos), `QrStructureTest` (13), `QrEncoderTest` (13), `QrCapacityTest` (9),
`QrPenaltyTest` (11), `QrReedSolomonTest` (11), `QrMaskChoiceTest` (4).

**O que os testes NÃO provam:** que um iPhone ou um Android **real** decodifica o símbolo na tela —
isso depende de câmera, brilho, contraste e distância, e é validação de device (passo do fundador,
como a `assembleDebug`). O que está provado é a estrutura (idêntica a terceiro) e a decodificação por
software (27/27 no jsQR).

**Divergência conhecida e inofensiva:** a regra 4 de penalidade tem duas leituras na prática. A kmplib
segue a Tabela 11 do ISO (45%–55% ⇒ sem penalidade); algumas bibliotecas usam `|ceil(p/5) − 10|`, que
cobra já em 55,0%. Isso muda **apenas qual máscara é escolhida** — as 8 produzem símbolo válido e
legível —, então o teste **mede** a concordância com a referência em vez de exigi-la, e falha se ela
despencar (sinal de que as regras 1 a 3, que **não** admitem duas leituras, foram quebradas).

### Consumidores

Nenhum a migrar (módulo novo). Confere QR (tela "Exportar Cofre") e `gerador-de-qr-code` nascem sobre
isto. `camera/barcode` **não foi tocado**.

## 2.102.0 — módulo `pix`: parser de BR Code (EMV MPM) + identidade de plaquinha (ago/2026)

**Aditiva.** Pacote novo `br.com.codecacto.kmplib.pix`, **`commonMain` puro**: zero `expect/actual`,
zero dependência nova, nenhum arquivo existente alterado. Quem já consome a lib não muda nada.

### O gap

Nenhuma lib do monorepo sabia **interpretar** o payload de um QR Code de Pix. A kmplib já lê o
código (`camera/barcode`, 2.97.0 — `BarcodeScannerView` com `BarcodeFormat.QR_CODE`, e o
`parseBarcode` de simbologia livre entrega o texto íntegro), mas o que chega ao app é uma string
opaca. Decodificá-la é `commonMain` puro e 100% testável: material de fundação por natureza — e o
tipo de código que, escrito dentro de um app, seria reescrito (diferente) no app seguinte.

O produto que motivou é o **Confere QR** (`confere-qr`, app offline AdsOnly): empresas espalham
plaquinhas de QR de Pix no balcão/mesa/totem, e existe o golpe de **trocar a plaquinha** por um QR de
outra conta. O app cadastra os QR legítimos e, na ronda do dia, confere cada plaquinha — e, além de
comparar com o cofre, **mostra quem receberia** (chave, nome, cidade), o que permite desconfiar de uma
plaquinha **mesmo nunca cadastrada**.

### O que entrou

- **`parseEmvTlv`** — parser TLV do EMV MPM (`ID(2)+tamanho(2)+valor`, com templates aninhados),
  **estrito no enquadramento e tolerante com ID desconhecido**. A assimetria é o contrato: estrutura
  que não fecha é payload corrompido (recusar, com `EmvTlvError` + posição); ID que a lib não conhece
  é campo de PSP novo (**preservar** — recusar faria o app acusar fraude num QR legítimo). Template
  cujo interior não é TLV vira folha com o valor intacto, em vez de derrubar o payload todo.
- **`PixCrc`** — CRC-16/CCITT-FALSE (polinômio `0x1021`, init `0xFFFF`, sem reflexão, sem XOR final),
  sobre os bytes **UTF-8** e cobrindo o payload **incluindo `"6304"`**. `compute` / `sign` (fecha um
  payload; é como as fixtures são montadas) / `isValid` / `declaredCrcOf` (normaliza a caixa — há
  emissor que grava o CRC minúsculo, e recusar por isso reprovaria um QR íntegro).
- **`BrCode`** — modelo tipado: formato (`00`), método de iniciação (`01`), conta Pix (`26`–`51`),
  MCC (`52`), moeda (`53`), **valor como string decimal** (`54`; `Double` para dinheiro segue
  proibido), país (`58`), nome (`59`), cidade (`60`), CEP (`61`), `txid` (`62`/`05`), CRC (`63`) e a
  árvore crua completa. `PixAccount` (GUI/chave/descrição/URL) e `inferPixKeyType`
  (CPF/CNPJ/e-mail/telefone/aleatória/desconhecida) — **sem validar DV como critério de rejeição**:
  chave com DV inválido é problema do PSP que a aceitou, e recusar aqui esconderia do usuário
  justamente o QR que ele precisa ver.
- **`parseBrCode(text: String?): BrCodeReading`** — ponto de entrada único, **nunca lança** (o insumo
  vem de câmera lendo etiqueta suja: lixo é o caso normal). Quatro desfechos, com **"CRC não confere"
  separado de "não é EMV"**: `Pix` · `NotPix` (é EMV de outro arranjo — cidadão de primeira classe,
  não erro) · `InvalidCrc` (adulterado/truncado — o produto alerta diferente) · `NotEmv`.
- **`PixIdentity` + `comparePix`** — o núcleo. Ver abaixo.

### O ponto que decide o produto: os dois regimes

- **Estático** (`01`=`11` **ou ausente** — ausente é o default do padrão): o payload é fixo, logo a
  identidade **é** o payload inteiro. Igualdade exata resolve.
- **Dinâmico** (`01`=`12`): o payload aponta para uma URL de cobrança que o PSP **troca a cada
  cobrança**. Igualdade exata **nunca** casa — e um app ingênuo marcaria **toda plaquinha legítima
  como fraude**. Pior que não ter a checagem: o funcionário aprende que "dá erro sempre" e passa a
  ignorar o alerta, inclusive o verdadeiro. A identidade dinâmica é derivada do que é estável:
  **host + prefixo de caminho + recebedor** (`PixEndpoint` + `PixReceiver`).

`comparePix` devolve **motivo tipado, nunca `Boolean`** — porque "o nome do recebedor é outro" é a
frase que faz parar o pagamento, e "não deu para comparar" é o oposto de "divergente":
`SamePlaque` · `ReceiverChanged(changed: Set<PixReceiverField>)` · `EndpointChanged(hostChanged)` ·
`PayloadChanged` · `RegimeChanged` · `NotComparable(reason)`. Precedência fixa e testada:
**NotComparable → ReceiverChanged → RegimeChanged → EndpointChanged → PayloadChanged → SamePlaque**
(recebedor diferente é o mais grave e vem antes de tudo).

Detalhes de segurança que ficaram na lib, e não em cada app:

- **Sem hash.** A identidade é `@Serializable` (`PixIdentity.encode`/`decode`, discriminador `type`),
  legível e auditável — o app pode exibir *por que* divergiu. `decode` nunca lança.
- **Normalização mínima e declarada.** O payload validado tem **só as bordas** aparadas (espaço, BOM,
  *zero-width*); o interior é intocado, porque mexer nele mudaria o CRC e poderia fazer dois payloads
  distintos virarem "iguais" — e "igual" aqui significa "plaquinha válida". Na comparação de
  recebedor, apara-se apenas apresentação: caixa/acento/espaço duplicado no nome e cidade, pontuação
  em CPF/CNPJ/telefone, caixa em e-mail/UUID. Chave de formato desconhecido é comparada **como veio**.
  Coberto por teste que dois documentos/nomes distintos **não** colidem depois de normalizados.
- **Armadilha de *userinfo*.** O host de `https://banco-de-verdade.com@servidor-do-golpe.com/x` é o
  que vem **depois do último `@`** — o olho lê o primeiro nome, o aparelho conecta no segundo.
- **Fail-closed onde falta base:** método de iniciação fora do padrão, dinâmico sem URL legível,
  leitura sem CRC válido e leitura não-Pix **não** produzem identidade (logo não entram no cofre nem
  viram veredito). A exibição de "quem recebe" continua funcionando — é o diferencial do produto.

### Testes

**70 casos novos** em `commonTest`, suíte **1735/0** (15 skipped pré-existentes):
`EmvTlvTest` (12), `PixCrcTest` (8), `BrCodeParserTest` (21), `PixIdentityTest` (29).

Fixtures **sintéticas** (`PixFixtures` — nada de dado real de comerciante), com o CRC gerado pela
própria `PixCrc.sign`. Isso não torna a suíte auto-referente: o algoritmo é ancorado em **fonte
externa** no `PixCrcTest` — o *check value* publicado do CRC-16/CCITT-FALSE (`"123456789"` → `0x29B1`)
e o valor inicial (`""` → `0xFFFF`, que prova a ausência de XOR final) —, e há teste que falha se o
sufixo `"6304"` sair do cálculo.

**Controle negativo:** trocando a identidade dinâmica pelo payload cru (o erro clássico), **7 dos 70**
falham, entre eles `QR dinamico legitimo com URL diferente e a MESMA plaquinha`.

### Consumidores

Nenhum a migrar (módulo novo). O **Confere QR** nasce sobre isto. `camera/barcode` **não foi tocado**.

## 2.101.0 — `api()` para tudo que vaza na API pública (conserto de fundação, ago/2026)

**Aditiva.** Nenhuma assinatura mudou, nenhum comportamento mudou, nenhum arquivo `.kt` foi tocado.
A mudança inteira está no `library/build.gradle.kts` (+ uma entrada nova no catálogo). Quem já
declarava as coordenadas por conta própria fica apenas **redundante** — continua compilando.

### O defeito

`library/build.gradle.kts` declarava `implementation(libs.kotlinx.datetime)`, mas a **API pública**
da lib exige tipos dessa biblioteca (`MoonPhaseEvent.instant: Instant`,
`MoonPhaseEvent.dateIn(timeZone: TimeZone)`, `NotificationScheduler.scheduleNotification(
scheduledTime: Instant, …)`, `ScheduleEvent.start: LocalDateTime`, todo o `core/util/TimeUtils`…).

Regra do Gradle: **tipo que aparece na API pública exige `api`, não `implementation`.** Com
`implementation` a dependência não é exportada, então o consumidor **não consegue nem nomear o tipo
que a lib exige dele** — e acaba declarando a coordenada no próprio build, **adivinhando a versão**.
Foi exatamente o que o **Desparasite-se** teve de fazer.

**Por que isso não é cosmético:** se o app declarar uma versão diferente, o Gradle resolve para a
**maior** — e no `kotlinx-datetime` 0.7.x o `Instant` deixou de ser classe própria e virou typealias
de `kotlin.time.Instant`. O resultado já está documentado no próprio build da lib (bloco do
RevenueCat): **R8 falhando no release com `Missing class kotlinx.datetime.Instant`**. Ou seja,
**compila em debug e quebra no release**, que é a pior hora de descobrir.

### A varredura (não só a datetime)

Auditoria de **toda** a API pública de `commonMain` + `androidMain`. Nove artefatos estavam no mesmo
erro; cinco outros foram **verificados** como uso genuinamente interno e continuam `implementation`.

| Artefato | Era | Virou | Tipo que vaza |
|---|---|---|---|
| `kotlinx-datetime` | implementation | **api** | `Instant`, `LocalDate`, `LocalDateTime`, `LocalTime`, `TimeZone` |
| `kotlinx-coroutines-core` | implementation | **api** | `Flow`, `StateFlow`, `CoroutineScope` |
| `kotlinx-serialization-json` | implementation | **api** | `KSerializer`, `Json`, `JsonObject`, `JsonElement` |
| `ktor-client-core` | implementation | **api** | `HttpClient`, `HttpClientEngine`, `HttpClientConfig<*>`, `ResponseException` |
| `lifecycle-viewmodel` | implementation | **api** | `ViewModel` (**supertipo** de `BaseViewModel`) |
| `compose.ui` | implementation | **api** | `Modifier`, `Color`, `Dp`, `ImageVector`, `TextStyle` |
| `compose.foundation` | implementation | **api** | `RowScope`, `ColumnScope`, `BoxScope` (slots) |
| `compose.material3` | implementation | **api** | `ColorScheme`, `Typography`, `SnackbarHostState` |
| `compose.components.resources` | implementation | **api** | `StringResource`/`DrawableResource` via o `Res` gerado **público** |
| `androidx.fragment` (androidMain) | **não declarada** | **api** | `FragmentActivity` em `KmpLib.setActivity(...)` |

Detalhes de cada caso:

- **`ViewModel`** é o supertipo público de `BaseViewModel`, a classe-base de **todo** ViewModel de
  **todo** app do ecossistema. Mesma classe de defeito da datetime, e mais silenciosa: os apps a
  recebiam por acaso, transitivamente, via `lifecycle-viewmodel-compose`.
- **Compose** — a kmplib **é** uma biblioteca de UI: 133 assinaturas públicas só com
  `modifier: Modifier = Modifier`, mais 214 com `Color`/`Dp`/`ImageVector`. É o mesmo padrão das
  bibliotecas oficiais (`androidx.compose.material3` declara `api` para `ui` e `foundation`).
- **`androidx.fragment`** não era declarada em lugar nenhum: `FragmentActivity` chegava ao consumidor
  **por acaso**, transitivamente por `api(firebase-auth-android)` → `play-services-base`. Um projeto
  **own-auth sem Firebase** (o padrão de todo projeto novo, ago/2026) ficaria sem conseguir nomear o
  tipo que a lib exige no `MainActivity`. Declarada na versão que já resolvia (`1.5.7`), então a
  resolução não muda.

**Verificados como internos — seguem `implementation`, de propósito** (documentado no build):
`ktor-client-logging`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`
(`HttpLogLevel` é enum próprio; o mapeamento é `internal`), `sqldelight-coroutines` (só as extensões
`asFlow`/`mapToList` dentro do `SyncStore`), `sentry-kotlin-multiplatform` (`SentryCrashReporter` é
`internal`; `CrashReporter` é **neutra ao fornecedor**), Firebase GitLive (`FirebaseAuth`/
`FirebaseUser`/`FirebaseStorage` são `private` — é o que permite a um projeto own-auth consumir a lib
sem falar Firebase), RevenueCat (`RevenueCatPurchaseRepository` e o mapeamento de erro são
`internal`), `compose.components.uiToolingPreview` (as 28 funções `@Preview` da lib são todas
`private`) e `compose.materialIconsExtended` (a lib usa **valores** `Icons.*` como default de
parâmetro, nunca um **tipo** do artefato — o tipo é `ImageVector`, do `compose.ui`).

### Prova

`metadataApiElements` do módulo Gradle publicado (é a variante que o `commonMain` do consumidor
compila contra):

- **2.100.0** — 6 dependências: `coil-compose`, `kmpnotifier`, `koin-core`, `kotlin-stdlib`,
  `sqldelight-runtime`, `compose-runtime`.
- **2.101.0** — 15: as 6 acima **+** `kotlinx-datetime`, `kotlinx-coroutines-core`,
  `kotlinx-serialization-json`, `ktor-client-core`, `lifecycle-viewmodel`, `ui`, `foundation`,
  `material3`, `components-resources`.

No POM do `kmplib-android` as mesmas coordenadas saíram de `runtime` para **`compile`**, e
`androidx.fragment` aparece pela primeira vez (também `compile`).
`components-ui-tooling-preview` continua `runtime`, como deve.

`./gradlew :kmplib:compileReleaseKotlinAndroid` verde · `:kmplib:testDebugUnitTest` **1665 testes,
0 falhas, 0 erros**.

#### Como conferir (e o arquivo que NÃO serve para conferir)

> **O `kmplib-<versão>.pom` da raiz não distingue `api` de `implementation`.** Ele é o POM de
> compatibilidade do módulo-raiz de um projeto KMP, cuja função é redirecionar para o Gradle Module
> Metadata: ali **toda** dependência sai com `<scope>runtime</scope>`, inclusive as declaradas
> `api`. Ler esse arquivo e concluir "está como `implementation`" é um **falso negativo** — aconteceu
> ao verificar esta própria versão. A pista de que a leitura não serve está no controle: na 2.100.0
> essas coordenadas **nem apareciam** no POM da raiz; passaram a aparecer justamente por terem virado
> `api`, e mesmo assim com `runtime`.
>
> Os dois arquivos que respondem de fato:
>
> ```bash
> V=2.101.0; M=~/.m2/repository/br/com/codecacto
> # 1) o que o commonMain do consumidor compila contra (esperado: 15, era 6 na 2.100.0)
> python3 -c "import json,sys;m=json.load(open('$M/kmplib/$V/kmplib-$V.module'));\
> print([len(v.get('dependencies',[])) for v in m['variants'] if v['name']=='metadataApiElements'])"
> # 2) o que o alvo Android exporta (esperado: scope=compile)
> grep -A3 kotlinx-datetime-jvm $M/kmplib-android/$V/*.pom
> ```

### Migração

**Nenhuma obrigatória.** Quem declarou a dependência por conta própria pode **remover** a linha do
próprio build (o Desparasite-se pode tirar o `kotlinx-datetime`), mas mantê-la também funciona.

---

## 2.100.0 — botões de ação na notificação: adiar e agir sem abrir o app (ago/2026)

**Aditiva.** Nenhum consumidor precisa mudar nada para continuar funcionando: `actions` entra com
default `emptyList()` no fim das assinaturas, e uma notificação sem ações é byte-a-byte a de sempre.

### O gap

A kmplib agendava notificação local, mas a notificação **não tinha botões**: o
`NotificationCompat.Builder` do Android era montado sem nenhum `addAction`, e no iOS não havia
`UNNotificationCategory`/`UNNotificationAction`. Ou seja, **nenhum app do ecossistema conseguia
oferecer "Adiar 30 min" nem "Marcar como tomada" na própria notificação** — a única saída era abrir o
app. Dois consumidores pediam isso por escrito, no mesmo domínio (lembrete de dose):

- **Desparasite-se** — RF-12 (adiar 15/30/60 min pela notificação) e Fluxo 3 do `docs/design/flows.md`
  ("marcar dose como tomada sem abrir o app").
- **Hora do Remédio** — `GAP-HR-M-02` ("ações na notificação") e `GAP-HR-M-03` ("adiar padronizado",
  hoje composto à mão no app com um agendamento único paralelo).

### A API

```kotlin
scheduler.scheduleDailyNotification(
    id = 42, title = "Nitazoxanida", body = "1 comprimido — 08:00", hour = 8, minute = 0,
    data = mapOf("doseId" to "d-17"),
    actions = listOf(
        NotificationAction.app(id = "MARK_TAKEN", title = "Marcar como tomada"),
        NotificationAction.snooze(minutes = 30, title = "Adiar 30 min"),
    ),
)

// no Application (NÃO numa Activity — a ação chega com o app morto):
NotificationActions.setHandler { event ->
    if (event.actionId == "MARK_TAKEN") doseRepository.marcarComoTomada(event.data["doseId"].orEmpty())
}
```

- **`NotificationAction`** (`id` estável, `title` já traduzido, `kind`, `snoozeMinutes`, `opensApp`,
  `destructive`) + fábricas `app(...)`, `snooze(...)` e **`snoozeOptions(listOf(15, 30, 60)) { "Adiar
  $it min" }`** — o app declara **só os intervalos**, a lib monta os botões.
- **`NotificationActions.setHandler(...)`** — ponto único de registro, no `Application`/bootstrap.
  Evento que chega antes do handler fica numa fila de 32 e é entregue no registro; handler que lança
  ou passa de 8 s é cortado com log (um `BroadcastReceiver` não pode lançar).
- **`NotificationActionEvent(notificationId, actionId, data)`** — o `data` do agendamento é por onde
  trafega o id de domínio. A lib nunca o interpreta.
- **`snoozeNotification(id, minutes)`** na interface (corpo default) — o MESMO caminho do botão,
  exposto para o "Adiar" que fica **dentro** da tela.

### Adiar é da lib, regra de domínio é do app

`NotificationActionKind.SNOOZE` é executado inteiramente pela kmplib: nenhuma linha de código de
plataforma no app. E o adiamento **não cria agendamento novo** — o campo novo
`ScheduledNotification.snoozedUntilMillis` desloca o disparo do MESMO `id`, então:

- adiar duas vezes continua sendo **um** lembrete (nada de id derivado, nada de alarme paralelo);
- **adiar um lembrete diário não mata a recorrência**: `hour`/`minute` ficam intactos e, depois do
  disparo adiado, o lembrete volta ao horário normal;
- reiniciar o aparelho no meio do adiamento **não perde** o disparo adiado (`plan()` passou a
  decidir pelo `nextTriggerMillis`), e um adiamento vencido é limpo em vez de disparar no passado.

### Persistência — os botões voltam iguais depois do reboot

`ScheduledNotification` ganhou `actions` e `snoozedUntilMillis`, **ambos com default**. Um registro
gravado pela 2.99.0 (`SharedPreferences`/`NSUserDefaults`) continua sendo lido sem erro: o campo novo
assume o default e o lembrete volta simplesmente sem ações — coberto por teste com o JSON literal da
versão anterior. Sem isso, o lembrete restaurado depois do boot voltaria sem botões, e a pessoa teria
"Adiar" na segunda e não teria na terça, sem explicação.

### Android — o receiver que existe justamente para NÃO abrir o app

- **`NotificationActionReceiver`**, declarado no manifesto da própria lib (`exported="false"`): todo
  consumidor herda só bumpando. `PendingIntent.getBroadcast` + `FLAG_IMMUTABLE`.
- **Pegadinha resolvida:** `PendingIntent` são considerados iguais quando `requestCode`, componente e
  `Intent.filterEquals` batem — e `filterEquals` **ignora os extras**. Sem uma `data: Uri` distinta
  por ação, "Marcar como tomada" e "Adiar 30 min" da mesma notificação virariam o mesmo
  `PendingIntent` e o segundo botão executaria o primeiro. Cada ação tem `Uri` própria + `requestCode`
  derivado.
- Broadcast (e não `Activity`, que abriria a tela, nem `Service`, que exigiria foreground service com
  notificação própria no Android 12+). Processo morto: o sistema sobe o app, roda
  `Application.onCreate()` e só então entrega. O handler roda dentro de **`goAsync()`**, então a
  gravação no banco local termina antes de o processo poder ser encerrado.
- A notificação é **dispensada** assim que uma ação é tocada.
- **Refactor interno junto:** a montagem do alarme e a exibição da notificação viraram
  `NotificationAlarms` e `NotificationPresenter` (internos). Antes a mesma lista de `putExtra` estava
  repetida em três arquivos — bastaria esquecer um para o lembrete restaurado perder os botões.

### iOS — categorias, e o adiamento com requisição própria

- `UNNotificationCategory` + `UNNotificationAction` registrados no `UNUserNotificationCenter`, com o
  **identificador da categoria derivado do conteúdo das ações**
  (`NotificationActionRules.categoryIdentifier`): dois lembretes com os mesmos botões compartilham
  categoria e o app não inventa nome nenhum. Como `setNotificationCategories` **substitui** o conjunto
  inteiro, o registro é remontado a partir do espelho da lib — registrar só a categoria do
  agendamento atual apagaria os botões de todos os outros.
- **Resposta:** `NotificationActionBridge` (`@ObjCName`, alimentado pelo delegate Swift — mesmo padrão
  do `ApplePushBridge`, porque o centro aceita **um** delegate por processo e num app com push ele já
  é do `AppDelegate`) **ou** `installNotificationActionDelegate()`, que instala o delegate da lib
  **só se não houver outro**.
- **Diferença de plataforma explorada de propósito:** no iOS a requisição é chaveada por `String`,
  então o disparo adiado ganha identificador próprio (`"<id>#snooze"`) e **convive** com a requisição
  `repeats = true` do lembrete diário. Sem isso, adiar hoje custaria o lembrete de amanhã (no Android,
  onde a chave é o `requestCode` `Int`, o reagendamento do próprio receiver já resolve).
- iOS **pendente de validação em macOS** (não compila em Linux).

### Testes

`NotificationActionTest` (23) — fábricas e validação, categoria determinística, adiamento que não
toca no horário regular, adiar duas vezes = um agendamento, reboot no meio do adiamento, adiamento
vencido que volta ao horário normal, disparo adiado perdido dentro da graça, ordenação da janela do
iOS pelo disparo efetivo, round-trip de serialização e **leitura do registro gravado pela versão
anterior**, entrega ao handler, fila de eventos pré-handler, teto da fila e handler que lança.
`NotificationReschedulingTest` (22) segue verde sem alteração. Suíte: **1665 testes, 0 falhas**;
`koverVerify` verde.

### Migrar (não feito nesta rodada — propagação é tarefa própria)

**Hora do Remédio** é candidato imediato: `GAP-HR-M-02` e `GAP-HR-M-03` deixam de existir, o
`DoseReminderScheduler.snooze` e o `snoozeNotificationId` locais podem sair (o adiamento paralelo com
id derivado vira `snoozeNotification(id, minutes)` da lib) e a `ConfirmDoseSheet` ganha par na própria
notificação.

---

## 2.99.0 — lembrete que sobrevive ao reboot + efemérides lunares de verdade (ago/2026)

Duas peças de fundação, ambas nascidas do app novo **Desparasite-se** (protocolo antiparasitário
ancorado em fases da lua, com lembrete de dose 12/12h) — que seria o 2º/3º consumidor de código hoje
duplicado em apps. Em vez de copiar, resolveu-se na fundação.

**Aditiva.** Nenhuma assinatura existente mudou; os métodos novos da interface `NotificationScheduler`
entram com corpo default, então qualquer fake/decorator que um app mantenha continua compilando.

---

### 1. `BOOT_COMPLETED` — o lembrete parou de morrer quando o celular reinicia (crítico)

**O defeito:** o `AlarmManager` do Android **zera todos os alarmes no boot**. A kmplib não registrava
receiver de boot e não guardava registro nenhum dos agendamentos — então, ao reiniciar o aparelho,
**todo lembrete local agendado por qualquer app do ecossistema desaparecia em silêncio** e só voltava
se o usuário abrisse o app. Estava documentado como limitação (`GAP-HR-M-04` do Hora do Remédio,
`RNF-01` do Desparasite-se), com cada app inventando o mesmo contorno: reagendar tudo na abertura.
Para lembrete de medicação de 12/12h isso não é detalhe técnico — é a promessa central do produto
falhando sem nenhum aviso.

**A correção (padrão-ouro da plataforma, não contorno):**

- **`BootCompletedReceiver`** declarado **no manifesto da própria lib**, junto da permissão
  `RECEIVE_BOOT_COMPLETED` — todo app consumidor herda o conserto **só bumpando a versão**, sem editar
  manifest. Escuta `BOOT_COMPLETED`, **`MY_PACKAGE_REPLACED`** (atualizar o app também apaga os
  alarmes) e as variantes `QUICKBOOT_POWERON` de fabricante.
- **Registro persistente dos agendamentos** (`ScheduledNotification` + `NotificationScheduleStore`),
  sem o qual não há o que reagendar: `SharedPreferences` no Android (leitura **síncrona** — o receiver
  de boot roda fora de escopo de corrotina, onde `runBlocking` num DataStore seria justamente o que a
  doc do Android proíbe) e `NSUserDefaults` no iOS.
- **`refreshScheduledNotifications()`** na interface comum: reconcilia registro × sistema
  operacional. Idempotente; chamar na abertura do app é barato.
- **Disparo perdido tem janela de graça de 1 h.** Celular desligado às 19:50, dose às 20:00, ligou às
  20:20 ⇒ **avisa**. Ligou no dia seguinte de manhã ⇒ **não avisa** (lembrete de ontem aparecendo hoje
  sugere tomar fora de hora — em app de medicação, ruído é perigoso).
- **Alarme exato no Android 12+**: `canScheduleExactAlarms()` e `requestExactAlarmPermission()`
  (abre `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`). Sem a permissão, a lib **continua agendando** com
  alarme inexato e loga o aviso — nunca fica em silêncio. **A lib NÃO declara
  `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`**: são permissões de uso restrito, e impô-las a todo app da
  fundação — inclusive aos que nem agendam nada — seria transferir risco de revisão na Play a quem não
  pediu. Quem precisa declara a sua.
- **Restrição de fabricante:** `openBatteryOptimizationSettings()` abre a lista geral de otimização de
  bateria (sem permissão restrita) — a mitigação possível para as camadas agressivas de Xiaomi/Samsung/
  Huawei, que matam alarme e são a causa nº 1 de "não tocou" mesmo com o código certo.
- **`NotificationReceiver` passou a ser declarado pela lib.** Antes, cada app tinha de lembrar de
  declará-lo à mão, e quem esquecesse tinha agendamento que nunca aparecia — sem erro de build. A
  declaração é idêntica à que os apps já usam, então o manifest merger mescla sem conflito.

**Correção de contrato junto:** `cancelAllNotifications()` só dispensava as notificações **da
bandeja** — os alarmes seguiam armados e voltavam a disparar, ao contrário do que o próprio KDoc
prometia. Com o registro, agora cancela de verdade.

**iOS — a diferença está documentada, não silenciada.** O `UNUserNotificationCenter` persiste os
agendamentos e sobrevive a reboot sozinho: **não há nem pode haver receiver de boot** (o iOS não
entrega broadcast de boot a apps de terceiros). O limite que **existe** lá é outro: **64 notificações
pendentes por app**, com o excedente **descartado em silêncio** — e um ciclo de 26 dias com dose de
12/12h pede 52 disparos só de dose. A lib passa a manter o espelho e registrar no sistema apenas a
**janela** dos próximos 60 (folga de 4 para o app), reabastecendo em `refreshScheduledNotifications()`.
Lembrete diário usa `repeats = true` (um pedido cobre disparos infinitos) e por isso tem prioridade na
janela.

**Testes:** `NotificationReschedulingTest` (22) — próximo disparo diário com fuso, virada de mês,
entrada inválida; plano pós-boot para diário e único; janela de graça (dentro, fora, configurável); o
cenário completo do protocolo de 26 dias sobrevivendo ao boot; teto do iOS (prioridade do diário,
vencidos não ocupam vaga); e o store. A regra vive em `commonMain` puro, com o "agora" por parâmetro —
a decisão testada é exatamente a que roda dentro do receiver.

**Pendência de macOS:** os `actual` iOS (espelho + janela) estão escritos conforme a API oficial, mas
**não compilam em Linux** — validação final no Mac, como o resto do iOS da lib.

---

### 2. `astro` — módulo novo: efemérides lunares por Meeus (não mais "idade média")

Promovido do `core/domain/moon` do **Lua Certa**, mas **não copiado: corrigido**. A implementação de
origem calculava a idade lunar por **módulo do mês sinódico médio** a partir de uma época fixa —
atalho clássico que trata 29,530588861 dias como constante. Ele **não é**: o ciclo real varia entre
~29,27 e ~29,83 dias, e o erro instantâneo passa de **meio dia**. Para escrever "hoje é lua crescente"
isso passa despercebido; para **ancorar um cronograma de 26 dias no instante da lua nova**, desloca o
ciclo inteiro em um dia. "Já estava assim" não autoriza subir o atalho para a fundação.

**O que entrou:** `br.com.codecacto.kmplib.astro` implementando **Meeus, _Astronomical Algorithms_
(2ª ed.)** — capítulo 49 para os instantes das 4 fases principais (série completa de termos
periódicos + as 14 correções planetárias) e capítulos 47/48 para a fração iluminada, com conversão
TT→UTC por **ΔT** (polinômios de Espenak & Meeus, 1600–2150). Erro típico de **segundos**.

- **`MoonCalculator`** — `phaseAt(instant)`, `phaseOn(date, timeZone)` (meio-dia local),
  `nextPhase(phase, from)`, `previousPhase(...)`, `nextPhases(..., count)`, `phasesBetween(start, end)`
  e os atalhos `nextNewMoon`/`nextFullMoon`.
- **`MoonPhaseEvent(phase, instant)`** — a fase é um **instante**; a data civil **depende do fuso** e
  por isso não é campo, e sim `dateIn(zone)`/`dateTimeIn(zone)`/`daysFrom(date, zone)`. Uma lua nova
  às 02:30 UTC cai no dia anterior em Brasília: guardar "a data" sem o fuso é exatamente como se erra
  o começo de um cronograma por um dia.
- **`MoonPhaseInfo`** — fase nomeada, `ageDays`, `cycleFraction`, `illuminationFraction`/
  `illuminationPercent`, `cycleLengthDays` e as luas novas **reais** que abrem e fecham o ciclo.
- **`MoonPhase`** (8 fases, com `glyph`, `isWaxing`, `group`), **`MoonPhaseGroup`**,
  **`PrincipalMoonPhase`** (as 4 que têm instante exato) e **`MoonPhaseTexts`** (rótulos injetáveis).
  O enum **não** carrega `displayName` em pt-BR: fundação com rótulo fixo obrigaria todo app a exibir
  português, contra a regra de o mobile seguir o idioma do aparelho.

**Namespace:** módulo próprio `astro`, não `core/*`. `core` é infraestrutura de app (formato, rede,
preferências); isto é um **domínio de cálculo** puro e autocontido, como `brdata` é um domínio de
dados. Fica pronto para crescer (nascer/pôr do sol, por exemplo) sem inchar `core`.

**Testes:** `MoonCalculatorTest` (27), validados contra **fontes externas**, não contra o próprio
algoritmo: o **Exemplo 49.a do livro de Meeus** (reproduzido ao segundo) e **oito eclipses** solares e
lunares dos catálogos da NASA de 1999 a 2024 (um eclipse só ocorre em lua nova/cheia, então o instante
do máximo ancora a fase com precisão de minutos), tolerância de 2 min. Mais: comportamento por fuso,
virada de ano, datas de 1900 e 2100, coerência de idade/ciclo/iluminação, e um **controle negativo**
que mede o desvio da aproximação por sinódico médio (passa de 5 h — é o teste que justifica o
algoritmo caro).

---

### Migração

- **Nada obrigatório.** Mudança aditiva: bumpar já traz a sobrevivência a reboot, sem tocar em código.
- **Apps com lembrete local**: o contorno "reagendar tudo na abertura" pode ficar — e, na verdade,
  **convém ficar por uma versão**. Quem já tinha alarmes agendados por uma kmplib anterior **não tem
  registro persistente**: o receiver de boot leria um registro vazio e não restauraria nada. Reagendar
  na primeira abertura depois da atualização é o que **semeia** esse registro (todo `schedule*` agora
  persiste). Depois disso, `scheduler.refreshScheduledNotifications()` (idempotente e mais barato) dá
  conta sozinho.
- **Lua Certa:** apagar `core/domain/moon` local e importar de `br.com.codecacto.kmplib.astro`.
- **Hora do Remédio:** o `GAP-HR-M-04` deixou de existir.

## 2.98.0 — login social no own-auth: Google e Apple sem Firebase (ago/2026)

Onda 0 do **Crédito na Mão**, lado mobile. Fecha o **Gap (a)** da arquitetura daquele projeto e
destrava o login social para **todo app novo do ecossistema**, que nasce em own-auth (`backlib-auth-local`),
não em Firebase Auth. Par backend: `POST /auth/social` + `GET /auth/social/nonce` do `backlib-oidc`.

**Aditivo. Nenhum dos 10 apps com Firebase Auth muda de comportamento nem precisa recompilar
diferente.**

### 1. Os providers saíram do pacote `firebase.*` — porque nunca foram de Firebase

`GoogleAuthProvider`, `AppleAuthProvider`, `GoogleSignInResult`, `AppleSignInResult` e
`GoogleAuthHolder` mudaram de `br.com.codecacto.kmplib.firebase.auth` para
**`br.com.codecacto.kmplib.auth.social`**. O código sempre foi neutro (Android = Credential Manager
+ `GetGoogleIdOption`, a API oficial vigente; iOS = `ASAuthorizationController` puro); só o **nome do
pacote** dizia o contrário, e um pacote que mente é o que faz o próximo dev concluir que login social
exige Firebase.

Os nomes antigos continuam existindo como **`typealias @Deprecated`** com `ReplaceWith`. Não é uma
cópia: é o mesmo tipo, então `is`/`as` e atribuição cruzada seguem valendo (coberto por teste).

### 2. `signInWithGoogle`/`signInWithApple` deixaram de lançar `unsupported(...)`

`EmailPasswordAuthRepository` agora fala com `POST {authBasePath}/social` e devolve o **mesmo shape na
raiz** (`{accessToken, refreshToken, expiresInSeconds}`) de `login`/`register`/`refresh`. **A API
pública não mudou** — as assinaturas já existiam no `IAuthRepository`; mudou o corpo.

- **`OwnAuthSocialService`** (interface **nova**, não método a mais numa interface existente — isso
  quebraria as fakes que os apps mantêm em `commonTest`): `socialNonce()` e
  `signInWithSocial(provider, idToken, nonce, name?, email?)`. Exposta por `OwnAuth.social`.
- **`SocialProvider`** (`GOOGLE`/`APPLE`) com `wire` (`"google"`/`"apple"`) e `userProviderId`
  (`"google.com"`/`"apple.com"`, o mesmo vocabulário do `AuthRepository` Firebase).
- **`SocialNonce`** e o `SocialBody` de fio; `OwnAuthConfig` ganhou `socialSuffix` e
  `socialNonceSuffix` (configuráveis, defaults `social` e `social/nonce`).
- `OwnAuthSession` ganhou **`providerId`** (default `"password"`, então sessão gravada antes desta
  versão lê sem perda) e `toUser()` parou de carimbar `"password"` fixo: `user.isGoogleProvider`
  passa a responder a verdade no own-auth. O **refresh preserva a origem** — renovar token não
  converte login social em login por senha.

### 3. O nonce vem do servidor. Sempre.

`GET /auth/social/nonce` é o único emissor. Nonce escolhido pelo próprio aparelho não amarra nada: um
`idToken` vazado (log, proxy, outro app no mesmo dispositivo) é reapresentado com o mesmo valor e
passa. Por isso os providers ganharam a sobrecarga **`signIn(nonce: String)`** (Android:
`GetGoogleIdOption.setNonce`; iOS Apple: SHA-256 hex do valor cru), e o `signIn()` sem nonce ficou
documentado como caminho **Firebase-only**.

Como `signInWithGoogle(idToken, accessToken)` — assinatura herdada do contrato Firebase — não tem
campo de nonce e no fluxo Google o nonce viaja *dentro* do `idToken`, o repositório guarda o último
nonce emitido por `socialNonce()` e o consome no primeiro uso. Sem nonce prévio, **falha explícito e
sem tocar a rede**, em vez de inventar um valor e receber do servidor um "credencial inválida" que
apontaria para o lugar errado.

### 4. O `accessToken` do Google nunca sai do aparelho como prova de identidade

O parâmetro continua na assinatura (compatibilidade) e é **ignorado**. Access token é credencial de
*autorização*: qualquer app obtém um para o próprio projeto e o apresenta a um servidor terceiro
(*token substitution*). Só o `idToken` — assinado, com `aud`/`nonce`/`exp` verificáveis — prova quem
é o usuário. Há teste que falha se a string aparecer no corpo.

### 5. Google no iOS saiu de stub: `GoogleSignInBridge`

`GoogleAuthProvider.ios` devolvia um erro dizendo "implemente no Swift". Agora existe
**`@ObjCName("GoogleSignInBridge")`**, mesmo padrão do `ApplePushBridge` (2.76.0): o Kotlin declara o
contrato e suspende; o Swift executa com o SDK oficial **GoogleSignIn-iOS** (SPM) e responde por
`onSignInSuccess`/`onSignInFailure`/`onSignInCancelled`. Fluxos serializados por `Mutex`, callback
tardio ignorado, e **sem executor registrado o erro diz exatamente o que falta**. Passo a passo Swift
completo no KDoc do bridge.

Não é reimplementação de OAuth à mão (o atalho errado): consumir o SDK por cinterop dentro da lib
obrigaria **todo** app consumidor a linkar o GoogleSignIn, inclusive os que não têm login social.

**Não compila em Linux — pendente de validação em macOS.**

### 6. Apple no Android continua indisponível — decisão, não lacuna

A Apple não publica SDK Android; a única alternativa seria o fluxo web (Custom Tabs + Services ID +
domínio verificado + deep link de retorno), com superfície de ataque própria (interceptação do
redirect) para atender um caso que nenhum app do portfólio tem. O padrão de mercado é não exibir o
botão. `AppleAuthProvider.signIn()` no Android devolve **erro explícito e legível**, nunca um
resultado vazio que a tela confunda com "cancelado". O app esconde o botão no Android.

### Testes

`OwnAuthSocialTest` (24) + `SocialProviderAliasTest` (4). Suíte total **1592, zero falha**.
**Controle negativo:** revertendo o `providerId` da sessão para `"password"` fixo, 4 testes falham.

### Migração (opcional, sem prazo)

Trocar `import br.com.codecacto.kmplib.firebase.auth.{GoogleAuthProvider, AppleAuthProvider,
GoogleSignInResult, AppleSignInResult, GoogleAuthHolder}` por `...auth.social.*`. O alias mantém tudo
compilando enquanto isso.

> **Nota:** `br.com.codecacto.kmplib.ui.screens.login.GoogleSignInResult`/`AppleSignInResult`
> (contrato da tela `LoginScreen`, campos não-nulos) são tipos **diferentes** e **não** foram
> unificados: fundi-los mudaria a nulidade de um campo público de tela e quebraria consumidores.
> Registrado no `docs/backlog.md`.

## 2.97.0 — leitura de código de barras: o produto entra pela câmera, não pelos 13 dígitos (ago/2026)

Fecha o **GAP-CV-M-01**. O módulo `camera` só sabia ler **placa veicular** (OCR); não havia caminho
para **código de barras**, e sem ele o app de validade de varejo que está nascendo (Controle de
Validade) vira "um app de digitar 13 dígitos de pé na gôndola" — a digitação manual continua sendo
requisito, mas como **saída**, não como caminho principal.

### Módulo novo: `camera/barcode`

- **`BarcodeScannerView`** — o componente pronto: preview + mira + lanterna + estados de permissão
  + anti-repetição + confirmação de leitura + slot de overlay para o app. É o que a tela de
  scanner usa.
- **`BarcodeCameraPreview`** (`expect`/`actual`) — preview cru com detecção contínua, para quem
  quiser compor a própria tela.
- **`BarcodeAnalyzer`** (`expect class`) — leitura a partir de **bytes de imagem** (foto da
  galeria), irmão do `PlateOcrAnalyzer`.
- **`ScannedBarcode`** / **`BarcodeFormat`** / **`BarcodeFormats`** (presets `RETAIL` · `COMMON` ·
  `ALL`) · **`Gtin`** · **`parseBarcode`** · **`parseTypedRetailBarcode`**.
- **`BarcodeScanDebounce`/`BarcodeScanDebouncer`**, **`BarcodeScanFeedback`**,
  **`BarcodeScannerState`/`BarcodeCameraStatus`**, **`BarcodeScannerTexts`**,
  **`BarcodeScannerHandle`**.

### Padrão-ouro, e por que cada escolha

- **Android = ML Kit Barcode Scanning sobre CameraX**, com o **modelo embarcado**
  (`com.google.mlkit:barcode-scanning`) e não a variante que baixa do Play Services: num depósito
  ou numa loja com Wi-Fi ruim a leitura tem de funcionar no primeiro uso.
- **iOS = `AVCaptureMetadataOutput` (AVFoundation) para o AO VIVO e `VNDetectBarcodesRequest`
  (Vision) para IMAGEM PARADA.** A Apple decodifica códigos dentro do próprio pipeline de captura;
  rodar um request de Vision por frame gastaria CPU e bateria à toa num app que fica com a câmera
  aberta o turno inteiro. Vision é o caminho oficial da imagem parada — e é lá que ele é usado.
- **Nada de ZXing, WebView ou wrapper de terceiros.**

### O que a lib resolve para não ser resolvido errado em cada app

- **Leitura contínua sem repetir.** A câmera reconhece o mesmo código em ~30 frames por segundo;
  sem filtro, apontar por dois segundos cadastraria cinquenta lotes. O `BarcodeScanDebouncer` é
  `commonMain` puro e determinístico (o tempo entra por parâmetro): supressão do **mesmo** código,
  intervalo entre **quaisquer** duas leituras e confirmação por N leituras iguais. Um código
  **diferente** passa em seguida **sem recriar a tela** (modo "escanear vários seguidos"), e
  `resetDebounce()` libera reler o **mesmo** produto na hora — duas caixas com validades
  diferentes é o caso normal do varejo.
- **Dígito verificador conferido na fronteira.** Um GTIN parcial/borrado é **descartado** em vez de
  virar produto errado no estoque. Inclui a expansão **UPC-E → UPC-A**, a normalização
  `toGtin13()`/`toGtin14()` e `isProductCode` (um QR nunca vira chave de catálogo; um ITF-14 sim, e
  só encurta para 13 dígitos se o excesso forem zeros — GTIN-14 com indicador ≠ 0 é **outro item**,
  a caixa, e confundi-lo com a unidade seria erro de estoque).
- **A pegadinha do UPC-A.** O iOS **não tem** UPC-A como simbologia: devolve EAN-13 com zero à
  esquerda; o Android devolve 12 dígitos e o tipo `UPC_A`. Comparar `format` faria o mesmo produto
  ter chaves diferentes por plataforma — `toGtin13()` iguala as duas (coberto por teste).
- **Nunca um beco sem saída.** Permissão pendente, **negada em definitivo** (com "Abrir
  Configurações"), aparelho sem câmera e falha de inicialização são estados nomeados, cada um com
  sua ação e com a **digitação manual** ao lado.
- **Feedback = vibração ligada, som desligado** (padrão da casa, igual ao `ChecklistItem`): a
  confirmação não exige olhar a tela, e o bipe é do ambiente, não do app — quem quiser liga
  (`BarcodeScanFeedback.FULL`).
- **Lanterna** ligável (gôndola é escura), com o botão **escondido** quando o aparelho não tem.

### i18n — os textos da lib passam a seguir o idioma do aparelho

Primeira vez que a kmplib traz **strings** nos Compose Multiplatform Resources
(`values` = pt-BR, `values-en`, `values-es`, `values-pt-rPT`). O padrão `*Texts` injetável
continua valendo — muda que, quando o app **não** passa nada, `rememberBarcodeScannerTexts()`
devolve os textos no idioma do dispositivo, sem seletor e sem trabalho do app.

### Infra de câmera fatorada (a lib parou de duplicar dentro de si mesma)

`CameraXPreview` (androidMain, interno) passou a ser a base **compartilhada** pelo OCR de placa e
pelo leitor de código de barras. Três defeitos do `CameraView` foram corrigidos na mudança —
valem para o MeuEstacionamento sem nenhuma alteração no app:

- **`unbind` no `onDispose`**: sair da tela agora desliga a câmera. Antes o bind ficava preso ao
  ciclo da Activity e a câmera seguia ligada (indicador do sistema aceso) em outra tela.
- **permissão reconsultada**: a versão anterior lia a permissão **uma única vez** (`remember`) e
  ficava presa no placeholder mesmo depois de o usuário conceder o acesso. Agora usa o
  `rememberPermissionState`, que confere e solicita.
- **callbacks na main thread** e `ProcessCameraProvider` fora dela (o `.get()` bloqueava a main);
  falha de bind virou erro reportado em vez de `runCatching` mudo com tela preta.

### Aditivos fora do módulo

- **`platform/permission/rememberPermissionState`** + `PermissionState` + `rememberPermissionManager`
  — o ciclo "conferir → pedir → mandar para as Configurações" num lugar só (o
  `rememberPermissionManager` já era citado no KDoc do `PermissionManager` e não existia).
- **`UrlLauncher.openAppSettings()`** — Android `ACTION_APPLICATION_DETAILS_SETTINGS`, iOS
  `openSettingsURLString`. Entra com **corpo default** (loga aviso), então `UrlLauncher` mantido por
  app segue compilando.
- `PlatformCapabilities.cameraCapture` teve o KDoc corrigido: passa a declarar que gate **também** a
  leitura de código de barras, e que o `false` no iOS é pendência de **validação em macOS**, não
  stub silencioso.

### Testes

`BarcodeParserTest` (18), `BarcodeScanDebouncerTest` (11), `BarcodeScannerStateTest` (7) = **36
novos**; suíte total **1.564 testes, 0 falhas**, `koverVerify` verde.

**Compatibilidade:** aditivo. Nenhuma assinatura pública existente mudou de forma incompatível.

**Pendente de macOS:** os `actual` iOS (`BarcodeCameraPreview.ios`, `BarcodeAnalyzer.ios`,
`BarcodeScanFeedback.ios`, `AppleBarcodeFormats.ios`) estão escritos conforme as APIs oficiais mas
**não compilam em Linux** — os klibs iOS saem da release no Mac do fundador.
`PlatformCapabilities.cameraCapture` segue `false` no iOS até lá; o app **não deve vender** o
scanner no iPhone antes dessa validação (a digitação manual cobre).

## 2.96.0 — a densidade da prancha vai até 5: tablet grande não vira fita no meio (jul/2026)

`GridDensity` tinha 3 degraus (1/2/3 colunas), pensados para celular. Num tablet grande isso
obriga o app a escolher entre botão gigante e uma **faixa estreita centralizada** com meia tela de
margem — nenhum dos dois é o que a pessoa quer ver num painel de comunicação.

- **`GridDensity.Four` (4) e `GridDensity.Five` (5)**: degraus de tela grande, onde 3 colunas de
  alvo confortável ainda deixam tela sobrando. Num celular eles valem (é escolha do usuário), mas
  o alvo fica pequeno — cabe ao app decidir quais degraus oferecer nas configurações.
- **`gridDensityOf(columns, fallback)`**: densidade pelo nº de colunas, com fallback para valor
  fora da faixa. Substitui o `when (columns)` manual que cada app repetia ao ler a preferência
  persistida — e que, ao ganhar degraus, calaria em `else` no degrau novo.

`effectiveGridColumns` e o `DensityGrid` não mudaram: continuam derivando colunas da largura, com
piso na densidade escolhida e teto em 8.

**Compatibilidade:** aditivo. Um `when (density)` exaustivo sobre `GridDensity` num consumidor
passa a exigir os dois ramos novos (erro de compilação ao subir de versão, nunca comportamento
silencioso).

## 2.95.0 — o volume do aparelho é do usuário: amplificação da fala vira opt-in (jul/2026)

`AndroidTtsController` **forçava** o volume de mídia no máximo e amplificava toda fala com um
`LoudnessEnhancer` (síntese em arquivo + ganho real de áudio). O comportamento nasceu para
comunicação assistiva, mas valia para **todo** consumidor de TTS da lib: o app sobrescrevia, sem UI
e sem pedir, o volume que a pessoa tinha escolhido no aparelho — inclusive um volume baixo
deliberado.

**Agora o padrão respeita o aparelho.** A fala continua roteada pelo stream de MÍDIA (o
`KEY_PARAM_VOLUME = 1.0` é *relativo* a ele: fica no topo do que o usuário permitiu, sem alterar o
volume do sistema).

A amplificação continua disponível, agora **explícita**:

```kotlin
tts.setVolumeBoost(true)   // volume de mídia no máximo + LoudnessEnhancer
```

- `TtsController.setVolumeBoost(enabled: Boolean)` entra com **corpo default vazio** na interface —
  quem implementa o contrato (fakes de teste, iOS) não precisa mexer em nada;
- iOS é no-op: a plataforma não permite forçar o volume do sistema;
- o fallback segue de pé — se qualquer etapa da amplificação falhar, cai na fala direta e nunca
  fica mudo.

**Mudança de comportamento (sem breaking de assinatura):** um app que dependia do volume forçado
precisa chamar `setVolumeBoost(true)` para manter o que tinha.

## 2.94.0 — a prova da recusa, a correlação por handles e a integridade do ciclo (jul/2026)

Fecha os **três achados** do code review que validou a 2.93.0 no consumidor real ("Todos a Bordo").
Todos são **pré-existentes** e valem para os ~14 apps da onda REST-CRUD. **Sem breaking change**:
nenhuma assinatura pública mudou de forma incompatível (só entraram parâmetros com default e APIs
novas). Migração de schema **v3 → v4 puramente aditiva**.

### 1 (P1) — o toque seguinte apagava a prova de que o servidor tinha recusado

`RestEntityMirror.putDirty` montava a linha com `attempts = 0, failed = 0, last_error = null`, e o
`upsert` gravava tudo. Zerar `failed` está **certo** (a intenção nova do usuário substitui a recusa
e devolve a linha à fila drenável); zerar `attempts` **não**, porque `attempts` é história de
*entrega*, não do payload. Sequência real e silenciosa:

1. o registro é recusado (4xx) → `attempts ≥ 1` → o app avisa "não salvo" ✔
2. no ponto seguinte, **sem sinal**, o usuário toca de novo → tudo era zerado
3. a linha virava `Pending(attempts = 0)`, **indistinguível de uma pendência nova legítima** — e a
   conferência do app a dava por boa: **"Tudo certo!"** com o servidor sem registro nenhum daquela
   criança, exatamente o desfecho que o produto existe para impedir.

**Correção — duas camadas com tempos de vida diferentes:**

| Camada | Colunas | Quem limpa |
|---|---|---|
| **estado ATUAL da falha** | `failed`, `fail_code`, `last_error` | uma escrita nova do usuário (correto, mantido) |
| **histórico de ENTREGA** | `attempts`, `rejections`, `reject_code`, `reject_error` | **só o servidor aceitar** a linha |

- schema v4 (`3.sqm`, `ALTER TABLE ADD COLUMN`): **`rejections` / `reject_code` / `reject_error`**;
- `markFailed` grava nas duas camadas; `clearFailed` (retry explícito) **não** toca no histórico —
  pedir "tentar de novo" sem sinal não pode transformar uma recusa em pendência confiável;
- `markClean`/`RestEntityMirror.writeClean` são o **único** ponto que zera o histórico, e zeram
  porque o servidor **aceitou** (o registro existe do outro lado);
- a política de preservação ficou concentrada no Kotlin (`RestEntityMirror.row`/`DeliveryHistory`),
  não dividida entre a SQL e o chamador.

**API nova (aditiva):** `RestRejection(count, code, message)` com `isQuota`; `RestRowState.rejection`
(`null` = **nunca** recusada, com garantia); e os três sinais derivados
`RestRowState.wasRejected`, **`RestRowState.hasDeliveryTrouble`** (o critério de "não pode ser dado
por bom") e `RestRowState.isUntriedPending` (a pendência **legítima** do offline-first — sinalizar
toda pendência transformaria o trajeto sem sinal num alarme contínuo). `RestRow` repassa os dois
primeiros.

### 2 (P1) — a documentação da própria lib induzia ao erro

O exemplo `it.rotaId == rotaId` aparecia em **três** lugares (`RestIdResolver`,
`OfflineFirstRestRepository.observeCanonicalId`, `RestEntityMirror.observeCanonicalId`) e é
**incorreto sempre que o drain puder ser interrompido** — que é o default (`applyDrainFailure`
devolve `false` em `RestFailureClass.Offline` e o drain aborta). Interrompido o drain, filhos já
migrados (FK = id do servidor) e filhos ainda locais (FK = id local) **convivem na mesma lista**:
comparar por igualdade contra qualquer um dos dois derruba a outra metade — no app, "sumiu da lista".

- **exemplos corrigidos** nos três lugares (e no catálogo), com o "ERRADO/CERTO" explícito;
- **`RestIdResolver.handlesOf(id): Set<String>`** promovido a API de primeira classe (`{id} ∪
  {canonical(id)} ∪ {clientIdOf(id)}`, resolvendo nos dois sentidos), mais a sobrecarga
  `handlesOf(ids: Iterable<String>)`. Era o helper que o app teve de escrever à mão;
- **`indexByHandle(items, idOf)`** — o mapa responde por qualquer handle (padrão "atributo do
  cadastro a partir de uma FK congelada": o ponto de parada sumindo do card, sem erro na tela);
- **`groupByRef(items, refOf): RestRefGroups<T>`** — filhos agrupados por pai, com `get`/`count`
  aceitando qualquer handle (memo interno) e `countByCanonicalId()` para alimentar uma lista inteira;
- **operador de fluxo, com o dispatcher certo** (a nota do dev): `Flow.resolvingIds(ids) { … }`
  resolve **fora do contexto do coletor** (`RestIdResolver(store, dispatcher = …)`) — consultar o
  remap é leitura de banco, e fazê-la no `map` de um fluxo coletado pela UI é SQLite na thread
  principal;
- no repositório: **`observeHandles(handle)`** (reativo, já com `flowOn`) e
  **`observeChildren(handle, children, refOf)`** — o atalho correto por construção.

### 3 (P2) — o ciclo de sync não reconferia o titular

`syncNow` conferia o escopo **uma vez, no início**. Um ciclo em voo atravessava um `setAccountScope`
e misturava Bearer e bucket: o PUSH subia a outbox do titular anterior com o token de quem acabou de
entrar — **vazamento de dado entre contas** (no consumidor, dado de criança).

- o motor **reconfere o titular antes de cada participante** (push e pull) e aborta o ciclo se ele
  mudar; abortar por troca de titular **não** é "falha de sincronização" (estado volta a `Idle`);
- `OfflineFirstRestRepository.drainOutbox` reconfere **antes de cada linha** e **antes de aplicar a
  resposta** de cada requisição: nada é escrito no espelho depois da troca — gravar sob o titular
  novo colocaria o dado de quem saiu dentro da conta de quem entrou. A outbox de quem saiu fica
  intacta;
- `refresh()`/`refreshPage()` reconferem entre o GET e a reconciliação (senão o dado lido sob A seria
  gravado no bucket de B). Sentinela nova `ACCOUNT_CHANGED_CODE (-4)`;
- **`RestCrudSyncEngine.setAccountScope(accountId, legacy)`** (novo, `suspend`) — troca o titular
  **sob o mesmo mutex do ciclo**: a troca espera o ciclo em execução terminar e um ciclo novo só
  arranca depois de ela ser aplicada. É o caminho recomendado para todo app com login, e o único que
  fecha **até a requisição em voo**. Habilitado pelo novo parâmetro de construtor `store: SyncStore?`
  (que também deriva o `accountScope` sozinho).

### Testes

**+29 casos** (`RestRejectionHistoryTest` 10, `RestHandleCorrelationTest` 12, `RestCrudSyncEngineTest`
+5, `OfflineFirstRestWriteTest` +2), incluindo a sequência exata do achado 1 (recusa → toque offline
→ estado final) e a troca de titular no meio do ciclo. Suíte: **1527 casos, 0 falhas**; `koverVerify`
verde.

**Controle negativo** (os testes novos falham sem a correção): revertendo a preservação do histórico
em `putDirty` → **4 falhas** em `RestRejectionHistoryTest`; trocando `handlesOf` pela correlação
ingênua (`setOf(canonical(id))`) → **7 falhas** em `RestHandleCorrelationTest`; desligando a
reconferência do titular → **4 falhas** (2 no motor, 2 no repositório).

### Migrar

- **Todos os apps da onda**: nada a fazer para receber as correções 1 e 3 (a preservação do histórico
  e as reconferências são comportamento interno). Apps **com login** devem trocar
  `store.setAccountScope(...)` por `engine.setAccountScope(...)` e construir o motor com `store =`.
- **"Todos a Bordo"**: `RestRowState.hasDeliveryTrouble` substitui o `isUnsaved` local derivado de
  `attempts`; `IdHandles.kt` pode ser **apagado** (`handlesOf` agora é da lib).

---

## 2.93.0 — offline-first REST-CRUD: o id migra, o handle do app não (jul/2026)

Fecha o **P0 `GAP-KL-M-RESTCRUD-IDMIGRATION`** — o terceiro defeito da mesma família, também
**pré-existente**, e a causa-raiz comum dos dois achados do code review do "Todos a Bordo" (um
bloqueante, um importante). Vale para **todos os ~14 apps** da onda REST-CRUD. **Sem breaking
change**: nenhuma assinatura pública mudou de forma incompatível.

### O defeito — uma raiz, dois estragos

Um registro criado offline nasce com id local (`local-…`) e ganha o id do servidor quando
sincroniza. A migração era feita apagando a linha do id local e reinserindo sob o id do servidor —
**e gravando o id do servidor também em `client_id`**, a única coluna que poderia servir de âncora.
Em paralelo, a tradução `clientId → serverId` vivia numa **variável local do ciclo de sync**
(`RestCrudSyncEngine.syncNow`), descartada ao fim dele.

1. **A tela esvaziava no meio do uso (bloqueante).** A UI navega com o id local, que congela no back
   stack. Ao voltar o sinal, o drain migra o id e **toda consulta pelo id local passa a devolver
   vazio**. No "Todos a Bordo": as telas de execução mostravam "nenhum passageiro" e a conferência
   final calculava sobre lista vazia — banner verde **"Tudo certo!" com as crianças ainda dentro do
   veículo**, exatamente a falha que o produto existe para impedir. Não era recuperável de dentro da
   tela.
2. **A FK do filho ficava impossível (perda definitiva de dado).** Um filho que não drenasse no
   mesmo ciclo do pai (sinal caiu no meio do drain, app fechado entre os dois `POST`s) perdia a
   tradução: no ciclo seguinte o pai não tem mais nada a drenar, o remap chega **vazio**, e o `POST`
   do filho sobe com a FK apontando para o id local. O backend tem `FOREIGN KEY … REFERENCES` com
   UUID: **4xx → terminal → `Failed` para sempre**, e cada "Tentar novamente" repetia o mesmo POST
   impossível.

### A correção

- **`client_id` virou âncora PERMANENTE.** Todo caminho de escrita limpa passou por um ponto único
  (`RestEntityMirror.writeClean`, usado por `putClean`/`confirm`/`markSynced`/`reconcile`/
  `mergeClean`) com três invariantes: a chave física passa a ser o id do servidor; **`client_id`
  nunca muda depois de atribuído**; migração de id é **registrada de forma durável**.
- **Handle estável — o consumidor não precisa saber que existe migração.** `SyncStore` ganhou
  `getByHandle`/`observeVisibleByHandle` (`selectByHandle`/`selectVisibleByHandle`: casa `local_id`
  **ou** `client_id` **ou** `server_id`). **Todo id aceito pelo `RestEntityMirror` e pelo
  `OfflineFirstRestRepository` é um handle**: `observeById`, `getCached`, `getById`, `stateOf`,
  `observeByIdWithState`, `update`, `delete`, `requeueFailed`, `discardFailed`. O id que o app
  recebeu no `create` vale para sempre — inclusive depois de reiniciar o processo.
- **Remap durável `clientId → serverId`** (tabela nova `sync_id_remap`, escopada por conta):
  gravado no **instante** da migração, sobrevive a ciclos, a **drenagem parcial** e a reinício de
  processo. `SyncStore.rememberServerId`/`resolveServerId`/`resolveClientId`/`countIdRemap`/
  `forgetServerId`.
- **Resolução de FK entre entidades que drenam em ciclos diferentes**, em duas camadas:
  `RestCrudEntity.remapRefs` passou a receber um mapa **materializado** com o remap do ciclo **mais**
  os mapeamentos duráveis dos ids que aquele payload realmente referencia (nada de `Map` preguiçoso
  que mentiria em `isEmpty()`); e o corpo enviado passa por uma varredura genérica
  (`RestPayloadRemap`) que traduz valores string iguais a um id conhecido. **A correção chega aos
  apps que nem implementam `remapRefs`** — nenhum precisa mudar.
- **Correlação de filhos na UI:** `OfflineFirstRestRepository.canonicalId(handle)` (síncrono),
  `observeCanonicalId(handle)` (reativo — emite o id do servidor assim que ele migra) e
  `ids: RestIdResolver` (`canonical`/`same`/`clientIdOf`/`isMigrated`), para comparar ids que podem
  ter migrado sem usar `==`.
- **Correções vizinhas encontradas no caminho:** `update()` normaliza um modelo cujo id é um handle
  antigo antes de falar com a rede (novo `RestCrudEntity.withId`, default delegando a `withLocalId`)
  — antes faria `PUT /…/local-…` num registro que já existia no servidor; o drain monta a URL de
  `PUT`/`DELETE` com o `server_id` **da linha**, não com o id que por acaso está no payload;
  `putClean` ganhou `replacingHandle` para o app que confirma uma criação por endpoint **próprio**
  migrar a linha local em vez de deixar uma órfã ao lado; e `newRestClientId()` ganhou sufixo
  aleatório, porque virou **chave** do remap durável e o contador reinicia com o processo.

### Migração de schema (v2 → v3)

`2.sqm` é **puramente aditivo** (cria `sync_id_remap` + índice por `client_id`): nenhuma linha é
movida, copiada ou dropada nas bases em produção. Registros criados offline **antes** desta versão e
já sincronizados tiveram o `client_id` sobrescrito lá atrás — para eles `client_id == server_id`, o
handle resolve por identidade e nada quebra.

### Testes

`RestIdMigrationTest` (**13**, novos): FK de pai e filho em **ciclos diferentes** (com e sem o hook
`remapRefs`), **drain interrompido no meio**, **reinício de processo** entre o `POST` do pai e o do
filho, consulta por handle depois da migração (`getCached`/`observeById`/`getById`/`stateOf`),
`update`/`delete` pelo handle antigo, `observeCanonicalId`, `RestIdResolver.same`, `client_id`
sobrevivendo a `markSynced`/`reconcile`/`confirm`, `putClean` de endpoint custom, isolamento do
remap por conta e a varredura genérica (não toca texto livre nem quebra corpo não-JSON).
**Controle negativo:** sabotando só o remap durável, **6 dos 13** falham; sabotando só a preservação
do `client_id`, **11 dos 13** — mais o teste do fluxo do motorista da 2.92.0. Suíte: **1.498 testes,
0 falhas**; `koverVerify` verde.

## 2.92.0 — offline-first REST-CRUD: o registro criado offline sobe como CREATE (jul/2026)

Fecha o **P0 `GAP-KL-M-RESTCRUD-PENDINGOP`**, defeito **pré-existente** da outbox que a 2.91.0
tornou visível (antes ele falhava calado; com o estado por linha, ele passou a acender "não salvo"
na cara do usuário). Vale para **todos os ~14 apps** da onda REST-CRUD que criam e editam um
registro dentro da mesma janela offline. **Sem breaking change** — nenhuma assinatura pública mudou.

### O defeito

`RestEntityMirror.putDirty` gravava `pending_op` com a operação **pedida pelo caller**, sem olhar o
estado da linha. Sequência real (iniciar a rota **sem rede** e marcar embarques):

1. offline, a linha nasce com `pending_op = CREATE` e id local (`server_id == null`);
2. o primeiro toque chama `update()` → a operação pendente **vira `UPDATE`**;
3. ao reconectar, o drain faz `PUT /v1/…/local-…` → **404** → classificado como recusa **terminal**
   → linha marcada `Failed`;
4. o reenvio repete o mesmo PUT impossível. **A execução offline inteira nunca subia.**

### A correção — máquina de estados da outbox num ponto só

- **`resolveOutboxOp(requested, knownLocally, hasServerId): SyncOpType?`** (`sync/rest`, pura e
  testável) é a nova fonte única da operação pendente. Invariante: **`server_id == null` ⇒ a linha
  nunca existiu no servidor**, logo toda escrita subsequente **continua sendo uma criação** (o
  payload muda, a operação não). `null` = **nada a enviar** (resolve-se localmente).
- **Transições corrigidas** (todas as que decidiam `pending_op` sem olhar o estado anterior):

  | Situação | Antes | Agora |
  |---|---|---|
  | `update` sobre linha com `CREATE` pendente (`server_id == null`) | virava `UPDATE` → `PUT /…/local-…` → 404 eterno | continua `CREATE`; o drain faz **um único POST** com o payload final |
  | `delete` sobre linha com `CREATE` pendente | enfileirava `DELETE /…/local-…` (404 previsível) | **remove a linha localmente**, sem tocar a rede |
  | `create` sobre linha que **já tem** `server_id` | re-`POST` → **duplicaria** o registro | vira `UPDATE` |
  | linha **desconhecida** no espelho | — | respeita o pedido (o id veio do app; inferir viraria POST duplicado) |
  | linha gravada por versão anterior (`UPDATE` sem `server_id`) | presa em 404 para sempre | **curada no drain**: sobe como POST |

- **`OfflineFirstRestRepository.update()`/`delete()`** passaram a **curto-circuitar** a rede quando a
  linha é local-only (`mirror.isLocalOnly(id)`): não há o que atualizar/apagar num id que o servidor
  não conhece. `update()` grava no espelho e devolve `Success` (a escrita **foi** aceita; o estado é
  `Pending`); `delete()` apaga local e devolve `Success`. Vale para os **dois** `RestWriteMode` — em
  `OnlineFirst` o mesmo `PUT /…/local-…` também acontecia.
- **`drainOutbox`** deriva a operação do **estado da linha** (não só do `pending_op` gravado), o que
  **cura** as linhas já corrompidas em aparelhos que rodaram a 2.91.0 — sem migração de schema.
- **API nova (aditiva):** `resolveOutboxOp(...)` e `RestEntityMirror.isLocalOnly(localId)`.

### Testes

`RestWriteStateTest` 10 → **15** (as 5 transições da máquina de estados, puras) e
`OfflineFirstRestWriteTest` 15 → **24**: o fluxo do motorista ponta a ponta (offline → toque →
reconexão: **um** POST, `clientId → serverId` remapeado, linha `Synced`), vários updates antes de
qualquer sync → **um** POST com o payload final, `delete` sobre `CREATE` pendente sem ida à rede,
cura de linha legada, e as duas **regressões** que garantem que linha já sincronizada continua indo
de `PUT`/`DELETE` ao servidor. Suíte: **1.485 testes, 0 falhas**; `koverVerify` verde.

## 2.91.0 — offline-first REST-CRUD: a escrita não some, e o espelho é por conta (jul/2026)

Fecha os **dois P0** que o tech-lead levantou na entrega do "Todos a Bordo"
(`GAP-KL-M-RESTCRUD-LOCALFIRST` e `GAP-KL-M-SYNC-ACCOUNTSCOPE`). Os dois defeitos valem para os
~14 apps da onda REST-CRUD, e no app estavam contornados na camada de UI — contorno que esta versão
existe para eliminar.

### GAP 1 — escrita perdida em erro de servidor

**O defeito.** `OfflineFirstRestRepository.create`/`update` eram *online-first* e só caíam na outbox
quando o erro era **falha de transporte** (código sentinela `-1`). Com rede presente e servidor
respondendo **4xx/5xx**, nada era gravado no espelho e nada entrava na fila: a escrita do usuário
**desaparecia**. No "Todos a Bordo" isso era uma criança marcada como desembarcada **sem que o
registro existisse**.

- **Toda falha passa a ser classificada** (`classifyRestFailure` → `RestFailureClass`):
  `Offline` · `Retryable` (5xx, 408, 429 e 401 pós-refresh) · `Terminal` (4xx de validação/403/404)
  · `Quota` (402). **401 é retentável** de propósito: o `DomainApiClient` já renovou o token e
  tentou de novo — se ainda falhou, a sessão expirou, e o certo é preservar a escrita para depois do
  novo login. **429 é retentável, 402 não** (limite de taxa passa; cota do plano só passa com
  upgrade). Resposta ilegível é **terminal**: repetir o POST duplicaria o registro.
- **Falha retentável agora vai para a outbox nos DOIS modos** — é a correção que os ~14 apps
  recebem **sem migrar nada**. Antes, um 502 momentâneo apagava a escrita.
- **`RestWriteMode.LocalFirst`** (opt-in, param de construtor): a escrita grava no espelho e entra
  na outbox **antes** de tocar a rede, e o resultado do servidor reconcilia depois. Recusa terminal
  deixa a linha **visível e marcada como não-salva, com o erro preservado** — nunca revertida em
  silêncio. `RestWriteMode.OnlineFirst` segue o default e, no erro terminal, continua devolvendo
  `Error` sem persistir (é o comportamento certo de um formulário, onde o usuário corrige o campo).
- **Estado por linha** (`RestRowState.Synced|Pending|Failed`, `RestRow<T>`): `observeAllWithState()`,
  `observeByIdWithState()`, `stateOf(id)`, `failedRows()`, `requeueFailed(id)`, `requeueAllFailed()`,
  `discardFailed(id)`. É o que torna desnecessário o overlay em memória que o app inventou — e que
  **morria com o processo**, levando junto a marcação recusada.
- **O drain deixou de retentar para sempre** o que nunca será aceito: consome só a fila **drenável**
  (`dirty = 1 AND failed = 0`); 4xx/402 durante o push marcam a linha como recusada (visível), e a
  linha só volta por retry **explícito** do app.
- **402 e 401 mantêm a semântica**: `DomainResult.Quota` continua abrindo o Paywall na hora; o
  refresh de token segue no `DomainApiClient`.

### GAP 2 — espelho local sem escopo de conta

**O defeito.** `synced_entity` era chaveada por `entity` + `local_id`, sem nada amarrando a linha à
conta que a criou. Num aparelho compartilhado, trocar de usuário vazava dado na **leitura** e, pior,
na **escrita**: o ciclo faz PUSH antes do PULL, então a outbox de A subia inteira **para a conta de
B** no servidor — permanente, porque o `reconcile` preserva linhas sujas de propósito.

- **`account_id` entrou na chave primária** do espelho (e do `sync_cursor`), e **toda** leitura/
  escrita/push do `SyncStore` filtra pelo titular corrente. **Isolar, não apagar:** trocar de conta e
  voltar **preserva a fila pendente de cada uma** — o contorno do app (`MirrorOwnerGuard`) apagava o
  espelho do usuário anterior, destruindo escrita não sincronizada de quem só trocou de conta.
- **API:** `SyncStore.accountScope: StateFlow<String>`, `setAccountScope(accountId, legacy)`,
  `countLegacyRows()`, `deleteAccountData(accountId)` (exclusão de conta/LGPD, sem tocar nas outras).
  As leituras reativas acompanham a troca de titular (`flatMapLatest`).
- **Migração de schema v1 → v2 automática e sem perda** (`1.sqm`; SQLite não altera PK, então é
  criar/copiar/dropar/renomear): as linhas existentes vão para o bucket **sem escopo**, que a
  primeira `setAccountScope` reivindica conforme a **`LegacyRowsPolicy`** — `Adopt` (default:
  preserva espelho e outbox de quem estava usando o app quando ele foi atualizado), `Isolate`
  (preserva invisível; o `refresh` repovoa) ou `Discard` (fail-closed, para aparelho compartilhado
  com dado sensível). **Nada é dropado** nas bases dos ~14 apps.
- **`RestCrudSyncEngine(accountScope = store.accountScope)`** (opcional, recomendado em todo app com
  login): enquanto o titular não for declarado, **nenhum ciclo roda** — trava que impede o push do
  bucket sem escopo com o Bearer de quem acabou de entrar.

### Compatibilidade

- **Sem breaking de fonte.** Os novos membros de `SyncStore` têm implementação default (os
  `FakeSyncStore` que os apps mantêm em `commonTest` seguem compilando); `writeMode` e `accountScope`
  são parâmetros novos **no fim** das assinaturas, com default = comportamento anterior.
- **Mudança de comportamento (intencional, sem opt-in):** falha retentável passa a cair na outbox e
  devolver `Success` com o modelo local (antes: `Error` e escrita perdida); e o drain para de
  retentar indefinidamente linha recusada por 4xx/402.
- `Synced_entity` (data class gerada) ganhou 4 colunas — só afeta quem **constrói** a linha à mão;
  nenhum app do portfólio faz isso (verificado).
- Testes: `RestWriteStateTest` (10), `OfflineFirstRestWriteTest` (15), `SyncAccountScopeTest` (9) +
  1 no `RestCrudSyncEngineTest` = **35 novos**, suíte verde.

## 2.90.0 — erro de compra classificado por código tipado, não por texto (jul/2026)

Fecha o `GAP-KL-M-PURCHASE-ERRORCODE`, registrado na 2.89.0 e priorizado pelo CTO como entrega
própria (é mudança de comportamento no caminho do dinheiro, em código sem cobertura).

**O defeito.** `RevenueCatPurchaseRepository.mapErrorCode(message)` classificava o erro de
`purchase`/`purchasePackage`/`purchaseConsumable` procurando `"network"`/`"store"`/`"pending"`/
`"declined"`/`"already owned"` **dentro da mensagem** do SDK. Duas consequências:

- a mensagem do RevenueCat é **localizada** — num aparelho em pt-BR nenhuma substring casa e todo
  erro de compra vira `UNKNOWN`. O alerta de pagamento chegava ao Discord existindo e **sem
  informar**, e a UI mostrava o mesmo texto para "cartão recusado" (o usuário resolve), "sem
  internet" (só tentar de novo) e "já é assinante" (restaurar);
- pior: dois desses textos **não existem em idioma nenhum**. O RevenueCat não tem código "declined"
  (recusa vem como `PurchaseInvalidError`) e "já possui" é *"This product is already active for the
  user"* — ou seja, `PAYMENT_DECLINED` e `ALREADY_OWNED` eram **inalcançáveis mesmo em inglês**.

- **Classificação agora sai do `PurchasesErrorCode`** (mesmo padrão que a 2.89.0 aplicou em
  `PurchaseIdentityError`), em `monetization/purchase/PurchaseErrorMapper.kt` (a superfície pública
  do erro — `PurchaseException`, `isPaymentIncident`, `PurchaseErrorTexts`/`userMessage` — fica em
  `PurchaseError.kt`):
  `PurchasesErrorCode.toPurchaseErrorCode()` recebe **só o código** — classificar por texto deixou de
  ser possível sem mudar a assinatura. A mensagem do SDK continua viajando em
  `PurchaseResult.Error.message`, mas **como diagnóstico técnico**, e o KDoc diz para não exibi-la.
- **`PurchaseErrorCode` ganhou 6 valores** (no fim do enum, ordinais dos 7 antigos preservados):
  `CONFIGURATION_ERROR`, `PURCHASE_NOT_ALLOWED`, `ALREADY_OWNED_BY_OTHER_USER`,
  `PURCHASE_IN_PROGRESS`, `INELIGIBLE`, `USER_CANCELLED`. Critério: cada valor só existe se **a UI
  ou o alerta agem diferente** por causa dele — daí `ProductNotAvailableForPurchaseError` reusar
  `PRODUCT_NOT_FOUND` (para o usuário é o mesmo "plano indisponível") em vez de inflar o enum.
- **Cancelamento não é erro** (o falso-positivo mais provável deste caminho, irmão do
  `LogOutWithAnonymousUserError` tratado na 2.89.0). `toPurchaseFailure(userCancelled)` devolve
  `Cancelled` se **o flag do SDK OU o código `PurchaseCancelledError`** disser desistência — basta
  uma das fontes, porque elas nem sempre concordam. Nos caminhos sem branch de cancelamento
  (`RestoreResult.Error`, falha de `getOfferings`) entra `USER_CANCELLED`, com
  `isPaymentIncident = false`; e `RevenueCatEntitlementProvider.restore()` converte esse código em
  `PurchaseOutcome.Cancelado` — desistir de restaurar deixou de ser "restauração falhou" (alerta).
- **`val PurchaseErrorCode.isPaymentIncident`** — separa falha **do sistema** (configuração, oferta,
  loja, código desconhecido ⇒ reportar via `PaymentAlertReporter` com `detalhe = "codigo=<NOME>"`) de
  falha **do usuário/ambiente** (rede, cartão, restrição, já assina, desistiu ⇒ mensagem na tela, sem
  alerta). Sem isso, ou se alerta tudo (enxurrada que esconde o incidente real) ou nada.
- **`PurchaseErrorTexts` + `PurchaseErrorCode.userMessage(texts)`** — o texto de tela por código,
  i18n injetável, defaults pt-BR que dizem **o que fazer**. A ação de cada código é a mesma em todo
  app do ecossistema, e até aqui cada um reescrevia o próprio `when` — quando reescrevia: dois apps
  exibiam a mensagem crua do SDK e um exibia literalmente o nome do enum (`code.name`).
- **Mesmo vício corrigido nos vizinhos** (perder o tipo, não só classificar por texto):
  `RestoreResult.Error` ganhou `code` (default `UNKNOWN`, retrocompatível); `getOfferings()` falha
  com **`PurchaseException(code, message)`** em vez de `IllegalStateException` (continua `Throwable`,
  quem lia `.message` não muda); `PurchaseOutcome.Falha` ganhou `code` (default `UNKNOWN`);
  `PurchaseManager.purchaseConsumable` sem monetização configurada devolve `CONFIGURATION_ERROR`, não
  `UNKNOWN`.
- **Testes — `PurchaseErrorMapperTest` (17)**, o primeiro deste caminho: classificação de cada código
  relevante; **a mensagem não classifica** (5 textos que casariam com as substrings antigas, cada um
  com código contrário, e o mesmo em pt-BR — falha se alguém reintroduzir `contains("network")`);
  cancelamento pelo flag, pelo código e por ambos; `isPaymentIncident` por valor; mensagem própria e
  não repetida para todo código; e um **guarda de bump do SDK**: código novo do RevenueCat que caia
  em `UNKNOWN` faz o teste falhar listando o nome, para a classificação ser consciente.

**Migração (source-breaking apenas para `when` exaustivo sobre `PurchaseErrorCode`):** **Super 8**
(`features/premium/PremiumViewModel.kt:482`) e **Prospecta**
(`features/premium/PremiumViewModel.kt:176`) têm `when (code)` sem `else` e param de compilar ao
bumpar. A migração é uma **simplificação**: apagar o `when` local e usar
`code.userMessage()` (ou `userMessage(PurchaseErrorTexts(...))` para customizar). **Minha Voz**
(`PremiumViewModel.kt:174`) mostra `code.name` ao usuário — trocar por `code.userMessage()`. Os
demais consumidores (LocAki, Influencer, Meu Barbeiro, Meu Advogado, TattooStudio, MinhaOS, MeuFrete,
PapelStudio, OlhoNoCPF, MinhaDespensa, MinhaObra, QuemMeDeve, Esquecido) só **constroem**
`PurchaseErrorCode`/`Falha` — compilam sem mudança.

## 2.89.0 — identidade de quem assina na loja: `identify`/`resetIdentity` (jul/2026)

Aditivo e retrocompatível. **Regularização**: a API nasceu na Onda 3 do TattooStudio (commit
`1c2abf6`, feito pelo dev-mobile para desbloquear a integração) e foi aqui revisada como dona da lib,
levada ao padrão-ouro do fornecedor, coberta por teste, versionada e publicada.

**O problema.** O `appUserId` só podia ser informado no **bootstrap** (`Purchases.configure`, via
`MonetizationManager.initialize`). Em produto multi-tenant quem assina é a **organização**, que só é
conhecida **depois do login** (`GET /me`). Sem trocar a identidade, o webhook do RevenueCat chega à
central com o app user anônimo/UID do usuário e **o entitlement nasce no tenant errado**: a
organização paga e continua bloqueada. Reconfigurar o SDK não é suportado pelo fornecedor, e chamar o
SDK por fora da lib fura a fundação — a forma oficial é `Purchases.logIn` **após** o configure.

- **API nova** em `PurchaseRepository`, exposta por `PurchaseManager` e pela fachada pública
  `MonetizationManager`: `suspend identify(appUserId): Result<Unit>`, `suspend resetIdentity():
  Result<Unit>`, `currentAppUserId(): String?`. Os três têm **implementação default na interface**
  (falha explícita / `null`), então fakes e implementações existentes seguem compilando — nenhum
  consumidor atual (Super 8, LocAki, Influencer, Meu Barbeiro, Meu Advogado, Incubadora) implementa
  `PurchaseRepository`, e a superfície que eles usam (`initialize`, `repository`, `isPremium`,
  `shouldShowAds`, `hasPurchase`, `config`, `purchaseConsumable`, `subscriptionState`) está intacta.
- **Nome `identify`/`resetIdentity`, não `logIn`/`logOut`** (decisão de dono da lib, mantida do
  commit original): a API pública da lib é neutra ao fornecedor (como `CrashReporter` é a Sentry) e,
  sobretudo, um `logOut()` na fachada de monetização colidiria com o `signOut()` do módulo de
  **autenticação** — dois "logout" no mesmo app, um derrubando a sessão e o outro não. A própria
  documentação do RevenueCat chama o tema de *Identifying Users*; o KDoc cita `Purchases.logIn/logOut`
  para quem procurar pelo nome do SDK.
- **`Result<Unit>` mantido**: é o padrão do módulo para operação de plumbing (igual a `getOfferings()`
  na mesma interface); os selados `PurchaseResult`/`RestoreResult` são para fluxo de compra do
  usuário, com `Cancelled` — que aqui não existe.
- **Falha tipada `PurchaseIdentityException(reason: PurchaseIdentityError, message)`** (era
  `IllegalStateException`/`IllegalArgumentException` genérica): `NOT_CONFIGURED` · `UNSUPPORTED` ·
  `INVALID_APP_USER_ID` · `NETWORK` · `STORE` · `UNKNOWN`. Erro no caminho do dinheiro tem de chegar
  ao Discord (`PaymentAlertKind.IdentidadeAusente` → `CrashReporter` → GlitchTip), e **queda de rede
  não pode virar enxurrada de alerta igual a contrato quebrado** — sem motivo tipado o app alertaria
  tudo do mesmo jeito. O motivo sai do **código tipado** do SDK (`PurchasesErrorCode`), nunca da
  mensagem (que é localizada).
- **Núcleo puro `PurchaseIdentity`** (commonMain, sem SDK): `check`/`isAnonymous`/
  `looksLikePersonalData`/`ANONYMOUS_ID_PREFIX`. Recusa **antes de tocar a rede** id em branco,
  valores reservados do RevenueCat (`null`/`none`/`nil`/`(null)`/`NaN`/`unknown`/`undefined`/
  `unidentified`/`anonymous`/`[]`/`no_user`, case-insensitive — tipicamente o que sai de quem
  serializa campo nulo do backend direto no id), id anônimo do próprio SDK e caractere de controle.
  Errar o App User ID não dá erro visível, dá dinheiro no lugar errado: por isso a regra virou função
  pura coberta caso a caso, e não `if` solto dentro do adapter.
  - **Dado pessoal (e-mail/CPF) só AVISA**, nunca bloqueia: o fornecedor desaconselha e-mail como App
    User ID (muda, e trafega para webhook/dashboard de terceiro — LGPD), mas recusar deixaria a
    compra inteira no tenant errado, que é pior que o aviso.
- **Padrão-ouro do fornecedor, além do `logIn` cru** (correções feitas nesta revisão):
  - **`resetIdentity` com app user já anônimo é sucesso no-op.** O SDK devolve
    `LogOutWithAnonymousUserError` nesse caso — um **falso incidente de pagamento** no logout de todo
    usuário que nunca chegou a ser identificado.
  - **Toda troca de sujeito invalida o catálogo em cache** (offerings/packages do repositório). A
    oferta do RevenueCat pode ser personalizada por app user (Targeting/Experiments) e cada
    `Package`/`StoreProduct` carrega o contexto de offering que **atribui a compra**: comprar um
    objeto buscado para o sujeito anterior atribui a receita errado.
  - `subscriptionState` — e portanto `MonetizationManager.isPremium` — passa a valer o entitlement
    **do novo sujeito** em ambas as direções (login e logout), nunca o de quem saiu.
- **Testes (14 novos):** `PurchaseIdentityTest` (6 — núcleo puro) e `PurchaseIdentityApiTest` (8 —
  defaults da interface, sem loja configurada, entitlement do novo sujeito, **não-herança entre
  tenants**, logout idempotente, id inválido sem tocar a loja, falha de rede preserva o estado,
  propagação até `MonetizationManager.isPremium`), sobre o novo `FakePurchaseRepository` reutilizável
  em `commonTest`. Suíte cheia: **1419 testes, 0 falhas**; `koverVerify` (40%) verde.
- **Não exposto no `EntitlementProvider`** de propósito: aquela fachada serve app single-user offline
  (ChamadaFacil/CallRecorder), e pôr identidade na interface obrigaria o `StubEntitlementProvider` a
  fingir que troca de sujeito. Quem precisa fala com o `MonetizationManager`.
- **Consumidor:** TattooStudio (`core/monetization/BillingIdentity.kt`, `billingAppUserId` =
  `organizationId`). Nenhum outro app precisa mudar.

## 2.88.0 — UI de execução de lista: `ChecklistItem`, `ProgressCounter` e `AppBanner` (jul/2026)

Aditivo. Três componentes promovidos **antes** de o primeiro app implementá-los localmente (gaps
GAP-TB-M-01/02/03 levantados pelo ux-designer no design do "Todos a Bordo"), porque são o coração
das telas de execução e nasceriam duplicados em ≥2 apps. Todos em `ui/components`, `commonMain`,
100% tokens de tema, zero cor hardcoded.

- **`ChecklistItem`** — item de lista em que o **item inteiro** é o alvo de toque (mínimo **64dp**,
  acima dos 48dp do Material, porque o uso é "de campo": motorista no trânsito, profissional de
  luva). 1 toque marca, o 2º desfaz, **sem diálogo** (marcar não é ação destrutiva; o desfazer é o
  próprio toque). Título + subtítulo, slots `leading`/`trailing`, tom por estado
  (`checkedTone`/`uncheckedTone`). Domínio-agnóstico: serve chamada/presença, check-in de evento,
  inventário, vistoria, portal do colaborador.
  - **Acessibilidade (padrão-ouro):** `Modifier.toggleable` + `Role.Checkbox` (um único nó semântico
    para o item todo) e `stateDescription` **do domínio** via `ChecklistItemTexts` — o leitor de tela
    lê *"Ana Beatriz, Rua das Flores 123, Embarcado"*, não "caixa de seleção marcada". Retorno
    **háptico** no toque: confirma o acerto sem exigir olhar a tela.
  - **Estado nunca só na cor** (WCAG 1.4.1): ícone diferente **e** tom de fundo diferente. Coberto
    por teste.
  - **`NEUTRAL` = ausência de tom** (fundo de superfície, borda de contorno). É o que permite que só
    os itens que significam algo chamem atenção numa lista longa — e que o MESMO componente sirva o
    caso "pendência crítica" (`uncheckedTone = DANGER`: item vermelho até ser resolvido) sem
    parâmetro extra.
- **`ProgressCounter`** (+ `CounterBadge` compacto e o modelo puro `CountProgress`) — contador
  operacional "X de Y" com barra fina e rótulo. **Deliberadamente distinto de
  `UsageMeter`/`UsageBadge`**, que são de **billing** (`UsageSnapshot`: cota paga, fonte de verdade
  no servidor, `-1` = ilimitado, paywall no esgotamento). Aqui não há cota nem servidor nem
  "esgotado" — é o andamento da tarefa do dia. Misturar as semânticas faria uma tela de operação
  herdar comportamento de cobrança.
  - Acessível: o bloco vira **um nó** que anuncia a frase inteira ("7 de 12 embarcados") + o
    `ProgressBarRangeInfo`, em vez de fragmentos desconexos e um percentual órfão.
  - Bordas cobertas por teste: `total = 0` (sem divisão por zero e **sem** pintar de "completo"),
    contagem acima do total (barra e `remaining` não estouram).
  - Reusa `AppProgressBar` (não desenha outra barra); `progressToneColor` virou público como fonte
    única do mapeamento tom → cor de progresso.
- **`AppBanner`** — faixa full-width com ícone + título + mensagem, ação e dismiss opcionais: o
  **par mobile do `Banner` da weblib**, fechando a paridade do padrão "aviso inline por tom, **erro =
  sólido**" (memória `error-banner-solid-standard`).
  - **`defaultBannerStyle(tone)`**: `DANGER` nasce `SOLID`, os demais `SOFT` — exatamente o default
    da weblib desde a 0.67.0. O app força `SOLID` quando o banner **é** o resultado da tela ("Tudo
    certo!"). `bannerLiveRegion(tone)` traduz o `role="alert"`/`role="status"` da web para
    `LiveRegionMode.Assertive`/`Polite`.
  - **Contraste do texto sobre fundo preenchido:** usa o par oficial do `ColorScheme` quando ele
    existe (`error`/`onError`) e, para os tons sem par no Material (`success`/`warning`/`info`),
    **deriva por contraste WCAG** (`ColorContrast.pickOnColor`). É o que impede um âmbar sólido de
    receber texto branco ilegível em qualquer paleta de app — coberto por teste.
  - **Tom = `StatusTone`** (o vocabulário que o kmplib já usa em `StatusBadge`), com a tabela de
    equivalência à weblib documentada no KDoc (`error` ↔ `DANGER`). Dois vocabulários dentro do mesmo
    app seria pior que a diferença de rótulo entre plataformas.
- **`statusToneColor(tone)`** promovido a público: fonte única do mapeamento tom → token, lido por
  `StatusBadge`, `ChecklistItem` e `AppBanner` (antes o `when` vivia privado dentro do `StatusBadge`).
- **`SolidErrorBanner` `@Deprecated`** → `AppBanner(tone = DANGER)`. Continua funcionando (delega),
  **mas o visual foi corrigido**: apesar do nome, ele pintava `errorContainer`/`onErrorContainer` —
  um vermelho **claro**, justamente o que o padrão "erro = banner sólido" proíbe, e o motivo de o
  LocaSys ter mantido uma cópia local *de verdade* sólida. Defaults passaram a `error`/`onError`.
  Efeito visível em quem não passa cores: **Meu Barbeiro** (4 telas) — a correção é a intenção.
- Testes: `ChecklistItemTest` (8), `ProgressCounterTest` (11), `AppBannerTest` (7) = **26**.
- **Migração (dev-mobile):** Meu Barbeiro (4 arquivos, `SolidErrorBanner` → `AppBanner`) e LocaSys
  (21 telas + **deletar** `core/ui/SolidErrorBanner.kt` local, que carregava o próprio pedido de
  promoção `GAP-LS-M-BANNER-01`).

## 2.87.0 — `MonetizationConfig.FreemiumQuota`: o default do ecossistema ganha nome (jul/2026)

Aditivo, retrocompatível. O `CLAUDE.md` declara "**freemium com limite de uso → paywall**" como o
modelo **default** da fábrica, e esse modelo não tinha representação no `MonetizationConfig`: quem o
queria era obrigado a configurar `PremiumOnly`, que dá o comportamento certo (`shouldShowAds =
false`, assinatura ligada) mas **descreve errado** — diz que não existe plano gratuito. Config que
mente é dívida: o próximo a ler assume que o app é pague-para-usar.

- **Novo modo `MonetizationConfig.FreemiumQuota(purchase)`** — tier gratuito real porém limitado por
  quota, paywall de assinatura e **nenhuma publicidade** (house ad dentro da ferramenta de trabalho
  de um profissional pagante é ruído, não receita). Comportamento idêntico a `PremiumOnly`; o que
  muda é a **verdade declarada** (`hasFreeTier = true`).
- **O modo descreve postura, nunca mecanismo.** `FreemiumQuota` **não liga nem conhece** mecanismo
  de quota: o enforcement continua server-side (admin-api / `backlib-quota`) e o cliente só exibe
  "X de Y" (`UsageMeter`) e abre o paywall no 402.
- **Postura saiu do `MonetizationManager` e virou contrato do `MonetizationConfig`:** as três
  perguntas — `showsAds`, `sellsSubscription`, `hasFreeTier` — são `abstract`, mais `purchaseConfig`,
  `modeName` e a regra pura `shouldShowAds(isPremium)`. Antes o manager derivava tudo por `is`-check
  (`_config is PremiumOnly || _config is Freemium`): modo novo esquecido ali devolveria `false` em
  **silêncio**, agora não compila sem responder as três. `MonetizationManager.initialize` ficou
  uniforme (sem `when` por modo) e ganhou `hasFreeTier`; o comportamento observável dos três modos
  antigos é **byte a byte o mesmo**.
- **Por que não foi uma reestruturação em booleanos ortogonais:** as dimensões **não** são
  ortogonais — exibir anúncio pressupõe existir tier gratuito. Três booleanos livres representariam
  oito combinações, várias ilegais (`ads + pague para usar`), o oposto de "estado ilegal não deve ser
  representável". Faltava **uma combinação legal**, não dimensionalidade. Teste cobre a invariante.
- **`RevenueCatEntitlementProvider`** passa a inicializar em `FreemiumQuota` (era `PremiumOnly`) e
  ganhou o parâmetro opcional `monetizationConfig`; `createEntitlementProvider` idem. Mesma postura
  real de quem usa a fachada (ChamadaFacil, CallRecorder, MundoBandeiras — todos pareados com
  `OfflineQuotaGate`). Único efeito observável: `hasFreeTier` deixa de mentir.
- Testes: `MonetizationConfigTest` (10). O `MonetizationManager` é `object` que fala com o SDK do
  RevenueCat na inicialização e não é unit-testável fora de device — por isso a decisão mora no
  config, como regra pura.
- **Espelhado na `casca-mobile`:** `MonetizationMode.FREEMIUM_QUOTA` (novo **default documentado** da
  fábrica), mapeamento extraído para a função pura `monetizationConfigFor(mode, purchase)` e
  `MonetizationModeTest` (6).

## 2.86.0 — Matriz de permissão por módulo (GAP-TS-KM-PERMMATRIX-01) (jul/2026)

Aditivo. Promove à lib o padrão "uma linha por módulo, com seletor Sem acesso / Ver / Ver e editar"
que o Influencer já implementou **duas vezes à mão** (mobile `PermissionsEditor.kt`, web
`PermissionsDialog.tsx`) e que o TattooStudio precisa igual — a 2ª duplicação real.

O motivo forte não foi economizar código, foi **fechar a divergência entre plataformas**: o web
filtrava módulos `NONE` antes de persistir e bloqueava salvar sem nenhum acesso; o mobile não fazia
**nenhum dos dois** (mandava o mapa inteiro e deixava salvar com tudo `NONE`). Por isso a regra saiu
de dentro do componente e virou **função pura testável**.

- **Novo módulo `permissions`** (commonMain puro, sem Compose): `PermissionLevel` (`NONE` < `VIEW` <
  `EDIT`, comparável por ordinal), `PermissionModuleSpec` (chave de fio + rótulo já resolvido pelo
  app — a lib **não** conhece o conjunto de módulos de ninguém), `PermissionFlagSpec` (flag booleana
  extra com dependência declarada de módulo/nível — o `contentsPost` do Influencer deixa de ser
  campo fixo dentro de um componente genérico), `PermissionMatrixState` (imutável, `copy()`),
  `normalized()`, `validate()`, `PermissionMatrixWire` (`{ modules: Map<String,String>, ...flags }`
  com parse tolerante) e `PermissionMatrixJson` (envelope JSON, nada lança).
- **Forward-compat sem perda de permissão:** chave de módulo que **este** cliente não renderiza é
  **preservada** no round-trip (app velho não revoga o que não entende). Já um **nível ilegível** é
  descartado com log — exibir "Sem acesso" e continuar concedendo escondido seria mentir para quem
  administra.
- **`ui/components/ModulePermissionMatrix`** — componente stateless (`state` + `onStateChange`),
  responsivo (`LocalIsCompact`: seletor em linha própria no telefone, ao lado do rótulo no
  tablet/desktop com largura máxima), modo `readOnly` com `StatusBadge` semântico (tela "minhas
  permissões"), bloco opcional de flags e i18n por `PermissionMatrixTexts` (defaults pt-BR iguais
  aos do web).
- **`SegmentedControl` ganhou `enabled` e `optionContentDescriptions`** (aditivos, no fim da
  assinatura). O segundo existe porque numa matriz o leitor de tela anunciava N vezes "Ver" sem
  dizer de que módulo; agora sai "Agenda, Ver e editar".
- Testes: `PermissionMatrixTest` (21).

## 2.85.0 — Senha: só comprimento por padrão, e mensagem que diz o que falta (jul/2026)

**Breaking de comportamento** (decisão do fundador): `PasswordValidator` deixa de exigir composição
por default. Quem dependia da regra antiga passa `PasswordRules.strong()` — a validação continua lá,
só não é mais o default.

- **`PasswordRules` default = só `minLength` ([DEFAULT_MIN_LENGTH] = 6).** `requireUppercase`,
  `requireLowercase`, `requireDigit` e `requireSpecialChar` nascem `false`. Composição obrigatória
  vira opt-in por **`PasswordRules.strong(minLength = 8)`**. Motivo: exigir maiúscula/símbolo cria
  atrito no cadastro sem ganho real (NIST SP 800-63B desaconselha) — e o mínimo passa a bater com o
  do backend (`AuthLocalConfig.minPasswordLength`).
- **`PasswordValidator.errorMessage(password, rules): String?`** — o motivo pronto para o campo
  ("A senha deve ter no mínimo 6 caracteres"), ou `null` se passa. Substitui o "Senha fraca" que os
  apps escreviam à mão e que não dizia a ninguém o que corrigir.
- `isValid` ganhou o parâmetro `rules` (era fixo no default).
- **Rótulo de força** (`getStrength`/`getStrengthLabel`) continua igual — é medidor opcional de UI,
  nunca barreira de cadastro.

### own-auth: o erro vem do servidor
`OwnAuthApi` passa a ler a `message` do envelope de erro do backend e usá-la em 400/409/422, em vez
do texto fixo local. Quem sabe o mínimo exigido é o servidor; o texto da lib virou fallback (e o de
senha deixou de dizer "fraca"). 401 segue com texto local de propósito — a resposta do servidor é
genérica ali para não revelar se o e-mail existe.

## 2.84.0 — Rastro de diagnóstico do login (opt-in, só debug) (jul/2026)

Aditivo e desligado por padrão. Nasceu de um login que falhava no aparelho e passava no `curl`, sem
nada no logcat para comparar.

- **`OwnAuthConfig(diagnostics = false)`** — quando ligado, o `OwnAuthApi` registra no `AppLogger`
  (tag `OwnAuthApi`): a rota chamada (`→ POST …`), o status da resposta (`← 401 …`), e o **e-mail
  exato que o app enviou**, entre delimitadores, com o comprimento e os **pontos de código dos
  caracteres não-ASCII**. É isso que revela o que a tela não mostra: espaço invisível colado pelo
  teclado, acento inserido pelo corretor ou palavra inteira trocada por sugestão.
- **A senha nunca é impressa** — só o comprimento e um aviso se houver espaço nas bordas.
- **Default `false` porque imprime dado pessoal no log do aparelho.** Ligue com
  `diagnostics = BuildInfo.isDebug`; em release fica mudo.

## 2.83.0 — Teclado do iOS não capitaliza nem autocorrige campo de identificador (jul/2026)

Correção de bug com impacto direto em login. Sem breaking: `AppTextField` ganhou dois parâmetros
opcionais (`capitalization`/`autoCorrect`) e o resto da lib passou a usar a fábrica nova.

### O bug
`KeyboardOptions` montada só com `keyboardType` + `imeAction` deixa capitalização e autocorreção
**não especificadas** — e cada plataforma resolve o "não especificado" do seu jeito. No Android o IME
desliga as duas sozinho em `KeyboardType.Email`; no **iOS** o `UITextField` fica com
`.sentences` + `.default`, ou seja **capitaliza a primeira letra e troca a palavra digitada por uma
sugestão** antes do envio. Resultado no Meu Barbeiro: o mesmo usuário entrava no Android e recebia
"e-mail ou senha inválidos" no iPhone — o backend recebia um e-mail que ninguém digitou. Qualquer
campo de e-mail, telefone, documento, código ou senha da lib sofria o mesmo.

### O que mudou
- **`appKeyboardOptions(keyboardType, imeAction, capitalization, autoCorrect)`** (novo,
  `ui/components/AppKeyboardOptions.kt`) — capitalização e autocorreção **derivadas do tipo do
  campo**: só `KeyboardType.Text` escreve como frase (capitaliza + autocorrige); todo o resto entra
  como identificador (`None` + autocorreção desligada). Helpers públicos `defaultCapitalizationFor` /
  `defaultAutoCorrectFor` para quem monta `KeyboardOptions` fora da lib.
- **`AppTextField`** usa a fábrica e aceita `capitalization`/`autoCorrect` explícitos (default `null`
  = derivado). Campo de senha entra como identificador mesmo com `keyboardType` de texto.
- **`AppTextArea`** segue como texto corrido (frase + autocorreção), que é o certo para observação.
- Migrados: `NumberField`, `SearchTopBar` (busca sem autocorreção — trocar "Hygor" por "Higor" no meio
  da digitação some com o resultado), `FeedbackScreen`, `ContactScreen`, `AppReviewDialog`.

## 2.82.0 — Fallback do paywall pela loja + alerta de pagamento no Discord (jul/2026)

Sem breaking para **consumidores**; breaking de **fonte** só para quem implementa `CrashReporter`
à mão (ver `BREAKING_CHANGES.md`). Nasceu do incidente de 26/07 (Super 8 docs/16 §A-24): o paywall
abriu sem plano nenhum e **sem erro em lugar nenhum** porque a identidade Firebase quebrou, e a
lista do paywall é a interseção `oferta central × Packages da loja`.

### Fallback do paywall (`ui/screens/paywall`)
- **`List<PurchasePackage>.toPaywallPlansFromStore(recommendedDurationMonths = null, planName =
  ::defaultPlanName, durationLabel = ::defaultDurationLabel, highlights = { emptyList() })`** — monta a
  vitrine **só com os Packages da loja** quando a oferta central não pôde ser lida. Não inventa oferta:
  os Packages saem do mesmo `monetizacao.yaml` que alimenta o catálogo central. Mais restritivo que o
  caminho normal, porque falta a confirmação de "o que está ativo": **só duração canônica (1/6/12)** —
  `$rc_three_month` residual e `lifetime` são **omitidos** (no caminho normal aparecem por último) —,
  **preço obrigatório e > 0**, ordem/selo pela fonte única `withDerivedHighlight`.
- **`defaultPlanName(durationMonths)`** — nome canônico pt-BR (**Mensal/Semestral/Anual**, sem
  "Premium" nem trimestral) para o card no fallback, onde não existe `Plan.nome` do catálogo.

### Oferta central: leitura com resultado explícito + TTL (`monetization/entitlement`)
- **`sealed interface PlansResult { Available(plans, fromCache) | Unavailable(message) }`** e
  **`EntitlementController.plansResult(forceReload = false)`**. `plans()` continua existindo e
  devolvendo `List<Plan>`, mas `emptyList()` é **ambíguo** ("falhou" × "nenhum plano ativo") e a
  ambiguidade custa dinheiro: era o mesmo paywall morto nos dois casos.
- **`EntitlementController(repository, plansCacheTtlMillis = DEFAULT_PLANS_CACHE_TTL_MILLIS /* 60s */)`** —
  o cache de planos **não expirava** (era eterno dentro da sessão; só `forceReload` derrubava), então
  ligar/desligar plano no admin central só aparecia com swipe-refresh ou matando o app. Agora tem TTL
  igual ao do `AdminApiEntitlementRepository`, `invalidatePlansCache()` e degradação segura (erro com
  cache válido serve o cache, em vez de zerar a tela).

### Alerta de pagamento (`monetization/alert`) — NOVO
- **`enum PaymentAlertKind`** (7 tipos: `OfertaCentralIndisponivel`, `PaywallSemPlano` (Fatal),
  `LojaIndisponivel`, `CompraFalhou`, `RestauracaoFalhou`, `EntitlementIndisponivel`,
  `IdentidadeAusente`) — cada um com **título FIXO** (o GlitchTip agrupa issue por título; contador no
  título viraria issue nova e enxurrada no Discord) e nível proporcional ao dano comercial.
- **`class PaymentAlertReporter(reporter, projeto, umaVezPorSessao = true)`** — `report(kind, detalhe,
  nivel, tagsExtra): Boolean`. Caminho: **app → `CrashReporter` → GlitchTip → alerta com destinatário
  Discord**. O app **nunca** fala com o Discord direto (webhook no binário é segredo público). Tags
  `area=pagamento`, `projeto`, `tipo`, `detalhe`. Anti-spam: 1× por tipo por sessão. **LGPD:** `detalhe`
  é técnico (contador/flag/código), jamais `uid`/e-mail/CPF/id de transação.

### Observabilidade (`observability`)
- **`CrashReporter.isActive`** — o app agora pode **falhar alto**: DSN ausente fazia `init` virar no-op
  silencioso, indistinguível de "nenhum erro aconteceu" (os 6 projetos mobile do GlitchTip tinham zero
  eventos e não havia como saber, de fora, se era isso).
- **`captureMessage(message, level, tags = emptyMap())`** — ganhou `tags` (o `captureException` já tinha);
  é o que dá roteamento/filtro ao alerta no painel e campos no embed do Discord.

## 2.78.1 — fix: FileProvider paths cobrem photos/ e videos/ (jul/2026)

Sem breaking. Correção de bug introduzido na 2.78.0.

### Fix
- `kmplib_file_paths.xml`: o FileProvider da lib expunha **apenas** `shared_files/`, mas os
  componentes `ImagePicker` (câmera → `cacheDir/photos`) e `VideoPicker` (`cacheDir/videos`)
  gravam em subpastas não declaradas. Em qualquer app consumidor que use a câmera/vídeo,
  `FileProvider.getUriForFile` lançava `IllegalArgumentException` e a ação falhava silenciosamente.
  Agora o `kmplib_file_paths.xml` cobre os três caminhos usados pela própria lib:
  `photos/`, `videos/` e `shared_files/`.

## 2.78.0 — Onda de manutenção da auditoria (jul/2026)

Sem breaking. Higiene, docs e três evoluções aditivas + quitação de dívida iOS (pendente de
validação em macOS).

### Higiene (P1-11)
- `git rm --cached` dos arquivos-lixo que estavam rastreados apesar do `.gitignore`: `teste.txt`,
  `test-output.log`, `cities_generated.txt`, `municipios.json`, `generate_cities.ps1`,
  `generate_kotlin.ps1` e as **configs Firebase órfãs** `docs/firebase/google-services.json` +
  `docs/firebase/GoogleService-Info.plist` (mantidos em disco, fora do versionamento).

### Docs (P1-1, P2-16, P2-1)
- `README.md`: coordenada `2.2.0` → `2.78.0`; nota apontando o catálogo como fonte viva + resumo
  das mudanças estruturais (sem Firestore, crashes→GlitchTip, sem AdMob).
- `BREAKING_CHANGES.md`: cabeçalho + tabela-resumo dos breaking 2.x (antes parava em 2.0.0).
- Planejamento morto da raiz arquivado em `docs/legacy/` (`ANALISE_CENTRALIZACAO.md`,
  `KMPLIB_REUSE_ANALYSIS.md`, `AJUSTES_REALIZADOS.md`, `TEST_SCENARIOS.md`,
  `UI_COMPONENTS_EXAMPLES.md`, `CHANGELOG_UI_COMPONENTS.md`, `FEEDBACK_USER_SYNC.md`).
- KDoc do mapa iOS: CocoaPods → **SPM `googlemaps/ios-maps-sdk`** (`IosMapBridge.kt`, `MapView.ios.kt`);
  `IOS_INTEGRATION.md` passou a listar o pacote SPM do Google Maps.
- **ADRs criados** (`docs/adr/`): ADR-001 (gate de cota offline) e ADR-0003 (render de PDF iOS
  nativo) — antes citados no código sem existirem como documento.

### Novo — `ui/components/OnboardingPager` (P1-5)
- Carrossel de introdução **config-driven** (`HorizontalPager` + indicadores + Pular/Próximo/Começar),
  tema via `AppTheme`, responsivo (`LocalIsCompact`). Elimina a maior duplicação real do portfólio
  (17 apps reimplementavam à mão). Lógica pura testável (`onboardingIsLastPage`/`...PrimaryLabel`/
  `...ShowSkip`/`...NextIndex`/`...PreviousIndex`). Testes `OnboardingPagerTest` (8).

### Novo — `observability/CrashReporter.initFromBuildConfig` (P2-2)
- Helper aditivo que elimina o boilerplate `expect/actual` de DSN/versão/debug repetido em ~37 apps:
  `initFromBuildConfig(dsn, appSlug, versionName, versionCode)` deriva `environment`/`release`
  canônicos + gate de DSN vazio, lendo `isDebug` do `BuildInfo` já existente. Derivação pura
  `crashReporterConfigFromBuildConfig(...)` + `crashReporterRelease(...)` + `CrashEnvironment`.
  Testes `CrashReporterBuildConfigTest` (6).

### Teste — `firebase/auth` (P2-11)
- `FakeAuthRepository` reutilizável (`commonTest`) + `AuthTest` (7): contrato do `IAuthRepository`,
  helpers de provider do `User`, transições do `AuthStateManager`. Fecha a lacuna "firebase/auth sem teste".

### Dívida iOS quitada em código (P1-9, P1-10) — PENDENTE DE VALIDAÇÃO EM macOS
- **PDF iOS multi-página:** renderer `renderIosPdfPaged` + `IosPageFlow` (marca d'água por página) e
  primitivas novas no `IosPdfCanvas` (`strokeRect`/`strokeRoundRect`/`fillCircle`/`imageCrop`/
  `measureWrappedHeight`). Os **7 geradores** que eram stub agora são reais espelhando o par Android:
  `Document`, `TableReport`, `FinanceReport`, `HoursReport`, `VaccinationCard`, `WorkReport`,
  `Inspection` (com os 2 de recibo, os 9 geradores estão implementados em código).
- **Câmera/OCR iOS:** `PlateOcrAnalyzer.ios` real via **Apple Vision** (`VNRecognizeTextRequest`) e
  `CameraView.ios` real via **AVFoundation** (`AVCaptureSession` + `AVCaptureVideoDataOutput` +
  preview) + Vision + JPEG do frame reconhecido.
- **`PlatformCapabilities.pdfGeneration`/`cameraCapture` permanecem `false`**: o build Kotlin/Native
  iOS não roda em Linux — o flip para `true` é o passo final após **compilar + validar em macOS**.
