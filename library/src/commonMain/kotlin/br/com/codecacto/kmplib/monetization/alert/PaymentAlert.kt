package br.com.codecacto.kmplib.monetization.alert

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.observability.CrashLevel
import br.com.codecacto.kmplib.observability.CrashReporter

/**
 * O que aconteceu de errado no caminho do dinheiro. Cada tipo tem **título estável** (o GlitchTip
 * agrupa issue por título — texto variável viraria uma issue nova a cada ocorrência e um alerta novo
 * no Discord) e um nível default proporcional ao dano comercial.
 *
 * @property slug identificador estável do tipo (vira tag `tipo` no evento).
 * @property titulo texto FIXO do evento — nunca interpolar contador/id aqui.
 * @property nivel severidade default (o chamador pode subir, ex.: reincidência).
 */
enum class PaymentAlertKind(
    val slug: String,
    val titulo: String,
    val nivel: CrashLevel,
) {
    /**
     * A oferta central (`admin-api /me/plans`) não veio e o paywall foi montado **pelos Packages da
     * loja** (fallback). O app continua vendendo — por isso não é `Fatal` —, mas está operando sem a
     * fonte de verdade da oferta: um plano desligado no admin pode reaparecer na tela.
     */
    OfertaCentralIndisponivel(
        slug = "oferta_central_indisponivel",
        titulo = "PAGAMENTO: oferta central indisponível — paywall montado pela loja (fallback)",
        nivel = CrashLevel.Error,
    ),

    /**
     * Os **dois** lados falharam (nem oferta central, nem Package da loja): o paywall abre vazio e
     * **não há como vender**. É o pior caso do funil — receita zero e o usuário vê uma tela morta.
     */
    PaywallSemPlano(
        slug = "paywall_sem_plano",
        titulo = "PAGAMENTO: paywall sem nenhum plano — impossível vender",
        nivel = CrashLevel.Fatal,
    ),

    /** A loja (RevenueCat/Offerings) não devolveu Package algum, mesmo com oferta central presente. */
    LojaIndisponivel(
        slug = "loja_indisponivel",
        titulo = "PAGAMENTO: loja sem pacotes de assinatura",
        nivel = CrashLevel.Error,
    ),

    /** A compra falhou dentro da loja (erro real, não cancelamento do usuário). */
    CompraFalhou(
        slug = "compra_falhou",
        titulo = "PAGAMENTO: compra falhou na loja",
        nivel = CrashLevel.Error,
    ),

    /** A restauração de compras falhou — usuário pagante pode ficar sem acesso. */
    RestauracaoFalhou(
        slug = "restauracao_falhou",
        titulo = "PAGAMENTO: restauração de compras falhou",
        nivel = CrashLevel.Error,
    ),

    /** A leitura do entitlement central falhou (o app fica em estado degradado). */
    EntitlementIndisponivel(
        slug = "entitlement_indisponivel",
        titulo = "PAGAMENTO: leitura de entitlement indisponível",
        nivel = CrashLevel.Warning,
    ),

    /**
     * Identidade ausente na hora de ler a oferta/entitlement — sem token não há `uid`, e o
     * `admin-api` responde 401. Foi a causa-raiz do paywall vazio de 26/07 (docs/16 §A-24 do Super 8).
     */
    IdentidadeAusente(
        slug = "identidade_ausente",
        titulo = "PAGAMENTO: sem identidade para ler a oferta (401 no admin-api)",
        nivel = CrashLevel.Error,
    ),
}

/**
 * **Canal de alerta de pagamento do ecossistema.** Regra da fábrica: *qualquer* erro no caminho do
 * dinheiro é prioridade e tem de chegar ao Discord do fundador — não pode morrer num log que ninguém
 * lê.
 *
 * O caminho é `app → CrashReporter (Sentry KMP) → GlitchTip self-host → alerta com destinatário
 * Discord`. **O app NUNCA fala com o Discord direto:** webhook dentro do binário é segredo público
 * (qualquer um decompila e spama o canal, sem rate limit). O GlitchTip é o único ponto que conhece o
 * webhook, agrupa por issue e já é o hub de erros de backend/web.
 *
 * Uso (ViewModel de monetização):
 * ```kotlin
 * private val alertas = PaymentAlertReporter(crashReporter, projeto = "super-8")
 * // ...
 * alertas.report(PaymentAlertKind.OfertaCentralIndisponivel, detalhe = "pacotes=2 token=ok")
 * ```
 *
 * **LGPD — o `detalhe` é técnico, nunca pessoal.** Contadores, flags e códigos de erro. Jamais
 * `uid`, e-mail, telefone, CPF ou id de transação: o evento sai do device para um hub compartilhado
 * e depois para uma mensagem de chat.
 *
 * **Anti-spam:** cada [PaymentAlertKind] é reportado **uma vez por sessão do app** (o paywall reabre
 * várias vezes e o fallback dispararia em todas). Reincidência dentro da sessão só entra no log
 * local. Passe `umaVezPorSessao = false` para desligar (útil em teste).
 *
 * @param reporter reporter injetado pelo app (Koin `crashReporterModule`). Se ele estiver inativo
 *   (DSN em branco), [report] avisa no log — alerta de pagamento indo para o vazio é, ele mesmo,
 *   um defeito.
 * @param projeto slug do projeto (`admin_projects.slug`), vira tag `projeto` no evento.
 */
