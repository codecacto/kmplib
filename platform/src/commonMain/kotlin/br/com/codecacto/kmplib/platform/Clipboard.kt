package br.com.codecacto.kmplib.platform

/**
 * Área de transferência do sistema — **copiar** (com marca de conteúdo sensível) e **colar**.
 *
 * ## Por que na lib, e não `LocalClipboardManager` do Compose
 * O `LocalClipboardManager` está **depreciado**, e o substituto (`LocalClipboard` +
 * `setClipEntry`) recebe um `ClipEntry` que é **específico de plataforma** (`ClipData` no Android,
 * `UIPasteboard` no iOS): não há como montá-lo em `commonMain`. Sem isto na fundação, cada app faria
 * o próprio `expect/actual` — ou continuaria na API depreciada, que some no próximo bump do Compose.
 *
 * ## Copiar dado sensível (2.270.0)
 * `copy(text, sensitive = true)` para senha temporária, código de acesso, token, chave: no Android
 * o clip leva `ClipDescription.EXTRA_IS_SENSITIVE` (API 33+; abaixo, a mesma chave literal, que a
 * prévia de alguns fabricantes já respeitava) e a prévia do sistema mostra `••••` em vez do texto;
 * no iOS o item vai com `localOnly` (não viaja para o Mac/iPad pela Área de Transferência
 * Universal) e **expira em [SENSITIVE_CLIP_EXPIRATION_SECONDS] segundos**. Texto comum continua
 * `sensitive = false`, o default — marcar tudo como sensível esconde da pessoa o que ela copiou.
 *
 * ## Colar (2.270.0)
 * [readText] lê o texto atual — para o botão **"Colar"** de um campo (cupom, código recebido por
 * outro canal). Chame **em resposta a um toque da pessoa**, nunca ao abrir a tela: no iOS 16+ a
 * leitura mostra o aviso do sistema ("App quer colar de…") e, fora de um gesto, ele aparece do nada;
 * no Android 10+ só o app em primeiro plano e com foco lê (fora disso volta `null`), e o Android 12+
 * mostra o aviso "colado de…". Para decidir se o botão aparece, use [hasText], que NÃO dispara o
 * aviso em nenhuma das duas plataformas (`hasPrimaryClip`/`hasStrings`).
 *
 * Uso:
 * ```kotlin
 * getClipboard().copy("chave-pix-da-loja")
 * getClipboard().copy(senhaTemporaria, label = "Senha temporária", sensitive = true)
 * val codigo = getClipboard().readText()   // no onClick do "Colar"
 * ```
 */
interface Clipboard {
    /**
     * Copia [text] para a área de transferência.
     *
     * [label] é o rótulo que o Android mostra na prévia do sistema ("Chave Pix copiada"). O iOS não
     * tem esse conceito e o ignora — parâmetro, e não constante, porque quem sabe o que é o texto é
     * a tela que copia.
     *
     * [sensitive] marca o conteúdo como sensível (ver o bloco "Copiar dado sensível" acima).
     */
    fun copy(text: String, label: String = "Texto", sensitive: Boolean = false)

    /**
     * `true` se há texto na área de transferência — **sem ler o conteúdo** e sem disparar o aviso
     * de colagem do sistema. Para mostrar/habilitar o botão "Colar".
     */
    fun hasText(): Boolean

    /**
     * O texto atual da área de transferência, ou `null` se não houver texto, se o sistema negar a
     * leitura (Android: app sem foco; iOS: a pessoa recusou o aviso) ou se a leitura falhar. Chame
     * na thread principal, dentro do clique de "Colar" (ver o bloco "Colar" acima).
     */
    fun readText(): String?
}

/** Por quanto tempo o iOS mantém um texto copiado com `sensitive = true` (2 minutos). */
const val SENSITIVE_CLIP_EXPIRATION_SECONDS: Long = 120

expect fun getClipboard(): Clipboard
