package br.com.codecacto.kmplib.permissions

import br.com.codecacto.kmplib.core.util.AppLogger

/**
 * Modelo genérico de **permissão granular por módulo** (matriz módulo × nível).
 *
 * Núcleo PURO (sem Compose, sem rede, sem domínio de app) do padrão que já foi implementado à mão
 * em ≥2 produtos multi-tenant do ecossistema — Influencer (mobile `PermissionsEditor.kt` + web
 * `PermissionsDialog.tsx`) e, agora, TattooStudio. A UI vive em
 * `ui/components/ModulePermissionMatrix`; a normalização/validação/serialização vivem aqui, como
 * funções puras testáveis (a divergência de comportamento entre mobile e web do Influencer nasceu
 * justamente de esconder essa regra dentro do componente).
 *
 * ### Genérico de propósito
 * A lib **não** conhece "CLIENTS/PLANS/AGENDA/FINANCE" nem "Agenda/Financeiro/Clientes/Empresa":
 * cada app declara os próprios módulos ([PermissionModuleSpec]) com a **chave de fio** e o **rótulo
 * já resolvido** (i18n é do app — a lib não depende dos Compose Resources do consumidor).
 *
 * ### Níveis
 * [PermissionLevel] é fixo (`NONE` < `VIEW` < `EDIT`) e comparável por **ordinal** — `level >=
 * PermissionLevel.VIEW` responde "pode ver?" sem tabela auxiliar. Só os **rótulos** são
 * parametrizáveis (`PermissionMatrixTexts`).
 *
 * @see PermissionMatrixState estado imutável em edição.
 * @see PermissionMatrixWire fronteira `{ modules: Map<String,String>, ...flags }`.
 */

/** Tag de log das mensagens deste módulo. */
private const val LOG_TAG = "PermissionMatrix"

