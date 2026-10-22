package br.com.codecacto.kmplib.inspection.model

/**
 * As regras PURAS das telas do kit — o que a tela calcula a partir do estado e o ViewModel do produto
 * reaproveita (filtro, contagens, pendências, faixa da fila). Testadas em `InspectionRulesTest`.
 */
object InspectionRules {

    /** As pílulas de filtro de cada fase, na ordem do desenho. */
    fun filtersFor(phase: InspectionPhase): List<ItemFilter> = when (phase) {
        InspectionPhase.ENTRY -> listOf(ItemFilter.ALL, ItemFilter.WITH_PROBLEM, ItemFilter.WITHOUT_PHOTO)
        InspectionPhase.EXIT -> listOf(ItemFilter.ALL, ItemFilter.CHANGED, ItemFilter.PENDING)
    }

    /** `true` se [item] entra no [filter]. */
    fun matches(item: ItemRowState, filter: ItemFilter, phase: InspectionPhase): Boolean = when (filter) {
        ItemFilter.ALL -> true
        ItemFilter.WITH_PROBLEM -> item.isProblem(phase)
        ItemFilter.WITHOUT_PHOTO -> item.photoCount == 0
        ItemFilter.CHANGED -> item.changedSinceEntry
        ItemFilter.PENDING -> item.isPending(phase)
    }

    /**
     * Os itens exibidos: tarefas guiadas PRIMEIRO (vêm antes dos itens do modelo, D5), depois os do
     * modelo, na ordem recebida; só os que passam no filtro.
     */
    fun visibleItems(items: List<ItemRowState>, filter: ItemFilter, phase: InspectionPhase): List<ItemRowState> {
        val (tarefas, demais) = items.partition { it.isGuidedTask }
        return (tarefas + demais).filter { matches(it, filter, phase) }
    }

    /** A contagem de cada pílula ("Todos · 10", "Com problema · 2"). */
    fun filterCounts(items: List<ItemRowState>, phase: InspectionPhase): Map<ItemFilter, Int> =
        filtersFor(phase).associateWith { f -> items.count { matches(it, f, phase) } }

    /** Itens com problema no cômodo (subtítulo "2 itens com problema"). */
    fun problemCount(items: List<ItemRowState>, phase: InspectionPhase): Int = items.count { it.isProblem(phase) }

    /** Contagem por estado, com TODOS os estados presentes (N/A = 0 aparece na legenda). */
    fun conditionCounts(conditions: List<ItemCondition?>): Map<ItemCondition, Int> =
        ItemCondition.entries.associateWith { c -> conditions.count { it == c } }

    /** Contagem por resultado da saída, com os três presentes. */
    fun resultCounts(results: List<ExitResult?>): Map<ExitResult, Int> =
        ExitResult.entries.associateWith { r -> results.count { it == r } }

    /**
     * Itens que podem receber "Marcar todos como Conforme": os da saída ainda SEM resultado e que não
     * mudaram desde a entrada (o que mudou exige olhar). Nada já avaliado é sobrescrito.
     */
    fun markAllConformingTargets(items: List<ItemRowState>): List<ItemRowState> =
        items.filter { !it.isGuidedTask && it.exitResult == null && !it.changedSinceEntry }

    /** A faixa da fila de envio: o que dizer, por prioridade (falha > offline > Wi-Fi > enviando). */
    fun queueBannerKind(summary: MediaQueueSummary): MediaQueueBannerKind = when {
        summary.total <= 0 -> MediaQueueBannerKind.HIDDEN
        summary.failed > 0 -> MediaQueueBannerKind.FAILED
        summary.isDone -> MediaQueueBannerKind.DONE
        summary.offline -> MediaQueueBannerKind.OFFLINE
        summary.waitingWifi > 0 && summary.confirmed + summary.waitingWifi >= summary.total ->
            MediaQueueBannerKind.WAITING_WIFI
        else -> MediaQueueBannerKind.UPLOADING
    }

    /** Fração enviada `0..1` da fila. */
    fun queueFraction(summary: MediaQueueSummary): Float =
        if (summary.total <= 0) 0f else (summary.confirmed.toFloat() / summary.total).coerceIn(0f, 1f)

    /** Reordena uma lista movendo [from] para [to] (mesma regra do `ReorderableList`). */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        val m = list.toMutableList()
        m.add(to, m.removeAt(from))
        return m
    }
}
