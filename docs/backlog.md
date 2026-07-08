# Backlog de evolução — kmplib

> Dono: lib-mobile. Itens para fazer a kmplib crescer. Priorizar o que serve a ≥2 apps.
> Processo: skill `lib-evolution`. Detecção em massa: comando `/lib-audit`.

### 2.69.0 — ConnectivityObserver idempotente + guarda de host + capacidades vendáveis (08/jul)
> Origem: uso real da 2.68.0 nos 5 apps que a consumiram no mesmo dia.

- [x] **[BUG] `ConnectivityObserver.start()`/`stop()` não eram idempotentes.** Achado pelo QuemMeDeve ao
      cablar o `ConnectivityGate` sobre o observer que o `RestCrudSyncEngine` já iniciara: o 2º `start()`
      **registrava um segundo `NetworkCallback`** (vazamento; no limite, `TooManyRequestsException` do
      Android) e o `onDispose` do gate chamava `stop()`, **matando o observer do auto-sync**. A API convidava
      ao erro — o app só escapava usando o overload sem observer (que cria o seu próprio, duplicando o
      monitor). **Desenho novo:** contagem de referência (`ActivationRefCounter`, commonMain puro) —
      `start()` liga o monitor nativo só na transição 0→1, `stop()` desliga só na 1→0, `stop()`
      desemparelhado é no-op. Política em commonMain; plataforma isolada no `internal expect class
      PlatformConnectivityMonitor` (Android `NetworkCallback`, iOS `NWPathMonitor`), cada `actual`
      idempotente por construção (defesa em profundidade). `refresh()` agora **preserva** o valor corrente
      quando a plataforma não sabe informar (nunca inventa "offline"); no iOS devolve o último estado
      empurrado pelo `NWPathMonitor` em vez de no-op. API pública inalterada (+ `isObserving`).
      Testes `ConnectivityObserverTest` (7).
- [x] **[BUILD] `publishToMavenLocal` produzia módulo Gradle incoerente no Linux.** Os alvos
      `iosX64/iosArm64/iosSimulatorArm64` eram declarados sempre; fora do macOS o KGP os desabilitava
      (`kotlin.native.ignoreDisabledTargets=true`), mas o `kmplib-<v>.module` ainda anunciava as variantes
      `iosArm64ApiElements-published` etc. com `available-at` para artefatos `kmplib-iosarm64`/`-iosx64`/
      `-iossimulatorarm64` **que nunca eram publicados** — o fallback `mavenLocal` de um clone isolado
      quebrava (reportado pelo MinhasVacinas). **Fix (padrão-ouro):** guarda de host com
      `HostManager.hostIsMac` (a mesma checagem do KGP) — alvos Apple **condicionais ao host**, nunca
      desativados; escape hatch `-Pkmplib.forceAppleTargets=true`. Fora do macOS o publish sai com
      `commonMain` + Android, módulo coerente (5 variantes, zero `available-at` órfão).
      - ⚠️ **Consequência conhecida:** com **um único alvo** o KGP não gera klib de metadata → o jar raiz
        publicado no Linux sai vazio (só `kotlin-project-structure-metadata.json`). Sem impacto prático
        (num host Linux o app consumidor também só tem Android habilitado, e resolve `kmplib-android`),
        mas **release oficial tem que sair de um Mac**: adicionada guarda que **falha** qualquer tarefa
        `*MavenCentral*` em host não-macOS.
- [x] **`platform/PlatformCapability` + `CapabilityFeature` + `ui/components/CapabilityGate`.** O
      `PlatformCapabilities` (2.68.0) já declarava a dívida iOS, mas o app ainda podia **vender** o que não
      tem — ChamadaFacil anuncia "Exportar PDF" como destaque do plano **Pro** e no iOS o gerador lança.
      Agora o destaque/menu se atrela à capacidade (`"Exportar PDF" requiring PdfGeneration`) e
      `availableValues()` o remove no alvo onde a feature não existe. Testes `PlatformCapabilityTest` (6).
- [x] **[BUG] Selo de plano do paywall era roubável — `toPaywallPlans`.** Auditoria disparada pela
      lib-web (weblib 0.58.0 achou 5 falhas na mesma família). Confirmadas no mobile, com nuances:
      1. **duração desconhecida** não virava `Int.MAX_VALUE` (a lib nunca teve `planDurationMonths`), mas
         era pior: o plano **sumia do paywall** (`durationMonths == null ⇒ omitido`) — o usuário não
         conseguia comprar. Agora: `null` ⇒ visível/assinável, **último**, **inelegível ao selo**.
      2. **Lógica duplicada** nos dois overloads de `toPaywallPlans` (e hardcoded em MinhaOS
         `features/paywall/PaywallScreen.kt:99`, `highlighted=false/true` literais). Extraída para a fonte
         única **`withDerivedHighlight(plans, forcedPlanId?)`**, pública — o app monta os `PaywallPlan` e
         a lib deriva ordem+selo. O `isRecommended` de entrada é **descartado**.
      3. **Rebaixar intervalo não-canônico para mensal** (mentira de preço "R$ 53,90/mês"): não existia,
         porque a lib ignora `Plan.intervalo` e usa `durationMonths`. Documentado para não regredir; o
         `durationLabel` de um trimestral diz "3 meses", nunca "mensal".
      4. **Selo indo para o Grátis** em empate de duração: `PaywallPlan.isFree` (novo) + `Plan.isPaidPlan`
         (free **ou preço zero**) ⇒ o grátis nunca é elegível. `preco` nulo/branco **não** é grátis (no
         gold-standard o preço vem da loja).
      5. **`lifetime` com `durationMonths = 1200` vencendo o anual**: real (dado cru do admin-api). Agora a
         **loja manda na duração** (`PurchasePackage.durationMonths`; `LIFETIME ⇒ null`) e do catálogo só
         se aceita duração **canônica**. Novo `enum PlanInterval { Monthly(1), SemiAnnual(6), Yearly(12) }`
         (`monetization/entitlement`) = whitelist da constituição; `isHighlightEligible` = ativo + pago +
         canônico. **Nenhum elegível ⇒ nenhum selo.** `forcedPlanId` só é honrado se elegível (senão cai no
         default — hardcode obsoleto não deixa o paywall sem selo). Correlação Plan×Package ganhou fallback
         por `storeProductId`, para o não-canônico ainda achar seu preço e aparecer.
      `PaywallPlan` ganhou `durationMonths: Int?` e `isFree: Boolean` (aditivos, com default).
      Testes `PaywallPlanMapperTest` (36 — as 4 combinações válidas, trimestral, lifetime residual,
      grátis empatado, preço branco, forçado inelegível, ordem estável dos não-canônicos).
      **Migrar:** MinhaOS (`PaywallScreen.kt:99` → `withDerivedHighlight`); Super 8 / LocAki podem largar
      o `recommendedStoreProductId` (o default já dá o selo à maior duração).
- [x] **Dívida iOS auditada e honesta:** os **9** geradores de PDF `iosMain/.../pdf/*.ios.kt` lançam
      `OsPdfNotSupportedException`/`ReciboPdfNotSupportedException` (verificado); `PdfRasterizer.ios` é real;
      as 2 sobrecargas de `CameraView.ios` desenham placeholder e **nunca** chamam o callback (não lançam,
      não silenciam — a UI diz que só existe em iOS nativo). Catálogo já marca ambos como stub.
      Permissão de câmera no iOS é real (`PermissionManager`), não stub.

**Aberto (precisa de host macOS):** implementar os 9 geradores de PDF iOS com **CoreText**
(`CTFramesetter`/`CTLine`) dentro de `UIGraphicsPDFRenderer`; implementar `CameraView.ios`
(`AVCaptureSession` + `AVCapturePhotoOutput` + Apple Vision). Ao pagar cada dívida, virar o flag
correspondente em `PlatformCapabilities.ios.kt` — nenhum app precisa mudar.

### 2.68.0 — Baseline de resiliência/UX + gate de cota offline + fronteira de tempo (08/jul)
> Origem: auditoria dos 28 apps do Onboarding. Todos os itens abaixo estavam duplicados em ≥2 apps.

- [x] **`ui/components/ErrorState` (rolável, com retry).** A lib só tinha `ErrorModal` (bloqueante, p/
      falha de **ação**) e `EmptyState` (sucesso com zero itens) — resultado: **falha de rede virava lista
      vazia silenciosa** em ~8 apps da onda (MinhaAgenda, Meu Plantão, MinhasVacinas, MinhaOS, QuemMeDeve,
      ChecklistVeicular, Esquecido, AmigoSecreto). Promovido de MinhaFrota (`ListStateComponents.kt`) +
      MeuFrete (`core/ui/ErrorState.kt`). **Rolável** (`ScrollableFillBox` — o gesto de pull-to-refresh só
      chega ao `PullToRefreshBox` se o filho rolar; e o conteúdo continua alcançável com teclado aberto).
      Também `OfflineErrorState` (atalho p/ falha de rede). Tokens do tema, `AppButton` reusado,
      `ErrorStateTexts` i18n.
- [x] **`ui/components/SearchTopBar` + `FilterIconButton`.** Padrão inegociável ([[search-topbar-android]],
      [[filters-topbar-icon]]): busca = **lupa no top bar** que revela o campo; filtros = **funil com badge**.
      Ninguém tinha — todos improvisavam `AppTextField` inline abaixo da barra (MinhaAgenda
      `ClientesListaContent.kt:73`, Exiba `OutdoorListContent.kt:64`, NúmerosDaSorte, MinhaDespensa
      `PantryScreen.kt:94`). `SearchTopBarState` hoisted (fechar limpa a query), foco automático,
      `filterBadgeLabel` cap "9+". Testes `SearchTopBarStateTest` (6).
- [x] **`ui/components/RefreshableBox` + `SyncRefreshBox` (pull-to-refresh).** Zero apps tinham wrapper;
      alguns usavam `PullToRefreshBox` cru. `RefreshableBox` = wrapper fino sobre o componente **oficial**
      do Material 3 (padrão-ouro; a lib não reimplementa gesto/indicador). `SyncRefreshBox` é a variante
      **offline-first**: o gesto dispara `RestCrudSyncEngine.syncNow()` (drena a outbox → depois reconcilia),
      não um mero refetch; sem rede, encerra o indicador na hora e chama `onOffline()` (a outbox fica
      intacta e o engine sincroniza sozinho ao reconectar). Decisão pura `resolveRefreshAction` testada
      (`RefreshableBoxTest`, 4).
- [x] **`monetization/quota/OfflineQuotaGate` + `DailyQuotaStore` + `QuotaRules` + `PremiumSource`.**
      O MESMO gate estava em 4 apps offline (Esquecido, ChamadaFacil, MundoBandeiras, NúmerosDaSorte
      `CotaDiariaLocal`). Promovido o denominador comum, cobrindo os **dois formatos** de limite:
      consumível diário (`tryConsume` + espelho dia-aware) e estrutural/lifetime (`assertStructural`,
      contagem do domínio). ADR-001 ponto a ponto: premium curto-circuita · servidor é a verdade quando
      existe (`assertUsage`; 402 sobrepõe o espelho e abre paywall) · **fail-open LIMITADO** (falha de rede
      libera só até o teto Free, depois bloqueia — nunca libera infinito, nunca autopromove) · `reconcile()`
      ao reconectar · app sem backend usa o espelho como gate.
      **Bug real corrigido de saída** (MundoBandeiras `core/di/AppModule.kt:90`): o gate lia `state.value`
      (snapshot do `StateFlow`) **antes** do 1º `refresh()` do RevenueCat → no cold start um assinante **Pro
      era tratado como Free e tinha a cota consumida**. Agora o sinal premium vem de `PremiumSource`;
      `EntitlementPremiumSource` **aguarda o primeiro refresh** (uma vez por processo, sob mutex) antes de
      ler o flow ([[premium-gate-revenuecat-direct]]). Testes `OfflineQuotaGateTest` (12).
- [x] **`core/time/BoundaryTime` + `EpochMillisSerializer`/`EpochMillisOrNullSerializer`.** O contrato da
      onda fixa **epoch millis (Long)**, mas backends emitem ISO-string e cada app remendou sozinho
      (`isoToMillis` no MinhasHoras, `DomainMappers.isoToEpoch` no MinhaOS, e o **pior**: PapelStudio
      `IaResultViewModel.kt:79` recarimbando com `currentTimeMillis()` — inventa instante). Conversão única:
      lê número/string-numérica/ISO (com e sem offset ⇒ UTC), escreve SEMPRE número. **Nunca inventa
      instante** (ausente/ilegível ⇒ `null`/`0`, jamais "agora"). Documenta e **impõe** a fronteira:
      `"2026-07-08"` (data de calendário — aniversário/vencimento) é **rejeitada**; use `LocalDate` com o
      serializer oficial do kotlinx-datetime. Testes `BoundaryTimeTest` (10).
- [x] **`platform/PlatformCapabilities` (dívidas iOS declaradas, não silenciosas).** `cameraCapture` e
      `pdfGeneration` = `false` no iOS. Motivo: `CameraView.ios` é placeholder (MeuEstacionamento herdou
      "foto de veículo não funciona no iPhone") e **os 9 geradores de PDF iOS lançam exceção** — o
      ChamadaFacil **vende export PDF como feature Pro** e ela não existe no iOS. Agora o app consulta o
      flag e **esconde/não vende** a feature enquanto a dívida não é paga. Mensagens dos stubs apontam para
      o flag.

#### Dívidas iOS — diagnóstico (NÃO implementável sem host macOS)
- **Correção de drift do catálogo:** a skill afirmava que `DocumentPdfGenerator` e
  `VaccinationCardPdfGenerator` eram "iOS FUNCIONAL". **São stubs.** Verificado: os 9 arquivos
  `pdf/*.ios.kt` lançam `OsPdfNotSupportedException`/`ReciboPdfNotSupportedException`. Apenas
  `PdfRasterizer.ios.kt` (renderPdfPagesToImages) é real. Catálogo corrigido.
