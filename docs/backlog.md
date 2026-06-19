# Backlog de evolução — kmplib

> Dono: lib-mobile. Itens para fazer a kmplib crescer. Priorizar o que serve a ≥2 apps.
> Processo: skill `lib-evolution`. Detecção em massa: comando `/lib-audit`.

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
