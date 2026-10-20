package br.com.codecacto.kmplib.alarm

/**
 * Um alarme agendado, como a lib o guarda para reagendar depois do reboot (Android).
 * Serialização própria, sem depender de JSON: `kind|triggerAt|label` (o rótulo vai por último e pode
 * conter `|`).
 */
internal data class AlarmRecord(
    val id: Int,
    val kind: AlarmKind,
    val triggerAtMillis: Long,
    val label: String,
) {
    fun encode(): String = "${kind.name}|$triggerAtMillis|$label"

    companion object {
        fun decode(id: Int, raw: String?): AlarmRecord? {
            if (raw == null) return null
            val parts = raw.split('|', limit = 3)
            if (parts.size != 3) return null
            val kind = AlarmKind.entries.firstOrNull { it.name == parts[0] } ?: return null
            val at = parts[1].toLongOrNull() ?: return null
            return AlarmRecord(id, kind, at, parts[2])
        }
    }
}

/**
 * Quais registros reagendar depois do boot, dado o relógio [nowMillis]: os futuros; os que venceram
 * com o aparelho desligado há no máximo [graceMillis] disparam já (o vigia ainda precisa saber); os
 * mais velhos saem. Pura, para teste.
 */
internal fun recordsToRestore(
    records: List<AlarmRecord>,
    nowMillis: Long,
    graceMillis: Long = 15 * 60_000L,
): Pair<List<AlarmRecord>, List<AlarmRecord>> {
    val (keep, drop) = records.partition { it.triggerAtMillis >= nowMillis - graceMillis }
    return keep to drop
}
