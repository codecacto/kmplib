package br.com.codecacto.kmplib.ui.components

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A conversão que liga o campo de data ao `DatePicker` do Material (2.181.0).
 *
 * O seletor do Material trabalha em **epoch millis UTC**, e é aqui que a data escolhida vira número
 * e volta a ser data. Converter no fuso do aparelho é o erro clássico do campo de data: quem está a
 * oeste de Greenwich abre o calendário no dia ANTERIOR ao que salvou, e o dia escolhido volta um dia
 * atrás ao ser confirmado. Estes testes travam o par nos dois sentidos.
 */
class AppDatePickerTest {

    @Test
    fun `a data vira o inicio do dia em UTC`() {
        val esperado = 1_788_566_400_000L // 2026-09-05T00:00:00Z
        assertEquals(esperado, dataParaMillisDoCalendario(LocalDate(2026, 9, 5)))
    }

    @Test
    fun `ida e volta devolve a MESMA data`() {
        val data = LocalDate(2026, 9, 5)
        assertEquals(data, millisDoCalendarioParaData(dataParaMillisDoCalendario(data)))
    }

    @Test
    fun `o ultimo instante do dia UTC ainda e o mesmo dia`() {
        // O DatePicker devolve o início do dia, mas o estado pode chegar com hora — o dia não pode
        // escorregar para o seguinte por causa disso.
        val inicio = dataParaMillisDoCalendario(LocalDate(2026, 9, 5))
        val quase = inicio + 86_399_999L
        assertEquals(LocalDate(2026, 9, 5), millisDoCalendarioParaData(quase))
    }

    @Test
    fun `virada de ano nao perde o dia`() {
        val ano = LocalDate(2026, 12, 31)
        assertEquals(ano, millisDoCalendarioParaData(dataParaMillisDoCalendario(ano)))
        val proximo = LocalDate(2027, 1, 1)
        assertEquals(proximo, millisDoCalendarioParaData(dataParaMillisDoCalendario(proximo)))
    }

    @Test
    fun `29 de fevereiro de ano bissexto sobrevive a ida e volta`() {
        val bissexto = LocalDate(2028, 2, 29)
        assertEquals(bissexto, millisDoCalendarioParaData(dataParaMillisDoCalendario(bissexto)))
    }
}
