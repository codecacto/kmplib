package br.com.codecacto.kmplib.platform

/**
 * Destinos de envio **direto** de imagem (2.354.0) — o "WhatsApp · Status · Instagram" da folha de
 * compartilhar de app de conteúdo, sem passar pela folha do sistema.
 *
 * Use [ShareHandler.isInstalled] para decidir se o botão aparece e [ShareHandler.shareImageTo] para
 * enviar. Destino que a plataforma não oferece de forma OFICIAL responde `false` em `isInstalled` —
 * o botão some (dois botões fazendo a mesma coisa, ou um que finge um caminho que não existe, é engano).
 *
 * | Destino | Android | iOS |
 * |---|---|---|
 * | [WHATSAPP] | `ACTION_SEND` com o pacote `com.whatsapp` (ou `com.whatsapp.w4b`) — o WhatsApp oferece conversa **ou** "Meu status" | `UIDocumentInteractionController` com arquivo `.wai` / UTI `net.whatsapp.image` (documentado pelo WhatsApp) |
 * | [WHATSAPP_STATUS] | **sem API oficial** → `isInstalled = false` | **sem API oficial** → `false` |
 * | [INSTAGRAM_STORIES] | `com.instagram.share.ADD_TO_STORY` + `source_application` (App ID da Meta) | `instagram-stories://share?source_application=…` com a imagem na área de transferência (expira em 5 min) |
 *
 * **O que o app declara:**
 * - Android: nada — o `<queries>` dos pacotes vem do manifesto da lib (`kmplib-platform`).
 * - iOS: `LSApplicationQueriesSchemes` com `whatsapp` e `instagram-stories` no `Info.plist`. Sem isso
 *   `canOpenURL` responde `false` sempre e os botões somem.
 * - Instagram (as duas plataformas): o **App ID do Facebook/Meta** do app em [ShareImageOptions.metaAppId].
 *   Sem ele a Meta recusa o compartilhamento (`isInstalled` também responde `false`).
 */
enum class ShareTarget {
    /** Conversa (ou status, pelo próprio WhatsApp). */
    WHATSAPP,

    /**
     * Direto no Status do WhatsApp. Nenhuma das plataformas tem caminho oficial para isso — mantido no
     * enum para a folha poder perguntar e esconder o botão sem regra especial.
     */
    WHATSAPP_STATUS,

    /** Stories do Instagram, pela integração oficial da Meta. */
    INSTAGRAM_STORIES,
}

/**
 * Opções do envio direto.
 *
 * @property metaAppId App ID do Facebook/Meta do app — **obrigatório** para [ShareTarget.INSTAGRAM_STORIES]
 *   (vai em `source_application`). Ignorado nos demais destinos.
 * @property title título (usado só onde a plataforma mostra).
 */
data class ShareImageOptions(
    val metaAppId: String? = null,
    val title: String = "",
)

/** Resultado do envio direto. */
sealed interface ShareTargetResult {
    /** O app de destino foi aberto com a imagem. */
    data object Opened : ShareTargetResult

    /** O destino não está instalado (ou a plataforma não oferece o destino, ou falta configuração). */
    data object NotAvailable : ShareTargetResult

    /** Falhou ao abrir; [reason] é técnico (para log), nunca para a tela. */
    data class Failed(val reason: String) : ShareTargetResult
}

/** Pacotes Android do WhatsApp, na ordem de preferência (o pessoal antes do Business). */
internal val WHATSAPP_ANDROID_PACKAGES: List<String> = listOf("com.whatsapp", "com.whatsapp.w4b")

/** Pacote Android do Instagram. */
internal const val INSTAGRAM_ANDROID_PACKAGE: String = "com.instagram.android"

/** Ação oficial da Meta para Stories no Android. */
internal const val INSTAGRAM_STORIES_ANDROID_ACTION: String = "com.instagram.share.ADD_TO_STORY"

/** Esquema do WhatsApp (iOS) — precisa estar no `LSApplicationQueriesSchemes`. */
internal const val WHATSAPP_IOS_SCHEME: String = "whatsapp://"

/** UTI exclusiva do WhatsApp para imagem (iOS, documentada pelo WhatsApp). */
internal const val WHATSAPP_IOS_IMAGE_UTI: String = "net.whatsapp.image"

/**
 * URL oficial de Stories do Instagram no iOS para [appId]. Puro, testável. `null` sem App ID válido.
 */
fun instagramStoriesUrl(appId: String?): String? {
    val id = appId?.trim().orEmpty()
    if (id.isEmpty() || !id.all { it.isDigit() }) return null
    return "instagram-stories://share?source_application=$id"
}

/** O App ID da Meta é só dígitos (o do Facebook for Developers). Puro. */
fun isValidMetaAppId(appId: String?): Boolean = instagramStoriesUrl(appId) != null

/**
 * Nome do arquivo temporário do envio ao WhatsApp no iOS: extensão `.wai` é o que faz o
 * `UIDocumentInteractionController` oferecer **só** o WhatsApp. Puro.
 */
fun whatsAppIosFileName(fileName: String): String {
    val base = sanitizeSharedFileName(fileName).substringBeforeLast('.').ifBlank { "imagem" }
    return "$base.wai"
}
