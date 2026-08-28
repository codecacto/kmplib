package br.com.codecacto.kmplib.permissions

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contrato da matriz de permissão por módulo — as regras que mobile e web precisam aplicar
 * IGUAIS (a divergência entre `PermissionsEditor.kt` e `PermissionsDialog.tsx` do Influencer
 * motivou a promoção à lib).
 */
class PermissionMatrixTest {

    private val agenda = PermissionModuleSpec("AGENDA", "Agenda")
    private val finance = PermissionModuleSpec("FINANCE", "Financeiro")
    private val contents = PermissionModuleSpec("CONTENTS", "Conteúdos")
    private val modules = listOf(agenda, finance, contents)

    private val postFlag = PermissionFlagSpec(
        key = "contentsPost",
        label = "Pode publicar conteúdo",
        requiresModule = "CONTENTS",
        requiresLevel = PermissionLevel.EDIT,
    )

    // ---------------------------------------------------------------- níveis

    @Test
    fun niveis_comparam_por_ordinal() {
        assertTrue(PermissionLevel.EDIT > PermissionLevel.VIEW)
        assertTrue(PermissionLevel.VIEW > PermissionLevel.NONE)
        assertTrue(PermissionLevel.EDIT.atLeast(PermissionLevel.VIEW))
        assertFalse(PermissionLevel.VIEW.atLeast(PermissionLevel.EDIT))
        assertTrue(PermissionLevel.VIEW.grantsAccess)
        assertFalse(PermissionLevel.NONE.grantsAccess)
    }

    @Test
    fun parse_de_nivel_e_tolerante_a_caixa_e_espaco() {
        assertEquals(PermissionLevel.EDIT, PermissionLevel.fromWireOrNull("edit"))
        assertEquals(PermissionLevel.VIEW, PermissionLevel.fromWireOrNull(" VIEW "))
        assertNull(PermissionLevel.fromWireOrNull("SUPER"))
        assertNull(PermissionLevel.fromWireOrNull(""))
        assertNull(PermissionLevel.fromWireOrNull(null))
    }

    // --------------------------------------------------------------- estado

    @Test
    fun estado_e_imutavel_e_modulo_ausente_e_sem_acesso() {
        val inicial = PermissionMatrixState.Empty
        val alterado = inicial.withLevel(agenda, PermissionLevel.EDIT)

        assertEquals(PermissionLevel.NONE, inicial.levelOf(agenda))
        assertEquals(PermissionLevel.EDIT, alterado.levelOf(agenda))
        assertEquals(PermissionLevel.NONE, alterado.levelOf(finance))
        assertTrue(alterado.grants("AGENDA"))
        assertFalse(alterado.grants("FINANCE"))
    }

    // -------------------------------------------------- normalização (unificada)

    @Test
    fun normalizacao_remove_modulos_sem_acesso() {
        val state = PermissionMatrixState.Empty
            .withLevel(agenda, PermissionLevel.EDIT)
            .withLevel(finance, PermissionLevel.NONE)

        val normalizado = state.normalized()

        assertEquals(mapOf("AGENDA" to PermissionLevel.EDIT), normalizado.levels)
        assertEquals(mapOf("AGENDA" to "EDIT"), state.toModuleMap())
    }

    @Test
    fun normalizacao_preserva_modulo_desconhecido_do_cliente() {
        // Servidor concedeu um módulo que ESTE app ainda não renderiza: salvar não pode revogar.
        val state = PermissionMatrixWire.parse(mapOf("FUTURO" to "EDIT", "AGENDA" to "VIEW"))

        val enviado = state.normalized().levels

        assertEquals(PermissionLevel.EDIT, enviado["FUTURO"])
        assertEquals(PermissionLevel.VIEW, enviado["AGENDA"])
    }

    // ------------------------------------------------------------- validação

