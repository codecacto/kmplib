# Changelog — kmplib

Histórico de versões. Fonte de verdade viva da superfície de APIs = skill `kmplib-catalog`;
breaking curados = `BREAKING_CHANGES.md`; decisões = `docs/adr/`.

> Nota: este arquivo foi (re)criado na 2.78.0 (auditoria — não havia `CHANGELOG.md` de raiz; a
> história pré-2.78 está no catálogo por versão e no `docs/legacy/CHANGELOG_UI_COMPONENTS.md`).

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
