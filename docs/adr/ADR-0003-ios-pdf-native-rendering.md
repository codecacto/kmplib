# ADR-0003 — Render de PDF no iOS com CoreText/UIGraphicsPDFRenderer (nativo, sem workaround)

- **Status:** Aceito · parcialmente implementado. Recibo (`OsPdfGenerator.ios`, `ReciboPdf.ios`) **real**
  desde a 2.77.0; os 7 geradores multi-página seguem stub (ver §Sequenciamento).
- **Data:** jul/2026 (projeto Arroba Certa; documentado retroativamente).
- **Referenciado por:** `IosPdfRenderer.ios.kt` (helper `IosPdfCanvas`/`renderIosPdf`),
  `OsPdfGenerator.ios.kt`, dívida `kmplib-ios-pdf-stub-debt`.

## Contexto

Os geradores de PDF da lib (`OsPdfData`, `ReciboPdfData`, `DocumentPdfData`, relatórios financeiro/horas/
obra/vistoria/vacinação, tabela) têm render Android nativo via `android.graphics.pdf.PdfDocument`. No
iOS, **todos** eram stubs que lançavam `OsPdfNotSupportedException` — travando features (algumas vendidas
como **Pro**) em produção iOS. O Arroba Certa precisa emitir **recibo** (fazenda/comprador/discriminação
de arroba) no iPhone.

A tentação era o atalho: renderizar HTML→imagem num `WKWebView`, ou marcar "Android-only". Isso viola o
**padrão-ouro** (usar a API oficial da plataforma). Complicador técnico real: as categorias de desenho de
texto do UIKit (`NSString.drawAtPoint`, `sizeWithAttributes`) **não são exportadas** no Kotlin/Native 2.x.

## Decisão

Renderizar com a **API oficial da Apple**, exportada no K/N: **CoreText** (`CTLine` +
`NSAttributedString` com os atributos `"NSFont"`/`"CTForegroundColor"`) desenhando dentro de um
`UIGraphicsPDFRenderer`/`CGContext`. Encapsular tudo num helper compartilhado **`IosPdfCanvas`**
(`IosPdfRenderer.ios.kt`) que espelha a superfície do `Canvas`/`Paint` do Android:

- `text(text, x, baselineY, size, bold, color, align)` — baseline nas **mesmas coordenadas** do Android
  (origem topo-esquerda), com a receita canônica de flip (`translate` até a baseline + `scale(1,-1)`)
  para o CoreText desenhar upright num contexto UIKit.
- `measure`/`ascent`/`truncate`/`wrappedText`, `line`/`fillRect`/`fillRoundRect`/`image`,
  `save`/`restore`/`translate`/`rotate`.
- `PdfColor.argb(0xAARRGGBB)` — converte o **mesmo literal ARGB** usado no Android → paridade de cor exata.
- `renderIosPdf(w, h) { }` — PDF de **uma** página.

Assim, cada gerador iOS usa as MESMAS coordenadas/cores do seu par Android (paridade Android=iOS).

## Sequenciamento (recibo primeiro) e exceção registrada

- **Feito (2.77.0):** `OsPdfGenerator.ios` e `ReciboPdf.ios` — os únicos consumidos pela Onda 1 do
  Arroba Certa. São de **página única** → encaixam direto no `renderIosPdf`.
- **Pendente (dívida remanescente):** os 7 geradores **multi-página** (`Document`, `FinanceReport`,
  `HoursReport`, `Inspection`, `TableReport`, `VaccinationCard`, `WorkReport`) exigem **paginação**
  (o `renderIosPdf` de página única não basta). A extensão para N páginas
  (`renderIosPdfPaged` + um `IosPageFlow` com `ensureSpace`/`newPage`, espelhando o `RenderCtx` do
  Android) e o porte de cada layout são feitos **espelhando o par Android**, mas a validação visual
  **exige host macOS** (o build Linux pula os alvos Apple). Portar sem compilar/validar em iOS é código
  cego — por isso o porte entra como aditivo, marcado como **pendente de validação em Mac**.

## Alternativas descartadas

- **HTML → imagem (WKWebView) / `@react-pdf` equivalente:** rejeitado — não é a API oficial de PDF do
  iOS; degrada nitidez/seleção de texto; viola o padrão-ouro.
- **Biblioteca de terceiros de PDF:** rejeitado — dependência extra para algo que a plataforma já faz
  nativamente (CoreText + `UIGraphicsPDFRenderer`).
- **Manter stub e nunca oferecer PDF no iOS:** rejeitado como estado final — mas adotado como
  **transitório seguro** via `PlatformCapabilities.pdfGeneration`/`CapabilityFeature`, para o app **não
  vender** o que ainda não existe, enquanto a dívida não é paga.

## Consequências

- **Positivas:** recibo real no iOS (paridade), template provado (`IosPdfCanvas`) para migrar os 7
  restantes; nada de workaround.
- **Negativas / aceitas:** `PlatformCapabilities.pdfGeneration` permanece `false` (flag coarse) até os 9
  saírem — apps que consomem só o recibo já podem checar o gerador específico. Os stubs **falham alto**
  (exceção com mensagem apontando o flag), nunca em silêncio. A validação visual dos portes multi-página
  fica pendente de um host macOS/CI.
