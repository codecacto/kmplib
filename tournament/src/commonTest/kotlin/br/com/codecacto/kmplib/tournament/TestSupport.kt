package br.com.codecacto.kmplib.tournament

import kotlin.random.Random

/** Sorteio determinístico: a verificação precisa ser reproduzível. */
internal fun fixedRandom(seed: Int): RandomSource = RandomSource.of(Random(seed))

internal fun ids(count: Int): List<Long> = List(count) { (it + 1).toLong() }

/** Participantes com `drawOrder` = id − 1 (o 1 foi sorteado primeiro). */
internal fun participants(vararg ids: Long): List<TournamentParticipant> =
    ids.map { TournamentParticipant(id = it, drawOrder = (it - 1).toInt()) }

internal fun isPowerOfTwo(value: Int) = value >= 2 && (value and (value - 1)) == 0

/** Vitória do lado A por pontos. */
internal fun win(a: Long, b: Long, score: Score.Points = Score.Points(1, 0)) =
    MatchResult(a, b, MatchOutcome.Decided(Side.A), score)

internal fun draw(a: Long, b: Long, goals: Int = 1) =
    MatchResult(a, b, MatchOutcome.Draw, Score.Points(goals, goals))

/** Vitória do lado A em sets: `setWin(1, 2, 6 to 4)`. */
internal fun setWin(a: Long, b: Long, vararg sets: Pair<Int, Int>) =
    MatchResult(a, b, MatchOutcome.Decided(Side.A), Score.Sets(sets.map { (x, y) -> SetScore(x, y) }))

/** Tabela fabricada: `table(10, 11)` = o 10 em 1º e o 11 em 2º. */
internal fun table(vararg ids: Long): List<StandingsRow> = ids.mapIndexed { position, id -> row(id, position + 1) }

internal fun row(
    id: Long,
    position: Int = 1,
    played: Int = 1,
    points: Int = 0,
    maxPoints: Int = 0,
    setsFor: Int = 0,
    setsAgainst: Int = 0,
    gamesFor: Int = 0,
    gamesAgainst: Int = 0,
    drawOrder: Int = id.toInt(),
    withdrawn: Boolean = false,
) = StandingsRow(
    position = position,
    participantId = id,
    drawOrder = drawOrder,
    played = played,
    wins = 0,
    draws = 0,
    losses = 0,
    points = points,
    maxPoints = maxPoints,
    setsFor = setsFor,
    setsAgainst = setsAgainst,
    gamesFor = gamesFor,
    gamesAgainst = gamesAgainst,
    withdrawn = withdrawn,
)

internal fun List<StandingsRow>.order(): List<Long> = map { it.participantId }