    @Test
    fun validacao_bloqueia_salvar_sem_nenhum_acesso() {
        val tudoNone = PermissionMatrixState.Empty
            .withLevel(agenda, PermissionLevel.NONE)
            .withLevel(finance, PermissionLevel.NONE)

        assertFalse(tudoNone.hasAnyAccess)
        assertEquals(PermissionMatrixIssue.NoAccess, tudoNone.validate())
        assertNull(tudoNone.validate(requireAnyAccess = false))

        val comAcesso = tudoNone.withLevel(finance, PermissionLevel.VIEW)
        assertTrue(comAcesso.hasAnyAccess)
        assertNull(comAcesso.validate())
    }

    // ------------------------------------------------ flags dependentes de módulo

    @Test
    fun flag_dependente_so_fica_disponivel_no_nivel_exigido() {
        val semEdit = PermissionMatrixState.Empty
            .withLevel(contents, PermissionLevel.VIEW)
            .withFlag(postFlag, true)

        assertFalse(semEdit.isFlagAvailable(postFlag))
        assertTrue(semEdit.isFlagChecked(postFlag)) // valor cru preservado
        assertFalse(semEdit.resolveFlag(postFlag)) // valor efetivo: não

        val comEdit = semEdit.withLevel(contents, PermissionLevel.EDIT)
        assertTrue(comEdit.isFlagAvailable(postFlag))
        assertTrue(comEdit.resolveFlag(postFlag))
    }

    @Test
    fun flag_sem_dependencia_vale_sozinha() {
        val livre = PermissionFlagSpec(key = "exportCsv", label = "Pode exportar")
        val state = PermissionMatrixState.Empty.withFlag(livre, true)

        assertTrue(state.isFlagAvailable(livre))
        assertTrue(state.resolveFlag(livre))
    }

    @Test
    fun normalizacao_desliga_flag_cuja_dependencia_caiu() {
        val state = PermissionMatrixState.Empty
            .withLevel(contents, PermissionLevel.EDIT)
            .withFlag(postFlag, true)
            .withLevel(contents, PermissionLevel.VIEW) // rebaixou depois de marcar

        assertEquals(false, state.normalized(listOf(postFlag)).flags["contentsPost"])
        assertEquals(mapOf("contentsPost" to false), state.toFlagMap(listOf(postFlag)))
    }

    @Test
    fun matriz_sem_flags_nao_inventa_campo() {
        val state = PermissionMatrixState.Empty.withLevel(agenda, PermissionLevel.EDIT)

        assertTrue(state.normalized().flags.isEmpty())
        assertTrue(state.toFlagMap().isEmpty())
    }

    // ---------------------------------------------------- fronteira (parse tolerante)

    @Test
    fun parse_ignora_nivel_desconhecido_e_chave_em_branco() {
        val state = PermissionMatrixWire.parse(
            mapOf(
                "AGENDA" to "EDIT",
                "FINANCE" to "SUPER", // valor que este cliente não sabe exibir
                "CONTENTS" to null,
                "   " to "VIEW",
            ),
        )

        assertEquals(PermissionLevel.EDIT, state.levelOf(agenda))
        assertEquals(PermissionLevel.NONE, state.levelOf(finance))
        assertEquals(PermissionLevel.NONE, state.levelOf(contents))
        assertEquals(1, state.levels.size)
    }

    @Test
    fun parse_de_mapa_nulo_devolve_estado_vazio() {
        val state = PermissionMatrixWire.parse(null)

        assertEquals(PermissionMatrixState.Empty, state)
        assertFalse(state.hasAnyAccess)
    }

    @Test
    fun round_trip_dto_do_app_preserva_o_que_foi_concedido() {
        val recebido = mapOf("AGENDA" to "EDIT", "FINANCE" to "NONE", "CONTENTS" to "EDIT")
        val state = PermissionMatrixWire.parse(recebido, mapOf("contentsPost" to true))

        val enviado = state.toModuleMap()

        assertEquals(mapOf("AGENDA" to "EDIT", "CONTENTS" to "EDIT"), enviado)
        assertEquals(true, state.toFlagMap(listOf(postFlag))["contentsPost"])
    }

    // ------------------------------------------------------------ envelope JSON

