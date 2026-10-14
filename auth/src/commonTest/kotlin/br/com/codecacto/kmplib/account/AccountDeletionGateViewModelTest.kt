package br.com.codecacto.kmplib.account

import br.com.codecacto.kmplib.core.network.ReauthRequiredException
import br.com.codecacto.kmplib.ui.components.typedConfirmationMatches
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 2.301.0 — o portão da conta com exclusão programada: consulta pós-login, restaurar, sair, erro. */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountDeletionGateViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Repositório falso com o mesmo efeito no estado que o real. */
    private class FakeRepo(private val state: AccountDeletionState) : AccountDeletionRepository {
        var statusResult: Result<AccountDeletionStatus> = Result.success(AccountDeletionStatus.NotPending)
        var restoreResult: Result<AccountDeletionStatus> = Result.success(AccountDeletionStatus.NotPending)
        var restoreGate: CompletableDeferred<Unit>? = null
        var statusCalls = 0
        var restoreCalls = 0

        override suspend fun requestDeletion(confirmation: String?) = Result.success(AccountDeletionStatus(pending = true))

        override suspend fun deletionStatus(): Result<AccountDeletionStatus> {
            statusCalls++
            return statusResult.onSuccess { state.report(it) }
        }

        override suspend fun restore(): Result<AccountDeletionStatus> {
            restoreCalls++
            restoreGate?.await()
            return restoreResult.onSuccess { if (it.pending) state.report(it) else state.clear() }
        }
    }

    private val pendente = AccountDeletionStatus(pending = true, daysRemaining = 12)

    @Test
    fun aposLogin_consulta_ePendenteCobre() = runTest {
        val st = AccountDeletionState()
        val repo = FakeRepo(st).apply { statusResult = Result.success(pendente) }
        val vm = AccountDeletionGateViewModel(repo, st)
        assertFalse(vm.state.value.isBlocking)

        vm.dispatch(AccountDeletionGateAction.AccountChanged("u1"))
        assertEquals(1, repo.statusCalls)
        assertTrue(vm.state.value.isBlocking)
        assertEquals(12, vm.state.value.pending?.daysRemaining)

        vm.dispatch(AccountDeletionGateAction.AccountChanged("u1"))
        assertEquals(1, repo.statusCalls, "a mesma conta (rotação, recomposição) não consulta de novo")
    }

    @Test
    fun deslogado_naoConsulta_eLimpa() = runTest {
        val st = AccountDeletionState()
        st.report(pendente)
        val repo = FakeRepo(st)
        val vm = AccountDeletionGateViewModel(repo, st)
        vm.dispatch(AccountDeletionGateAction.AccountChanged(null))
        assertEquals(0, repo.statusCalls)
        assertFalse(vm.state.value.isBlocking)
    }

    @Test
    fun consultaQueFalha_naoBloqueia() = runTest {
        val st = AccountDeletionState()
        val repo = FakeRepo(st).apply {
            statusResult = Result.failure(AccountDeletionRequestException(-1, "Sem conexão"))
        }
        val vm = AccountDeletionGateViewModel(repo, st)
        vm.dispatch(AccountDeletionGateAction.AccountChanged("u1"))
        assertFalse(vm.state.value.isBlocking, "quem bloqueia é o 403 do servidor")
    }

    @Test
    fun o403DaRede_cobreSemConsulta() = runTest {
        val st = AccountDeletionState()
        val vm = AccountDeletionGateViewModel(FakeRepo(st), st)
        assertTrue(st.reportIfPending(403, """{"code":"ACCOUNT_PENDING_DELETION","details":{"daysRemaining":2}}"""))
        assertEquals(2, vm.state.value.pending?.daysRemaining)
    }

    @Test
    fun restaurar_tiraACobertura_eAvisa() = runTest {
        val st = AccountDeletionState()
        val repo = FakeRepo(st).apply { statusResult = Result.success(pendente) }
        val vm = AccountDeletionGateViewModel(repo, st)
        vm.dispatch(AccountDeletionGateAction.AccountChanged("u1"))

        val gate = CompletableDeferred<Unit>()
        repo.restoreGate = gate
        vm.dispatch(AccountDeletionGateAction.Restore)
        assertTrue(vm.state.value.isRestoring)
        vm.dispatch(AccountDeletionGateAction.Restore)
        vm.dispatch(AccountDeletionGateAction.SignOut)
        assertEquals(1, repo.restoreCalls, "toque duplo não restaura duas vezes")

        gate.complete(Unit)
        assertEquals(AccountDeletionGateEffect.Restored, vm.effect.first())
        assertFalse(vm.state.value.isBlocking)
        assertFalse(vm.state.value.isRestoring)
        assertFalse(st.isPending)
    }

    @Test
    fun restaurar_falha_mostraErro_eContinuaCoberto() = runTest {
        val st = AccountDeletionState()
        val repo = FakeRepo(st).apply {
            statusResult = Result.success(pendente)
            restoreResult = Result.failure(AccountDeletionRequestException(-1, "Sem conexão com a internet"))
        }
        val vm = AccountDeletionGateViewModel(repo, st)
        vm.dispatch(AccountDeletionGateAction.AccountChanged("u1"))
        vm.dispatch(AccountDeletionGateAction.Restore)

        val erro = assertNotNull(vm.state.value.restoreError)
        assertEquals("Sem conexão com a internet", erro.message)
        assertTrue(erro.isOffline)
        assertTrue(vm.state.value.isBlocking)
        assertFalse(vm.state.value.isRestoring)

        vm.dispatch(AccountDeletionGateAction.DismissError)
        assertNull(vm.state.value.restoreError)
    }

    @Test
    fun restaurar_falhaSemFrase_ouReauth_usaOTextoPadrao() = runTest {
        val st = AccountDeletionState()
        val repo = FakeRepo(st).apply {
            statusResult = Result.success(pendente)
            restoreResult = Result.failure(AccountDeletionRequestException(500, ""))
        }
        val vm = AccountDeletionGateViewModel(repo, st)
        vm.dispatch(AccountDeletionGateAction.AccountChanged("u1"))
        vm.dispatch(AccountDeletionGateAction.Restore)
        assertEquals(AccountDeletionGateError(message = null), vm.state.value.restoreError)

        repo.restoreResult = Result.failure(ReauthRequiredException(300L, "x"))
        vm.dispatch(AccountDeletionGateAction.Restore)
        assertEquals(AccountDeletionGateError(message = null), vm.state.value.restoreError)
    }

    @Test
    fun restaurar_servidorAindaPendente_eErro() = runTest {
        val st = AccountDeletionState()
        val repo = FakeRepo(st).apply {
            statusResult = Result.success(pendente)
            restoreResult = Result.success(pendente)
        }
        val vm = AccountDeletionGateViewModel(repo, st)
        vm.dispatch(AccountDeletionGateAction.AccountChanged("u1"))
        vm.dispatch(AccountDeletionGateAction.Restore)
        assertTrue(vm.state.value.isBlocking)
        assertNotNull(vm.state.value.restoreError)
    }

    @Test
    fun sair_pedeOLogout_eACoberturaFicaAteAContaSair() = runTest {
        val st = AccountDeletionState()
        val repo = FakeRepo(st).apply { statusResult = Result.success(pendente) }
        val vm = AccountDeletionGateViewModel(repo, st)
        vm.dispatch(AccountDeletionGateAction.AccountChanged("u1"))

        vm.dispatch(AccountDeletionGateAction.SignOut)
        assertEquals(AccountDeletionGateEffect.SignOutRequested, vm.effect.first())
        assertTrue(vm.state.value.isBlocking, "sem piscar a área logada antes do logout terminar")

        vm.dispatch(AccountDeletionGateAction.AccountChanged(null))
        assertFalse(vm.state.value.isBlocking)
        assertFalse(st.isPending)
    }

    @Test
    fun trocaDeConta_naoHerdaOBloqueio_eConsultaANova() = runTest {
        val st = AccountDeletionState()
        val repo = FakeRepo(st).apply { statusResult = Result.success(pendente) }
        val vm = AccountDeletionGateViewModel(repo, st)
        vm.dispatch(AccountDeletionGateAction.AccountChanged("u1"))
        assertTrue(vm.state.value.isBlocking)

        repo.statusResult = Result.success(AccountDeletionStatus.NotPending)
        vm.dispatch(AccountDeletionGateAction.AccountChanged("u2"))
        assertEquals(2, repo.statusCalls)
        assertFalse(vm.state.value.isBlocking)
    }

    @Test
    fun restaurarSemPendencia_naoFazNada() = runTest {
        val st = AccountDeletionState()
        val repo = FakeRepo(st)
        val vm = AccountDeletionGateViewModel(repo, st)
        vm.dispatch(AccountDeletionGateAction.Restore)
        assertEquals(0, repo.restoreCalls)
    }

    // ---------------------------------------------------------------- textos e regras puras

    @Test
    fun confirmacao_EXCLUIR_ignoraCaixaEEspacos() {
        assertTrue(typedConfirmationMatches("EXCLUIR", ACCOUNT_DELETION_CONFIRMATION_WORD))
        assertTrue(typedConfirmationMatches(" excluir ", ACCOUNT_DELETION_CONFIRMATION_WORD))
        assertTrue(typedConfirmationMatches("Ex Cluir", ACCOUNT_DELETION_CONFIRMATION_WORD))
        assertFalse(typedConfirmationMatches("EXCLUI", ACCOUNT_DELETION_CONFIRMATION_WORD))
        assertFalse(typedConfirmationMatches("", ACCOUNT_DELETION_CONFIRMATION_WORD))
        assertFalse(typedConfirmationMatches("EXCLUIR!", ACCOUNT_DELETION_CONFIRMATION_WORD))
    }

    @Test
    fun erroDoCampo_soDepoisDeDigitarAlgoQueNaoConfere() {
        assertNull(accountDeletionFieldError("", "EXCLUIR", "erro"), "campo intocado não fica vermelho")
        assertNull(accountDeletionFieldError("   ", "EXCLUIR", "erro"))
        assertEquals("erro", accountDeletionFieldError("EXC", "EXCLUIR", "erro"))
        assertNull(accountDeletionFieldError("excluir", "EXCLUIR", "erro"))
    }

    @Test
    fun prazoPorDias_semPromessaDeData() {
        val t = AccountPendingDeletionTexts(
            title = "após 30 dias do pedido",
            remainingDays = { fillDaysTemplate("Faltam %1\$d dias para liberar", it) },
            remainingOneDay = "Falta 1 dia para liberar",
            remainingNone = "O prazo já terminou",
            message = "m",
            restore = "Restaurar conta",
            signOut = "Sair",
            restoreFailed = "falhou",
        )
        assertEquals("Faltam 12 dias para liberar", t.remainingFor(12))
        assertEquals("Falta 1 dia para liberar", t.remainingFor(1))
        assertEquals("O prazo já terminou", t.remainingFor(0))
        assertEquals("O prazo já terminou", t.remainingFor(-2))
        assertNull(t.remainingFor(null), "sem prazo do servidor, sem linha")
        assertEquals("in 30 days", fillDaysTemplate("in %d days", 30))
    }

    @Test
    fun idsUnicosComPrefixo() {
        val ids = listOf(
            AccountDeletionTestTags.TELA, AccountDeletionTestTags.TITULO, AccountDeletionTestTags.PRAZO,
            AccountDeletionTestTags.MENSAGEM,
            AccountDeletionTestTags.ERRO, AccountDeletionTestTags.BTN_RESTAURAR, AccountDeletionTestTags.BTN_SAIR,
        )
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith("conta-exclusao-pendente") })
        assertEquals(30, ACCOUNT_DELETION_GRACE_DAYS)
    }
}
