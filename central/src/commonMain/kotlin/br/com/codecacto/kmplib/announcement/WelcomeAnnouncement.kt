@file:OptIn(ExperimentalTime::class)

package br.com.codecacto.kmplib.announcement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import br.com.codecacto.kmplib.core.locale.appLanguageTag
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.currentPlatform
import br.com.codecacto.kmplib.ui.announcement.Announcement
import br.com.codecacto.kmplib.ui.announcement.AnnouncementEvent
import br.com.codecacto.kmplib.ui.announcement.AnnouncementEventListener
import br.com.codecacto.kmplib.ui.announcement.AnnouncementFrequency
import br.com.codecacto.kmplib.ui.announcement.AnnouncementKind
import br.com.codecacto.kmplib.ui.tour.ProductTourAccountStore
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/*
 * ─────────────────────── Boas-vindas do 1º acesso (2.371.0) ───────────────────────
 *
 * Par do `useWelcomeAnnouncement` da weblib (≥ 0.328.1). Regra da fábrica (constituição, 18/nov/2026):
 * a conta que PAGA o produto, ao entrar pela 1ª vez depois do cadastro, vê UMA vez o comunicado de
 * boas-vindas cadastrado no Nexus → Comunicados (tipo "Boas-vindas"), com o WhatsApp do suporte.
 *
 * - Só conta NOVA: criada há até 7 dias ([isNewAccount]). Sem a data de criação, não mostra — sem saber
 *   se a conta é nova, quem usa o app há meses levaria um "seja bem-vindo".
 * - "Já viu" fica NA CONTA, pela chave [WELCOME_SEEN_KEY] no MESMO store do tour
 *   ([ProductTourAccountStore], `backlib-product-tour`: `GET …/me/tours/seen` · `PUT …/me/tours/seen/welcome`).
 *   Sem store, não mostra; store que falha na leitura = não mostra (não arrisca repetir a quem já viu).
 * - Busca `GET {appsApi}/public/announcements?…&audience=newAccount` (o servidor só manda `welcome` com
 *   esse parâmetro), fica com o primeiro `kind == welcome`.
 * - "Viu" = o modal APARECEU (evento SHOWN) — marca na conta; falha ao gravar não quebra nada.
 */

/** Chave do "já viu" na conta. Uma só para todas as boas-vindas: quem viu uma não vê outra. Igual à weblib. */
const val WELCOME_SEEN_KEY: String = "welcome"

/** Quantos dias a conta conta como nova, por padrão (regra da fábrica). */
const val DEFAULT_NEW_ACCOUNT_DAYS: Int = 7

/**
 * A conta é nova — criada há até [days] dias, e não no futuro além de 1 dia (relógio torto)? `null` ou
 * data ilegível = `false`. Mesma régua do `isNewAccount` da weblib.
 */
fun isNewAccount(createdAt: Instant?, now: Instant, days: Int = DEFAULT_NEW_ACCOUNT_DAYS): Boolean {
    if (createdAt == null) return false
    return now - createdAt <= days.days && createdAt - now <= 1.days
}

/** [isNewAccount] a partir do ISO-8601 que o servidor manda (`User.createdAt`). Ilegível = `false`. */
fun isNewAccount(createdAtIso: String?, now: Instant, days: Int = DEFAULT_NEW_ACCOUNT_DAYS): Boolean =
    isNewAccount(parseAccountCreatedAt(createdAtIso), now, days)

/** Lê o ISO-8601 da criação da conta (`2026-11-18T13:05:00Z`, com ou sem fração/fuso numérico). */
fun parseAccountCreatedAt(value: String?): Instant? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    return runCatching { Instant.parse(trimmed) }.getOrNull()
}

/**
 * Como ligar as boas-vindas num app.
 *
 * @property accountCreatedAt criação da conta logada, ISO-8601 — o `User.createdAt` do own-auth
 *   (kmplib ≥ 2.371.0 + backlib ≥ 0.273.0). `null` = não mostra.
 * @property accountStore o "já viu" na conta — o MESMO store do tour (`firstRunAccountStore` do
 *   `ProductTourHost`). `null` = não mostra.
 * @property enabled `false` desliga (ex.: sessão que não paga — o cliente final de um SaaS).
 * @property newAccountDays janela de conta nova (default 7).
 */
data class WelcomeAnnouncementConfig(
    val accountCreatedAt: String?,
    val accountStore: ProductTourAccountStore?,
    val enabled: Boolean = true,
    val newAccountDays: Int = DEFAULT_NEW_ACCOUNT_DAYS,
)

/**
 * A regra das boas-vindas sem Compose (testável). [load] devolve o comunicado a mostrar (ou `null`);
 * [markShown] grava na conta, uma vez.
 */
