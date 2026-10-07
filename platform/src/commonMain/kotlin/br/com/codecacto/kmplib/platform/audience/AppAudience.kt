package br.com.codecacto.kmplib.platform.audience

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Para quem o app é feito (2.259.0).
 *
 * - [GENERAL] — o default; nada muda.
 * - [KIDS] — app da categoria INFANTIL das lojas (Google Play **Famílias** / Apple **Kids**). Liga o
 *   [ParentalGate] em todo ponto da lib que leva para FORA do app (ver [KmpLibAudience]).
 */
enum class AppAudience {
    GENERAL,
    KIDS,
}

/**
 * Público do app, declarado UMA vez na inicialização (2.259.0).
 *
 * ```kotlin
 * // Application.onCreate (Android) e MainViewController / initKoin (iOS), ANTES da primeira tela
 * KmpLibAudience.configure(AppAudience.KIDS)
 * ```
 *
 * ## O que o modo infantil faz
 *
 * As lojas exigem, em app infantil, que todo toque que leva para fora do app passe por um **portão
 * de pais** — um desafio que só adulto resolve (Apple App Review Guideline **1.3** e *Kids
 * Category*; Google Play **Families Policy**). Com [AppAudience.KIDS], a lib aplica o portão
 * sozinha, sem nenhuma linha por tela:
 *
 * | Saída | Onde |
 * |---|---|
 * | Abrir URL, e-mail, telefone, WhatsApp, mapa, loja, assinaturas, Configurações | todo `getUrlLauncher()` |
 * | Compartilhar (texto, link, imagem, arquivo, `.ics`, "Compartilhar app") | todo `getShareHandler()` |
 * | Link em texto do Compose (`LinkAnnotation`/`LocalUriHandler`) | `AppTheme` |
 * | Clique em house ad (banner e intersticial) | via `UrlLauncher` |
 * | "Desenvolvido por CodeCacto": WhatsApp, e-mail, site, apps, **formulário de contato** | `DeveloperScreen` |
 * | "Avaliar" (`AppReviewDialog`) | vira portão → página da loja, sem o formulário de feedback |
 * | Compra (assinatura, item, consumível) | `RevenueCatPurchaseRepository` |
 *
 * E cala o que a lib dispara sozinha para a criança: o pedido automático de avaliação
 * (`AppReviewManager`) não acontece.
 *
 * O portão aparece pelo `ParentalGateHost` (`kmplib-ui`), que o `AppTheme` já instala. **Sem host
 * na tela, o portão NEGA** (fecha em segurança) e registra um aviso no log.
 *
 * Publicidade: em app infantil os house ads só podem mostrar criativos aprovados — isso é do
 * SERVIDOR (`contentPolicy: CURATED` do projeto no Nexus → Publicidade); o app não sabe e não
 * precisa saber.
 */
object KmpLibAudience {
    private val _current = MutableStateFlow(AppAudience.GENERAL)

    /** Público corrente, observável. */
    val current: StateFlow<AppAudience> = _current.asStateFlow()

    /** `true` quando o app foi declarado infantil. */
    val isKids: Boolean
        get() = _current.value == AppAudience.KIDS

    /** Declara o público do app. Chamar na inicialização, antes da primeira tela. */
    fun configure(audience: AppAudience) {
        _current.value = audience
    }
}
