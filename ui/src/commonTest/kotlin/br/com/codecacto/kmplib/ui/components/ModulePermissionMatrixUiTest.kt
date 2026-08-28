package br.com.codecacto.kmplib.ui.components

import br.com.codecacto.kmplib.permissions.PermissionLevel
import br.com.codecacto.kmplib.permissions.PermissionMatrixIssue
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Os dois helpers de UI da matriz de permissão. Moravam no `PermissionMatrixTest` do módulo
 * `permissions`, que é onde o resto da matriz é testado — e ali eles obrigavam `kmplib-platform`
 * a enxergar `kmplib-ui`, que é o contrário da direção real (a tela usa a matriz, não o inverso).
 */
class ModulePermissionMatrixUiTest {

    @Test
    fun textos_default_espelham_o_par_web() {
        val texts = PermissionMatrixTexts()

        assertEquals("Sem acesso", texts.labelFor(PermissionLevel.NONE))
        assertEquals("Ver", texts.labelFor(PermissionLevel.VIEW))
        assertEquals("Ver e editar", texts.labelFor(PermissionLevel.EDIT))
        assertEquals(texts.noAccessError, texts.messageFor(PermissionMatrixIssue.NoAccess))
    }

    @Test
    fun tom_do_selo_no_modo_somente_leitura() {
        assertEquals(StatusTone.NEUTRAL, permissionLevelTone(PermissionLevel.NONE))
        assertEquals(StatusTone.INFO, permissionLevelTone(PermissionLevel.VIEW))
        assertEquals(StatusTone.SUCCESS, permissionLevelTone(PermissionLevel.EDIT))
    }
}
