package br.com.codecacto.kmplib.core.config

/**
 * Camada de dados de domínio de um app do ecossistema CodeCacto — **tipo canônico**.
 *
 * Antes deste tipo, cada app do arquétipo A redefinia um `sealed interface DataLayer` local (com
 * variantes divergentes — `LocalOnly`/`None`/`KtorOnly(baseUrl)`/`Hybrid`/`FirestoreOnly`),
 * herdadas do clone da Casca. Confirmado em ≥4 apps (Números da Sorte, Chamada Fácil, Esquecido,
 * Doses de Alegria) → promovido à kmplib (GAP-CF-M-05). O app declara a camada no `AppConfig`
 * apontando para este enum, **sem redefinir o conceito**.
 *
 * ## Por que enum (e não sealed com `baseUrl`)
 * No ecossistema atual a URL de dados NÃO é por-DataLayer: os endpoints centrais são **constantes
 * padronizadas** (apps-api / admin-api central) já declaradas no `AppConfig` (`appsApiBaseUrl`,
 * base do admin-api). As antigas variantes `KtorOnly(baseUrl)`/`Hybrid(baseUrl)` eram resíduo de
 * casca e colapsam em [Central] — a base vem da config padrão, não do enum. Isso mantém o tipo
 * **estável e trivial** (classificação de arquétipo), no padrão-ouro "dados = API/banco central,
 * SEM Firestore como banco".
 *
 * ## Mapa arquétipo → camada
 * - Arquétipo **A** 100% local sem persistência de domínio real (só assets + preferências) → [None].
 * - Arquétipo **A** offline-first, dados de domínio no dispositivo (DataStore/SQLDelight); toques
 *   online (feedback/contato/quota/ads) via central → [LocalOnly].
 * - Arquétipos **B/D** (offline-first com sync, marketplace, full-stack) → [Central].
 * - Legado → [Firestore] (@Deprecated; PROIBIDO em projeto novo).
 */
enum class DataLayer {

    /**
     * App **100% local sem persistência de domínio real** — lê de assets empacotados e/ou de
     * preferências (`AppPreferences`/DataStore), sem estado de domínio próprio. Arquétipo A puro
     * (ex.: Doses de Alegria: acervo em assets + horário da dose em prefs). Toques online
     * (feedback/contato/developer) vão ao apps-api central via `CentralServices`.
     */
    None,

    /**
     * **Persistência de domínio 100% local** no dispositivo (DataStore ou SQLDelight via módulo
     * `sync`/`LocalRepository`), sem backend próprio. Arquétipo A offline-first (ex.: Números da
     * Sorte, Chamada Fácil, Esquecido). Nenhum dado de domínio sai do aparelho; os toques online
     * (feedback/contato/quota/ads) usam a API central.
     */
    LocalOnly,

    /**
     * **Dados de domínio vêm da API/banco central** (backlib/Ktor + Postgres central) — via `core/data`
     * (`Repository`/`RestRepository`) e/ou do módulo `sync` (offline-first + sync). Arquétipos B/D.
     * A base da API é uma constante padronizada do ecossistema (config do app), não deste enum.
     */
    Central,

    /**
     * **Legado — Firestore como banco.** Mantido apenas para classificar apps herdados ainda não
     * migrados; **PROIBIDO em projeto novo** (decisão de arquitetura jun/2026: SEM Firestore como
     * armazenamento; Firebase só Auth). Migre para [LocalOnly] (offline) ou [Central].
     */
    @Deprecated(
        message = "Firestore como banco foi banido do ecossistema (jun/2026). Use LocalOnly (offline) " +
            "ou Central (API/banco central). Valor mantido só p/ classificar legado não migrado.",
        level = DeprecationLevel.WARNING,
    )
    Firestore;

    /**
     * `true` quando os dados de domínio ficam **no dispositivo** ([None] ou [LocalOnly]) — arquétipo A.
     * Útil para decidir bootstrap (ex.: não amarrar `core/data`/sync REST quando é local).
     */
    val isLocal: Boolean
        get() = this == None || this == LocalOnly

    /**
     * `true` quando os dados de domínio vêm do **backend central** ([Central]) — arquétipos B/D.
     */
    val usesCentralData: Boolean
        get() = this == Central
}
