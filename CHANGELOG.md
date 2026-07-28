# Changelog — kmplib

Histórico de versões. Fonte de verdade viva da superfície de APIs = skill `kmplib-catalog`;
breaking curados = `BREAKING_CHANGES.md`; decisões = `docs/adr/`.

> Nota: este arquivo foi (re)criado na 2.78.0 (auditoria — não havia `CHANGELOG.md` de raiz; a
> história pré-2.78 está no catálogo por versão e no `docs/legacy/CHANGELOG_UI_COMPONENTS.md`).

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
`OfflineFirstRestWriteTest` 15 → **22**: o fluxo do motorista ponta a ponta (offline → toque →
reconexão: **um** POST, `clientId → serverId` remapeado, linha `Synced`), vários updates antes de
qualquer sync → **um** POST com o payload final, `delete` sobre `CREATE` pendente sem ida à rede,
cura de linha legada, e as duas **regressões** que garantem que linha já sincronizada continua indo
de `PUT`/`DELETE` ao servidor. Suíte: **1.483 testes, 0 falhas**; `koverVerify` verde.

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
