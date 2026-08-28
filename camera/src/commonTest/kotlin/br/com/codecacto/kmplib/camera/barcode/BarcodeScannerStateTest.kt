package br.com.codecacto.kmplib.camera.barcode

import br.com.codecacto.kmplib.platform.permission.PermissionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Máquina de estados da tela de scanner. Cada caminho de falha tem um estado NOMEADO — a tela
 * nunca pode ser um preview preto sem explicação nem saída.
 */
class BarcodeScannerStateTest {

    @Test
    fun `sem permissao ainda pedivel, o estado pede permissao`() {
        assertEquals(
            BarcodeScannerState.PermissionRequired,
            barcodeScannerStateOf(PermissionStatus.NOT_REQUESTED, null),
        )
        assertEquals(
            BarcodeScannerState.PermissionRequired,
            barcodeScannerStateOf(PermissionStatus.DENIED, BarcodeCameraStatus.Ready()),
        )
    }

    @Test
    fun `negacao definitiva leva ao caminho das Configuracoes`() {
        assertEquals(
            BarcodeScannerState.PermissionPermanentlyDenied,
            barcodeScannerStateOf(PermissionStatus.PERMANENTLY_DENIED, null),
        )
    }

    @Test
    fun `a permissao tem precedencia sobre a situacao da camera`() {
        // Sem permissão a câmera nem é ligada: reportar "falha de inicialização" para quem só não
        // deu acesso mandaria o usuário para o botão errado.
        assertEquals(
            BarcodeScannerState.PermissionRequired,
            barcodeScannerStateOf(PermissionStatus.DENIED, BarcodeCameraStatus.Failed("boom")),
        )
    }

    @Test
    fun `com permissao, o estado segue a situacao da camera`() {
        assertEquals(
            BarcodeScannerState.Starting,
            barcodeScannerStateOf(PermissionStatus.GRANTED, null),
        )
        assertEquals(
            BarcodeScannerState.Scanning,
            barcodeScannerStateOf(PermissionStatus.GRANTED, BarcodeCameraStatus.Ready(true)),
        )
        assertEquals(
            BarcodeScannerState.CameraUnavailable,
            barcodeScannerStateOf(PermissionStatus.GRANTED, BarcodeCameraStatus.Unavailable),
        )
    }

    @Test
    fun `falha de inicializacao preserva a mensagem tecnica para a tela`() {
        val state = barcodeScannerStateOf(
            PermissionStatus.GRANTED,
            BarcodeCameraStatus.Failed("bindToLifecycle falhou"),
        )
        assertEquals(BarcodeScannerState.InitializationFailed("bindToLifecycle falhou"), state)
    }

    @Test
    fun `derivados isLive e isPermissionIssue classificam os estados`() {
        assertTrue(BarcodeScannerState.Scanning.isLive)
        assertFalse(BarcodeScannerState.Starting.isLive)
        assertFalse(BarcodeScannerState.InitializationFailed().isLive)

        assertTrue(BarcodeScannerState.PermissionRequired.isPermissionIssue)
        assertTrue(BarcodeScannerState.PermissionPermanentlyDenied.isPermissionIssue)
        assertFalse(BarcodeScannerState.CameraUnavailable.isPermissionIssue)
    }

    @Test
    fun `lanterna so e anunciada quando o aparelho tem uma`() {
        assertFalse((BarcodeCameraStatus.Ready() as BarcodeCameraStatus.Ready).torchAvailable)
        assertTrue(BarcodeCameraStatus.Ready(torchAvailable = true).torchAvailable)
    }
}