class PaymentAlertReporter(
    private val reporter: CrashReporter,
    private val projeto: String,
    private val umaVezPorSessao: Boolean = true,
) {

    private val jaReportados = mutableSetOf<String>()

    /**
     * **Rede de seguranca de privacidade do canal.** O `detalhe` deveria ser sempre tecnico, mas quem
     * chama frequentemente repassa `exception.message` de SDK (Firebase, loja, HTTP) — e essas
     * mensagens carregam URL com `?key=…`, token, e-mail. O destino do evento e um hub compartilhado e,
     * depois, uma **mensagem de chat**: nao da para confiar em disciplina de chamador.
     *
     * Redige e-mail, CPF, telefone, `key/token/secret/senha/authorization/bearer` com valor longo
     * (>=12 chars — `token=ok` e `token=ausente` sobrevivem, `key=AIzaSy…` nao) e qualquer sequencia
     * opaca de 24+ chars (chave, JWT, base64, id). Trunca em [LIMITE_DETALHE].
     *
     * O texto CRU continua no log local do dispositivo (quem chama loga a causa completa) — o que sai
     * do device e a versao redigida.
     */
    private fun sanitizar(bruto: String): String {
        if (bruto.isBlank()) return ""
        var texto = bruto
            .replace(PADRAO_EMAIL, OCULTO)
            .replace(PADRAO_CPF, OCULTO)
            .replace(PADRAO_TELEFONE, OCULTO)
            .replace(PADRAO_SEGREDO) { m -> "${m.groupValues[1]}${m.groupValues[2]}$OCULTO" }
            .replace(PADRAO_OPACO, OCULTO)
        if (texto.length > LIMITE_DETALHE) texto = texto.take(LIMITE_DETALHE) + "…"
        return texto
    }

    /**
     * Reporta um alerta de pagamento. Devolve `true` se o evento foi ENVIADO de fato (útil para
     * teste e para o app não mentir no log).
     *
     * @param detalhe contexto técnico curto e SEM PII (ex.: `"oferta=0 pacotes=2 token=ausente"`).
     * @param nivel sobrescreve o [PaymentAlertKind.nivel] default.
     * @param tagsExtra tags adicionais do evento (para aparecer no Discord, a tag precisa constar em
     *   `tags_to_add` do destinatário no GlitchTip).
     */
    fun report(
        kind: PaymentAlertKind,
        detalhe: String = "",
        nivel: CrashLevel = kind.nivel,
        tagsExtra: Map<String, String> = emptyMap(),
    ): Boolean {
        val repetido = umaVezPorSessao && !jaReportados.add(kind.slug)
        val detalheSeguro = sanitizar(detalhe)

        // Log local SEMPRE — mesmo repetido, mesmo com reporter morto: é a única evidência que
        // sobrevive num device sem DSN configurado.
        AppLogger.w(
            TAG,
            "${kind.slug}: ${kind.titulo}${if (detalheSeguro.isBlank()) "" else " · $detalheSeguro"}" +
                if (repetido) " (repetido na sessão — não reenviado)" else "",
        )
        if (repetido) return false

        if (!reporter.isActive) {
            AppLogger.e(
                TAG,
                "ALERTA DE PAGAMENTO NÃO ENVIADO: CrashReporter inativo (SENTRY_DSN ausente?). " +
                    "Este alerta deveria estar no Discord agora.",
            )
            return false
        }

        val tags = buildMap {
            put("area", AREA_PAGAMENTO)
            put("projeto", projeto)
            put("tipo", kind.slug)
            if (detalheSeguro.isNotBlank()) put("detalhe", detalheSeguro)
            // Tag de chamador também passa pelo redator: mesma razão do `detalhe`.
            tagsExtra.forEach { (chave, valor) -> put(chave, sanitizar(valor)) }
        }
        reporter.captureMessage(kind.titulo, nivel, tags)
        return true
    }

    /** Libera o alerta [kind] para ser reenviado nesta sessão (ex.: teste, ou reset explícito). */
    fun reset(kind: PaymentAlertKind? = null) {
        if (kind == null) jaReportados.clear() else jaReportados.remove(kind.slug)
    }

    companion object {
        /** Valor da tag `area` que marca o evento como do caminho do dinheiro. */
        const val AREA_PAGAMENTO: String = "pagamento"

        /** Tamanho maximo do `detalhe` que sai do device (o resto e truncado). */
        const val LIMITE_DETALHE: Int = 300

        private const val TAG = "PaymentAlert"
        private const val OCULTO = "[oculto]"

        private val PADRAO_EMAIL = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
        private val PADRAO_CPF = Regex("""\b\d{3}\.?\d{3}\.?\d{3}-?\d{2}\b""")
        private val PADRAO_TELEFONE = Regex("""\b\(?\d{2}\)?\s?9?\d{4}-?\d{4}\b""")

        /**
         * `chave=valor` sensivel com valor LONGO (>=12). O limite preserva os diagnosticos uteis do
         * ecossistema (`token=ok`, `token=ausente`) e mata o que e segredo de verdade (`key=AIzaSy…`).
         */
        private val PADRAO_SEGREDO =
            Regex("""(?i)\b(key|token|secret|senha|password|authorization|bearer)\b(\s*[:=]\s*|\s+)\S{12,}""")

        /** Sequencia opaca de 24+ chars (chave, JWT, base64, id de transacao) em qualquer posicao. */
        private val PADRAO_OPACO = Regex("""[A-Za-z0-9_\-.]{24,}""")
    }
}
