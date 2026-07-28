# Changelog — kmplib

Histórico de versões. Fonte de verdade viva da superfície de APIs = skill `kmplib-catalog`;
breaking curados = `BREAKING_CHANGES.md`; decisões = `docs/adr/`.

> Nota: este arquivo foi (re)criado na 2.78.0 (auditoria — não havia `CHANGELOG.md` de raiz; a
> história pré-2.78 está no catálogo por versão e no `docs/legacy/CHANGELOG_UI_COMPONENTS.md`).

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
