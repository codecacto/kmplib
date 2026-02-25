package br.com.codecacto.kmplib.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import br.com.codecacto.kmplib.ui.components.BottomNavItem
import br.com.codecacto.kmplib.ui.components.NavigationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Testes para componentes de navegação
 */
class NavigationTest {

    // BottomNavItem tests
    @Test
    fun `BottomNavItem can be created`() {
        val item = BottomNavItem(
            icon = Icons.Default.Home,
            label = "Home",
            route = "home"
        )
        assertEquals("Home", item.label)
        assertEquals("home", item.route)
        assertNull(item.badge)
    }

    @Test
    fun `BottomNavItem can have badge`() {
        val item = BottomNavItem(
            icon = Icons.Default.Home,
            label = "Home",
            route = "home",
            badge = 5
        )
        assertEquals(5, item.badge)
    }

    @Test
    fun `BottomNavItem copy works correctly`() {
        val original = BottomNavItem(
            icon = Icons.Default.Home,
            label = "Home",
            route = "home"
        )
        val updated = original.copy(badge = 10)
        assertEquals(10, updated.badge)
        assertEquals(original.label, updated.label)
        assertEquals(original.route, updated.route)
    }

    // NavigationType tests
    @Test
    fun `NavigationType has all variants`() {
        val types = NavigationType.entries
        assertEquals(3, types.size)
        assert(NavigationType.NONE in types)
        assert(NavigationType.BACK in types)
        assert(NavigationType.MENU in types)
    }
}
