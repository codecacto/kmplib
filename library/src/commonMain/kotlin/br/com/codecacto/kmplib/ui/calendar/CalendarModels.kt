package br.com.codecacto.kmplib.ui.calendar

import kotlinx.datetime.LocalDateTime

/* ─────────────────────────── modelo de dados (domínio-agnóstico) ───────────────────────────
 * Par mobile de `@codecacto/weblib/calendar`. O calendário NUNCA conhece "agendamento"/"barbeiro":
 * recebe eventos e recursos genéricos — o Influencer consome em modo recurso-único (1 coluna/timeline)
 * e o Meu Barbeiro em modo N-recursos (uma coluna por profissional), o MESMO componente. Cor/rótulo de
 * status ficam no consumidor (via `eventColors`/`renderEvent`), nunca hardcoded na lib.
 *
 * Paridade com a weblib: os nomes e a semântica (`ScheduleEvent`, `ScheduleResource`, `ScheduleBlock`,
 * `BusinessWindow`, `CalendarViewMode`, `TimeSlot`, `WorkRange`, `MinuteRange`) são espelhados. A
 * diferença de plataforma: o web recebe `Date | ISO string` e coage por parede local; o mobile recebe
 * **`kotlinx.datetime.LocalDateTime`** (parede local por construção, sem fuso) — decisão deliberada para
 * fazer a aritmética do grid só em `LocalDateTime`/`LocalTime` e NUNCA em `Instant` (typealias de
 * `kotlin.time.Instant` no kotlinx-datetime 0.7.x, raiz do incidente de release R8). O boundary de API
 * (ISO string → `LocalDateTime`) é do app (ou use `parseCalendarDateTime`).
 */

/** Uma faixa de minutos-do-dia (`[startMin, endMin)`). `endMin` pode passar de 1440 (cruza meia-noite). */
data class MinuteRange(val startMin: Int, val endMin: Int)

/** Faixa de expediente/disponibilidade em minutos-do-dia. Ex.: 08:00–19:00 = `WorkRange(480, 1140)`. */
data class WorkRange(val startMin: Int, val endMin: Int)

/** Janela horária visível da grade (minutos-do-dia). */
data class BusinessWindow(val startMin: Int, val endMin: Int)

/** Um recurso (coluna) da grade — ex.: um profissional, uma sala. */
data class ScheduleResource(
    /** Chave estável — casa com `ScheduleEvent.resourceId`. */
    val id: String,
    /** Rótulo exibido no cabeçalho da coluna. */
    val label: String,
    /** URL de avatar/foto opcional (ex.: foto do profissional). */
    val avatar: String? = null,
    /** Subtítulo opcional (ex.: especialidade). */
    val subtitle: String? = null,
    /**
     * Faixas de minutos-do-dia em que o recurso está disponível (expediente do profissional, com
     * intervalos). Fora delas a coluna é sombreada (fora-de-expediente). Vazio ⇒ sem sombreado.
     */
    val availableRanges: List<WorkRange> = emptyList(),
)

/**
 * Um evento posicionável (ex.: um agendamento). `start`/`end` são parede local (`LocalDateTime`). A
 * posição é por **minuto real** (`top`/`height` derivam de início e duração), não por balde-de-hora.
 */
data class ScheduleEvent(
    val id: String,
    /** Início (parede local). */
    val start: LocalDateTime,
    /** Fim (parede local). Se ≤ início, o bloco usa a altura mínima. */
    val end: LocalDateTime,
    /** Coluna/recurso a que pertence (default de distribuição). */
    val resourceId: String? = null,
    /** Texto principal do bloco (ex.: nome do cliente). */
    val label: String? = null,
    /** Linha secundária (ex.: serviço). */
    val description: String? = null,
    /** Chave de status do domínio (agnóstica) — o consumidor mapeia status→cor em `eventColors`. */
    val status: String? = null,
    /** Buffer (min) após o fim — desenhado como "rabo" esmaecido. */
    val bufferMin: Int = 0,
    /** Rótulo livre de tipo/kind (ex.: origem). */
    val kind: String? = null,
)

/** Tipo de camada de bloqueio na grade. */
enum class ScheduleBlockVariant {
    /** Só sombreado (fora-de-expediente). */
    OffHours,

    /** Hachurado (folga/almoço/férias/feriado/fechamento). */
    Block,
}

/** Uma faixa de indisponibilidade (almoço, folga, feriado, fechamento). */
data class ScheduleBlock(
    val id: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    /** Coluna alvo; `null` = cobre TODAS as colunas (fechamento do estabelecimento). */
    val resourceId: String? = null,
    /** `Block` = hachurado (folga/almoço); `OffHours` = só sombreado. Default `Block`. */
    val variant: ScheduleBlockVariant = ScheduleBlockVariant.Block,
    /** Rótulo curto (ex.: "Almoço"). */
    val label: String? = null,
)