- **Caminho gold-standard do PDF iOS:** o motivo registrado ("APIs de desenho de texto do UIKit não
  exportadas no K/N 2.x" — as categorias `NSString.drawAtPoint:withAttributes:`/`sizeWithAttributes:`)
  é real, mas **existe caminho oficial**: desenhar com **CoreText** (`CTFramesetterCreateWithAttributedString`,
  `CTFrameDraw`/`CTLineDraw`), que É exportado no Kotlin/Native, dentro de `UIGraphicsPDFRenderer` /
  `CGContext`. Layout lógico já está compartilhado (`pdf/ReciboPdfLayout.kt`, baselines em pt).
  **Não implementado aqui** porque Kotlin/Native iOS **não compila em Linux** — escrever ~9 renderers
  sem conseguir compilar nem ver o PDF seria código cego (pior que a dívida declarada). Requer host macOS.
- **`CameraView`/`rememberCameraPermission` iOS:** idem — `AVCaptureSession` + `AVCaptureVideoDataOutput`
  (OCR ao vivo via `VNRecognizeTextRequest`/Apple Vision) + `AVCapturePhotoOutput`
  (`fileDataRepresentation()` → JPEG). Núcleo de parsing (`extractPlate`) já é commonMain testado; falta só
  o pipeline nativo. Requer host macOS.

### 2.67.0 — Aviso global de "sem internet" + 2 promoções ≥2 (07/jul)
- [x] **PRIORIDADE FUNDADOR — `ui/components/ConnectivityGate` (aviso "sem internet").** App agora é
      **online-por-padrão** ([[default-online-not-offline-first]]) → todo app precisa avisar quando offline.
      Camada de UI sobre o `ConnectivityObserver` já existente (reusado, não duplicado). `ConnectivityGate`
      observa a conectividade e mostra/esconde **automaticamente** o aviso (some sozinho quando volta a
      rede). **Plugue de 1 linha** no root (dentro do `AppTheme`): `ConnectivityGate { AppNavHost() }`.
      Dois estilos: **`Modal`** (default, bloqueante — `NoInternetModal` com "Tentar novamente") e
      **`Banner`** (não-bloqueante, offline-first). Também `rememberIsOnline(observer): State<Boolean>`
      (estado p/ lógica) e `ConnectivityObserver.refresh()` novo (reavalia a rede na hora — Android
      re-consulta, iOS no-op). Textos i18n (`ConnectivityTexts`, defaults pt-BR); tokens do tema, sem
      hardcode; `AppButton` reusado. **Cablado no root da `casca-mobile` + `MinhaAgenda`** (ambos
      compilam Android). Testes `ConnectivityGateTest` (3). Catálogo atualizado.
- [x] **`monetization/entitlement/EntitlementProvider` (fachada de assinatura offline).** Idêntico em
      ChamadaFacil + CallRecorder (≥2). Promovido parametrizando `PurchaseConfig` (removida a dependência
      do `RevenueCatConfig` de cada app). `interface EntitlementProvider` + `RevenueCatEntitlementProvider`
      + `StubEntitlementProvider` (fail-closed) + `createEntitlementProvider(purchaseConfig?)`. Testes
      `EntitlementProviderTest` (4). **Migrar:** ChamadaFacil + CallRecorder (deletar cópia local, importar
      da lib — passar `if (RevenueCatConfig.temChave) purchaseConfig else null`).
- [x] **`account/AccountDeletionService` (LGPD — wipe + export + delete conta).** Idêntico em ≥3 apps
      (MinhaAgenda/MinhaOS/QuemMeDeve/Meu Plantão/MinhasHoras). Wipe atômico `DELETE /v1/me/data`, export
      `GET /v1/me/export`, delete Firebase por ÚLTIMO; `AccountDeletionResult { Completed,
      DataWipedAccountPending }`; reusa `DomainApiClient` + `IAuthRepository`; paths + textos configuráveis.
      Testes `AccountDeletionServiceTest` (5, MockEngine + fake auth). **Migrar:** os apps citados (deletar
      cópia local; MinhaAgenda usa `ContaRepository`/`me/data` próprio — adotar o serviço da lib).
- **Publicado:** `br.com.codecacto:kmplib:2.67.0` em mavenLocal (Android + metadata; iOS linka no Mac —
  expect/actual Darwin do `ConnectivityObserver.refresh()` corretos, no-op documentado).

### sync.rest — B1: perda de dados no `refresh()` sem paginação (2.66.0)
- [x] **2.66.0 — B1 (BLOQUEANTE, perda de dados) — `OfflineFirstRestRepository.refresh()` paginado.**
      **Achado:** code-review da migração do **QuemMeDeve** (Onda 3). `refresh()` fazia **1 único GET**
      (`GET {collection}`, sem `?page=&size=`) e tratava a resposta como o conjunto AUTORITATIVO:
      `descriptor.decodeList` desembrulhava só o `.data` da **página 1** (size padrão do servidor = 20) e
      `RestEntityMirror.reconcile()` fazia `deleteHard` de toda linha local **limpa** cujo `server_id` não
      viesse na resposta. **Consequência:** domínio com total de registros > size da página (ex.: devedores,
      ilimitados no plano Free) tinha **todo registro além dos 20 primeiros fisicamente apagado do espelho
      a cada `refresh()`** (chamado por list/observe/count); device novo nunca via além da página 1.
      **Correção (padrão-ouro offline-first — o espelho DEVE conter o dataset completo):**
      - `refresh()` agora **pagina o envelope `PageResponse{data,page,size,total}` até esgotar** (itera
        `page` com `?size=100` = teto do contrato, acumula TODOS os itens num mapa por id) e só então chama
        `reconcile()` sobre o **conjunto COMPLETO** — aí o `deleteHard` volta a ser seguro. Parada: página
        vazia/incompleta (`items.size<size`), `total` alcançado, ou nenhum id novo (anti-loop); `MAX_REFRESH_PAGES`.
        Erro em qualquer página **aborta sem reconciliar** (nunca deleta sobre conjunto parcial).
      - Contrato do descriptor estendido **de forma retrocompatível**: novo `fun decodePage(body):
        RestPage<T>` (itens + `page`/`size`/`total`) com **default derivado de `decodeList`** (metadados
        `null`) → descritores existentes (só `decodeList`) **compilam sem mudança** e já paginam pela parada
        por página incompleta; quem expõe o envelope sobrescreve `decodePage` p/ parada precisa por `total`.
        `data class RestPage<T>(items, page?, size?, total?)` com `hasNextPage`. Novo param de ctor
        `refreshPageSize=100` (aditivo).
      - **NÃO** adotado o paliativo "size grande num único GET" (teto 100 não resolve o caso geral e mantém
        o `deleteHard` inseguro) — a paginação real é a correção.
      - **Variante upsert-only** (candidato **C-02** ReciboFácil `refreshFirstPage(putClean)`): novo
        `refreshPage(page,size): DomainResult<RestPage<T>>` recarrega UMA página via `RestEntityMirror.mergeClean`
        (upsert, **sem** reconcile-delete) — para domínios de paginação/busca **server-side pura** (espelho =
        cache parcial). `mergeClean` = metade aditiva da `reconcile`, preserva linhas dirty.
      - **Testes** (`OfflineFirstRestRepositoryTest` 7→12, `:kmplib:testDebugUnitTest --tests "...sync.rest.*"`
        → 27/27 verdes): pagina N>size (25 itens/size 10/3 páginas) preservando TODOS; refresh só apaga o
        item genuinamente removido no servidor (não a página 2+); regressão total≤size (1 página); preserva
        dirty na reconciliação paginada; `refreshPage` upsert-only não apaga outras páginas.
      - **Publicado:** `br.com.codecacto:kmplib:2.66.0` em mavenLocal.
      - **Consumidores a revalidar (14 apps sync.rest da Onda 3):** foco **QuemMeDeve** (motivou o achado —
        re-bumpar p/ 2.66.0 e revalidar list/sync de devedores > 20). ReciboFácil pode adotar `refreshPage`
        (C-02) para as telas com paginação server-side. Demais apps: bump quando tocados (sem mudança de
        código — a correção é interna ao `refresh()` e o contrato do descriptor é retrocompatível).
- [ ] **PENDÊNCIA fora de escopo (não-B1):** `pdf.InspectionPdfDataTest` está VERMELHO no working tree
      (`defaultInspectionPdfFileName` gera `vistoria-.pdf` quando a placa é vazia; esperado `vistoria.pdf`).
      É da feature InspectionPdf (câmera 2.65, ainda não commitada), **não do fix B1** — bloqueia
      `:kmplib:koverVerify`/`test` agregado até ser corrigido pelo dono dessa feature.

### Câmera — captura de foto no OCR de placa (2.65.0)
- [x] **2.65.0 — GAP-ME-04 — `CameraView` com captura de FOTO (JPEG) no reconhecimento (motivado pelo
      MeuEstacionamento RF-15).** A `CameraView` só devolvia a placa (`onPlateCaptured`), então o app
      salvava `fotoBytes = null` e nunca guardava a foto do veículo. Padrão transversal (todo app que faz
      OCR de câmera também quer a foto do que leu). **Sobrecarga ADITIVA/retrocompatível**
      `@Composable CameraView(onCapture: (placa, jpegBytes) -> Unit, modifier)` — entrega placa
      normalizada + **JPEG** do frame (compat `image/jpeg` do Storage) de forma atômica; a antiga
      `onPlateCaptured` continua igual (sem ambiguidade — arity do lambda). **Android padrão-ouro
      CameraX:** `Preview`+`ImageAnalysis`(ML Kit)+`ImageCapture`; ao detectar a placa, `takePicture`
      (in-memory) → `imageProxyToUprightJpeg` (rotação aplicada aos pixels, q=85) → `onCapture`.
      **iOS:** placeholder honesto nas 2 sobrecargas (dívida `AVCaptureSession`+`AVCapturePhotoOutput`+
      Vision em host macOS, junto do `PlateOcrAnalyzer.ios`). Arquivos: `camera/CameraView.kt`,
      `camera/CameraView.android.kt` (refat. `CameraViewImpl`), `camera/JpegCapture.android.kt` (novo),
      `camera/CameraView.ios.kt`. Build Android + common + iOS SimulatorArm64 SUCCESSFUL; publicado
      `br.com.codecacto:kmplib:2.65.0`. Consumidor a migrar: MeuEstacionamento (`onPlateCaptured`→`onCapture`).

### Onboarding de legados — offline-first REST-CRUD (2.63.0 → multipart multi-parte 2.64.0)
- [x] **2.64.0 — GAP-CV-M-MULTIPART-01 — upload multipart de múltiplas partes nomeadas (motivado pela
      migração do ChecklistVeicular).** O endpoint de foto-prova de vistoria exige `full`+`thumb` num ÚNICO
      request multipart de partes nomeadas — o `postMultipart` (parte única, campo `file`) não atendia.
      Aditivo/não-breaking:
      - **`DomainApiClient.postMultipartParts(path, parts: List<MultipartPart>)`** + **`MultipartPart(fieldName,
        fileName, bytes, mimeType)`**. Padrão Ktor oficial (`MultiPartFormDataContent` + `formData{append}` →
        `Content-Disposition: form-data; name=<campo>; filename=<arquivo>` por parte — a forma documentada do
        Ktor, sem atalho). `postMultipart` (parte única) passou a **delegar** a `postMultipartParts` (mantém a
        assinatura antiga). Mesma resiliência do resto do cliente: host-scoped (não vaza Bearer), 401→refresh+
        retry (os bytes ficam em memória → o retry reconstrói o `MultiPartFormDataContent`, sem "stream
        consumido"), 402→Quota, 429/rede → Error.
      - **`RestUploadQueue.enqueueParts(MultipartUploadRequest)`** + **`MultipartUploadRequest(id, fileName,
        path, parts)`** — enfileira o upload multi-parte reusando a mesma fila/UI (`UploadItem`/`UploadStatus`/
        `UploadProgressItem`/`UploadQueueView`), processado por `process()`/`retry()`.
      - **Revisão (padrão-ouro):** o dev NÃO deixou atalho — a montagem multipart já era a documentada do
        Ktor e a delegação preserva compat. Nada a corrigir na implementação de rede.
      - **Testes:** `DomainApiClientTest` (7→9 — parte-única + multi-parte, ambos lendo o corpo real do
        request e conferindo que `name=full` **e** `name=thumb` viajam no mesmo request), `RestUploadQueueTest`
        (2→4 — `enqueueParts` sucesso + falha/retry). Rodado `:kmplib:testDebugUnitTest --tests
        "...sync.rest.*"` → 0 falhas. `compileKotlinMetadata` + `publishToMavenLocal` (2.64.0) OK.
      - **Consumidor:** `Onboarding/ChecklistVeicular/mobile` — re-bumpar para **2.64.0** e trocar o envio de
        foto-prova (2 partes) por `postMultipartParts`/`enqueueParts` (com dev-mobile).

- [ ] **GAP-KL-M-UPLOAD-PERSIST — upload resiliente/persistente (fila sobrevive à morte do processo).**
      **Reportado na revisão do 2.64.0.** Hoje TANTO `firebase/storage/UploadQueue` QUANTO
      `sync/rest/RestUploadQueue` guardam requests+bytes SÓ em memória (`mutableMapOf` + `StateFlow`): se o
      app é morto no meio do upload, a fila é perdida (o usuário precisa reanexar). Decisão desta revisão:
      **NÃO é saneamento direto** desta demanda — (a) o comportamento é consistente com a irmã do Firebase
      (mudar só o REST criaria divergência), e (b) o padrão-ouro de upload resiliente NÃO é serializar bytes
      grandes numa linha do SQLite, e sim **persistir uma referência de arquivo (content URI / caminho de
      cache) + agendar upload em background com a API oficial de cada SO** (WorkManager no Android /
      `URLSession` background no iOS). Isso é uma feature transversal (expect/actual + integração com o ciclo
      de background dos apps), serve ≥2 filas/apps, e deve ser projetada como unidade — por isso backlog, não
      remendo. Escopo do item: fila de upload persistente única (Firebase Storage + REST-CRUD), retomada na
      abertura do app, e opcionalmente upload em background. Prioridade: **média** (nenhum app hoje faz upload
      de lote crítico em background; ChecklistVeicular fecha o fluxo em foreground). Registrar consumidores
      quando ≥1 app exigir retomada pós-kill.

### Onboarding de legados — offline-first REST-CRUD (2.63.0)
- [x] **2.63.0 — GAP-MH-M-SYNC-01 — bloco offline-first REST-CRUD promovido (piloto MinhasHoras da Onda 3;
      maior multiplicador do programa: repete em ~14 apps que migram Firestore→backend REST-CRUD central).**
      A `SyncEngine` `/pull`+`/push` baseada em cursor (módulo `sync`) **não encaixa** num backend REST-CRUD
      comum (`GET/POST/PUT/PATCH/DELETE` por recurso); o piloto tinha que reescrever à mão a camada
      offline-first. Promovido ao pacote **`br.com.codecacto.kmplib.sync.rest`** (coexiste com o engine
      `/pull`+`/push` — não o substitui). Itens:
      - **`DomainApiClient`** (+ `DomainResult`/`DomainTokenProvider`/`DomainApiTexts`) — cliente REST de
        domínio, Ktor core puro + Bearer Firebase, **401→refresh+retry**, **402→Quota (paywall)**, **429→
        Error** rate-limit, offline→`Error(OFFLINE_CODE)`. **Host-scoped** (lição MeuFrete: nunca vaza token
        p/ outro host). Multipart + getBytes p/ anexos.
      - **`RestEntityMirror<T>`** — espelho SQLDelight com semântica REST-CRUD (reconcile por GET, não cursor).
      - **`OfflineFirstRestRepository<T>`** (+ `RestCrudEntity<T>`) — repositório genérico offline-first:
        leitura do cache, escrita otimista online-first, outbox offline, reconcile por GET, **remap de FK**
        (id temporário local → serverId). Usado por composição.
      - **`RestCrudSyncEngine`** (+ `RestCrudSyncParticipant`) — coordenador REST-CRUD: push na ordem de
        dependência (pais→filhos com remap acumulado) + pull; auto-sync por conectividade + `syncNow()`.
      - **`RestUploadQueue`** — fila de upload multipart autenticado (reusa `UploadItem`/`UploadStatus`/UI).
      - **Decisão de design (padrão-ouro):** manter os DTOs de fio no app (o descritor `RestCrudEntity` faz
        o mapeamento domínio↔fio); Ktor core puro sem ContentNegotiation (igual aos demais serviços da lib);
        composição (não herança) nos repos de domínio p/ evitar clash de assinatura com as interfaces do app
        e permitir endpoints custom via `repo.mirror`+`api`. `RestCrudEntity.remapRefs` genérico resolve a FK
        de qualquer relação pai→filho (não acopla a empresa/lançamento).
      - **Testes:** `DomainApiClientTest` (7), `OfflineFirstRestRepositoryTest` (7), `RestCrudSyncEngineTest`
        (2), `RestUploadQueueTest` (2) = 18, todos verdes (MockEngine + `FakeSyncStore`). Suíte da lib: 1055
        testes, 0 falhas. `:kmplib:compileDebugKotlinAndroid` + `publishToMavenLocal` OK.
      - **Consumidor migrado (prova):** `Onboarding/MinhasHoras/mobile` — `EmpresaCrud`/`LancamentoCrud`
        (`RestCrudEntity`) + `HttpEmpresaRepository`/`HttpLancamentoRepository` por composição do
        `OfflineFirstRestRepository` + `HttpAnexoRepository` via `RestEntityMirror`+`postMultipart`; DI usa
        `RestCrudSyncEngine(listOf(empresa, lancamento), connectivity)`. Pontes locais `EntityMirror`/
        `SyncCoordinator`/`core.network.DomainApiClient` removidas (arquivos esvaziados — o fundador remove do
        git). `:composeApp:compileDebugKotlinAndroid` BUILD SUCCESSFUL.
      - **A migrar (mesmo padrão, na adequação de cada app com dev-mobile):** os ~14 apps 🔥 Firestore→central
        da Onda 3 (ChecklistVeicular, Exiba, MeuEstacionamento, Meu Plantão, MinhaAgenda, MinhaDespensa,
        MinhaObra, MinhaOS, MinhasVacinas, PapelStudio, QuemMeDeve, ReciboFacil, Emprestei, Salmos…): trocar a
        camada offline-first artesanal por `OfflineFirstRestRepository`/`RestCrudSyncEngine`/`DomainApiClient`.
        **Integrar ao AppConfig/DI da Casca** p/ app novo do arquétipo B/D já nascer com o bloco.
      - **Fica como ponte local (não é gap de lib):** os DTOs de fio (`*Response`/`Upsert*Request`) e os
        endpoints **custom** de cada app (ex.: `PATCH /arquivada`, `PATCH /status`, `GET /count`) — são
        contrato de domínio; a lib fornece as primitivas (`mirror`+`api`) para implementá-los sem duplicar a
        mecânica de sync.

### Onboarding de legados — gaps transversais do arquétipo A (2.62.0)
- [x] **2.62.0 — GAP-CF-M-05 — `DataLayer` canônico em `core/config` (confirmado em ≥4 apps da Onda 1:
      NumerosDaSorte, ChamadaFacil, Esquecido, DosesDeAlegria).** Cada app redefinia um `sealed interface
      DataLayer` local com variantes divergentes (`LocalOnly`/`None`/`KtorOnly(baseUrl)`/`Hybrid(baseUrl)`/
      `FirestoreOnly`) herdadas do clone da Casca. Promovido à kmplib como **`enum class DataLayer { None,
      LocalOnly, Central, @Deprecated Firestore }`** (`br.com.codecacto.kmplib.core.config`).
      - **Decisão de design (padrão-ouro):** enum (não sealed com `baseUrl`). No ecossistema atual a URL de
        dados NÃO é por-DataLayer — os endpoints centrais (apps-api/admin-api) são constantes padronizadas já
        no `AppConfig`. As variantes `KtorOnly(baseUrl)`/`Hybrid(baseUrl)` eram resíduo de casca e colapsam em
        **`Central`** (dados via backend central: `core/data`/`sync`). `None` = 100% local sem persistência de
        domínio (assets+prefs); `LocalOnly` = domínio no dispositivo (DataStore/SQLDelight). `Firestore` =
        `@Deprecated` (banido jun/2026), mantido só p/ classificar legado não migrado. Helpers `isLocal`/
        `usesCentralData`.
      - **Testes:** `DataLayerTest` (4 — isLocal/usesCentralData por valor + entries esperadas). `:kmplib:
        compileDebugKotlinAndroid` BUILD SUCCESSFUL; `testDebugUnitTest --tests *DataLayerTest*` verde.
      - **Consumidor migrado (prova):** NumerosDaSorte — removido o `sealed interface DataLayer` local do
        `AppConfig.kt`, importado o enum da kmplib; `val dataLayer = DataLayer.LocalOnly` inalterado.
        `:composeApp:compileDebugKotlinAndroid` BUILD SUCCESSFUL.
      - **A migrar (mesmo enum local → kmplib `core/config/DataLayer`):** ChamadaFacil, Esquecido,
        DosesDeAlegria (e demais apps do arquétipo A no onboarding). Atenção: DosesDeAlegria usa `None`;
        os que têm `KtorOnly(baseUrl)`/`Hybrid`/`FirestoreOnly` locais mapeiam p/ `Central` (ou `LocalOnly`).
        Fazer com dev-mobile na adequação de cada app. **Integrar ao AppConfig da Casca** (mobile) para novo
        app já nascer apontando ao enum da lib.
- [x] **2.62.0 — GAP-DA-M-08 (decisão: NÃO promover — a lib JÁ tem).** DosesDeAlegria reimplementou um
      `NotificacaoScheduler` local (expect/actual + BootReceiver) para lembrete diário recorrente, com
      comentário desatualizado ("a kmplib agenda apenas eventos únicos"). **Falso:** `platform/
      NotificationScheduler.scheduleDailyNotification(id, title, body, hour, minute, data, channelId,
      isCritical)` existe **desde a 2.17.0** (Android `AlarmManager` + reagendamento no receiver; iOS
      `UNCalendarNotificationTrigger repeats=true`). **Padrão = consumir `getNotificationScheduler()
      .scheduleDailyNotification(...)` da lib**, não reimplementar. Migração dos apps (DosesDeAlegria e
      afins) é feita na adequação de cada um, com dev-mobile — a lib não muda. Nenhum item novo de lib.

### Onboarding de legados — gaps transversais do arquétipo A (2.61.0)
- [x] **2.61.0 — GAP-NS-M-03 + GAP-NS-M-04 (piloto NumerosDaSorte, confirmados ≥2 apps offline do
      arquétipo A).** Padrão-ouro: engines oficiais (OkHttp/Darwin) + plugin oficial `Logging` do Ktor.
      - **GAP-NS-M-03 — `core/network/createHttpClient`**: factory multiplataforma de `HttpClient` Ktor
        (`expect/actual createPlatformHttpClientEngine()` — OkHttp no Android, Darwin no iOS). `HttpClientOptions`
        (timeouts 30/15/30s, `enableLogging`, `logLevel`, `installJsonContentNegotiation`, `json`) +
        `enum HttpLogLevel` + `val DefaultHttpClientJson`. ContentNegotiation OPT-IN (default OFF — serviços
        centrais usam Ktor core puro); logging opt-in delegando ao `AppLogger`. `expectSuccess=false`.
      - **GAP-NS-M-04 — `core/central/CentralServices.initialize`**: amarração única, idempotente e
        best-effort dos 3 toques públicos do apps-api (Feedback/Contact/Developer). `CentralServicesConfig`
        (projectSlug, httpClient, appVersion, appsApiBaseUrl, contactSource, userId/userEmail, gates por
        serviço) + `updateUser`.
      - **Deps novas:** `ktor-client-okhttp` (androidMain), `ktor-client-darwin` (iosMain),
        `ktor-client-logging` + `ktor-client-content-negotiation` + `ktor-serialization-kotlinx-json`
        (commonMain — negociação era só commonTest antes).
      - **Testes:** `HttpClientFactoryTest` (3) + `CentralServicesTest` (4). `:kmplib:compileDebugKotlinAndroid`
        BUILD SUCCESSFUL; `testDebugUnitTest` (novos) verde.
      - **Consumidor migrado (prova):** NumerosDaSorte — removidos `HttpClientFactory.{kt,android,ios}` e
        `NetworkBootstrap.kt` locais; `AppModule` usa `kmplib…createHttpClient`, `App.kt` usa
        `CentralServices.initialize`. App compila (`:composeApp:compileDebugKotlinAndroid` BUILD SUCCESSFUL).
      - **A migrar (mesma amarração local → CentralServices/createHttpClient):** demais apps do arquétipo A
        conforme forem onboardados (Super 8 e próximos legados offline). Fazer com dev-mobile no onboarding.

### Monetização — migração para Offerings/Packages do RevenueCat (2.56.0)
- [x] **2.56.0 — assinatura via Offerings/Packages (gold-standard RevenueCat), substitui compra por ID
      cru.** `PurchaseConfig.offeringId` (default `"default"`); `PurchaseRepository.getOfferings()` +
      `purchasePackage(packageId)`; DTO `PurchasePackage` + `enum PurchasePackageType`; `getProducts()`/
      `purchase(productId)` `@Deprecated` (mantidos p/ consumíveis); `SubscriptionPeriod` sem `QUARTERLY`.
      `PaywallPlanMapper` ganhou overload `toPaywallPlans(packages: List<PurchasePackage>, ...)` (correlação
      por `durationMonths`); overload legado por `storeProductId` `@Deprecated`. Fix no wire `PlanDto` do
      `AdminApiEntitlementRepository`: decodifica `tipo`/`durationMonths`. Android compila; testes verdes
      (novos: mapper por packages + decode tipo/durationMonths). **Consumidores a migrar (Super 8, LocAki,
      Influencer): próxima etapa, com dev-mobile.**
- [x] **2.57.0 — RESOLVIDO: reconciliação do `AdminApiEntitlementRepository` ao contrato ATUAL do
      admin-api (drift que quebrava leitura de plano/uso/entitlement via central em Super 8/LocAki/
      Influencer).** A kmplib agora bate nas rotas apps-facing `GET /v1/projects/{slug}/me/{entitlement,
      usage/{feature},plans}` (Firebase ID token Bearer; `tenant` derivado do `firebaseUid` no servidor —
      NUNCA no path/body/query), desserializa **DTO puro SEM envelope** com campos **pt**
      (`plano/features/validoAte/fonte/atualizadoEm/ativo`; `contagem/limite/restante/janelaFim`;
      `nome/preco/moeda/intervalo/ativo/tipo/durationMonths/storeProductId`). `feature` virou **segmento
      de path** (era query). Default free (200, `ativo=false`) tratado como não-premium sem erro; regra de
      SEGURANÇA "nunca autopromover" agora ancorada em `ativo` (inativo → Free). **Assinatura do construtor
      inalterada** (`httpClient/baseUrl/projectSlug/authToken/cacheTtlMillis`) → DI dos apps NÃO muda.
      **GAP de backend registrado:** não existe `POST /me/assert` (Firebase-authed); o único `assert`
      (`/v1/{slug}/{tenant}/assert`) exige service token, inviável no device. `assertUsage` NÃO inventa
      rota — degrada seguro para `AssertResult.Failed(501)` (nunca `Allowed`); o gate real de enforcement
      é o **402 na ação de domínio** (`ResponseException.quotaExceededOrNull()` → Paywall) e a UX "X de Y"
      usa `getUsage`. Testes reescritos (Ktor MockEngine): novo contrato de URLs, ausência de envelope,
      campos pt, free-default, tipo/durationMonths, degradação segura do assert. **→ dev-backend:** avaliar
      expor `POST /v1/projects/{slug}/me/assert` (Firebase-authed) se algum app precisar de pré-check de
      cota client-side; hoje não é bloqueante (enforcement server-side na ação cobre).

### Minha Voz (CAA) — TTS + grade acessível de densidade + acessibilidade de tema (origem: ux-designer 2026-07-02, `Minha Voz/docs/design/{flows,wireframes}.md`)
> App de CAA (prancha de comunicação) para quem lê/entende mas não fala (idosos/pós-AVC/afasia).
> **Acessibilidade é requisito de 1ª classe** e o app nasce da Casca (kmplib). 3 gaps de plataforma
> transversais (servem além do Minha Voz) + 1 gap menor. **NENHUM implementado ainda** — abertos para
> o lib-mobile. Ordem de prioridade: **a (TTS) → b (grade de densidade) → c (fonte/contraste) → d (idioma)**.
>
> **Status (2026-07-02 → 2.51.0):** gaps **a (TTS), b (grade de densidade) e c (fonte/contraste)
> ENTREGUES** pelo lib-mobile. Falta apenas o gap **d (GAP-MV-M-LOCALE-01, BAIXA)**, mantido em
> avaliação (regra do backlog: só com ≥2 consumidores).
>
> **Refino visual/acessibilidade (2026-07-02 → 2.52.0):** feedback do fundador sobre o Minha Voz —
> ENTREGUE pelo lib-mobile (ver item abaixo).

- [x] **REFINO-MV-2.52.0 — `CommunicationTile` colorido + alto contraste perceptível + paleta `Teal`**
      — **ENTREGUE na 2.52.0.** Dois problemas reportados pelo fundador:
      (1) tiles `Normal` usavam `surface`/`onSurface` → viravam uma "parede" branca (claro)/preta
      (escuro), sem cor. **Correção:** tons agora **PREENCHIDOS** — `Normal`→`primary`/`onPrimary`
      (sólido colorido, mudança-chave), `Quick`→`secondaryContainer`/`onSecondaryContainer` (tonal
      harmônico), `Alert`→`error`/`onError` (mantido). Extraídos helpers puros testáveis
      `enum TileColorRole`, `communicationTileRoles(tone)` e `communicationTileBorderWidth(highContrast)`.
      (2) alto contraste era imperceptível (tema claro já é quase branco/preto; HC só trocava por
      branco/preto puro). **Correção:** `AppTheme` agora provê **`LocalHighContrast`**
      (`ProvidableCompositionLocal<Boolean>`, default false); o `CommunicationTile` lê e, no HC, desenha
      **borda grossa 3.dp** (`outline`, preto no HC) em todos os tons. E o `createHighContrastLightColorScheme`
      ficou **dramático/legítimo (WCAG AAA)**: `primary` = quase-preto `#0A0A0A` (não a marca) + `onPrimary`
      branco → tile `Normal` vai de "teal" para "quase-preto + texto branco + borda preta grossa"; `outline`
      preto; `error` `#8A0000`; Quick = container cinza claro `#E6E6E6` + texto preto. HC dark coerente
      (`primary` branco). **Paleta nova `AppColorPalettes.Teal`** (CAA/Minha Voz): `primary` `#0F766E`
      (teal-700, texto branco AAA-large), `secondary` `#0D9488` (teal-600 p/ Quick), `error` `#DC2626`.
      Testes: `DensityGridTest` ampliado (11 — tom→token + borda HC) + novo `HighContrastColorSchemeTest`
      (7 — primary escuro/outline ink/Quick não some/Teal). `:kmplib:compileDebugKotlinAndroid` +
      `:kmplib:testDebugUnitTest` BUILD SUCCESSFUL; publicado em mavenLocal. commonMain (Android+iOS
      compartilham). **Consumidor a migrar:** app Minha Voz → `AppTheme(colorPalette =
      AppColorPalettes.Teal, darkTheme = false, highContrast = <pref>)`.

- [x] **GAP-MV-M-TTS-01 (ALTA — coração do app) — `TtsController` (síntese de voz nativa, expect/actual)**
      — **ENTREGUE na 2.51.0.** Novo módulo `platform/tts/`. `interface TtsController` (`state:
      StateFlow<TtsState>`, `suspend speak(text, langTag, rate=1f)`, `stop()`, `suspend
      isLanguageAvailable(langTag): TtsVoiceAvailability`, `setRate(rate)`, `release()`), enums
      `TtsState { Idle, Speaking, Error }` / `TtsVoiceAvailability { Available, MissingData,
      NotSupported }`, factory `createTtsController()` + helper Compose `rememberTtsController()`
      (release em `onDispose`). **Android:** `android.speech.tts.TextToSpeech` + `UtteranceProgressListener`
      (state), `isLanguageAvailable` mapeado por `ttsAvailabilityFromAndroidCode` (`LANG_MISSING_DATA`→
      MissingData, `LANG_NOT_SUPPORTED`→NotSupported); `TtsControllerHolder.init` no `KmpLib.init`.
      **iOS:** `AVSpeechSynthesizer` + `AVSpeechSynthesisVoice(language:)` (nil→indisponível), rate
      ancorado no `AVSpeechUtteranceDefaultSpeechRate` e clampado no intervalo do sistema. **Best-effort:
      NADA lança**; voz ausente nunca bloqueia (só reporta). Helpers puros `normalizeTtsLangTag`
      (BCP-47), `clampTtsRate` (0.5f..2f). NÃO abre tela de instalação de voz (host via
      `getUrlLauncher()`). **`speak` sem `rate` (default sentinela `Float.NaN`) usa a velocidade corrente
      de `setRate`** (helper puro `resolveTtsSpeakRate`; `rate` explícito sobrepõe só naquela fala — fix de
      code-review). Testes `platform/tts/TtsControllerTest` (14 — normalização/clamp/mapeamento/rate
      corrente vs explícita).
      `:kmplib:compileDebugKotlinAndroid`+`compileCommonMainKotlinMetadata` BUILD SUCCESSFUL; testes
      10/0/0. **actual iOS completo; klib/framework iOS pendente de host macOS (P-IOS).**
      **Seleção de voz por gênero — ENTREGUE na 2.59.0** (motivada pelo Minha Voz): `enum TtsVoiceGender
      { Female, Male }` + `fun setVoiceGender(gender)` no contrato (default `Female`; guarda estado, aplica
      nas próximas falas — mesmo padrão de `setRate`). **Best-effort:** depende das vozes instaladas; sem
      voz do gênero pedido mantém a voz padrão (não regride volume/amplificação/rate). **Android:** antes de
      sintetizar (amplificado + direto) escolhe `Voice` de `engine.voices` do locale via heurística pura
      `pickVoiceName(names, gender, langTag)`/`ttsVoiceGenderHint(name)` e faz `engine.voice = ...`.
      **iOS:** filtra `AVSpeechSynthesisVoice.speechVoices()` por idioma+`gender` (iOS 13+), senão fallback
      `voiceWithLanguage`. Testes `TtsControllerTest` 26/0/0 (12 novos p/ gênero). `:kmplib:
      compileDebugKotlinAndroid`+`testDebugUnitTest` BUILD SUCCESSFUL.

  <details><summary>Especificação original (entregue na 2.51.0)</summary>

  **GAP-MV-M-TTS-01 (ALTA — coração do app) — `TtsController` (síntese de voz nativa, expect/actual)**
      — o app precisa **falar qualquer frase** em qualquer dos 4 idiomas (pt-BR/pt-PT/en/es), sem gravar
      áudio. Padrão-ouro = API nativa do SO: **Android `TextToSpeech`**, **iOS `AVSpeechSynthesizer`**,
      via `expect/actual` (mesmo modelo dos demais `platform/*` da lib). **Transversal:** serve qualquer
      app do ecossistema que precise ler texto em voz alta (acessibilidade, leitura assistida).
      - **API commonMain sugerida** (`platform/tts/` ou `media/`): `interface TtsController {
        val state: StateFlow<TtsState>; suspend fun speak(text: String, langTag: String, rate: Float =
        1f); fun stop(); suspend fun isLanguageAvailable(langTag: String): TtsVoiceAvailability;
        fun setRate(rate: Float); fun release() }`. `enum TtsState { Idle, Speaking, Error }`.
        `enum TtsVoiceAvailability { Available, MissingData, NotSupported }`. Factory
        `createTtsController()` + helper Compose `rememberTtsController()` (release em `onDispose`).
        `langTag` BCP-47 (`pt-BR`,`pt-PT`,`en-US`,`es-ES`).
      - **Detecção de voz ausente (requisito, R-01):** `isLanguageAvailable` mapeia
        `TextToSpeech.isLanguageAvailable` (Android → `LANG_MISSING_DATA` = `MissingData`) e a
        disponibilidade de `AVSpeechSynthesisVoice(language:)` no iOS. **Best-effort:** nada lança;
        voz ausente NUNCA bloqueia (o Destaque exibe o texto de qualquer forma — fala é complemento).
        Expor também um caminho para abrir a instalação de voz do sistema no Android (Intent
        `ACTION_INSTALL_TTS_DATA`) — pode ser via `getUrlLauncher()`/callback do host, não dentro do controller.
      - **Reúso:** `AppLogger`, `StateFlow`/coroutines; NÃO recriar player (é síntese, não `AudioPlayer`).
      - **Consumo Minha Voz:** Destaque (`speak` ao abrir se "Falar em voz alta" ON, botão Repetir),
        Configurações (velocidade + "Testar voz" + status de voz), troca de idioma (revalida voz).
      - **Testes:** lógica pura de mapeamento de disponibilidade/normalização de `langTag`/clamp de rate
        (commonTest). Android/iOS actuals validados manualmente (fala) — iOS pende host macOS (P-IOS).

  </details>

- [x] **GAP-MV-M-GRID-01 (ALTA) — grade acessível de densidade variável 1/2/3 col + tile pictograma+texto**
      — **ENTREGUE na 2.51.0.** `ui/components/DensityGrid.kt` + `ui/components/CommunicationTile.kt`
      (commonMain puro). `enum GridDensity(val columns: Int) { One(1), Two(2), Three(3) }` +
      `fun <T> DensityGrid(items, density, key: (T)->Any, modifier, contentPadding=16dp,
      header: (@Composable ()->Unit)?, itemContent)` sobre `LazyVerticalGrid(GridCells.Fixed(
      density.columns))` — colunas = escolha do usuário (hoisted), NÃO do breakpoint; `header` ocupa
      todas as colunas (`GridItemSpan(maxLineSpan)`). `enum TileTone { Normal, Quick, Alert }` +
      `CommunicationTile(label, icon: ImageVector, onClick, modifier, tone=Normal, enabled=true)`:
      alvo = tile inteiro (`Modifier.clickable` + `Role.Button`), `contentDescription=label`
      (`clearAndSetSemantics`), **haptic** (`LocalHapticFeedback.performHapticFeedback(LongPress)`),
      pictograma **+** texto sempre visíveis, altura mínima generosa (≥96dp; +passo no compacto via
      `LocalIsCompact`). Tons por token (pares coesos — fix de code-review): `Alert`→`colorScheme.error`+`onError`;
      `Quick`→`secondaryContainer`+`onSecondaryContainer`; `Normal`→`surface`+`onSurface`+borda
      `outline`. **Zero cor hardcoded.** Testes `ui/components/DensityGridTest` (3 —
      `GridDensity`→colunas). Consumo Minha Voz: Home (grade + faixa urgente) e Categoria.

  <details><summary>Especificação original (entregue na 2.51.0)</summary>

  **GAP-MV-M-GRID-01 (ALTA) — grade acessível de densidade variável 1/2/3 col + tile pictograma+texto**
      — a prancha (Home/Categoria) é uma grade de **alvos enormes** (≥64dp, muito maiores no modo 1 col)
      com **pictograma + texto** e densidade **escolhida pelo usuário** (1 = um botão enorme por vez;
      2/3 = grade), **persistida**. Hoje a lib só tem `LazyVerticalGrid` cru + `gridColumns()` por
      breakpoint — **não cobre**: (1) densidade como *escolha explícita do usuário* (não só do
      `LocalIsCompact`), (2) tile acessível de comunicação com alvo garantido + haptic + rótulo p/ leitor
      de tela. **Candidato a preset acessível reutilizável** (qualquer app "board/launcher" acessível).
      - **API sugerida** (`ui/components/`): `enum GridDensity(val columns: Int) { One(1), Two(2),
        Three(3) }`; `fun <T> DensityGrid(items: List<T>, density: GridDensity, key: (T)->String,
        itemContent: @Composable (T)->Unit, modifier, contentPadding, header: @Composable (()->Unit)? )`
        — colunas = `density.columns` (independe do breakpoint; alvo mínimo grande garantido, aumenta no
        1 col). `CommunicationTile(label: String, icon: <pictograma>, onClick, modifier, tone:
        TileTone = Normal, enabled = true)` com `enum TileTone { Normal, Quick, Alert }` — `Alert` usa
        `AppColors.current.error` (faixa urgente); alvo de toque = tile inteiro (`Role.Button`),
        `contentDescription` = label (leitor de tela), **haptic** no toque, pictograma **+** texto
        sempre visíveis. Cores 100% por tokens (`MaterialTheme.colorScheme`/`AppColors.current`), zero
        hardcode. Toggle de densidade fora do componente (top bar/`SegmentedControl`), estado hoisted.
      - **Reúso:** `LazyVerticalGrid`/`LazyColumn` de foundation, `LocalIsCompact` (ajuste fino de padding/
        alvo), `AppColors`; espelha a filosofia data+slot dos componentes de lista existentes
        (`MultiSelectList`/`TimelineList`).
      - **Consumo Minha Voz:** Home (grade + faixa urgente + respostas rápidas), Categoria (subtemas).
      - **Testes:** `gridColumns`/mapeamento de `GridDensity` (pura, commonTest).

  </details>

- [x] **GAP-MV-M-A11Y-01 (MÉDIA) — escala de fonte global + tema de alto contraste no `AppTheme`**
      — **ENTREGUE na 2.51.0** (aditiva, retrocompatível). `AppTheme` ganhou `fontScale: Float = 1f`
      e `highContrast: Boolean = false` (últimos params antes do `content`; defaults = comportamento
      atual, nada quebra). `fontScale` multiplica toda a `AppTypography` via `scaleTypography(
      typography, fontScale)` (helper puro em `AppTypography.kt`, clampado) e é exposto em
      `LocalFontScale`. Novo `enum AppFontScale(val scale) { Small(0.9f), Medium(1f), Large(1.3f),
      ExtraLarge(1.6f) }` (+ `next/previous/fromScale`) + `clampFontScale` (0.8f..2f) em
      `ui/theme/AppFontScale.kt`. `highContrast=true` seleciona `createHighContrastLightColorScheme`/
      `createHighContrastDarkColorScheme` (AAA ≥7:1: superfícies branco/preto puro razão ~21:1,
      bordas fortes, primária da marca preservada como acento) em `AppColorScheme.kt`. Testes
      `ui/theme/AppFontScaleTest` (9 — degraus/next/previous/fromScale, clamp, `scaleTypography`
      multiplica/identidade em 1f/aplica clamp). Consumo Minha Voz: Configurações (fonte P/M/G/GG +
      Alto contraste on/off) → `AppTheme(fontScale=, highContrast=)` no root.

  <details><summary>Especificação original (entregue na 2.51.0)</summary>

  **GAP-MV-M-A11Y-01 (MÉDIA) — escala de fonte global + tema de alto contraste no `AppTheme`**
      — acessibilidade pede **tamanho de fonte ajustável (global)** e **alto contraste** como
      preferências, aplicáveis à árvore inteira. Hoje `AppTheme(darkTheme, colorPalette, fontFamily,
      content)` **não** tem `fontScale` nem paleta de alto contraste (existe só o precedente
      `ReaderFontSize` S/M/L/XL 0.85–1.6× no `ui/reader`, restrito ao leitor). **Padrão da casa**
      (útil além do Minha Voz — qualquer app com público de baixa visão).
      - **API sugerida (aditiva, retrocompatível):** `AppTheme(..., fontScale: Float = 1f,
        highContrast: Boolean = false, content)`. `fontScale` multiplica a `AppTypography`
        (reaproveitar a escala `ReaderFontSize` como base — ex.: `enum AppFontScale(val scale: Float) {
        Small(0.9f), Medium(1f), Large(1.3f), ExtraLarge(1.6f) }`) e/ou provê um `LocalFontScale`.
        `highContrast=true` seleciona um par de `ColorScheme` de **contraste máximo** (WCAG AAA:
        onSurface/surface com razão ≥7:1, bordas fortes) derivado da paleta — não uma cor nova hardcoded.
        Defaults preservam o comportamento atual (nenhum consumidor quebra).
      - **Reúso:** `createAppTypography`, `AppColorPalette`/`create*ColorScheme`, `ReaderFontSize` como
        referência de degraus. Persistência fica no app (prefs).
      - **Consumo Minha Voz:** Configurações (Tamanho da fonte P/M/G/GG + Alto contraste on/off) →
        alimentam `AppTheme(fontScale=, highContrast=)` no root; reflete em todas as telas na hora.
      - **Testes:** clamp/mapeamento de `AppFontScale` → typography (pura).

  </details>

- [ ] **GAP-MV-M-LOCALE-01 (BAIXA/menor) — seletor de idioma mobile com bandeira (paridade do `LocaleSwitcher` web)**
      — a weblib tem `LocaleSwitcher` (bandeira SVG), a kmplib **não** tem equivalente mobile. Minha Voz
      (e futuros apps i18n) precisa de um seletor de idioma com bandeira em Configurações/Menu.
      **Avaliar antes de implementar:** pode ser um componente enxuto (`LanguageSelector(current,
      options: List<AppLocale(tag, label, flag)>, onSelect)` sobre `AppBottomSheet`) **ou** ficar no app
      até ≥2 consumidores (regra do backlog — não implementar especulativamente). i18n em si segue o
      padrão-ouro Compose Resources (não é gap). Registrar como candidato; decisão lib-mobile/CTO.

### Padronização de planos de assinatura — oferta do admin-api → paywall (origem: fundador 2026-06-30 → 2.50.0)
- [x] **`Plan` ganhou `tipo`/`durationMonths` + `PaywallPlanMapper` (oferta padronizada → `PaywallPlan`)** —
      Fase 2 (mobile) da padronização de planos. O backend (Fase 1, já commitada) faz
      `EntitlementController.plans()` devolver a OFERTA do projeto (só planos ativos, ordenada) com campos
      ADITIVOS por plano: `tipo` ("MENSAL"|"SEMESTRAL"|"ANUAL"), `durationMonths` (1|6|12) e
      `storeProductId`. Esta fase consome isso na lib, **sem tocar backend/web**.
      - **`monetization/entitlement/Entitlement.kt` — modelo `Plan`:** campos novos **ADITIVOS, NULLABLE,
        `@Serializable` com defaults** (retrocompatíveis — não quebram consumidores nem payloads antigos):
        `@SerialName("tipo") val tipo: String? = null` e `@SerialName("durationMonths") val durationMonths:
        Int? = null` (`storeProductId` já existia). Regra do ecossistema: **só 3 tipos — MENSAL(1)/
        SEMESTRAL(6)/ANUAL(12), SEM "TRIMESTRAL"** em nenhum lugar do código novo. `durationMonths` é a
        chave de ordenação da oferta.
      - **Novo `ui/screens/paywall/PaywallPlanMapper.kt`** — extensão
        `List<Plan>.toPaywallPlans(priceLabelProvider: (storeProductId)->String?, recommendedStoreProductId:
        String? = null, durationLabel: (Int)->String? = ::defaultDurationLabel): List<PaywallPlan>`.
        Compartilhado porque ≥2 apps (Super 8, LocAki) fazem este mapeamento (skill `lib-evolution`).
        Regras INEGOCIÁVEIS implementadas:
        - **Ordem FIXA Mensal → Semestral → Anual** — `sortedBy(durationMonths)` ASC explícito.
        - **Preço SEMPRE da loja (gold-standard)** — a lib NUNCA calcula preço. `priceLabelProvider`
          resolve o preço já formatado por `storeProductId` (host lê de RevenueCat/StoreKit/Play Billing)
          e vira o `PaywallPlan.priceLabel`. Plano sem preço resolvido (`null`) é **OMITIDO** (nada de "—"
          persistente).
        - **`PaywallPlan.id = storeProductId`** (chave de seleção/compra).
        - **Recomendado = maior `durationMonths` exibido** (default; catálogo NÃO tem flag `isRecommended`).
          App pode forçar via `recommendedStoreProductId`.
        - **Filtra/omite** planos inativos, free, sem `storeProductId` ou sem `durationMonths`.
        - `defaultDurationLabel(1)="1 mes"`, `(6)="6 meses"`, `(12)="1 ano"` (pt-BR, sem "trimestral").
      - **`PaywallScreen`/`PaywallContract` (2.48.0/2.49.0) NÃO mudaram** — o `PaywallPlan` produzido pelo
        mapper já casa com a tela canônica theme-driven (`headerIcon` opcional).
      - **Testes:** `ui/screens/paywall/PaywallPlanMapperTest` (commonTest, 9 casos: ordem ASC, recomendado
        default/forçado, omissão sem-preço/inativo/free/sem-FK/sem-duração, priceLabel da loja +
        highlights/durationLabel propagados, entrada vazia, durationLabel nulo). `:kmplib:testDebugUnitTest
        --tests *PaywallPlanMapperTest*` 9/0/0; `:kmplib:compileCommonMainKotlinMetadata` BUILD SUCCESSFUL.
      - **Build/publish:** bump **2.49.0 → 2.50.0** (aditivo, retrocompatível); `:kmplib:publishToMavenLocal`
        → `br.com.codecacto:kmplib:2.50.0`. **klibs iOS** pendentes de host macOS (P-IOS) — tudo commonMain
        puro, compila em iOS sem mudança.
      - **Migração (dev-mobile, com CTO):** **Super 8** e **LocAki** trocam o mapeamento local
        `Plan → PaywallPlan` por `plans.toPaywallPlans(priceLabelProvider = { id ->
        state.getStorePrice(id) })`; remover ordenação/escolha de recomendado caseiras. Bump kmplib → 2.50.0.
      - **Follow-up (NÃO implementado — backend, fora do escopo desta fase):** se o produto quiser um plano
        recomendado que NÃO seja o de maior duração, adicionar uma flag `isRecommended` (ou `destaque`) por
        plano na tabela `plans` do admin-api + expor em `EntitlementController.plans()`; o mapper já aceita
        `recommendedStoreProductId` para honrar essa escolha sem nova mudança de API mobile. Reportar a
        lib-backend.

### Paywall canônico — repaginação visual / polish (origem: fundador 2026-06-30 → 2.49.0)
- [x] **PaywallScreen repaginada (tela de pagamento PADRÃO do ecossistema)** — ajuste só de **casca
      visual**, sem tocar lógica/contrato de negócio. Resolve o feedback do fundador, mantendo o
      gold-standard: stateless, parametrizável e **100% `MaterialTheme.colorScheme`** (ZERO `Color(...)`/
      gradiente com cor fixa) — adapta à primária de cada app (ex.: teal do Super 8).
      - **Card do plano = fundo `surface` SEMPRE** (recomendado e não): removido o `primaryContainer`
        cheio que deixava o card recomendado "tudo verde". Destaque do recomendado agora vem de
        **acentos**: borda primária 2dp + **elevação 6dp** + badge proeminente. Primária só como acento
        (badge, borda, preço, ícones de check, CTA). CTA: recomendado/selecionado em `AppButton` primária
        cheia (chamada principal); demais em `OutlinedButton` de borda primária.
      - **Badge "Recomendado" proeminente** (`RecommendedBadge`): pill `Surface` cor primária com
        `shadowElevation = 4dp`, ícone `Icons.Filled.Star` + label em **bold** (`onPrimary`).
      - **Header centralizado**: `Column(Modifier.fillMaxWidth(), horizontalAlignment = CenterHorizontally)`
        (antes faltava `fillMaxWidth()`, colava à esquerda).
      - **Ícone do topo premium**: default `Icons.Filled.WorkspacePremium` (coroa) num disco discreto
        `primary.copy(alpha = 0.12f)` (não mais disco `primaryContainer` cheio com `Star`). Checks dos
        highlights também ganharam disco discreto 12%.
      - **Param novo OPCIONAL `headerIcon: ImageVector? = null`** em `PaywallScreen` **e** `PaywallContent`
        (último parâmetro, após `modifier`) — o app pode passar seu próprio ícone/logo no topo; `null`
        usa o default. **Compatível** com o host atual do Super 8 (chama por args nomeados, sem `headerIcon`).
      - **Contrato `PaywallContract.kt` inalterado** (mesmos `PaywallPlan`/`PaywallState`/`PaywallAction`/
        `PaywallTexts`); `NeedHelpSection` preservada. `LegalDisclosureSection` ganhou leve fundo
        `surfaceVariant@40%` (era `surface`) para separar do card de plano.
      - `:kmplib:compileCommonMainKotlinMetadata` BUILD SUCCESSFUL; `:kmplib:publishToMavenLocal` →
        `br.com.codecacto:kmplib:2.49.0`. Tudo commonMain puro (`material-icons-extended` já no classpath
        — `WorkspacePremium`/`Star` confirmados). **klibs iOS** seguem pendentes de host macOS (P-IOS),
        sem mudança de código necessária.
      - **Migração:** NENHUMA obrigatória — Super 8 (único consumidor) continua compilando como está. Para
        usar o ícone próprio, passar `headerIcon = ...` no `PaywallScreen` do host (opcional).

### Paywall canônico rico — convergência da PremiumScreen do Super 8 (origem: tech-lead 2026-06-30 → 2.48.0)
- [x] **Paywall fino → tela canônica RICA, stateless, parametrizável** — **ENTREGUE na 2.48.0**. A
      `PaywallScreen` da kmplib era fina/genérica e **sem consumidor** (confirmado por grep). Reescrita
      como a tela canônica de assinatura, absorvendo a riqueza da `PremiumScreen` local do Super 8
      (preço localizado da loja, disclosure legal de auto-renovação Apple/Google, restore, bloco de
      assinatura ativa, e seção "Precisa de ajuda?"). Padrão de telas de lib (`DeveloperScreen`/
      `FeedbackScreen`/`LoginScreen`): stateless, `*Texts` com defaults pt-BR, tema 100% por tokens
      (`MaterialTheme` — ZERO `Color(...)`/gradiente hardcoded), callbacks; estado fica no app.
      - **Contrato reescrito** (`ui/screens/paywall/PaywallContract.kt`; breaking interno coberto pelo
        bump, sem consumidor antes): `PaywallPlan(id, name, description?, priceLabel, pricePerMonthLabel?,
        durationLabel?, badgeLabel?, highlights, isRecommended)` — **`priceLabel` = preço JÁ FORMATADO da
        loja (gold-standard; a lib NUNCA calcula preço de Double)**. `PaywallState(plans, selectedPlanId?,
        usage: UsageSnapshot?, isPremium, subscription: SubscriptionInfo?, isLoadingPlans, isPurchasing,
        purchasingPlanId?, error?)` — reúsa `UsageSnapshot` (entitlement) e `SubscriptionInfo`
        (monetization.purchase). `PaywallAction`: `SelectPlan(planId)`/`Restore`/`Privacy`/`Terms`/
        `OpenDeveloper`/`ManageSubscription`/`Back`/`DismissError`. `PaywallTexts` i18n completo.
      - **Dois composables** (`PaywallScreen.kt`): `PaywallContent(state, onAction, texts, modifier)`
        (só conteúdo, embutível — ex.: bottom sheet de limite de uso) e `PaywallScreen(state, onAction,
        texts, snackbarHostState? = null, modifier)` (wrapper `Scaffold` + top bar voltar + slot de
        snackbar). Render: header → `UsageMeter` (se usage) → `isPremium`? bloco "assinatura ativa"
        (data dd/MM/yyyy via `formatDateBrFromMillis` + gerenciar) : cards de plano → **disclosure legal
        de auto-renovação** → restaurar → `NeedHelpSection` → card de erro.
      - **Novo componente** `ui/components/NeedHelpSection.kt` (`NeedHelpSection(title, description,
        buttonText, onOpenDeveloper, modifier)`) — card "Precisa de ajuda?" + `OutlinedButton`; serve
        qualquer tela de pagamento/assinatura; usado no rodapé do paywall.
      - **NÃO tocou** `PurchaseManager`/`EntitlementController`/repositórios de compra (só UI/contrato).
      - `:kmplib:compileCommonMainKotlinMetadata` BUILD SUCCESSFUL; `:kmplib:publishToMavenLocal` →
        `br.com.codecacto:kmplib:2.48.0` (`kmplib` metadata + `kmplib-android.aar`). **klibs iOS
        pendentes de host macOS** (P-IOS) — tudo commonMain puro, compila em iOS sem mudança.
      - **Migração (dev-mobile, Super 8):** apontar o consumo para a `PaywallScreen` da kmplib e
        descontinuar a `PremiumScreen` local. O ViewModel mapeia `PremiumPlan` → `PaywallPlan` (preço
        via `state.getStorePrice(plan)` → `priceLabel`), `SubscriptionInfo` → `state.subscription`,
        `isPremium` → `state.isPremium`; ações `SelectPlan`→`onPurchase`, `Restore`→`onRestorePurchases`,
        `Privacy`/`Terms`→abrir links legais, `OpenDeveloper`→`DeveloperScreen`/contato,
        `ManageSubscription`→`onManageSubscription`, `Back`→nav, `DismissError`→`clearError`. Textos via
        `stringResource` nos `PaywallTexts`.

### Item 5 — Camada offline-first / persistência local (origem: decisão do fundador 2026-06-27 → 2.42.0)
- [x] **DB local = SQLDelight (decisão registrada)** — avaliado SQLDelight vs Room KMP. **Escolha:
      SQLDelight 2.0.2** (já era a tecnologia do módulo `sync`; mantida). Justificativa: KMP-native e
      maduro (drivers oficiais 1ª-classe Android `AndroidSqliteDriver` + iOS `NativeSqliteDriver`),
      SQL type-safe verificado em compile-time, `Flow` reativo via `coroutines-extensions` (integra com
      Compose), zero dependência de runtime AndroidX. Room-KMP ainda arrasta o toolchain KSP/AndroidX e
      tem suporte iOS/native mais novo e menos provado no ecossistema Compose MP. **Não reintroduzir
      nada de Firestore** (removido na mesma versão).
- [x] **`sync/LocalRepository<T>` — persistência LOCAL pura (arquétipo A)** — gap real: o
      `SyncableRepository` existente exige `SyncEngine`+outbox (faz sentido só p/ arquétipo B). Apps 100%
      locais (Super 8, Calculadora BTU) não tinham um caminho limpo. `LocalRepository` reusa o MESMO
      SQLDelight (`SyncStore`/`createSyncDatabase`/`synced_entity`) gravando sempre **limpo** (`dirty=0`,
      delete físico) — sem fila de push inútil. API DAO: `observeAll`/`observeById` (Flow), `getAll`/`get`,
      `put`/`putAll`/`delete`/`clear`. Adicionado `SyncStore.getVisible(entity)` (leitura síncrona, reusa
      `selectAllVisible`) — `FakeSyncStore` atualizado. Testes `LocalRepositoryTest` (6 casos).
- [x] **`sync/RestSyncPort` — transporte REST genérico de sync sobre Ktor (arquétipo B)** — o "helper
      opcional de sync com a API central reusando o Ktor client" pedido. Impl de `SyncPort` (`pull`/`push`
      em `POST {base}{pathPrefix}/pull|push` com os DTOs de `SyncWire`), padrão Ktor core puro + kotlinx-json
      (sem ContentNegotiation), Bearer Firebase ID token. **Propaga erro** (lança) — o `DefaultSyncEngine`
      já o envolve em try/catch → `SyncResultSummary.failure` e preserva a outbox p/ retry. Remove o
      boilerplate de cada app reimplementar o transporte. Testes `RestSyncPortTest` (4 casos, MockEngine).
- **Sem bump** (acumula na **2.42.0** não publicada). `:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL;
  `:kmplib:testDebugUnitTest *LocalRepositoryTest*/*RestSyncPortTest*` 10/0/0. **iOS:** nada novo de
  `actual` — `LocalRepository`/`RestSyncPort` são commonMain puro sobre os drivers SQLDelight iOS que já
  existem (`NativeSqliteDriver`); klib iOS pende de host macOS (P-IOS). **Pendência backend:** o
  contrato de fio `/sync/v1/{slug}/pull|push` precisa ser implementado no backlib/apps-api (item p/
  lib-backend) para o arquétipo B fechar ponta-a-ponta; o RestSyncPort já fala o shape esperado.

### Remoção do Firestore morto + auditoria de gaps (origem: decisão do fundador 2026-06-27 → 2.42.0)
- [x] **Firestore removido da kmplib** — decisão DEFINITIVA: SEM Firestore como armazenamento em nenhum
      projeto; Firebase fica só em **Auth** e **Crashlytics**. `firebase/firestore/FirestoreService.kt`
      (`save/get/delete/observeDocument`, `batch`, `runTransaction`) era **código morto** — nenhum
      consumidor real (grep nos 4 apps + na própria lib: Meu Advogado só o citava num comentário; nada no
      Koin; `AppReviewDialog` já usa `FeedbackService`/REST). **Removido:** o arquivo + as deps GitLive
      `firebase-firestore` (commonMain) e `firebase-firestore-android` (api androidMain) do
      `library/build.gradle.kts` + os aliases órfãos no `gradle/libs.versions.toml`. Comentários obsoletos
      corrigidos em `KmpLib.kt` e `core/data/Repository.kt`. CRUD agora é só `core/data` (REST/apps-api).
      `:kmplib:compileDebugKotlinAndroid` **BUILD SUCCESSFUL**. Bump **2.41.0 → 2.42.0** (remoção de API
      pública legada não usada; sem impacto em consumidor). Catálogo `kmplib-catalog` atualizado.
      **Pendências:** publicar (`publishToMavenLocal`/Central — fundador) e, nos apps, remover a dep órfã
      `gitlive.firebase.firestore` dos `build.gradle.kts` de Meu Advogado e LocAki (não usada; opcional,
      com dev-mobile).
- [~] **Auditoria dos gaps i18n + deep-link router** — concluída: NENHUM justifica implementação agora
      (ver detalhe na seção "Prioridade média"). i18n = já coberto pelo Compose Resources oficial (Super 8
      em produção); deep-link router = sem consumidor real. Ambos mantidos como backlog/registro.

### Hardening docs/09 M2 — Paridade iOS dos geradores de PDF (origem: 2026-06-26)
- [x] **GAP-PDF-IOS-PARITY — portar todos os renderers iOS de PDF que eram placeholder para
      `UIGraphicsPDFRenderer` real** — fecha a divergência com "paridade web=Android=iOS" e o padrão-ouro
      (API nativa, nunca "não suportado"). Antes, vários `*.ios.kt` lançavam `OsPdfNotSupportedException`.
      Agora **funcionais** (não placeholder), espelhando o layout lógico do Android e reusando as
      primitivas já comprovadas em produção (`ReciboPdf.ios.kt`/`DocumentPdfGenerator.ios.kt`/
      `VaccinationCardPdfGenerator.ios.kt`: `NSString.drawAtPoint` com conversão baseline→topo via
      `ascender`, `CGContext*`, `UIImage.imageWithData`/`drawInRect`, marca d'água via `CGAffineTransform`):
      - `OsPdfGenerator.ios.kt` — gerador BASE de OS/orçamento/recibo (página única, igual ao Android).
      - `FinanceReportPdfGenerator.ios.kt` — relatório financeiro (2 tabelas + totais, paginação).
      - `TableReportPdfGenerator.ios.kt` — tabela genérica (colunas ponderadas, zebra, paginação, marca d'água).
      - `HoursReportPdfGenerator.ios.kt` — horas extras (tabela + totais com destaque + grade de comprovantes
        com center-crop via `CGContextClipToRect`).
      - `WorkReportPdfGenerator.ios.kt` — obra (barras de progresso, grade de fotos, diário).
      - `PdfRasterizer.ios.kt` — `renderPdfPagesToImages` via `CGPDFDocument` + contexto de imagem UIKit
        (`UIGraphicsBeginImageContextWithOptions`), espelhando o `PdfRenderer` do Android (150 DPI, fundo branco).
      - KDoc do commonMain atualizado (não dizem mais "iOS: placeholder").
      - **Compilação iOS:** NÃO validável neste servidor (Linux → `compileKotlinIosSimulatorArm64` SKIPPED;
        Kotlin/Native Apple só compila em macOS). commonMain compila (`compileCommonMainKotlinMetadata`
        SUCCESSFUL). **P-IOS:** validar build do klib/framework + render visual em host macOS/CI.
        Maior risco a checar primeiro = `PdfRasterizer.ios.kt` (único sem arquivo iOS de referência;
        usa `CGPDF*`/contexto de imagem UIKit). Sem bump de versão (mesma 2.41.0 até validar em macOS).

### Influencer — Paridade web↔mobile: Mapa OSM + PDF estruturado (origem: 2026-06-19)
- [x] **GAP-INF-M-MAP-01 — mapa OSM/Leaflet multiplataforma (Android + iOS, sem chave paga)** —
      **ENTREGUE na 2.40.0**. Novo subpacote `map/osm` (distinto do `map/MapView` Google Maps/GAP-02, que
      exige API key faturável e tem iOS placeholder). **Decisão técnica:** WebView + Leaflet + tiles
      `tile.openstreetmap.org` — espelha exatamente o stack da web do Influencer
      (`web/src/components/map/MapInner.tsx`: `react-leaflet` + OSM), **sem nenhuma chave de API**, e
      **iOS funcional desde já** (`WKWebView`, não placeholder). WebView via expect/actual
      (`OsmMapWebView`): Android `android.webkit.WebView`, iOS `WKWebView` (Compose `UIKitView`).
      - **API pública (`br.com.codecacto.kmplib.map.osm`):**
        `OsmMap(markers: List<OsmMarker>, modifier, center: OsmLatLng = OsmDefaults.BRAZIL_CENTER, zoom =
        12f, height = 360.dp, onMarkerClick: (markerId: String) -> Unit = {})` (N pins — espelha
        `ClientsMap`); `OsmSinglePinMap(point: OsmLatLng, modifier, label?, zoom = 15f, height = 220.dp,
        scrollEnabled = false)` (1 pin só-leitura — espelha `SinglePinMap`); `OsmPickerMap(value:
        OsmLatLng?, onPick: (OsmLatLng) -> Unit, modifier, height = 220.dp, zoomWithValue = 15f, zoomEmpty
        = 12f)` (escolher localização por clique — espelha `PickerMap`). Modelos `OsmLatLng(latitude,
        longitude)`, `OsmMarker(id, position, title = "")`; defaults `OsmDefaults.BRAZIL_CENTER` (São
        Paulo, igual ao web) / `CITY_ZOOM` / `STREET_ZOOM`.
      - **Bridge JS→Kotlin:** template Leaflet único em `OsmLeafletHtml` (commonMain puro, testável);
        clique em pin/mapa volta por `window.kmpBridge.postMessage(json)` (Android `@JavascriptInterface`;
        iOS `WKScriptMessageHandler` + shim webkit). Parser de mensagem leniente sem kotlinx-json.
      - **Testes:** `map/osm/OsmLeafletHtmlTest` (commonTest, 10 casos: Leaflet+OSM sem key, centro/zoom,
        pins id/título, picker on/off, gestos interactive on/off, shim só-iOS, escape JSON, parse
        marker/map/inválido). `:kmplib:compileDebugKotlinAndroid` + `compileCommonMainKotlinMetadata` BUILD
        SUCCESSFUL; testes 10/0/0. **klibs/framework iOS pendentes de host macOS** (P-IOS); o `actual` iOS
        está completo (WKWebView), validar render visual em macOS.
      - **Consumo no Influencer mobile:** tela "Mapa de clientes" → `OsmMap` (mapear `ClientDto` com
        lat/lng → `OsmMarker(id, OsmLatLng(lat,lng), name)`, `onMarkerClick` navega p/ ficha); ficha do
        cliente → `OsmSinglePinMap`; form de cliente → `OsmPickerMap` (clique define lat/lng). Sem chave,
        sem dependência nova no app. Bump kmplib → 2.40.0.

- [x] **GAP-INF-M-PDF-01 — gerador de PDF de documento estruturado (Android + iOS funcional)** —
      **ENTREGUE na 2.40.0**. Novo template `pdf/DocumentPdf*` para espelhar os PDFs `@react-pdf` da web
      do Influencer (contrato de parceria, relatório mensal do cliente) — documento **multi-seção**
      genérico, distinto do `OsPdfData` (financeiro fixo "TOTAL R$") e do `TableReportPdfData` (tabela
      pura). **iOS NÃO é placeholder** (ao contrário do `TableReportPdfGenerator.ios`, que ainda lança):
      render real via `UIGraphicsPDFRenderer`/Core Graphics, espelhando o layout do Android.
      - **Modelo (`DocumentPdfData`):** `company: DocumentPdfCompany(name, phone?, email?, address?,
        logoBytes?)`, `title`, `subtitle?`, `headerInfo: List<DocumentInfoRow>` (chave→valor),
        `sections: List<DocumentSection>`, `footer?`, `watermark`/`watermarkText`. `DocumentSection`
        (sealed): `Info(title?, rows, emptyText)` / `Table(title?, columns: List<DocumentColumn>, rows:
        List<DocumentRow>, emptyText)` / `Cards(title?, cards: List<DocumentCard>, emptyText)` /
        `Paragraph(title?, text)` / `Total(title?, label, value)`. Tudo **texto já formatado** pelo app
        (dinheiro como string "R$ ..."; a lib não conhece moeda).
      - **API:** `interface DocumentPdfGenerator { fun generate(data): ByteArray }`,
        `expect fun createDocumentPdfGenerator()`, `generateDocumentPdfBytes(data)`,
        `generateAndShareDocumentPdf(data, shareHandler = getShareHandler(), fileName, shareTitle)`
        (reusa `ShareHandler`), `defaultDocumentPdfFileName(title)`. Android `PdfDocument`; iOS
        `UIGraphicsPDFRenderer` — ambos com paginação, zebra, marca d'água -45°.
      - **Viabilidade do PDF KMP fiel:** CONFIRMADA — gerador KMP nativo (não server-side). Reaproveita o
        padrão já validado de Android `PdfDocument` + iOS `UIGraphicsPDFRenderer` (mesma técnica do
        `ReciboPdf.ios.kt`, que o fundador já valida em macOS). Layout legível com as MESMAS informações
        da web (não pixel-a-pixel).
      - **Testes:** `pdf/DocumentPdfDataTest` (commonTest, 6 casos: filename sanitiza/.pdf/fallback,
        `equals` com logoBytes, composição da shape de contrato, defaults de seção). Testes 6/0/0;
        `:kmplib:publishAndroidReleasePublicationToMavenLocal` + `publishKotlinMultiplatformPublication...`
        BUILD SUCCESSFUL → `br.com.codecacto:kmplib:2.40.0`. **klib iOS pendente de host macOS** (P-IOS);
        actual iOS completo.
      - **Consumo no Influencer mobile:** PDF do contrato → `DocumentPdfData(title="Contrato de Parceria",
        headerInfo=[org/cliente], sections=[Table("Termos", plano/vigência/qtds), Total("Valor mensal")])`;
        PDF do relatório mensal → `sections=[Cards("Entregas"), Table("Conteúdos postados"),
        Info("Financeiro"), Total]`. Chamar `generateAndShareDocumentPdf(data)`.

- [G10] **Removidos os `.bak` órfãos do FilePicker** — `FilePicker.android.kt.bak` e
      `FilePicker.ios.kt.bak` (gap G10 da auditoria) deletados na rodada da 2.40.0.

### Influencer — Fase 4 Fatia A / Dashboard (origem: 2026-06-19)
- [x] **GAP-INF-M-CHART-01 — componente `BarChart`/`StackedBarChart` Compose nativo (Android/iOS)** —
      **ENTREGUE na 2.39.0**. `ui/components/BarChart.kt` (commonMain puro, sem expect/actual, sem lib de
      gráfico externa). Promovido do contorno local `RevenueBarChart` do Influencer (Fase 4 Fatia A) — 3º
      consumidor do padrão (Influencer + MeuFrete G-MF-M-04 + Locador G-M1). Espelha a API
      "data + cor + altura + emptyMessage" do `SimpleBarChart` da weblib, em **barras verticais** (layout
      de dashboard mobile "últimos N meses"); cobre **1 série** e **≥2 séries empilhadas** + legenda.
      - **API (série única):** `BarChart(data: List<BarChartEntry>, modifier, barColor =
        colorScheme.primary, chartHeight = 128.dp, valueFormatter: ((Double)->String)? = null,
        emptyMessage = "Sem dados para exibir.")`; `data class BarChartEntry(label: String, value:
        Double)` (negativo → 0).
      - **API (empilhado, ≥2 séries):** `StackedBarChart(data: List<StackedBarEntry>, modifier,
        seriesColors = [primary, secondary, tertiary], chartHeight = 128.dp, valueFormatter?,
        emptyMessage)` — cobre "recebido + a receber" do dashboard. `data class StackedBarEntry(label,
        segments: List<BarSegment>)` + `data class BarSegment(value: Double, color: Color? = null)`
        (segmentos de baixo p/ cima na ordem da lista; cor própria ou de `seriesColors` por posição;
        `valueFormatter` recebe o TOTAL da barra).
      - **Legenda:** `ChartLegend(items: List<ChartLegendItem>, modifier)` + `data class
        ChartLegendItem(label, color)`.
      - **Padrões de plataforma:** estado vazio (sem dados / tudo zero) → `emptyMessage` centralizado;
        cores via tokens do tema (`MaterialTheme.colorScheme` — **sem hardcode**); responsivo via
        `LocalIsCompact` (compacto reduz altura ~25%, barras mais finas, oculta o valor no topo); valor
        formatado por **callback do chamador** (a lib NÃO conhece moeda/domínio — `valueFormatter`).
      - **Testes:** `ui/components/BarChartTest` (commonTest, 8 casos verdes: `barChartMax`/
        `stackedBarChartMax` — maior valor, lista vazia → 0, negativo tratado como 0, soma de segmentos,
        todos zero → 0). `:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL; `:kmplib:testDebugUnitTest
        --tests *BarChartTest*` 8/0/0; `:kmplib:publishToMavenLocal` BUILD SUCCESSFUL →
        `br.com.codecacto:kmplib:2.39.0` (`kmplib` metadata + `kmplib-android`; **klibs iOS pendentes de
        host macOS — P-IOS**; commonMain puro, deve compilar em iOS sem mudança; validar render visual em
        macOS).
      - **Migração (NÃO feita nesta entrega — coordenar com CTO/dev-mobile):** **Influencer** — substituir
        `RevenueBarChart`/`RevenueChartLegend`/`RevenueBar`/`toBars`/`maxStacked`/`compactMoney` locais
        (`mobile/composeApp/.../presentation/main/tabs/HomeDashboard.kt`) por `StackedBarChart` +
        `ChartLegend` da kmplib: mapear `RevenueSeriesPointDto` → `StackedBarEntry(monthLabel,
        listOf(BarSegment(received), BarSegment(toReceive)))` (mantém `monthLabelOf`/`toBar` no app, que é
        específico de domínio), `valueFormatter = { compactMoney(it) }` (mover o helper de moeda compacta
        para o app), e `ChartLegend(listOf(ChartLegendItem(recebido, colorScheme.primary),
        ChartLegendItem(aReceber, colorScheme.secondary)))`. **MeuFrete (G-MF-M-04)** e **Locador (G-M1)**
        — usar `BarChart` (série única: lucro por caminhão / receita por mês). Bump kmplib → 2.39.0 nos
        consumidores.

- [x] **Ads simplificados: remover AdMob/Firebase Ads + custom por projeto+superfície** —
      **ENTREGUE na 2.38.0**. A publicidade da kmplib passa a ser **APENAS house ads** (anúncios
      próprios via apps-api). **AdMob/Firebase Ads removidos por completo:** pacote `firebase/ads`
      (`AdManager`/`AdConfig`/`AdRemoteConfig`/`BannerAd`/`InterstitialAdController`/`AppOpenAdController`
      + holders Android/iOS + `IosAdUtils`), dep `play-services-ads`, cinterop/framework iOS
      `GoogleMobileAds` (`linkerOpts -weak_framework` + `cinterops.create` + `.def`), e a linha de
      cobertura `firebase.ads.AdManagerHolder`. Fontes Firestore legadas de ads também removidas
      (`CustomAdRepository`/`AdRoutingRepository`/`FirestoreAdStatsRecorder`) — REST é a única fonte.
      - **`ads/custom` repontado para projeto + superfície:** `CustomAdConfig` trocou `appId`/
        `placementId`/`collection` por **`projectSlug`** + **`surface`** (default "app").
        `RestCustomAdSource` agora chama `GET /public/ads?project={slug}&surface={surface}` (era
        `/public/ads/app/{appId}`). `CustomAd` SIMPLIFICADO (só `id`/`imageUrl`/`targetUrl`/`format`/
        `title`/`ctaLabel`; removidos `placementId`/`active`/`priority`/`weight`/`startsAt`/`endsAt`/
        `appId`/`appIds`). `selectAd` simplificado (rotação simples/sorteio uniforme — sem priority/
        weight/placement/janela). `CustomBannerAd`/`CustomInterstitialAd` perderam `placementId`.
      - **`ads/router`:** `AdProvider` agora só `CUSTOM`/`OFF` (ADMOB removido); `AdRouting.ALL_ADMOB`
        removido. `ManagedBannerAd`/`ManagedInterstitialAd` viraram 2 branches (CUSTOM/OFF) — API pública
        mantida (sem `placementId`).
      - **`ads/stats`:** `AdProviderTag` só `CUSTOM` (ADMOB removido); `AdFormat` sem `APP_OPEN`;
        `RestAdStatsRecorder`/`POST /public/ad-stats` mantidos.
      - **`monetization` repontado:** `MonetizationConfig` não carrega mais `AdConfig` (`AdsOnly` virou
        `data object`; `Freemium(purchase)`); `MonetizationManager` não usa mais `AdManager`/Remote
        Config — `shouldShowAds` = !premium (gate dos house ads). `KmpLib.init/setActivity/clearActivity`
        não chamam mais `AdManagerHolder`.
      - Testes atualizados (custom/router/stats); `CustomAdFiltersTest` removido (helpers de filtro
        client-side deixaram de existir). **BREAKING — migração nos apps:** trocar
        `CustomAdConfig(appId=..., placementId=...)` por `CustomAdConfig(projectSlug=..., surface="app")`;
        remover `placementId` dos composables `Custom*`/`Managed*`; trocar `MonetizationConfig.AdsOnly(ads)`/
        `Freemium(ads, purchase)` por `AdsOnly`/`Freemium(purchase)`; remover qualquer uso de
        `firebase/ads`, `AdConfig`, `AdProvider.ADMOB`, `AdRouting.ALL_ADMOB`. **iOS: republicar de host
        macOS (P-IOS)** — commonMain puro compila; remoção do cinterop GoogleMobileAds simplifica o build
        iOS. Pendência (fora do escopo): a dep `firebase-config` ficou órfã (só `AdRemoteConfig` a usava) —
        candidata a remover. Origem: simplificação de plataforma (só anúncios internos).

- [x] **Ads (house ads + routing + stats) → apps-api central (REST)** — **ENTREGUE na 2.37.0**.
      Épico "Monitoramento → banco central": os 3 caminhos de ads do kmplib saíram do Firestore
      (`custom_ads`, `app_ad_configs`, `ad_stats`) e passaram a usar os endpoints públicos do apps-api
      — mesma migração do `DeveloperInfoService`/`FeedbackService`. Interfaces preservadas
      (`CustomAdSource`/`AdRoutingSource`/`AdStatsRecorder`); só a fonte mudou (API pública dos
      composables intacta). Padrão Ktor core puro + `handleApiCall`/`ApiResult` + kotlinx-json; tudo
      best-effort.
      - **Novas sources REST (default):** `RestCustomAdSource` (`GET /public/ads/app/{appId}?placement=`),
        `RestAdRoutingSource` (`GET /public/ad-config/{appId}`), `RestAdStatsRecorder`
        (`POST /public/ad-stats`). `CustomAdConfig` ganhou `httpClient`/`appsApiBaseUrl`;
        `AdRouter.initialize`/`AdStats.initialize` ganharam `httpClient`/`appsApiBaseUrl`. Fallbacks
        `EmptyCustomAdSource`/`EmptyAdRoutingSource`/`NoopAdStatsRecorder` quando falta `httpClient`.
      - **`selectAd`:** ad com `format` em branco (caso REST) casa com banner E interstitial (placement
        decide o slot); ads Firestore (format preenchido) seguem o filtro estrito.
      - **Legado Firestore mantido como opt-in** (`CustomAdRepository`/`AdRoutingRepository`/
        `FirestoreAdStatsRecorder`) — não é mais default, mas continua injetável via `source`/`recorder`.
      - Testes: `RestCustomAdSourceTest` (8), `RestAdRoutingSourceTest` (6), `RestAdStatsRecorderTest`
        (3) — Ktor MockEngine. Origem: transversal (todo app monetizado).

- [x] **Force Update (atualização obrigatória) — módulo `appupdate`** — **ENTREGUE na 2.34.0**.
      Lado mobile do Force Update, consumindo o endpoint PÚBLICO do admin-api
      `GET /public/app-version?project=&platform=&versionCode=`. Serve todo app do ecossistema (a
      capacidade de forçar atualização é transversal de plataforma). Modelado no padrão do `feedback`
      (config com `httpClient`+`baseUrl`+`projectSlug`, best-effort tolerante a falha).
      - **API:** `AppUpdateConfig(projectSlug, httpClient, currentVersionCode, adminApiBaseUrl=
        "https://admin-api.codecacto.com.br", platform=currentPlatform)`; `AppUpdateService(config)
        .check(): AppUpdateStatus`; `sealed AppUpdateStatus { None | Soft(storeUrl?, message?,
        latestVersionName?) | Hard(storeUrl?, message?) }`; `AppVersionCheckResponse` (DTO do fio);
        UI `AppUpdateGate(config, texts, content)` + `HardUpdateScreen`/`SoftUpdateDialog` +
        `AppUpdateTexts` (defaults pt-BR).
      - **Robustez:** `check()` NUNCA lança/bloqueia — rede/timeout/4xx/5xx/corpo inválido/`action`
        desconhecida → `None` (só `action=="hard"` legítimo bloqueia). Hard = tela full-screen sem
        fechar/voltar; Soft = diálogo dispensável; None/checando = passa o `content`.
      - **Reúso (não duplicou):** `handleApiCall`/`ApiResult` (core/network), `getUrlLauncher()`
        (platform, abre a loja), `currentPlatform` (expect/actual), tema via `MaterialTheme.colorScheme`.
      - Testes `AppUpdateServiceTest` (8 — GET/query params, mapeamento none/soft/hard, action
        desconhecida/maiúscula, corpo inválido, 500 best-effort). Origem: transversal (≥2 apps).

### MinhasVacinas — Onda 3/4 (origem: ux-designer 2026-06-14, `MinhasVacinas/docs/design/wireframes.md`)
> Gaps ALTA (componente-coração + template PDF) entregues na 2.32.0; **GAP-MV-M-06 (multi-seleção em
> massa, pré-requisito da "aplicação em lote" do Rebanho, Onda 4) entregue na 2.33.0.** App KMP de
> controle de vacinas, 4 flavors (infantil/adulto/pet/rebanho). Demais gaps (M-01..M-04) seguem abertos
> no design (não promovidos nesta entrega).
>
> **Dívida pré-existente rastreada (NÃO resolvida nesta entrega):** **MV-3-S-02** — `AndroidShareHandler`
> não limpa `cacheDir/shared_files` → PDFs sensíveis (carteira/comprovante) acumulam no cache. Avaliar
> uma rotina de limpeza (TTL / limpar no `shareFile` antes de gravar, ou `cleanSharedCache()` exposto) no
> `ShareHandler` da kmplib — serve todo app que compartilha arquivo. Origem: triagem CTO
> `2026-06-14-design-minhasvacinas.md`.

- [x] **GAP-MV-M-05 — Timeline / Calendário vacinal (componente genérico)** — **ENTREGUE na 2.32.0**.
      `ui/components/TimelineList.kt` (commonMain puro, sem expect/actual). Componente GENÉRICO (não
      acoplado a vacinas) de linha do tempo/cronograma vertical — serve o calendário vacinal dos 4
      flavors + qualquer timeline do ecossistema (status de cobrança, etapas, marcos).
      - **API:** `TimelineList(items: List<TimelineItem>, modifier, onItemClick: ((String)->Unit)?,
        scrollable=true|false, contentPadding, trailingContent)`; `TimelineItem(id, title, dateLabel?,
        subtitle?, status: TimelineStatus, badgeLabel?, badgeColor?, indicatorColor?, muted, enabled)`;
        `enum TimelineStatus(None|Done|Pending|Late|Scheduled)`.
      - **Reúso (não duplicou):** badge de status compõe o `StatusBadge` existente; cores via tokens do
        tema (`AppColors.current.success/info`, `colorScheme.primary/error/onSurfaceVariant`) — **sem
        hardcode**; responsividade via `LocalIsCompact`. App mapeia dose TOMADA/PENDENTE/ATRASADA/
        AGENDADA → `TimelineStatus`. `muted=true` = linha "legado/não-agendável" (Aftosa no Rebanho).
      - `:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL; `:kmplib:publishToMavenLocal` →
        `br.com.codecacto:kmplib:2.32.0`. **Consumo dev-mobile:** telas Detalhe/Carteira (6) e
        Calendário (7).
- [x] **GAP-MV-M-06 — Lista multi-seleção com seleção em massa (componente genérico)** — **ENTREGUE
      na 2.33.0**. `ui/components/MultiSelectList.kt` (commonMain puro, sem expect/actual). Componente
      GENÉRICO (não acoplado a "animal/vacina") de lista selecionável com seleção em massa para a
      "aplicação em lote" do flavor Rebanho (uma vacinação aplicada a N animais) — e qualquer fluxo de
      ação em lote do ecossistema. `AppMultiSelect` é dropdown e NÃO cobre lista grande com checkbox por
      linha + seleção em massa.
      - **API:** `fun <T> MultiSelectList(items: List<T>, key: (T)->String, selectedIds: Set<String>,
        onToggleItem: (String)->Unit, itemContent: @Composable (T)->Unit, modifier, onToggleAll:
        (()->Unit)?, enabled: (T)->Boolean = { true }, showHeader=true, selectedLabel: (Int)->String,
        selectAllLabel, clearLabel, header: (@Composable (MultiSelectSummary)->Unit)?, contentPadding)`.
        Estado de selecionados **controlado/hoisted** (`Set<String>` no chamador — a lib não guarda
        seleção). Slot de conteúdo por item genérico (consumidor renderiza a linha). Header/topbar de
        seleção opcional (contagem "N selecionados" + ação **selecionar todos / limpar**); checkbox por
        linha com alvo de toque = linha inteira (acessível, `toggleable`+`Role.Checkbox`); slot `header`
        para `FilterChipRow` (faixa etária/lote).
      - **Helpers puros (sem Compose, testáveis):** `data class MultiSelectSummary(selectedCount,
        totalCount)` (`allSelected`/`noneSelected`); `multiSelectSummary(selectedIds, enabledIds)`
        (ignora ids "fantasma" fora da lista selecionável); `toggleSelection(current, id)`;
        `toggleAllSelection(current, enabledIds)` (todos selecionados → limpa; senão → seleciona todos,
        preservando ids alheios).
      - **Reúso (não duplicou):** `Checkbox` do Material 3 (mesmo do `AppCheckbox`), `TextButton`
        temático; cores via `MaterialTheme.colorScheme` (linha selecionada = `primary` a 8% alpha; header
        = `surfaceVariant`) — **sem hardcode**; responsividade via `LocalIsCompact` (padding de linha
        maior no expandido); item `enabled=false` aparece esmaecido (alpha) e fora da contagem/seleção em
        massa. `enabled` predicado por item (animais já vacinados não selecionáveis).
      - Testes: `ui/components/MultiSelectListTest` (commonTest, 14 casos verdes: summary none/all/partial/
        ghost-id/empty, toggle add/remove/round-trip, toggleAll select-all/clear/preserva-alheios).
      - `:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL; `:kmplib:testDebugUnitTest --tests
        *MultiSelectListTest*` 14/0/0; `:kmplib:publishToMavenLocal` BUILD SUCCESSFUL →
        `br.com.codecacto:kmplib:2.33.0` (`kmplib` metadata + `kmplib-android.aar`; klibs iOS pendentes
        de host macOS — commonMain puro, compila em iOS sem mudança). **Consumo dev-mobile:** tela
        Aplicação em lote (8c) do Rebanho — `MultiSelectList` da lista de animais com "Selecionar todos",
        `FilterChipRow` no slot `header`, contador no header de seleção, e `Revisar e confirmar (N)`
        usando `MultiSelectSummary.selectedCount`. **Próximo passo: dev-mobile integra na Onda 4.**
- [x] **GAP-MV-M-07 — Template PDF "Carteira de Vacinação" + base p/ comprovante Rebanho** —
      **ENTREGUE na 2.32.0**. Módulo `pdf` estendido (reusa a infra dos demais geradores).
      - `pdf/VaccinationCardPdfData.kt` (commonMain) — `VaccinationCardPdfData(holder, title, subtitle?,
        logoBytes?, columns[], items[], emptyText, generatedAtLabel, notice?, footer?, watermark,
        watermarkText?)`; `VaccinationHolder(name, lines[])` + `VaccinationHolderLine(label, value)`;
        `VaccinationColumn(label, align, weight)`; `VaccinationItem(cells[], statusColorArgb?, muted)`.
        GENÉRICO: serve carteira humana/pet E **comprovante do Rebanho** via `notice` ("documento
        organizacional, não-oficial") — sem hardcode de domínio.
      - `pdf/VaccinationCardPdfGenerator.kt` (commonMain `expect` + helpers `generateVaccinationCardPdfBytes`,
        `generateAndShareVaccinationCardPdf`, `defaultVaccinationCardPdfFileName`). **Android:**
        `PdfDocument` nativo (cabeçalho, bloco do titular, caixa de aviso, tabela paginada com colunas
        ponderadas + ponto de status, marca d'água).
      - **iOS — DIFERENCIAL (não placeholder):** `VaccinationCardPdfGenerator.ios.kt` implementado com
        **`UIGraphicsPDFRenderer`/UIKit** (modelado no `ReciboPdf.ios.kt`), paridade de layout com o
        Android. **RISCO/pendência conhecida:** validação VISUAL no iOS só em host macOS — neste ambiente
        Windows os targets iOS são desabilitados (`compileKotlinIos* = SKIPPED`) e não há klib iOS no
        mavenLocal (item de prioridade alta "Publicar artefatos iOS a partir de host macOS"). Código
        escrito por construção; **NÃO foi validado visualmente** — pende de macOS/CI.
      - Testes: `VaccinationCardPdfDataTest` (commonTest, 8 casos verdes).
      - `:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL; `:kmplib:testDebugUnitTest --tests
        *VaccinationCardPdfDataTest*` 8/0/0; `:kmplib:publishToMavenLocal` →
        `br.com.codecacto:kmplib:2.32.0` (`kmplib` metadata + `kmplib-android`). **Consumo dev-mobile:**
        tela Exportar/Compartilhar PDF (9); carteira humana/pet (sem `notice`) e comprovante Rebanho
        (com `notice` + `footer` de lote/lab/validade). iOS usa fallback de texto até validar em macOS.

### sync — fix de corretude: resolução de FKs por-clientId (origem: code-review MeuFrete Onda 3, 2026-06-14)

- [x] **BUG-SYNC-01 — `SyncOp.refs` sempre `null`; FK clientId→serverId nunca remapeada** —
      **CORRIGIDO na 2.31.1**.
      - **Causa:** `DefaultSyncEngine.toSyncOp()` enviava o payload como está e fixava `refs = null`
        (apesar do comentário "remapeia refs clientId→serverId"). No offline-first real, uma filha
        criada offline (ex.: frete) referencia o **clientId local** de um pai também criado offline
        (ex.: cliente); no push o servidor gera um `id` novo para o pai → a FK (clientId local) não
        resolvia no servidor → filho rejeitado (`not_found`). O backend já sabe resolver via
        `SyncOp.refs` em ordem topológica, mas o cliente nunca populava `refs`.
      - **Fix (desenho):** (1) **`SyncableEntity<T>.refsOf(model): Map<String,String> = emptyMap()`**
        (novo, default não-quebra implementadores) — declara `{campoFkNoWire → clientIdLocal do pai}`.
        (2) `SyncRefResolver` (novo, `internal`, puro/sem I/O) decide por FK: **backfill** no payload
        quando o pai já tem `serverId` no espelho (substitui clientId→serverId direto no payload) OU
        **`SyncOp.refs`** quando o pai ainda está pendente no mesmo lote (servidor resolve em ordem
        topológica). O engine injeta o resolvedor de serverId via `store.getByClientId(...)`. Valor que
        já seja serverId no payload é preservado. (3) **Ordem de push** já respeita dependência (ordem de
        `register` → `sortedBy registryIndex`) — confirmado.
      - **Refactor de apoio:** `SyncStore` virou **interface** (impl `SqlDelightSyncStore`); `SyncStore(db)`
        preservado via `operator invoke` no companion (call-site dos apps inalterado). Permite `FakeSyncStore`
        em teste e exercitar o engine pelo caminho REAL.
      - **Testes:** `SyncRefResolverTest` (7, lógica pura) + `DefaultSyncEngineRefsTest` (4, caminho real do
        engine via `FakeSyncStore` + `SyncPort` que captura o `SyncPushRequest`): (a) pai pendente no lote →
        `refs` do filho = `{clientRef→clientId do pai}`; (b) pai já com serverId → payload do filho sai com a
        FK = serverId (backfill), `refs=null`; + entidade sem refs e pai órfão. **Pacote `sync.*` verde:**
        `testDebugUnitTest --tests "...sync.*"` → DefaultSyncEngineRefsTest 4/4, SyncRefResolverTest 7/7,
        SyncWireTest 4/4, SyncModelsTest 6/6, 0 falhas.
      - **Build/publish:** `:kmplib:publishToMavenLocal` BUILD SUCCESSFUL → `br.com.codecacto:kmplib:2.31.1`
        (`kmplib` metadata + `kmplib-android`). iOS klibs validam só em host macOS (commonMain puro compila).
      - **Migração (dev-mobile, MeuFrete):** implementar `refsOf` nas entidades — freight →
        `{"clientRef" to clientRef, "truckId" to truckId}`; expense → `{"freightId" to freightId}`;
        revenue → `{"freightId" to freightId}`; truck/client → sem refs (default). Bump kmplib → 2.31.1.

### ShareHandler — fix de segurança/infra (origem: security-review, 2026-06-14)

- [x] **BUG-SHARE-AND-01 — `ShareHandler.shareFile` quebrado no Android (FileProvider ausente) +
      exceção engolida** — **CORRIGIDO na 2.31.0**.
      - **Causa:** `ShareHandler.android.kt` grava em `cacheDir/shared_files` e chama
        `FileProvider.getUriForFile(context, "${packageName}.fileprovider", file)`, mas **nenhum
        `<provider>` FileProvider estava declarado** (nem na kmplib nem nos apps) → runtime lançava
        `IllegalArgumentException` ("Couldn't find meta-data for provider"). Pior: o `catch (e:
        Exception)` **engolia** a exceção e só logava → chamador (ex.: ExportService) recebia "sucesso"
        sem nada compartilhado. Quebrava TODO compartilhamento de arquivo no Android.
      - **Fix:** (1) `library/src/androidMain/AndroidManifest.xml` (novo) declara o `<provider>`
        `androidx.core.content.FileProvider` com authority `${applicationId}.fileprovider` (placeholder
        substituído no merge do app) + `library/src/androidMain/res/xml/kmplib_file_paths.xml`
        (`<cache-path name="shared_files" path="shared_files/"/>` — só o necessário, prefixo
        `kmplib_` p/ evitar colisão). (2) `androidx.core:core` declarado explicitamente no androidMain
        (garante a classe FileProvider em runtime). (3) `shareText`/`shareImage`/`shareFile` (Android +
        iOS) agora **logam E RELANÇAM** a exceção — não reportam mais sucesso silencioso. KDoc do
        contrato comum atualizado (`@throws`).
      - **Contrato:** assinatura pública INALTERADA (segue `Unit`), mas agora **pode lançar** —
        chamadores devem envolver em try/catch. Consumidores internos (`generateAndShare*`,
        `shareCard*`) propagam naturalmente (não tinham try/catch).
      - **Colisão conhecida:** app que já declare seu próprio FileProvider com a MESMA authority
        (`${applicationId}.fileprovider`) terá conflito de provider duplicado no merge → remover o
        provider próprio (passa a vir da kmplib) ou `tools:replace`.
      - **Build/publish:** `:kmplib:compileDebugKotlinAndroid` + `:kmplib:processDebugManifest` BUILD
        SUCCESSFUL; merged manifest confirma o provider c/ `${applicationId}`; AAR publicado
        (`kmplib-android-2.31.0.aar`) contém manifest + `res/xml/kmplib_file_paths.xml`;
        `publishToMavenLocal` → `br.com.codecacto:kmplib:2.31.0`. **Suíte `testDebugUnitTest` tem 51
        falhas PRÉ-EXISTENTES e alheias** (PasswordValidator/ads router+stats/HandleApiCall/AppPrefs/UI
        components Avatar/LoadingOverlay/OfflineBanner) — nenhuma toca ShareHandler. iOS klibs validam
        só em host macOS (commonMain/iosMain puros compilam).

### MinhasHoras — Onda 2 (origem: 2026-06-14)

- [x] **GAP-MH-M-02 — Template de PDF "Relatório de horas extras" + suporte** — **ENTREGUE na 2.29.0**.
      Dossiê de cobrança: tabela de lançamentos + totais (destaque do pendente) + **comprovantes
      embarcados como imagens**. Reusa EXATAMENTE o padrão do `WorkReportPdfGenerator` (cabeçalho,
      grade de imagens, marca d'água, helpers de escala/decode).
      - `pdf/HoursReportPdfData.kt` (commonMain) — shape **CONGELADO** com paridade exata com a weblib:
        `HoursReportPdfData(company, periodLabel, companyLabel, generatedAtLabel, entries[],
        totalHoursLabel, totalPendingLabel?, totalPaidLabel?, totalContestedLabel?, attachments[],
        watermark, watermarkText?)`; `HoursReportPdfCompany(name, phone?, logoBytes?)`;
        `HoursReportEntry(date, start, end, durationLabel, valueLabel?, statusLabel)`;
        `HoursReportAttachment(imageBytes, caption?)` (anexo SEMPRE imagem).
      - `pdf/HoursReportPdfGenerator.kt` (commonMain `expect` + helpers `generateHoursReportPdfBytes`,
        `generateAndShareHoursReportPdf`, `defaultHoursReportPdfFileName`). **Android:** `PdfDocument`
        nativo (paginação, tabela, totais com caixa de destaque do pendente, grade de comprovantes).
        **iOS:** placeholder (`OsPdfNotSupportedException` — dívida conhecida, host macOS).
      - `pdf/PdfRasterizer.kt` — `expect fun renderPdfPagesToImages(pdfBytes): List<ByteArray>`;
        **Android** via `android.graphics.pdf.PdfRenderer` (DPI 150, PNG, sem dep externa); **iOS**
        placeholder. Habilita anexar comprovante em PDF como páginas-imagem.
      - `firebase/storage/StorageService.downloadBytes(path, httpClient, maxSizeBytes = 25 MB):
        Result<ByteArray>` — download de bytes genérico (GitLive 2.1.0 não expõe em commonMain → GET
        via Ktor HttpClient injetado pelo app).
      - Testes: `HoursReportPdfDataTest` (commonTest, 7 casos verdes: fileName/sanitização/edge,
        equals+hashCode de company/attachment, defaults do shape congelado, value opcional).
      - `:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL; `:kmplib:testDebugUnitTest` (filtro
        `*HoursReport*`) verde; `:kmplib:publishToMavenLocal` → `br.com.codecacto:kmplib:2.29.0`
        (klibs iOS pendentes de host macOS — commonMain puro). **Nota:** a suíte completa
        `testDebugUnitTest` tem falhas **pré-existentes e alheias** (PasswordValidator/ads/network/
        prefs/UI components — não tocadas neste PR). Consumo dev-mobile: gerar o dossiê na tela de
        cobrança do MinhasHoras; iOS usa fallback de texto até o render macOS.

### Pendências de testabilidade / API de prefs (origem: code-review ChamadaFacil T0.3, 2026-06-14)

- [ ] **GAP-PREFS-TEST-01 — promover `FakeAppPreferences` a source set publicável** — Média · serve
      todo app que persiste via `AppPreferences` (ChamadaFacil, Doses, Salmos, Call Recorder...).
      Hoje o `FakeAppPreferences` in-memory mora em `library/src/commonTest` e **não vem no artefato
      publicado** → cada app consumidor recria o fake no próprio `commonTest` (duplicação). Sugestão:
      expor um artefato de testes (`kmplib-test`) ou mover o fake para um `commonMain` de um módulo
      `core/prefs-testing` consumível com `testImplementation`. Origem concreta: ChamadaFacil teve de
      recriar `FakeAppPreferences` local para testar os repositórios de persistência.
- [ ] **GAP-PREFS-KEYS-01 — enumeração de chaves em `AppPreferences`** — Média · habilita saneamento
      de órfãos em storage local. A interface só tem `has/remove/clear`, sem `keys()`/`keysWithPrefix()`.
      Sem isso, apps que persistem coleções por chave-composta (ex.: `chamada_<turmaId>_<data>`) não
      conseguem varrer e descartar chaves órfãs após uma cascata de exclusão não-atômica (ficou como
      DÍVIDA Onda 0 no ChamadaFacil `TurmaPrefsRepository`). Sugestão: `suspend fun keys(): Set<String>`
      e/ou `keysWithPrefix(prefix): Set<String>` (Android: `SharedPreferences.all.keys`; iOS:
      `dictionaryRepresentation()`).

### Números da Sorte — design MVP (origem: ux-designer 2026-06-14, `NumerosDaSorte/docs/design/lib-gaps.md`)
> App KMP standalone (Arq. A, offline) de geração de jogos de loteria por "temas" (aleatório/horóscopo/
> sorte/numerologia), freemium, persona 55+. 5 gaps mobile + 1 web. Reusa amplamente a kmplib
> (AppTopBar/BackTopBar/AppButton/AppTextField/AppDatePicker/AppTimePicker/AppCheckbox/AppSwitch/
> ThemeChipGrid/SegmentedControl/FilterChipRow/Card/FormContainer/EmptyState/SkeletonBox/LoadingOverlay/
> ErrorModal/Toast/ConfirmationDialog/AppBottomSheet/StatusBadge/UsageMeter/UsageBadge/**PaywallScreen**+
> entitlement/ShareHandler/NotificationScheduler/UrlLauncher/LocalIsCompact/gridColumns).

- [x] **GAP-NS-M-05 — Esferas de dezenas + animação de revelar** — **ENTREGUE na 2.27.0** (coração do
      app, bloqueador da Onda 1/Resultado). `ui/components/LotteryBallRow.kt` (commonMain puro, sem
      expect/actual). `LotteryBallRow(numbers, columns=3, variant=Normal|Compact, tone: BallTone |
      accentColor, numberColor?, animateReveal=true, revealKey, revealTotalMillis=600, ballSize?,
      spacing?, contentDescriptionOverride?)`. Grid Column-de-Rows; esferas circulares grandes
      (Normal 56dp ≥ 48dp a11y persona 55+) ou compactas (36dp, Histórico). Animação de revelar em
      **stagger** scale+fade distribuído em `revealTotalMillis` (helper puro `ballRevealDelayMillis`);
      re-dispara ao mudar `revealKey`; `animateReveal=false` = movimento reduzido (instantâneo).
      Acento SEM hardcode (token via `BallTone` / `accentColor` derivado do `AppTheme`). A11y:
      `clearAndSetSemantics` com descrição agregada. Lógica pura testada em `LotteryBallRowTest`
      (10 casos, verdes). **Genérico** (qualquer app de loteria — Quina/Lotofácil futuros).
      `:kmplib:compileDebugKotlinAndroid` + `testDebugUnitTest` + `:kmplib:publishToMavenLocal` BUILD
      SUCCESSFUL → `br.com.codecacto:kmplib:2.27.0` (klibs iOS pendentes de host macOS — commonMain
      puro, deve compilar em iOS sem mudança; validar render visual em macOS). Consumo dev-mobile:
      tela de Resultado (Onda 1) + reuso compacto no Histórico (Onda 2) + base do card compartilhável
      (GAP-NS-M-06).
- [x] **GAP-NS-M-06 — Card de jogo compartilhável + render-to-PNG temático** — **ENTREGUE na 2.29.0**
      (Onda 2 / compartilhar jogo). Módulo `ui/share` estendido (não recriou o motor de captura), em
      `ui/share/GameShareCard.kt` + `ui/share/GameShareCardRender.kt` (commonMain puro, sem expect/actual).
      **Spec:** `GameShareCardSpec(numbers: List<Int>, columns=3, label="", icon="", narrative="",
      brand="", disclaimer="", format=ShareCardFormat.SQUARE)` — genérico p/ qualquer app de loteria;
      **nenhum texto de domínio hardcoded** (rótulo/narrativa/marca/**disclaimer** vêm do chamador →
      compliance "Apenas diversão / +18 / sem vínculo" é passada pelo app). **Estilo:**
      `GameShareCardStyle(background, accentColor, onAccentColor, titleColor, narrativeColor, brandColor,
      disclaimerColor)` + `GameShareCardStyle.fromColorScheme(scheme, accent=scheme.primary,
      onAccent=scheme.onPrimary)` — **acento (identidade do tema) SEM hardcode**, derivado do `AppTheme`/
      token pelo chamador (reaproveita `ShareCardBackground`/`ShareCardFormat` do card devocional).
      **Composable preview** `GameShareCard(spec, style, modifier, textFontFamily=SansSerif)` — **reusa
      `LotteryBallRow`** (2.27.0, `animateReveal=false`) nas esferas, fiel ao PNG. **Render off-screen:**
      `renderGameShareCardToPng(spec, style, textMeasurer, targetWidthPx=1080, textFontFamily): ByteArray`
      — desenha ícone/rótulo/esferas (círculos via Canvas)/narrativa/marca/disclaimer off-screen via
      `CanvasDrawScope` + `TextMeasurer` e codifica com `encodeBitmapToPng` (`platform/BitmapEncoder`),
      **sem host de UI** (Android + iOS). Atalhos `shareGameCardImage(...)` (render → `ShareHandler.shareImage`)
      e `shareGameCardText(...)` (fallback texto: rótulo + dezenas + marca + disclaimer). **Reusa (não
      recriou):** `renderShareCardToPng`/`encodeBitmapToPng`/`ShareHandler` (mesmo motor de viralização do
      Salmos GAP-SAL-03, novo skin) + `LotteryBallRow` (GAP-NS-M-05). Lógica/contrato do spec testados em
      `ui/share/GameShareCardTest` (8 casos commonTest, verdes). `:kmplib:compileDebugKotlinAndroid` +
      `:kmplib:compileKotlinIosSimulatorArm64` + `testDebugUnitTest` (GameShareCardTest 8/0/0) +
      `:kmplib:publishToMavenLocal` BUILD SUCCESSFUL → `br.com.codecacto:kmplib:2.29.0` (`kmplib` metadata +
      `kmplib-android`; klibs iOS pendentes de host macOS — commonMain puro, deve compilar em iOS sem
      mudança; validar render visual em macOS). Consumo dev-mobile: tela Resultado + Histórico (Onda 2,
      botão "Compartilhar" → `shareGameCardImage` / `shareGameCardText`).
- [ ] **GAP-NS-M-02 — Card de seleção de tema (ícone+título+badge premium/cadeado)** — Média · Onda 0/1.
      Card grande clicável com estado bloqueado (cadeado/"Pro") e selecionável. `ThemeChipGrid` é chip
      pequeno (inadequado p/ 55+). Sugestão `FeatureCard`/`SelectableFeatureCard` + slot `StatusBadge`.
      Contorno: `Card` + `gridColumns` local. Candidato a reuso (grade de modos em apps lúdicos).
- [ ] **GAP-NS-M-03 — Bottom sheet "limite atingido" → Paywall** — Média · Onda 3. Sheet padrão
      "atingiu o limite" + CTA "Ver o Pro"/"Agora não", consumindo `QuotaExceeded`/`UsageSnapshot` e
      abrindo `PaywallScreen`. Complementa `monetization/entitlement`. Sugestão
      `LimitReachedSheet(usage, onUpgrade, onDismiss, texts)`. Contorno: `AppBottomSheet` local.
      Todo app freemium repete — promover.
- [ ] **GAP-NS-M-04 — NotificationScheduler: reboot + filtro de dias** — Média · Onda 3. (1) Sem
      `BOOT_COMPLETED` receiver no Android (limitação já documentada) → lembrete diário se perde após
      reboot; (2) `scheduleDailyNotification` agenda todo dia, mas o app precisa de dias específicos
      (ter/qui/sáb). Sugestão: `daysOfWeek: Set<DayOfWeek>` + receiver opcional de boot na lib.
      Mitigável (reagendar na abertura + filtrar no app) → não bloqueia.

> **Reuso confirmado (NÃO recriar) — citado no design do Números da Sorte:**
> `PaywallScreen`+entitlement (Paywall, só copy de loteria), `UsageMeter`/`UsageBadge` (cota na Home),
> `ShareHandler` (envio do compartilhamento), `ThemeChipGrid`+`ChipItem` selecionável (seleção de signo),
> `AppDatePicker`/`AppTimePicker`/`AppTextField`/`AppSwitch`/`AppCheckbox` (Perfil/Pessoais/Ajustes).
> **GAP-NS-W-01 (Footer landing)** → weblib, baixa, montar local.

### MeuFrete — design MVP (origem: ux-designer 2026-06-14, `RotaCerta/docs/04-design.md`)
> Gaps de UI do design das telas mobile do MeuFrete (gestão de fretes + financeiro; Arq. D full-stack,
> offline-first crítico). App reusa MUITO da kmplib: `LoginScreen`/`RegisterScreen` 2.0, `map/`
> (`MapView`/`MapMarker`/`LatLng`/`CameraPositionState`) + `location/` (`LocationProvider`), `mask`
> (`PlateVisualTransformation`/`CurrencyMask`/`CpfMask`/`CnpjMask`/`PhoneMask`), `core/money` (`Money`),
> `core/network` (`ConnectivityObserver`), `AppFab`/`AppBottomNavBar`/`AppTopBar`/`FilterChipRow`/
> `ThemeChipGrid`/`SegmentedControl`/`AppBottomSheet`/`AppDatePicker`, `ImagePicker` + `UploadQueue`/
> `UploadQueueView` (2.26.0), `monetization/entitlement` + `PaywallScreen`/`UsageMeter` (2.24.0),
> `FinanceReportPdfGenerator` (2.21.0, pós-MVP), `LocalIsCompact` (2.23.0).
> Bloqueadores reais são da **Onda 3** (G-MF-M-01, G-MF-M-02) — destravar antes dela (alinhado a R1/R2).
- [x] **G-MF-M-01 — Seletor de origem/destino (place picker)** — **ENTREGUE na 2.30.0** (T1c). Módulo
      `map/route`: `@Composable PlacePicker(geocoding, initial?, locationProvider?, onPicked, onDismiss, ...)`
      reusa `MapView`/`MapMarker`/`rememberCameraPositionState`/`LocationProvider` — busca com autocomplete
      (≥3 chars) + long-press p/ soltar pino + reverse geocode, retornando `GeocodeResult{label, position,
      secondaryLabel?}`. Provedores genéricos `RouteProvider`/`GeocodingProvider` + impl ORS
      (`OrsRouteProvider`/`OrsGeocodingProvider`) sobre `HttpClient` injetado, `apiKey` por construtor.
      **Offline degrada** p/ pino + rótulo manual (cobre G-MF-M-05). Testes `RouteModelsTest` (3, verdes).
      commonMain puro (iOS compila sem mudança; validar render/Map em macOS).
- [x] **G-MF-M-02 — `SyncQueueView` (visão da fila de sync offline)** — **ENTREGUE na 2.30.0** (T1b). Par
      do `UploadQueueView`: `ui/components/SyncQueueView(state, items, onRetry, onRetryAll, onDiscard)` +
      `SyncQueueItemRow` + `SyncStatusBadge` (pendente/sincronizando/conflito/erro via tokens do tema) e
      `SyncBanner(state)` ("Sincronizando N itens…"/offline/erro, par do `OfflineBanner`). Consome
      `SyncState`/`SyncQueueItem` do módulo `sync/`. Genérico p/ qualquer app offline-first com backend
      próprio.
- [ ] **G-MF-M-03 — Card/indicador financeiro de lucro** — Receita − Despesa = Lucro + margem % + R$/km,
      cor por sinal via tokens success/error. Recorrente: Detalhe frete, Detalhe caminhão, Relatório.
      Distinto de `UsageMeter` (cota). **Média · Onda 2.** Fallback: `Card` + `Text` tokenizados + `Money`.
- [x] **G-MF-M-04 — Chart simples (barras) para Compose MP** — lucro por caminhão / receita por mês.
      **COMPONENTE ENTREGUE na 2.39.0** via `GAP-INF-M-CHART-01` (`ui/components/BarChart` — use `BarChart`
      para série única). **Falta só integrar no MeuFrete** (Onda 2) — não recriar; mapear os dados para
      `List<BarChartEntry>` e bumpar kmplib → 2.39.0.
- [x] **G-MF-M-05 — Estado "rota indisponível offline" no `MapView`** — **ENTREGUE na 2.30.0** (T1c, junto
      do `PlacePicker`). Quando a busca/reverse do `GeocodingProvider` falha (sem rede), o `PlacePicker`
      exibe o aviso "Busca de rota indisponível offline. Solte o pino no mapa e informe o local." + campo de
      rótulo manual, mantendo o pino do mapa (cache). Cobre o cenário sem precisar de componente extra.

> **Reuso de gaps já no backlog (NÃO recriar) — citados no design do MeuFrete:**
> **GAP-CR-05 (`SettingsRow`/`ListItem`)** → Conta/Perfil — +1 consumidor (≥4 apps, reforça promoção);
> **GAP-SAL-05 / GAP-RF-M-04 (`OnboardingPager`)** → onboarding inicial (se virar carrossel);
> **GAP-CR-03 (`SwipeableListItem`)** → swipe p/ excluir despesa/frete (opcional).

### Locador — design MVP (origem: ux-designer 2026-06-13, `Locador/docs/design/wireframes.md`)
> Gaps de UI detectados no design das telas mobile do Locador. Complementam G5/G6 do roadmap do projeto.
> Vários servem a ≥2 apps (paridade com a weblib) — priorizar esses.
- [~] **G-M1 — Chart simples p/ Compose MP** — Dashboard mobile (receita 6 meses). **Barras
      ENTREGUES na 2.39.0** (`ui/components/BarChart`/`StackedBarChart`, via `GAP-INF-M-CHART-01`) — usar
      no dashboard do Locador (não recriar). **Falta a variante ÁREA/LINHA** (`SimpleAreaChart` da weblib),
      ainda não promovida — abrir item próprio se o Locador precisar de área e não bastar barras. Média.
- [ ] **G-M3 — Banner persistente (info/warning) no topo de tela** — hoje só há `OfflineBanner`/`Toast`.
      Usado no banner de trial, aviso de template de régua fora do aprovado e aviso "conecte sua conta".
      Espelhar o `Banner` (info/success/warning/error) da weblib. Média.
- [ ] **G-M5 — Timeline/Steps de status (vertical, themable)** — Detalhe da cobrança
      (gerada → enviada → paga). Par do gap web G-W4 (paridade mobile/web). Média.
- [ ] **G-M2 — Onboarding pager + indicador de páginas (dots)** — composable `OnboardingPager` reutilizável
      (todo app novo da Casca tem onboarding de 3 telas). Baixa.
- [ ] **G-M4 — Card de cobrança** (valor + encargos + status + ação rápida) e **G-M6 — Card de imóvel**
      (apelido + status alugado/vago + inquilino) — padrões recorrentes; refinam o G6 do roadmap.
      Confirmar duplicação via `/lib-audit` antes de promover. Baixa.

### Chamada Fácil — design MVP (origem: ux-designer 2026-06-14, `ChamadaFacil/docs/design/wireframes.md`)
> Gaps de UI do design do app de chamada (professor solo). Nenhum é bloqueante — todos têm fallback
> local com componentes existentes; o app pode nascer sem eles. Avaliar promoção via `/lib-audit`.
- [x] **GAP-CF-M-06 — Template de PDF de TABELA genérico (relatório não-financeiro)** — **ENTREGUE na
      2.30.0**. Os geradores de PDF da lib eram todos de domínio financeiro (`OsPdfData`/`OsPdfGenerator`
      imprime SEMPRE caixa "TOTAL R$"; `FinanceReportPdfData`; etc.) → forçar o `OsPdfData` para um
      relatório de frequência produzia uma caixa "TOTAL R$ 0,00" residual (layout de fatura enganoso).
      Novo template de **tabela puro** (cabeçalho + N colunas + M linhas, **SEM dinheiro/total**), genérico
      p/ qualquer relatório tabular do ecossistema. **Reusa EXATAMENTE** a infra existente (mesmo
      `expect/actual`, mesmo `PdfDocument` Android, mesmos helpers de paginação/decode/wrap/truncate do
      `WorkReportPdfGenerator`, mesma `OsPdfNotSupportedException` no iOS) — não criou pipeline paralelo.
      - `pdf/TableReportPdfData.kt` (commonMain) — `TableReportPdfData(company, title, subtitle?, columns[],
        rows[], emptyText, summary?, footer?, watermark, watermarkText)`; `TableReportPdfCompany(name,
        phone?, email?, address?, logoBytes?)` (mesmo mecanismo de logo do `OsPdfData`);
        `TableReportColumn(label, align: TableReportAlign = START|CENTER|END, weight: Float = 1f)`;
        `TableReportRow(cells: List<String>)` (células posicionais, texto já formatado pelo app — a lib não
        soma nada). `summary` é texto livre, **NUNCA "TOTAL R$"**.
      - `pdf/TableReportPdfGenerator.kt` (commonMain `expect` + helpers `generateTablePdfBytes`,
        `generateAndShareTablePdf` [integra `ShareHandler`], `defaultTablePdfFileName`). **Android**
        (`TableReportPdfGenerator.android.kt`): `PdfDocument` nativo — cabeçalho logo+empresa+título/
        subtítulo, linha de cabeçalho da tabela em **negrito** com fundo neutro, linhas com **zebra**/
        strokes leves usando **tokens neutros** (sem cor financeira), colunas ponderadas + alinhamento por
        coluna, **paginação automática** (repete o cabeçalho da tabela no topo de cada página), resumo +
        rodapé, marca d'água -45°. **Sem nenhum bloco monetário.** **iOS**
        (`TableReportPdfGenerator.ios.kt`): placeholder (`OsPdfNotSupportedException` — dívida conhecida,
        host macOS), igual aos demais geradores.
      - Testes: `TableReportPdfDataTest` (commonTest, 8 casos verdes: fileName/sanitização/extensão,
        equals+hashCode de company com/sem logoBytes, defaults seguros sem dinheiro, alinhamento/peso
        default da coluna, shape de relatório de frequência).
      - `:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL; `:kmplib:compileDebugUnitTestKotlinAndroid`
        BUILD SUCCESSFUL; `:kmplib:testDebugUnitTest --tests *TableReportPdfDataTest*` → 8/0/0 (verde);
        `:kmplib:publishToMavenLocal` BUILD SUCCESSFUL → `br.com.codecacto:kmplib:2.30.0` (`kmplib` metadata
        + `kmplib-android.aar`; klibs iOS pendentes de host macOS — commonMain puro, deve compilar em iOS
        sem mudança). **Nota de ambiente:** a build sofreu contenção com a entrega concorrente do
        offline-first (Onda 5/sync) — diretório renomeado mid-build, daemon morto e cache incremental
        corrompido; resolvido com `--no-configuration-cache` + `--gradlew --stop` + limpeza de
        `library/build/kotlin/compile*`. A suíte `testDebugUnitTest` completa tem falhas **pré-existentes
        e alheias** (não tocadas neste PR). **Consumo dev-mobile (ChamadaFacil):** substituir o
        `toOsPdfData()` por um `toTablePdfData()` no export do relatório de frequência (ver handoff).
- [ ] **GAP-CF-M-01 — `OnboardingPager` + indicador de páginas (dots) reutilizável** — Onboarding de
      1ª vez (proposta + aviso LGPD). **DUPLICA o gap G-M2 do Locador → 2 consumidores, promover.**
      Todo app novo da Casca tem onboarding de ~3 telas. Fallback: `HorizontalPager` + Row de dots local.
      Baixa (mas alta por reuso).
- [ ] **GAP-CF-M-02 — Header sticky de contagem (presentes/faltas), tokenizado** — tela Chamada
      (crítica): contador fixo no topo sempre visível, cor por estado (success/error via tokens).
      Fallback: Row fixada com `StatusBadge`/texto tokenizado. Média — avaliar se vira `CounterHeader`
      genérico ou fica local ao app.
- [ ] **GAP-CF-M-03 — `AttendanceRow` (linha de presença com toggle)** — tela Chamada: linha de aluno
      de alvo grande (≥56dp), toggle Presente↔Falta em 1 toque, estado distinto por **cor+ícone+texto**
      (acessível a daltônicos; falta = `errorContainer` + ✕ + "FALTOU"). Específico do padrão "chamada
      por inversão de default". Fallback: `Card`/Row clicável + `Avatar` + ícone + fundo por estado.
      Média. Sugestão: nascer LOCAL no app (Onda 1) e promover só se um 2º app pedir.

## Entregue (do origin/main — feedback/core/data)
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
- [x] **`DeveloperInfoService` → apps-api central** — entregue na **2.36.0** (última peça
      "Firestore-como-banco" da tela "Sobre o desenvolvedor"). O `DeveloperInfoService` deixou de ler
      do Firestore REST do projeto `code-cacto` (coleção `developer_apps` + doc `developer_info/contact`,
      **API key web hardcoded REMOVIDA**) e passou a fazer um **único GET PÚBLICO**
      `GET {appsApiBaseUrl}/public/developer` → `{ contact:{whatsapp,email,site},
      apps:[{id,name,description,logoUrl,storeUrl,order}] }` no backend central **apps-api**. Reusa
      `core/network` (`handleApiCall`/`ApiResult`) + Ktor `HttpClient` puro (sem ContentNegotiation),
      mesmo padrão de `FeedbackService`/`AppUpdateService`; resposta cacheada em memória (1 request por
      sessão da tela). Best-effort: rede/4xx/5xx/corpo inválido/não-inicializado → fallback (contato
      padrão + lista vazia), nunca lança/quebra a `DeveloperScreen`. **Mudança de config:** novo
      `DeveloperConfig(httpClient, appsApiBaseUrl?)` + `DeveloperInfoService.initialize(config)` no
      bootstrap, substituindo o antigo `configure(projectId, apiKey)`; **removido** o `expect/actual
      httpGet` (actuals Android/iOS deletados). API pública da tela inalterada (`fetchContact()`/
      `fetchApps()`, modelos `DeveloperContact`/`DeveloperApp`). 8 testes (`DeveloperInfoServiceTest`,
      MockEngine). **Consumidores a adotar no próximo build:** todo app que usa `DeveloperScreen`
      (Super 8, LocAki, Meu Advogado, Influencer, ...) — chamar `DeveloperInfoService.initialize(...)`
      no mesmo ponto do `FeedbackService.initialize(...)`. Sem inicializar, a tela cai no fallback.

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
- [x] **SQLDelight / offline-first sync** — **ENTREGUE na 2.30.0** (T1a — MeuFrete Onda 3). Novo módulo
      **GENÉRICO** `br.com.codecacto.kmplib.sync` sobre SQLDelight 2.0.2 (dialeto SQLite 3.38 c/ UPSERT;
      driver Android `AndroidSqliteDriver` + iOS `NativeSqliteDriver`). Agnóstico de domínio (não acopla
      Firestore nem MeuFrete): o app registra `SyncableEntity<T>` e implementa `SyncPort` sobre o ApiClient
      dele.
      - Schema (`commonMain/sqldelight/.../SyncEntity.sq`): espelho único `synced_entity(entity, local_id,
        server_id?, client_id, payload_json, updated_at?, dirty, pending_op?, deleted, base_updated_at?,
        last_error?, PK(entity, local_id))` + `sync_cursor(entity PK, cursor)` → gera `SyncDatabase`.
      - API: `SyncableEntity<T>`, `SyncPort`, `SyncEngine`(+`DefaultSyncEngine`), `SyncableRepository<T>`,
        `SyncStore`, `createSyncDatabase(name)` (expect/actual; `SyncDatabaseHolder` p/ Context Android,
        ligado no `KmpLib.init`/`initSync`). Estados `SyncState`/`SyncOpType`/`SyncItemStatus`/
        `SyncQueueItem`/`SyncResultSummary`.
      - **Reconciliador:** `startAutoSync(connectivity)` observa `ConnectivityObserver.isOnline` → dispara
        `syncNow` no offline→online com **backoff exponencial**; `syncNow` = push (drena outbox na ordem de
        registro/dependência) → pull (deltas/tombstones + cursor paginado). **LWW server-side** (cliente
        aplica `applied|conflict|rejected`, nunca decide vencedor); remapeia clientId→serverId.
      - **DTOs de fio (`SyncWire.kt`) — paridade EXATA com o backend** (compartilhar com backlib):
        `SyncPullRequest/SyncDelta/SyncPullResponse/SyncOp/SyncPushRequest/SyncResult/SyncPushResponse`
        (`payload = JsonElement` p/ reusar DTOs por-papel sem união de tipos); `SyncOpStatus`.
      - UI (T1b): `ui/components/SyncQueueView` + `SyncBanner` (G-MF-M-02, abaixo).
      - Testes: `SyncWireTest` (4) + `SyncModelsTest` (6) commonTest, verdes.
      - `:kmplib:compileDebugKotlinAndroid` + `:kmplib:publishToMavenLocal` BUILD SUCCESSFUL →
        `br.com.codecacto:kmplib:2.30.0` (`kmplib` metadata + `kmplib-android`; klibs iOS pendentes de host
        macOS — driver nativo iOS escrito, valida em Mac). **Dívida conhecida (iOS):** validar o
        `NativeSqliteDriver` em host macOS. Serve a apps offline-first (MeuFrete, Meu Fisio, Prospecta) e
        marketplace.
- [x] **UI de upload com progresso** — **ATENDIDO na 2.26.0** (via GAP-MO-M-06/07 do MinhaObra; 3º
      consumidor). Componente reutilizável sobre `StorageService.uploadBytesWithProgress`:
      `firebase/storage/UploadQueue` (fila sequencial + retry, `items: StateFlow<List<UploadItem>>`) +
      `ui/components/UploadProgressItem`/`UploadQueueView` (barra + % / check / erro+retry). **(GAP-06 /
      Exiba)** — Exiba pode consumir o mesmo componente para o upload da Onda 2. Preview de imagem do item:
      compor com `ImageGallery`/`AsyncImage`.

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
- [x] **GAP-ME-04 — `CameraView` com captura de FOTO (JPEG)** — entregue na 2.65.0 (Android; iOS
      placeholder honesto). Motivação: MeuEstacionamento RF-15 — o app precisava salvar a **foto do
      veículo** junto da placa lida, mas a `CameraView` só devolvia a placa (`onPlateCaptured`), então
      `fotoBytes` ficava sempre `null`. Padrão transversal (todo app que faz OCR de câmera também quer a
      foto do que leu). **Evolução ADITIVA/retrocompatível** (sobrecarga, não quebra `onPlateCaptured`):
        - Nova sobrecarga `@Composable expect fun CameraView(onCapture: (placa: String, jpegBytes:
          ByteArray) -> Unit, modifier = Modifier)` — no reconhecimento entrega placa **normalizada** +
          **JPEG** do frame (compat `image/jpeg` do `firebase/storage`/StorageProvider), de forma atômica.
          Sem ambiguidade com a antiga (resolução por arity do lambda: `(String)->Unit` vs
          `(String,ByteArray)->Unit`).
        - **Android (padrão-ouro CameraX):** pipeline compartilhado `Preview` + `ImageAnalysis` (OCR ML
          Kit, throttle 2s) + **`ImageCapture`**; ao detectar a placa, dispara
          `ImageCapture.takePicture` (CAPTURE_MODE_MINIMIZE_LATENCY, em memória), converte para **JPEG
          upright** (`imageProxyToUprightJpeg`: decode + `Matrix.postRotate(rotationDegrees)` + recompress
          q=85) e devolve via `onCapture`. A sobrecarga só-placa segue SEM bind de `ImageCapture` (nada
          muda). Best-effort: falha de captura → nenhuma foto, sem crash.
        - **iOS:** placeholder honesto para AMBAS as sobrecargas (Box + ícone, nunca chama o callback,
          nunca lança). **Pendência iOS:** `UIViewControllerRepresentable` com `AVCaptureSession`
          (`AVCaptureVideoDataOutput` p/ Vision + `AVCapturePhotoOutput` p/ o still →
          `fileDataRepresentation()`) em host macOS — mesma dívida do `PlateOcrAnalyzer.ios`.
        - **Arquivos:** `camera/CameraView.kt` (+overload), `camera/CameraView.android.kt` (refatorado
          p/ `CameraViewImpl` compartilhado), `camera/JpegCapture.android.kt` (novo helper),
          `camera/CameraView.ios.kt` (+overload placeholder).
        - **Migração do consumidor:** MeuEstacionamento troca `CameraView(onPlateCaptured = { placa ->
          FotoCapturada(placa, null) })` por `CameraView(onCapture = { placa, bytes ->
          FotoCapturada(placa, bytes) })`.
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
- [x] **GAP-SAL-07 — Expor `LocalIsCompact` (CompositionLocal de breakpoint)** — **entregue na 2.23.0.**
      `ui/theme/Responsive.kt`: `LocalIsCompact` (`compositionLocalOf`, default `true`/compacto),
      `ProvideIsCompact(threshold = CompactWidthThreshold, content)` (mede largura via `BoxWithConstraints`
      puro de foundation — sem dependência `material3-window-size-class`, funciona em Android/iOS/Desktop),
      `CompactWidthThreshold = 600.dp` e helper puro `gridColumns(isCompact, compact, expanded)`. Promove os
      contornos locais de Salmos (`core/ui/Responsive.kt`), Emprestei e StatusHub (`core/util/ScreenSize.kt`).
      **3+ consumidores confirmados.** Migração: trocar imports locais por `br.com.codecacto.kmplib.ui.theme.*`
      e remover o arquivo local (próxima passada, com dev-mobile).

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
- [x] **GAP-MOS-M-01 / GAP-EMP-M-01 — `AppFab` (FloatingActionButton themável)** — **entregue na 2.23.0.**
      `ui/components/AppFab.kt`: FAB padronizado pelo tema (cores default `primary`/`onPrimary`, overrideáveis),
      `icon` + `contentDescription` (a11y), `label`/`extended` para a variante estendida (ícone+texto) e
      `enabled` (esmaece + suprime clique). Promove os contornos locais do Emprestei (`core/ui/AppFab.kt`) e do
      MinhaOS/MOS. **2+ consumidores confirmados.** Migração: trocar o `AppFab` local por
      `br.com.codecacto.kmplib.ui.components.AppFab` (próxima passada, com dev-mobile). Telas: A5 (Home), A6.

> **Reuso de gaps já no backlog (NÃO recriar) — citados no design do MinhaOS:**
> **GAP-CR-04 (`PaywallScreen`)** → A16 Paywall — modelo freemium **definido** (5 OS/mês + marca d'água;
> Pro R$24,90/mês · R$249/ano), **2º+ consumidor confirmado, reforça promoção**;
> **GAP-CR-05 (`SettingsRow`/`ListItem`)** → A15 Perfil/Configurações — +1 consumidor;
> **GAP-SAL-05 / GAP-RF-M-04 (`OnboardingPager`)** → A2 Onboarding — +1 consumidor (contorno: `HorizontalPager`);
> **GAP-09 (single-select em `AppMultiSelect`)** → A14 unidade do serviço (contorno: `SegmentedControl`);
> **GAP-11 (`ClipboardHandler`)** → A9 copiar link público (contorno: `ShareHandler`);
> **UI de upload com progresso** (prioridade alta/média) → A4/A6 upload do logo/fotos (contorno: `LoadingOverlay`).

### QuemMeDeve — gaps reportados no fechamento da Onda 3 (product-owner, 2026-06-13)

> Origem: `QuemMeDeve/docs/roadmap.md` (dívidas da Onda 3 — compartilhar resumo amigável + PDF).
> App KMP Android/iOS offline-first (Arq. B) sobre Firestore. A Onda 3 reusou o `FinanceReportPdfGenerator`
> da kmplib (2.21.0) via mapper para `FinanceReportPdfData` e o `ShareHandler`/`UrlLauncher` p/ compartilhar.
> Dois gaps detectados — ambos **Baixa**, contornados no app hoje.

- [ ] **GAP-QMD-M-04 — Template de PDF "resumo de cobrança / extrato do devedor" + render iOS do
      `FinanceReport`** — Baixa. (a) Hoje o QuemMeDeve **mapeia** o resumo do devedor para
      `FinanceReportPdfData` (relatório financeiro) por falta de um template dedicado de **"extrato do
      devedor / resumo de cobrança"** (devedor + parcelas em aberto + total a receber, tom não
      intimidatório). Candidato a template próprio de PDF na kmplib (par mobile/web). (b) O
      `FinanceReportPdfGenerator` **só tem render nativo no Android** (iOS = placeholder/fallback de texto,
      herda o item de prioridade alta "publicar artefatos iOS / render PDF iOS em host macOS") — por isso o
      app **cai em fallback de texto no iOS** ao gerar PDF. **Contorno atual no app:** mapear para
      `FinanceReportPdfData` + fallback iOS → compartilhar texto. Confirmar 2º consumidor antes de promover
      o template dedicado.
- [ ] **GAP-QMD-M-05 — `PhoneFormatters` normalizar p/ DDI/WhatsApp (E.164)** — Baixa. Hoje a kmplib formata
      telefone BR (`PhoneMask`/`PhoneFormatters`) mas **não normaliza para E.164** (DDI + DDD, sem
      separadores) exigido pelo deep link do WhatsApp. O QuemMeDeve implementou `normalizeBrPhone` **no app**
      (`ShareSummaryViewModel`) como contorno. Candidato a item de lib: helper puro
      `normalizeToE164(raw, defaultCountryCode = "55"): String?` em `core/format`. Serve a qualquer app que
      compartilhe via WhatsApp (≥2 apps prováveis). **Contorno atual:** função local no app.

### MinhaObra — gaps reportados pelo ux-designer (2026-06-13)

> Origem: `MinhaObra/docs/design/wireframes.md` (§ Gaps de lib) + `flows.md`. App KMP Android/iOS + web
> (paridade) sobre Firebase (Auth+Firestore+Storage), Arq. A standalone (sem backend Ktor no MVP).
> 2 papéis: GESTOR (cria/edita) × CLIENTE (read-only). 4 módulos: obras→etapas (% progresso), registro
> fotográfico datado, diário de obra, relatório PDF + link/portal do cliente. Freemium: Free 1 obra ativa
> + PDF com marca d'água; Pro destrava (entitlement server-side via admin-api). **Foco de canteiro:** toques
> grandes, alto contraste, upload resiliente a rede ruim. Reusa AMPLAMENTE a kmplib: `LoginScreen`/
> `RegisterScreen` 2.0, `AuthRepository`(+`IAuthRepository`), `FirestoreService` (+`runTransaction`),
> `StorageService` (+`deletePrefix` LGPD), `AppTopBar`/`BackTopBar`/`AppBottomNavBar`, `AppTextField`/
> `AppTextArea`/`FormContainer`/`NumberField`, `Card`/`Avatar`/`StatusBadge`/`EmptyState`/`SkeletonBox`/
> `LoadingOverlay`/`OfflineBanner`/`ConfirmationDialog`/`ErrorModal`/`Toast`, `ImagePicker`/`FilePicker`/
> `FullScreenImageViewer`/`ZoomableBox`, `AppDatePicker`/`SegmentedControl`/`FilterChipRow`/`AppMultiSelect`/
> `AppCheckbox`/`AppSwitch`/`AppBottomSheet`/`ThemeChipGrid`, `EmailValidator`/`PhoneMask`,
> `BrazilianStates`/`BrazilianCities`, `ShareHandler`/`UrlLauncher`/`BitmapEncoder`, `ReaderView` (termos),
> **`monetization/entitlement` + `PaywallScreen` + `UsageMeter`/`UsageBadge` (2.22.0 — Onda 5 SEM gaps)**.

- [x] **GAP-MO-M-09 — Template de PDF de ACOMPANHAMENTO DE OBRA (marca d'água Free condicional)** —
      **ENTREGUE (preparação adiantada Onda 2) na 2.26.0 (Android render; iOS placeholder).** Novo par no
      módulo `pdf/`: modelo comum serializável **`WorkReportPdfData`** (`pdf/WorkReportPdfData.kt`) +
      `interface WorkReportPdfGenerator`, `createWorkReportPdfGenerator()` (expect/actual),
      `generateWorkReportPdfBytes(data)`, `generateAndShareWorkReportPdf(...)` (reusa `ShareHandler`),
      `defaultWorkReportPdfFileName(workName)` (`pdf/WorkReportPdfGenerator.kt`). **Shape do C-5 (espelhar na
      weblib GAP-MO-W-09 — manter IDÊNTICO):** `company: WorkReportPdfCompany(name, phone?, logoBytes?)` (logo
      só Pro); `workName`; `address?`; `periodLabel?`; `generatedAtLabel`; `overallProgress: Float (0..1)`;
      `stages: List<WorkReportStage(name, statusLabel, progress: Float)>`; `photos: List<WorkReportPhoto(
      imageBytes: ByteArray, caption?, takenAtLabel?)>` (**bytes, não URL** — a lib não baixa; carimbo DP-7
      resolvido no app e passado como `takenAtLabel`); `diaryEntries: List<WorkReportDiary(dateLabel,
      weatherLabel?, crewLabel?, notes)>`; `watermark: Boolean` + `watermarkText?` (regra de plano decidida no
      app). **Android:** render nativo `PdfDocument` (A4, cabeçalho marca+período, bloco da obra com barra de
      progresso geral, seção Etapas com barra/%, grade 2-col de fotos com center-crop + legenda + carimbo,
      blocos de diário com wrap, paginação automática, marca d'água -45° por página quando `watermark=true`).
      **iOS:** placeholder (`OsPdfNotSupportedException`; TODO `UIGraphicsPDFRenderer` em host macOS). Testes
      `WorkReportPdfDataTest` (5 casos commonTest, verdes). **Decisão DP-1: PDF client-side no MVP.**
      **PENDENTE p/ Onda 4:** (1) fechar/validar o shape com o lib-web (C-5) — `WorkReportPhoto` usa
      `imageBytes`; se a weblib preferir URL, alinhar; (2) render iOS em host macOS; (3) o app monta o
      `WorkReportPdfData` a partir do domínio MinhaObra.
- [x] **GAP-MO-M-02 — `AppProgressBar` (barra de progresso linear themável)** — **ATENDIDO em 2.25.0.**
      `ui/components/AppProgressBar.kt` (commonMain). API: `AppProgressBar(progress: Float /* 0f..1f */, ...)`
      + overload `AppProgressBar(percent: Int /* 0..100 */, ...)`; `tone: ProgressTone`
      (Primary/Success/Warning/Error/Info → tokens do tema), `height: Dp = 8.dp`, `showLabel`, `label`,
      overrides `color`/`trackColor`. Cores via `MaterialTheme.colorScheme` + `AppColors.current` (sem
      hardcode); a11y via `ProgressBarRangeInfo`. Clamp defensivo (`normalizeProgress`/`percentToFraction`,
      testados em `AppProgressBarTest`). Usado no progresso geral da obra (dashboard 2.1), por etapa (2.2)
      e nas listas de obras (1.1/1.2).
- [x] **GAP-MO-M-05 — `ImageGallery` (grid de imagens com lazy load + estado por item)** —
      **ENTREGUE (preparação adiantada Onda 2) na 2.26.0.** `ui/components/ImageGallery.kt` (commonMain):
      `ImageGallery(items: List<GalleryItem>, onItemClick: (id) -> Unit, multiSelect: Boolean = false,
      selectedIds: Set<String> = emptySet(), columns: Int = 3, spacing, contentPadding)` — `LazyVerticalGrid`
      + `AsyncImage` (Coil, já dependência da lib) com center-crop. Tipo `GalleryItem(id, model: Any?, status)`
      e enum `GalleryItemStatus` (NONE/UPLOADING/FAILED/UPLOADED) → overlay por item (spinner / ícone de erro /
      check de sucesso). **Modo navegação** (default) dispara `onItemClick`; **modo seleção** (`multiSelect`)
      alterna seleção com overlay de acento + check (o app mantém o `Set` e atualiza no callback) — atende o
      seletor de fotos do relatório (3.2/5.1, fecha GAP-MO-M-08). Cores 100% via tema. **PENDENTE p/ Onda 3:**
      agrupamento por data (o app passa seções ou a lib ganha overload futuro); render visual iOS (commonMain
      puro + Coil, deve compilar sem mudança).
- [x] **GAP-MO-M-06/07 — UI de upload com progresso/fila + compressão de imagem no cliente** —
      **ENTREGUE (preparação adiantada Onda 2) na 2.26.0.** (a) **Fila de upload com progresso/retry:**
      `firebase/storage/UploadQueue.kt` (commonMain) — classe `UploadQueue(storageService)` que reusa
      `StorageService.uploadBytesWithProgress`, processa **sequencialmente** (resiliente a rede de canteiro,
      NFR do PRD), expõe `items: StateFlow<List<UploadItem>>` e oferece `enqueue`/`enqueueAll`/`process()`/
      `retry(id)`/`remove(id)`. Modelo `UploadItem(id, fileName, fraction, status, downloadUrl?, errorMessage?)`
      (derivados `percent`/`isTerminal`/`isFailed`/`isUploading`), enum `UploadStatus`
      (PENDING/UPLOADING/COMPLETED/FAILED) e `UploadRequest(id, fileName, path, bytes, mimeType?)`. UI:
      `ui/components/UploadProgressItem.kt` — `UploadProgressItem(item, onRetry?)` (barra + % / check verde /
      erro+retry) e `UploadQueueView(items, onRetry?)` (lista vertical). (b) **Compressão de imagem:**
      `platform/ImageCompressor.kt` (expect/actual) — `ImageCompressor.compress(bytes, maxDimension=1600,
      quality=80, format=JPEG): ByteArray` + `createImageCompressor()`, enum `ImageCompressFormat` (JPEG/PNG).
      **Android:** `Bitmap`/`BitmapFactory` (scale-down + recompress). **iOS:** **implementado de fato** via
      Skia (`org.jetbrains.skia`, mesmo motor do `BitmapEncoder` — decode → resize linear → encode), validar
      qualidade em host macOS. Best-effort: entrada indecodificável volta como veio (nunca lança). Testes
      `UploadItemTest` (4 casos commonTest, verdes). Fecha também a lacuna geral **"UI de upload com progresso"
      / GAP-06** (3º consumidor — promoção confirmada). **PENDENTE p/ Onda 3:** depende do contrato C-4
      (Storage/paths) para o app cablar `path`/`bytes`; cancelamento de upload em curso não exposto (limitação
      do GitLive 2.1.0 — `uploadBytesWithProgress` sem progresso intermediário real até GitLive evoluir).
- [ ] **GAP-MO-M-10 — `QRCode` no app (mostrar QR do portal do cliente)** — Baixa · Onda 4. A weblib já tem
      `QRCode`; a kmplib não. Útil para o gestor mostrar o QR do link do portal (5.3). Sugestão:
      `QRCode(content: String, size: Dp, logo: ImageBitmap? = null)` (commonMain via lib QR multiplataforma
      ou render próprio). Candidato a `ui/components`. Contorno no MVP: compartilhar só o link via `ShareHandler`.
- [ ] **GAP-MO-M-03 — Lista reordenável (drag-to-reorder) para etapas** — Média · Onda 2.
      **= GAP-10 (Exiba)** já no backlog (Prioridade baixa) — 2º consumidor confirmado (reordenar etapas
      da obra, tela 2.2). Reforça promoção. Contorno no MVP: campo "ordem" (`NumberField`) + reordenar lógico.
- [ ] **GAP-MO-M-04 — `AppSlider` (0–100%) para % de progresso da etapa** — Baixa · Onda 2.
      Slider themável para a tela 2.3. Sugestão: `AppSlider(value, valueRange, onValueChange, steps?)` sobre
      o `Slider` do Material 3. Contorno no MVP: `NumberField` 0–100 (não trava a onda).

> **Reuso de gaps já no backlog (NÃO recriar) — citados no design do MinhaObra:**
> **Deep-link router** (Prioridade média) → GAP-MO-M-01: abrir convite `/convite/[token]` no app (telas 0.6/2.4);
> **UI de upload com progresso** (Prioridade alta / GAP-06) → GAP-MO-M-06: +1 consumidor;
> **GAP-10 (lista reordenável)** → GAP-MO-M-03: +1 consumidor;
> **GAP-CR-05 (`SettingsRow`/`ListItem`)** → Configurações (7.3) — +1 consumidor.
> **Onda 5 (monetização) SEM gaps:** `monetization/entitlement` + `PaywallScreen` + `UsageMeter`/`UsageBadge`
> (2.22.0) cobrem Paywall (6.1)/Status (6.3); `PurchaseManager`/RevenueCat cobre o checkout (6.2).

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
- [~] **i18n (strings parametrizadas)** — **NÃO É GAP DE LIB (auditoria 2026-06-27).** O mecanismo
      OFICIAL **Compose Multiplatform Resources** (`org.jetbrains.compose.resources` →
      `stringResource(Res.string.x, arg1, arg2)` + `composeResources/values-*/strings.xml`) já cobre i18n
      **com parametrização** e está EM PRODUÇÃO no Super 8 (pt/en/es: `values-en`, `values-es`,
      `LocalizedEnums.kt`, `ValidationError.kt` usa `stringResource(..., name)`). As telas da própria lib
      já recebem os textos via `*Texts` injetados pelo app (`PaywallTexts`/`FeedbackTexts`/`DeveloperTexts`/
      `ContactTexts`). Construir um módulo i18n próprio na kmplib **reinventaria o framework oficial** e
      violaria o padrão-ouro (além de não conseguir compartilhar o `Res` gerado por-módulo). Recomendação
      para apps multi-idioma: usar Compose Resources direto. Item mantido só p/ registro; **nada a
      implementar na lib**.
- [~] **Deep-link router** — **SEM CONSUMIDOR REAL (auditoria 2026-06-27); especulativo.** Grep nos 4
      apps ativos (Super 8 / Meu Advogado / LocAki / Influencer): nenhum tem handling de deep link — só os
      intent-filters padrão `MAIN`/`LAUNCHER` no manifest; o `ACTION_VIEW` do LocAki é apenas abrir URL no
      browser (equivalente a `UrlLauncher`). A demanda só aparece **antecipada** no design do MinhaObra
      (GAP-MO-M-01: abrir convite `/convite/[token]`), que não é app ativo. Pela regra "não inventar feature
      sem consumidor", NÃO implementar agora. Reabrir quando ≥1 app real precisar (aí projetar
      `DeepLinkRouter` parser commonMain + `onNewIntent`/`UIApplicationDelegate` expect/actual).
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
- [x] **2.26.0 — Preparação adiantada das Ondas 3/4 do MinhaObra (galeria/upload/compressão + PDF de obra).**
      Entregue durante a Onda 2 (sem bloquear) para reduzir risco de cronograma. **(a) Par PDF de obra
      (GAP-MO-M-09, prioridade máxima):** novo template no módulo `pdf/` — `WorkReportPdfData` (shape do C-5,
      espelhado na weblib GAP-MO-W-09) + `WorkReportPdfGenerator` (expect/actual; Android render `PdfDocument`
      com barra de progresso/etapas/grade de fotos/diário/marca d'água; iOS placeholder), atalhos
      `generateWorkReportPdfBytes`/`generateAndShareWorkReportPdf`/`defaultWorkReportPdfFileName`. **(b)
      Galeria (GAP-MO-M-05):** `ui/components/ImageGallery` (`GalleryItem`/`GalleryItemStatus`, multiSelect,
      Coil). **(c) Upload (GAP-MO-M-06):** `firebase/storage/UploadQueue` (fila sequencial + retry,
      `UploadItem`/`UploadStatus`/`UploadRequest`) + `ui/components/UploadProgressItem`/`UploadQueueView` —
      fecha também GAP-06/Exiba "UI de upload". **(d) Compressão (GAP-MO-M-07):** `platform/ImageCompressor`
      (expect/actual; Android Bitmap, iOS Skia real). Tudo **aditivo** (não quebra API). Testes
      `WorkReportPdfDataTest` (5) + `UploadItemTest` (4) verdes; `:kmplib:compileDebugKotlinAndroid` e
      `:kmplib:publishToMavenLocal` BUILD SUCCESSFUL → `br.com.codecacto:kmplib:2.26.0` (`kmplib` metadata +
      `kmplib-android`; klibs iOS pendentes de host macOS). **Pendências p/ Ondas 3/4:** fechar shape C-5
      (imageBytes vs URL) com lib-web; contrato C-4 Storage; render iOS dos PDFs; app monta os modelos.
- [x] **2.24.1 — Fix de seguranca: `EntitlementDto.toModel()` ignorava `active`/`status` (autopromocao).**
      Achado na verificacao cross-component da Onda 2R (Emprestei). O `toModel` derivava `planCode` so do
      `plan?.code`; como o admin-api devolve `plan` mesmo para entitlement EXPIRED/CANCELED, um direito
      inativo com `plan.code = premium_*` era mapeado como premium (`isFree=false`) => acesso ilimitado sem
      direito vigente. Correcao: o plano pago so vale quando `active || status == "ACTIVE"`; caso contrario
      rebaixa para `free` sem features (espelha a weblib `premium = active && !isFreePlan`). Modelo
      `Entitlement` ganhou `isPremium` (= nao-free) e `isFree` passou a tratar `plano` vazio como free.
      Testes novos em `EntitlementModelTest` cobrem activePremium→premium, inativo(EXPIRED/CANCELED)→Free,
      statusACTIVE→premium, sem plano→Free. Suite do modulo verde; Android compila; publicado em mavenLocal.
- [x] **2.24.0 — `monetization/entitlement` alinhado ao contrato canonico `/monet` do admin-api + `assertUsage`
      (GAP-EMP-M-02).** Correcao reportada pelo code-review do Emprestei: o modulo falava rotas `/v1/{slug}/...`
      e esperava payload direto em PT — quebrado contra o admin-api real. Agora fala o **contrato de fio
      canonico** (`AdminUnificado/admin-api/docs/03-monetizacao-contrato.md`):
        - **Rotas:** `AdminApiEntitlementRepository` migrou de `/v1/{slug}/entitlement|usage/{feature}|plans`
          para **`GET /monet/{slug}/{entitlement,usage?feature=,plans}`** + **`POST /monet/{slug}/assert`**.
        - **Envelope `{ ok, data }`:** desembrulha `data` antes de mapear (helper `unwrap`).
        - **Mapeamento EN→PT na camada de rede** (decisao: modelos PT mantidos, sem churn nos consumidores):
          DTOs de fio internos `EntitlementDto`/`UsageDto`/`PlanDto` (`active`/`plan`/`status`/`source`/
          `validUntil`; `count`/`limit`/`remaining`/`unlimited`; `code`/`name`/`price`/`limits`) mapeados para
          `Entitlement`/`UsageSnapshot`/`Plan`.
        - **Auth:** envia o **Firebase ID token do usuario** em `Authorization: Bearer <idToken>` (passar
          `authToken = { auth.getIdToken().getOrNull() }` — `IAuthRepository.getIdToken` ja existe). **Removido
          o `?tenant=`** (servidor deriva o tenant do uid; divergente → 403).
        - **`assertUsage(feature, currentCount, amount=1): AssertResult` (novo, GAP-EMP-M-02):** `POST .../assert`
          body `{feature, currentCount, amount}` → `AssertResult.Allowed` (200) | `Denied(QuotaExceeded)` (402, de
          `error.details`) | `Failed(code, message)` (rede/HTTP). Classifica por **status** (robusto a
          `expectSuccess` on/off). No `EntitlementController`: `assertUsage(...)` (cru) e
          `assertUsageInto(state, ...): Pair<EntitlementState, Boolean>` (abre paywall no Denied / seta error no
          Failed). **Centraliza o `QuotaAssertClient` que cada app freemium recriava.**
        - **`parseQuotaExceeded`/`quotaExceededOrNull` corrigidos:** leem de `error.details` do envelope e
          convertem `limite`/`contagem` **string→int** (BigDecimal serializado); mantem retrocompat com payload
          direto. Modelos `QuotaExceeded`/`Entitlement`/`UsageSnapshot`/`Plan` **inalterados** (compativel).
      Testes `monetization/entitlement/*`: +6 casos (`assertUsage`/`assertUsageInto` Allowed/Denied/Failed,
      envelope canonico com strings + numerico) — **verdes**. `:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL;
      `:kmplib:testDebugUnitTest --tests "...entitlement.*"` SUCCESSFUL; `:kmplib:publishToMavenLocal` BUILD
      SUCCESSFUL → `br.com.codecacto:kmplib:2.24.0` (`kmplib` metadata + `kmplib-android`; targets iOS pendentes de
      host macOS — codigo e commonMain puro). **Migracao Emprestei:** remover o `QuotaAssertClient` local e usar
      `EntitlementRepository.assertUsage`/`EntitlementController.assertUsageInto`; conferir que o `authToken`
      injeta o Firebase ID token e que nao envia `tenantId`.
- [x] **2.22.0 — Padrao freemium-com-limite (doc 03 §4): `monetization/entitlement` + UsageMeter + PaywallScreen.**
      Materializa o Pilar 3 de Monetizacao no mobile, **reusando** `PurchaseManager`/RevenueCat (compra) e
      `core/network` (`ApiResult`/`handleApiCall`) — **NAO recriou billing**. Quota e **server-side** (fonte de
      verdade = `admin-api`/`backlib-quota`); o cliente so EXIBE "X de Y" e abre paywall, nunca decide/incrementa.
      Novo submodulo `monetization/entitlement` (commonMain puro):
        - **Modelos serializaveis** espelhando o contrato do admin-api: `Entitlement` (`plano`/`features:
          Set<String>`/`validoAte`/`fonte`; `hasFeature`/`isFree`/`FREE`), `UsageSnapshot` (`feature`/`contagem`/
          `limite` [-1=ilimitado]/`restante?`/`janelaFim?`; derivados `remaining`/`isExhausted`/`fraction`/
          `isUnlimited`), `Plan` (`plano`/`nome`/`preco: String?` decimal canonico — NUNCA Double/`moeda`/
          `intervalo`/`storeProductId?`/`destaques`).
        - **402 → Paywall:** `QuotaExceeded(feature, limite, contagem, upgradeUrl?)` +
          `ResponseException.quotaExceededOrNull()` (extrai do 402/429 do Ktor) + `parseQuotaExceeded(body)` (corpo
          bruto) + `toUsageSnapshot()`.
        - **Leitura (fonte de verdade):** `interface EntitlementRepository` (`getEntitlement`/`getUsage`/`getPlans`
          → `ApiResult`) + impl `AdminApiEntitlementRepository(httpClient, baseUrl, projectSlug, authToken)` (Ktor
          core puro + kotlinx-json, **sem ContentNegotiation** — nao forca config no HttpClient do app; rotas
          `/v1/{slug}/entitlement|usage/{feature}|plans`). So LE — cliente nunca se autopromove.
        - **MVI:** `EntitlementState` (embute no State da tela: `entitlement`+`isPremium`+`usage`+`paywall`;
          `hasFeature`=premium OU entitlement; `withUsage`/`showingPaywall`/`dismissingPaywall`) +
          `EntitlementController(repository)` (reducer: `refresh`/`refreshUsage`/`plans(cache)`/`purchase(plan)` via
          RevenueCat/`restore`).
        - **Offline/UX:** `LocalUsageCounter(prefs, projectSlug)` (contagem local via `AppPreferences` — so UX
          otimista, NUNCA gate de negocio).
        - **UI:** `ui/components/UsageMeter(usage, label?, warnThreshold)` + `UsageBadge(usage)` (tema via
          `MaterialTheme`/AppTheme, barra vira cor de erro perto do limite, sem hardcode) +
          `ui/screens/paywall/PaywallScreen(state, onAction, texts)` stateless (padrao telas 2.0) com
          `PaywallState`/`PaywallAction`(SelectPlan/Restore/Dismiss)/`PaywallTexts` — cards de plano (preco BRL,
          destaques) + CTA `AppButton` que dispara compra. Sem cores hardcoded.
      Testes `monetization/entitlement/*` (22 commonTest: `EntitlementModelTest` 12, `EntitlementStateTest` 5,
      `EntitlementControllerTest` 5 com fake repository) — **todos verdes**.
      `:kmplib:compileCommonMainKotlinMetadata` BUILD SUCCESSFUL; `:kmplib:compileDebugKotlinAndroid` BUILD SUCCESSFUL;
      `:kmplib:testDebugUnitTest --tests "...entitlement.*"` **22/22 verdes**; `:kmplib:publishToMavenLocal` BUILD
      SUCCESSFUL → `br.com.codecacto:kmplib:2.22.0` (`kmplib` metadata + `kmplib-android`; targets iOS SKIPPED em
      Windows — todo o codigo novo e **commonMain puro**, compila em iOS sem mudanca; klibs iOS pendentes de host
      macOS, herdam item de prioridade alta). **Fecha GAP-CR-04 (`PaywallScreen`)** e atende os reusos previstos por
      ReciboFacil (Onda 4) e MinhaOS (A16). **Consumo nos apps:** injetar `AdminApiEntitlementRepository` +
      `EntitlementController` (Koin); ViewModel guarda `EntitlementState` no State; na rota consumivel, ao receber
      `402` chamar `quotaExceededOrNull()` → `state.copy(ent = ent.showingPaywall(quota))`; renderizar `PaywallScreen`
      quando `ent.isPaywallOpen`; `SelectPlan` → `controller.purchase(plan)` → apos webhook, `controller.refresh(ent)`.
- [x] **2.33.0 — RECONCILIAÇÃO de merge (origin/main ↔ local).** As duas pontas evoluíram o módulo
      `monetization/entitlement` em paralelo a partir da 2.22.0. Resolução (regra UNIÃO): adotado o
      **contrato canônico local** (envelope `{ok,data}`, DTOs EN→PT, `assertUsage`/`AssertResult`/
      `assertUsageInto`, `Entitlement.isPremium`, segurança `EntitlementDto.toModel()` rebaixa inativo →
      free, `UsageSnapshot`/`Plan` Int) por ser o mais completo e o documentado no `kmplib-catalog`. **Do
      origin/main foram preservados:** o **cache curto em memória (TTL 60s)** + `invalidateCache()` (portados
      para o `AdminApiEntitlementRepository` canônico) e, integralmente e sem conflito, **`core/data`
      (`RestRepository` online-first)** e o **feedback identificado 2.25.0** (campos `name/email/whatsapp`).
      **Não re-adicionado (dívida consciente p/ não causar churn de contrato):** o modo `/me`
      (`pathPrefix`/`forUserAuth`) do origin/main 2.22.0 e os testes do contrato antigo
      (`AdminApiEntitlementRepositoryTest`/`QuotaExceededTest`/`UsageSnapshotTest`, que assumiam o shape
      sem-envelope/Long) — removidos por incompatibilidade com o contrato canônico. Se algum consumidor
      depender do `forUserAuth`/`pathPrefix`, reabrir item para re-portar sobre o contrato canônico.
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
