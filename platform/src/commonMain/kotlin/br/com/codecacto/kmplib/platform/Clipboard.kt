package br.com.codecacto.kmplib.platform

/**
 * Área de transferência do sistema — **copiar** um texto.
 *
 * ## Por que na lib, e não `LocalClipboardManager` do Compose
 * O `LocalClipboardManager` está **depreciado**, e o substituto (`LocalClipboard` +
 * `setClipEntry`) recebe um `ClipEntry` que é **específico de plataforma** (`ClipData` no Android,
 * `UIPasteboard` no iOS): não há como montá-lo em `commonMain`. Sem isto na fundação, cada app faria
 * o próprio `expect/actual` — ou continuaria na API depreciada, que some no próximo bump do Compose.
 *
 * ## Só copiar
 * Ler a área de transferência não entra: é o caminho por onde um app lê o que a pessoa copiou de
 * outro app (uma senha, um código de banco), e nenhum produto da fábrica precisa disso. Quando algum
 * precisar — colar um cupom, por exemplo —, entra com o motivo declarado.
 *
 * Uso:
 * ```kotlin
 * getClipboard().copy("chave-pix-da-loja")
 * ```
 */
interface Clipboard {
    /**
     * Copia [text] para a área de transferência.
     *
     * [label] é o rótulo que o Android mostra na prévia do sistema ("Chave Pix copiada"). O iOS não
     * tem esse conceito e o ignora — parâmetro, e não constante, porque quem sabe o que é o texto é
     * a tela que copia.
     */
    fun copy(text: String, label: String = "Texto")
}

expect fun getClipboard(): Clipboard