class WelcomeAnnouncementLoader(
    private val client: AnnouncementClient?,
    private val config: WelcomeAnnouncementConfig,
    private val clock: Clock = Clock.System,
) {
    private val markLock = Mutex()
    private var marked = false

    /** O comunicado de boas-vindas, com `frequency = ONCE`; `null` quando não é para mostrar. Nunca lança (salvo cancelamento). */
    suspend fun load(
        platform: String = currentPlatform,
        locale: String = appLanguageTag(),
    ): Announcement? {
        val store = config.accountStore ?: return null
        if (!config.enabled || client == null) return null
        if (!isNewAccount(config.accountCreatedAt, clock.now(), config.newAccountDays)) return null
        val seen = try {
            store.isSeen(WELCOME_SEEN_KEY)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            true // sem resposta da conta, não arrisca mostrar de novo a quem já viu
        }
        if (seen) return null
        val list = client.fetchOrNull(platform, locale, AnnouncementAudience.NEW_ACCOUNT) ?: return null
        return pickWelcomeAnnouncement(list)
    }

    /** Marca [WELCOME_SEEN_KEY] na conta quando o modal de boas-vindas aparece. Uma vez; falha não quebra. */
    suspend fun markShown(announcement: Announcement) {
        if (announcement.kind != AnnouncementKind.WELCOME) return
        val store = config.accountStore ?: return
        markLock.withLock {
            if (marked) return
            marked = true
            try {
                store.markSeen(WELCOME_SEEN_KEY)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Acessório: a marca local (`once`) segura neste aparelho.
                AppLogger.w(TAG, "Falha ao marcar as boas-vindas como vistas na conta.")
            }
        }
    }

    private companion object {
        const val TAG = "WelcomeAnnouncement"
    }
}

/** O primeiro `kind == welcome` da lista (o servidor já ordena o mais novo primeiro), com `frequency = ONCE`. */
fun pickWelcomeAnnouncement(list: List<Announcement>): Announcement? =
    list.firstOrNull { it.kind == AnnouncementKind.WELCOME }?.copy(frequency = AnnouncementFrequency.ONCE)

/**
 * O que [rememberWelcomeAnnouncement] devolve: a lista (zero ou um comunicado) e o ouvinte que marca na
 * conta quando o modal aparece.
 */
@Stable
class WelcomeAnnouncementState internal constructor(
    private val announcementState: State<Announcement?>,
    /** Passe em `onEvent` do host (o [RemoteAnnouncementHost] com `welcome = …` já faz). */
    val listener: AnnouncementEventListener,
) {
    /** Zero ou um comunicado (`kind = welcome`) — junte com os outros no host. */
    val announcements: List<Announcement>
        get() = listOfNotNull(announcementState.value)
}

/**
 * **Boas-vindas do 1º acesso** (2.371.0) — o comunicado "Boas-vindas" do Nexus, em modal, UMA vez por
 * conta, só para conta criada há até 7 dias. Busca uma vez por conta/idioma; a lista vem vazia sem
 * [WelcomeAnnouncementConfig.accountCreatedAt], sem store, com a conta antiga ou que já viu.
 *
 * O caminho curto é `RemoteAnnouncementHost(welcome = WelcomeAnnouncementConfig(…))`; este é para quem
 * monta o próprio `AnnouncementHost`:
 *
 * ```kotlin
 * val boasVindas = rememberWelcomeAnnouncement(WelcomeAnnouncementConfig(user?.createdAt, tourNaConta))
 * AnnouncementHost(announcements = locais + boasVindas.announcements, onEvent = boasVindas.listener)
 * ```
 */
@Composable
fun rememberWelcomeAnnouncement(
    config: WelcomeAnnouncementConfig?,
    client: AnnouncementClient? = AnnouncementService.client,
    platform: String = currentPlatform,
    locale: String = appLanguageTag(),
): WelcomeAnnouncementState {
    val loader = remember(config, client) { config?.let { WelcomeAnnouncementLoader(client, it) } }
    val scope = rememberCoroutineScope()
    val announcement = if (loader == null) {
        remember { mutableStateOf<Announcement?>(null) }
    } else {
        produceState<Announcement?>(initialValue = null, loader, platform, locale) {
            value = loader.load(platform, locale)
        }
    }
    val listener = remember(loader) {
        AnnouncementEventListener { a, event ->
            if (loader != null && event == AnnouncementEvent.SHOWN && a.kind == AnnouncementKind.WELCOME) {
                scope.launch { loader.markShown(a) }
            }
        }
    }
    return remember(announcement, listener) { WelcomeAnnouncementState(announcement, listener) }
}
