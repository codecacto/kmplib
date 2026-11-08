package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/** O id de cada chip do [ThemeChipGrid] é contrato com os flows Maestro (2.351.0). */
class ThemeChipGridTestTagTest {

    @Test
    fun `prefixo padrao e chip`() {
        assertEquals("chip", THEME_CHIP_TEST_TAG_PREFIX)
    }

    @Test
    fun `tag do chip junta prefixo e id`() {
        assertEquals("chip-ansiedade", themeChipTestTag(THEME_CHIP_TEST_TAG_PREFIX, "ansiedade"))
        assertEquals("tema-luto", themeChipTestTag("tema", "luto"))
    }
}
