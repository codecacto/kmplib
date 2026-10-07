package br.com.codecacto.kmplib.sync

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import org.koin.core.error.NoDefinitionFoundException
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/*
 * O `LocalRepository` NUNCA foi registrado pela lib: o `kmplib-sync` não depende de Koin, e quem
 * declara um `single` por entidade é o app. Estes testes travam três coisas:
 *
 * 1. um grafo montado SÓ com core + sync (sem `kmplib-firebase` no classpath deste módulo desde a
 *    2.260.0) resolve o `LocalRepository` de cada entidade — a suspeita de 07/out/2026 de que a
 *    2.260.0 tinha "tirado o registro" ao soltar o firebase;
 * 2. sem `named`, a 2ª declaração SOBRESCREVE a 1ª (o Koin indexa pela classe apagada);
 * 3. qualificador em `val` de topo declarada DEPOIS do `module { }` chega `null` ao `single` — a
 *    causa real do Conta Bênçãos fechar ao abrir (`No definition found for type
 *    'br.com.codecacto.kmplib.sync.LocalRepository' and qualifier 'counters'`).
 */

@Serializable
private data class Counter(val id: String, val count: Int = 0)

@Serializable
private data class Day(val id: String, val reps: Int = 0)

private object CounterEntity : SyncableEntity<Counter> {
    override val name: String = "counter"
    override val serializer: KSerializer<Counter> = Counter.serializer()
    override fun clientIdOf(model: Counter): String = model.id
    override fun serverIdOf(model: Counter): String? = null
    override fun updatedAtOf(model: Counter): String? = null
}

private object DayEntity : SyncableEntity<Day> {
    override val name: String = "day"
    override val serializer: KSerializer<Day> = Day.serializer()
    override fun clientIdOf(model: Day): String = model.id
    override fun serverIdOf(model: Day): String? = null
    override fun updatedAtOf(model: Day): String? = null
}

/** O padrão do KDoc: `named` literal dentro do `single`. */
private val moduloCorreto: Module = module {
    single<SyncStore> { FakeSyncStore() }
    single<LocalRepository<Counter>>(named("counters")) { LocalRepository(CounterEntity, get()) }
    single<LocalRepository<Day>>(named("days")) { LocalRepository(DayEntity, get()) }
}

/** Armadilha 2: duas entidades sem qualificador. */
private val moduloSemQualificador: Module = module {
    single<SyncStore> { FakeSyncStore() }
    single { LocalRepository(CounterEntity, get()) }
    single { LocalRepository(DayEntity, get()) }
}

/**
 * Armadilha 3, reproduzida com a MESMA ordem do arquivo do app: o módulo vem antes do qualificador.
 * Não reordene estas duas declarações — a ordem É o teste.
 */
private val moduloComQualificadorTardio: Module = module {
    single<SyncStore> { FakeSyncStore() }
    single(qualificadorTardio) { LocalRepository(CounterEntity, get()) }
}
private val qualificadorTardio = named("counters")

class LocalRepositoryKoinTest {

    @Test
    fun `grafo so com core e sync resolve o LocalRepository de cada entidade`() = runTest {
        val koin = koinApplication { modules(moduloCorreto) }.koin

        val counters = koin.get<LocalRepository<Counter>>(named("counters"))
        val days = koin.get<LocalRepository<Day>>(named("days"))

        counters.put(Counter("c1", 3))
        days.put(Day("2026-10-07", 3))

        assertEquals(3, counters.get("c1")?.count)
        assertEquals(3, days.get("2026-10-07")?.reps)
        // Cada qualificador é um singleton próprio, e o store é um só.
        assertSame(counters, koin.get<LocalRepository<Counter>>(named("counters")))
        assertEquals(listOf(Counter("c1", 3)), counters.getAll())
        assertEquals(listOf(Day("2026-10-07", 3)), days.getAll())
    }

    @Test
    fun `sem named a segunda declaracao sobrescreve a primeira`() = runTest {
        val koin = koinApplication { modules(moduloSemQualificador) }.koin

        // Pede o de Counter, recebe o de Day: o tipo genérico some em runtime.
        val pedido: LocalRepository<*> = koin.get<LocalRepository<Counter>>()
        val day = koin.get<LocalRepository<Day>>()
        day.put(Day("d1"))
        assertEquals(1, pedido.getAll().size, "o repositório 'de Counter' enxerga as linhas de Day")
    }

    @Test
    fun `qualificador declarado depois do modulo chega nulo e a resolucao falha`() {
        val koin = koinApplication { modules(moduloComQualificadorTardio) }.koin

        assertFailsWith<NoDefinitionFoundException> {
            koin.get<LocalRepository<Counter>>(qualificadorTardio)
        }
        // Foi registrado SEM qualificador — exatamente o que o app não pede.
        koin.get<LocalRepository<Counter>>()
    }
}
