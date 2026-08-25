# Changelog — kmplib

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