/**
 * Nível de acesso a um módulo. **Comparável por ordinal**: `NONE < VIEW < EDIT`.
 *
 * ```kotlin
 * if (state.levelOf("AGENDA") >= PermissionLevel.VIEW) { /* mostra o menu Agenda */ }
 * ```
 */
enum class PermissionLevel {
    /** Sem acesso — o módulo nem aparece para o membro. */
    NONE,

    /** Somente leitura. */
    VIEW,

    /** Leitura e escrita. */
    EDIT,
    ;

    /** `true` quando o nível concede algum acesso (ou seja, é diferente de [NONE]). */
    val grantsAccess: Boolean get() = this > NONE

    /** `true` quando este nível é pelo menos [minimum] (comparação por ordinal). */
    fun atLeast(minimum: PermissionLevel): Boolean = this >= minimum

    companion object {
        /**
         * Parse **tolerante** de um valor de fio: aceita qualquer caixa e espaços em volta
         * (`"edit"`, `" EDIT "` → [EDIT]); valor desconhecido/nulo/vazio ⇒ `null`.
         *
         * É o que permite ao servidor introduzir um valor novo sem quebrar cliente velho — cabe a
         * quem chama decidir o que fazer com o `null` (ver [PermissionMatrixWire.parse]).
         */
        fun fromWireOrNull(raw: String?): PermissionLevel? {
            val normalized = raw?.trim()?.uppercase().orEmpty()
            if (normalized.isEmpty()) return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

/**
 * Um módulo da matriz: a **chave de fio** + o **rótulo já resolvido** pelo app.
 *
 * @param key chave persistida (ex.: `"AGENDA"`). É o que trafega no JSON — nunca traduzir.
 * @param label rótulo exibido (o app resolve i18n antes de passar).
 * @param description linha secundária opcional (explica o que o módulo abrange).
 */
data class PermissionModuleSpec(
    val key: String,
    val label: String,
    val description: String? = null,
)

/**
 * Uma **flag booleana extra** da matriz, opcionalmente **dependente de um módulo/nível**.
 *
 * É o slot genérico que substitui o vazamento de domínio do Influencer (`contentsPost` era campo
 * fixo dentro de um componente que se pretendia genérico): a flag declara de quem depende, e a lib
 * resolve. Apps sem flag (TattooStudio) passam a lista vazia e nada é renderizado.
 *
 * ```kotlin
 * PermissionFlagSpec(
 *     key = "contentsPost",
 *     label = "Pode publicar conteúdo",
 *     description = "Permite postar (mover para Postado). Requer Conteúdos = Ver e editar.",
 *     requiresModule = "CONTENTS",
 *     requiresLevel = PermissionLevel.EDIT,
 * )
 * ```
 *
 * @param key chave persistida da flag (ex.: `"contentsPost"`).
 * @param label rótulo exibido.
 * @param description linha secundária opcional (o texto que explica a dependência é do app).
 * @param requiresModule chave do módulo do qual a flag depende. `null` ⇒ flag independente.
 * @param requiresLevel nível mínimo exigido em [requiresModule]. Ignorado quando não há dependência.
 */
data class PermissionFlagSpec(
    val key: String,
    val label: String,
    val description: String? = null,
    val requiresModule: String? = null,
    val requiresLevel: PermissionLevel = PermissionLevel.EDIT,
)

/**
 * Estado **imutável** da matriz em edição (`data class` + `copy()` — nada de `mutableStateMapOf`).
 *
 * Guardado no State do MVI do app: `setState { copy(perms = novo) }`.
 *
 * ### Chaves desconhecidas são PRESERVADAS (forward-compat)
 * [levels]/[flags] são mapeados por `String`, não por enum do app: se o servidor passar a conceder
 * um módulo que **este** cliente ainda não renderiza, a entrada continua no estado e volta intacta
 * no `save` — um app velho **não revoga** o que não entende. (O contrário — dropar o que não
 * conhece — foi o que fez o mobile do Influencer arriscar perda silenciosa de permissão.)
 * A exceção é um **valor de nível** ilegível: aí a entrada é descartada com log, porque exibir
 * "Sem acesso" enquanto se preserva um acesso concedido seria mentir para quem administra.
 */
data class PermissionMatrixState(
    val levels: Map<String, PermissionLevel> = emptyMap(),
    val flags: Map<String, Boolean> = emptyMap(),
) {

    /** Nível corrente do módulo. Ausente ⇒ [PermissionLevel.NONE]. */
    fun levelOf(moduleKey: String): PermissionLevel = levels[moduleKey] ?: PermissionLevel.NONE

    /** Nível corrente do módulo. Ausente ⇒ [PermissionLevel.NONE]. */
    fun levelOf(module: PermissionModuleSpec): PermissionLevel = levelOf(module.key)

    /** Cópia com o nível do módulo alterado. */
    fun withLevel(moduleKey: String, level: PermissionLevel): PermissionMatrixState =
        copy(levels = levels + (moduleKey to level))

    /** Cópia com o nível do módulo alterado. */
    fun withLevel(module: PermissionModuleSpec, level: PermissionLevel): PermissionMatrixState =
        withLevel(module.key, level)

    /** Cópia com o valor **cru** da flag alterado (a dependência é resolvida na leitura). */
    fun withFlag(flagKey: String, enabled: Boolean): PermissionMatrixState =
        copy(flags = flags + (flagKey to enabled))

    /** Cópia com o valor **cru** da flag alterado. */
    fun withFlag(flag: PermissionFlagSpec, enabled: Boolean): PermissionMatrixState =
        withFlag(flag.key, enabled)

    /**
     * A flag pode ser marcada? (`true` quando não há dependência ou quando o módulo do qual ela
     * depende está no nível exigido). Controla o `enabled` do toggle.
     */
    fun isFlagAvailable(flag: PermissionFlagSpec): Boolean {
        val required = flag.requiresModule ?: return true
        return levelOf(required).atLeast(flag.requiresLevel)
    }

    /** Valor **cru** guardado da flag (o que o usuário marcou), sem resolver a dependência. */
    fun isFlagChecked(flag: PermissionFlagSpec): Boolean = flags[flag.key] == true

    /**
     * Valor **efetivo** da flag: marcada **E** com a dependência satisfeita. É este que vai ao fio —
     * assim, baixar o módulo de `EDIT` para `VIEW` desliga a flag dependente automaticamente.
     */
    fun resolveFlag(flag: PermissionFlagSpec): Boolean = isFlagAvailable(flag) && isFlagChecked(flag)

    /** Algum módulo com acesso (`VIEW` ou `EDIT`)? Base da validação de "salvar". */
    val hasAnyAccess: Boolean get() = levels.values.any { it.grantsAccess }

    /** Atalho de autorização: o membro tem pelo menos [minimum] no módulo? */
    fun grants(moduleKey: String, minimum: PermissionLevel = PermissionLevel.VIEW): Boolean =
        levelOf(moduleKey).atLeast(minimum)

    companion object {
        /** Estado vazio (nenhum módulo concedido, nenhuma flag). */
        val Empty: PermissionMatrixState = PermissionMatrixState()
    }
}

/**
 * **Normalização canônica** — a regra unificada que mobile e web devem aplicar antes de persistir:
 *
 * 1. módulos em [PermissionLevel.NONE] são **removidos** (não se guarda `{"X":"NONE"}`);
 * 2. cada flag declarada é gravada com o valor **efetivo** ([PermissionMatrixState.resolveFlag]) —
 *    dependência não satisfeita vira `false`;
 * 3. chaves não declaradas (módulos/flags que este cliente não conhece) são **preservadas**.
 *
 * O mobile do Influencer não fazia (1) nem (2) de forma explícita e o web fazia só na hora do save,
 * escondido no componente — daí a divergência entre plataformas.
 *
 * @param flags flags declaradas pelo app (vazio quando o produto não usa flags).
 */
fun PermissionMatrixState.normalized(
    flags: List<PermissionFlagSpec> = emptyList(),
): PermissionMatrixState {
    val grantedLevels = levels.filterValues { it.grantsAccess }
    val resolvedFlags = if (flags.isEmpty()) {
        this.flags
    } else {
        this.flags + flags.associate { it.key to resolveFlag(it) }
    }
    return PermissionMatrixState(levels = grantedLevels, flags = resolvedFlags)
}

/** Problema de validação da matriz. */
enum class PermissionMatrixIssue {
    /** Nenhum módulo com acesso — salvar criaria um membro que não enxerga nada. */
    NoAccess,
}

/**
 * Valida a matriz antes de salvar. `null` = pode salvar.
 *
 * Regra padrão (a mesma do web do Influencer, que o mobile **não** tinha): exigir ao menos um
 * módulo com acesso. Produto que aceite membro sem nenhum acesso passa `requireAnyAccess = false`.
 */
fun PermissionMatrixState.validate(
    requireAnyAccess: Boolean = true,
): PermissionMatrixIssue? =
    if (requireAnyAccess && !hasAnyAccess) PermissionMatrixIssue.NoAccess else null

/**
 * Fronteira de serialização da matriz: `{ "modules": { "AGENDA": "EDIT", ... }, "<flag>": true }`.
 *
 * Strings dos dois lados e **parse tolerante** (chave/valor desconhecido não derruba nada) — é o
 * que permite ao servidor acrescentar módulo sem quebrar cliente velho.
 */
object PermissionMatrixWire {

    /** Nome do campo que carrega o mapa de módulos no JSON de fronteira. */
    const val MODULES_FIELD: String = "modules"

    /**
     * Lê a matriz da fronteira. **Nunca lança.**
     *
     * - chave de módulo desconhecida (com nível válido) ⇒ **preservada** (forward-compat);
     * - valor de nível ilegível ⇒ entrada **descartada** com `AppLogger.w` (fail-closed: melhor
     *   revogar do que exibir "Sem acesso" e continuar concedendo escondido);
     * - `NONE` explícito ⇒ mantido no estado (a UI mostra igual) e removido na [normalized].
     *
     * @param modules mapa cru `chave -> nível` vindo do DTO do app.
     * @param flags mapa cru de flags booleanas (ex.: `mapOf("contentsPost" to dto.contentsPost)`).
     */
    fun parse(
        modules: Map<String, String?>?,
        flags: Map<String, Boolean> = emptyMap(),
    ): PermissionMatrixState {
        val parsed = LinkedHashMap<String, PermissionLevel>()
        modules?.forEach { (key, rawLevel) ->
            if (key.isBlank()) return@forEach
            val level = PermissionLevel.fromWireOrNull(rawLevel)
            if (level == null) {
                AppLogger.w(LOG_TAG, "nivel desconhecido para o modulo '$key': '$rawLevel' (ignorado)")
                return@forEach
            }
            parsed[key] = level
        }
        return PermissionMatrixState(levels = parsed, flags = flags)
    }

    /**
     * Mapa de módulos pronto para o fio: **já normalizado** (sem `NONE`), valores em caixa alta
     * canônica. Use no DTO do app: `dto.copy(modules = state.toModuleMap())`.
     */
    fun toModuleMap(state: PermissionMatrixState): Map<String, String> =
        state.normalized().levels.mapValues { (_, level) -> level.name }

    /**
     * Mapa de flags pronto para o fio, com as dependências já resolvidas.
     *
     * @param declared flags declaradas pelo app; as não declaradas presentes no estado são
     *   preservadas como estão.
     */
    fun toFlagMap(
        state: PermissionMatrixState,
        declared: List<PermissionFlagSpec> = emptyList(),
    ): Map<String, Boolean> = state.normalized(declared).flags
}

/** Açúcar: mapa de módulos pronto para o fio (ver [PermissionMatrixWire.toModuleMap]). */
fun PermissionMatrixState.toModuleMap(): Map<String, String> = PermissionMatrixWire.toModuleMap(this)

/** Açúcar: mapa de flags pronto para o fio (ver [PermissionMatrixWire.toFlagMap]). */
fun PermissionMatrixState.toFlagMap(
    declared: List<PermissionFlagSpec> = emptyList(),
): Map<String, Boolean> = PermissionMatrixWire.toFlagMap(this, declared)