/**
 * Tom semântico de uma camada de **destinação** ([ScheduleLayer]).
 *
 * **Enum próprio, e não o `StatusTone` dos selos, de propósito** — é a decisão que a weblib tomou em
 * `LayerTone` e que o mobile espelha. Dois motivos: (a) `Primary`/`Accent` não existem no vocabulário
 * de status, e sem eles não há como pintar uma destinação com a cor do produto (o design do Minha
 * Arena pede exatamente isso em "Social"); (b) os cinco tons semânticos resolvem para cores
 * **semânticas** do tema (`AppColors.success/warning/info`, `error`), não para a cor de marca — se
 * "Aluguel" saísse de `primary`, uma arena de marca vermelha teria "Aluguel" e "Bloqueado"
 * indistinguíveis. A cor da marca fica nos CTAs e na seleção; na grade ela só entra quando o app pede
 * por [Primary]/[Accent].
 *
 * Resolvido por `layerToneColor(tone)`.
 */
enum class LayerTone {
    /** Ausência de tom — cinza do tema (`onSurface`), o par mobile de `var(--foreground)`. */
    Neutral,
    Info,
    Success,
    Warning,
    Danger,

    /** Cor de marca do app (`colorScheme.primary`). */
    Primary,

    /** Cor de realce do app (`colorScheme.tertiary` — o mais próximo de `var(--accent)` no Material 3). */
    Accent,
}

/**
 * Textura de uma camada de **destinação** ([ScheduleLayer]) — o segundo canal, além da cor, que
 * distingue uma destinação da outra.
 *
 * Existe por duas razões que se somam: estado **nunca** pode depender só de cor (WCAG 1.4.1), e a
 * paleta é do cliente. Numa arena de marca vermelha, "Aluguel" e "Bloqueado" ficariam quase idênticos
 * se a única diferença fosse o tom — a textura é o que mantém a grade legível com **qualquer** cor de
 * marca.
 *
 * Espelha `pattern: "solid" | "dots" | "stripes" | "hatch"` da weblib.
 */
enum class LayerPattern {
    /** Só o preenchimento (sem textura). Reserve para a destinação mais comum da grade. */
    Solid,

    /** Pontilhado fino `∙∙∙`. */
    Dots,

    /** Listrado diagonal `///`. */
    Stripes,

    /** Hachura cruzada `▨` — a mais "densa", boa para "aqui não se opera". */
    Hatch,
}

/**
 * Uma faixa de **DESTINAÇÃO**: *o que aquela faixa daquela coluna **É*** (Aluguel · Clubinho · Social ·
 * Aula · Bloqueado…), desenhada **atrás** da ocupação, sem competir com ela.
 *
 * **Não é [ScheduleBlock], e a diferença não é cosmética.** Bloqueio responde *"aqui não pode"* — uma
 * negação, com duas variantes ([ScheduleBlockVariant]) que dizem a mesma coisa em intensidades
 * diferentes. Destinação responde *"aqui é isto"*: [kind] é **aberto** (o domínio define quantos
 * propósitos quiser), tem rótulo próprio e entra na **legenda**. Modelar destinação como mais uma
 * variante de bloqueio devolveria o consumidor ao ponto de partida — a grade voltaria a saber apenas
 * que a faixa está indisponível, sem saber o que ela é.
 *
 * As duas camadas **convivem**: a destinação é o fundo (a regra da semana), o bloqueio é a exceção
 * pontual por cima (folga, almoço, chuva).
 *
 * **Sobreposição é resolvida, não empilhada:** quando duas faixas do mesmo escopo se cruzam, **a
 * última da lista vence** no trecho comum (ver `flattenLayers`) — é o que permite a "exceção do dia"
 * cobrir o padrão semanal sem o app ter de recortar faixas na mão. Um minuto tem, portanto, no máximo
 * **uma** destinação, e é a mesma que o `layerAtMinute` devolve.
 *
 * @param id Chave estável da faixa.
 * @param start Início (parede local).
 * @param end Fim (parede local). Duração ≤ 0 é ignorada no desenho e no `layerAt`.
 * @param resourceId Coluna alvo; `null` = **todas** as colunas (regra da arena/estabelecimento inteiro).
 * @param kind Chave do domínio, **agnóstica** (ex.: `"RENTAL"`, `"CLUBINHO"`, `"AULA"`). É por ela que
 *   a legenda e o estilo são resolvidos — a lib nunca interpreta o valor.
 * @param label Rótulo desta faixa. `null` ⇒ cai no rótulo da legenda daquele [kind] e, faltando os
 *   dois, no próprio [kind].
 * @param tone Override pontual do tom. `null` (o normal) ⇒ herda da legenda daquele [kind].
 * @param pattern Override pontual da textura. `null` (o normal) ⇒ herda da legenda daquele [kind].
 */
data class ScheduleLayer(
    val id: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val kind: String,
    val resourceId: String? = null,
    val label: String? = null,
    val tone: LayerTone? = null,
    val pattern: LayerPattern? = null,
)

/** Modo de visão do calendário. */
enum class CalendarViewMode {
    Day,
    Week,
    Month,
}

/** Um slot de horário (saída do motor de disponibilidade OU de `generateTimeSlots`). */
data class TimeSlot(
    /** Início em minutos-do-dia. */
    val startMin: Int,
    /** Fim em minutos-do-dia (opcional). */
    val endMin: Int? = null,
    /** Livre para escolha. */
    val available: Boolean,
    /** Rótulo custom; default "HH:mm" derivado de `startMin`. */
    val label: String? = null,
    /** Motivo da indisponibilidade (ex.: "passou", "ocupado") — a11y/tooltip. */
    val reason: String? = null,
)