    @Test
    fun json_serializa_modules_com_as_flags_como_irmas() {
        val state = PermissionMatrixState.Empty
            .withLevel(agenda, PermissionLevel.VIEW)
            .withLevel(finance, PermissionLevel.NONE)
            .withLevel(contents, PermissionLevel.EDIT)
            .withFlag(postFlag, true)

        val json = PermissionMatrixJson.toJsonObject(state, listOf(postFlag))
        val modules = json[PermissionMatrixWire.MODULES_FIELD]!!.jsonObject

        assertEquals("VIEW", modules["AGENDA"]!!.jsonPrimitive.content)
        assertEquals("EDIT", modules["CONTENTS"]!!.jsonPrimitive.content)
        assertNull(modules["FINANCE"]) // NONE não é persistido
        assertEquals(true, json["contentsPost"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun json_le_envelope_tolerando_tipos_inesperados() {
        val source: JsonObject = buildJsonObject {
            put(
                PermissionMatrixWire.MODULES_FIELD,
                buildJsonObject {
                    put("AGENDA", "EDIT")
                    put("FINANCE", 7) // não é string → ignorado
                },
            )
            put("contentsPost", true)
            put("observacao", "texto qualquer") // não é booleano → não vira flag
        }

        val state = PermissionMatrixJson.fromJsonObject(source)

        assertEquals(PermissionLevel.EDIT, state.levelOf(agenda))
        assertEquals(PermissionLevel.NONE, state.levelOf(finance))
        assertTrue(state.isFlagChecked(postFlag))
        assertEquals(mapOf("contentsPost" to true), state.flags)
    }

    @Test
    fun json_invalido_ou_ausente_nao_lanca() {
        assertEquals(PermissionMatrixState.Empty, PermissionMatrixJson.fromJsonObject(null))
        assertEquals(PermissionMatrixState.Empty, PermissionMatrixJson.decodeFromString(null))
        assertEquals(PermissionMatrixState.Empty, PermissionMatrixJson.decodeFromString("  "))
        assertEquals(PermissionMatrixState.Empty, PermissionMatrixJson.decodeFromString("{quebrado"))
    }

    @Test
    fun json_round_trip_por_texto() {
        val state = PermissionMatrixState.Empty
            .withLevel(agenda, PermissionLevel.EDIT)
            .withFlag(postFlag, false)

        val texto = PermissionMatrixJson.encodeToString(state, listOf(postFlag))
        val voltou = PermissionMatrixJson.decodeFromString(texto)

        assertEquals(PermissionLevel.EDIT, voltou.levelOf(agenda))
        assertFalse(voltou.resolveFlag(postFlag))
        assertTrue(texto.contains("\"${PermissionMatrixWire.MODULES_FIELD}\""))
    }

    @Test
    fun json_de_modulos_com_primitivo_string_valida_o_nivel() {
        val source = buildJsonObject {
            put(
                PermissionMatrixWire.MODULES_FIELD,
                buildJsonObject { put("AGENDA", JsonPrimitive("view")) },
            )
        }

        assertEquals(PermissionLevel.VIEW, PermissionMatrixJson.fromJsonObject(source).levelOf(agenda))
    }

    @Test
    fun modulos_declarados_pelo_app_nao_vazam_para_a_lib() {
        // A lib só conhece chaves opacas: o conjunto de módulos é do app (Influencer ≠ TattooStudio).
        val tattoo = listOf(
            PermissionModuleSpec("AGENDA", "Agenda"),
            PermissionModuleSpec("FINANCEIRO", "Financeiro"),
            PermissionModuleSpec("CLIENTES", "Clientes"),
            PermissionModuleSpec("EMPRESA", "Empresa"),
        )
        val state = tattoo.fold(PermissionMatrixState.Empty) { acc, m ->
            acc.withLevel(m, PermissionLevel.VIEW)
        }

        assertEquals(4, state.toModuleMap().size)
        assertEquals("VIEW", state.toModuleMap()["EMPRESA"])
        assertEquals(modules.size, 3) // conjunto do Influencer segue independente
    }
}
