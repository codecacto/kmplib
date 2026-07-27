package br.com.codecacto.kmplib.monetization.alert

import br.com.codecacto.kmplib.observability.CrashLevel
import br.com.codecacto.kmplib.observability.CrashReporterConfig
import br.com.codecacto.kmplib.observability.FakeCrashReporter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentAlertReporterTest {

    private fun reporterAtivo() = FakeCrashReporter().apply {
        init(CrashReporterConfig(dsn = "https://k@errors.codecacto.com.br/1", environment = "test", release = "app@1+1"))
    }

    @Test
    fun envia_evento_com_titulo_estavel_nivel_e_tags() {
        val crash = reporterAtivo()
        val alertas = PaymentAlertReporter(crash, projeto = "super-8")

        val enviado = alertas.report(
            PaymentAlertKind.OfertaCentralIndisponivel,
            detalhe = "oferta=0 pacotes=2 token=ok",
        )

        assertTrue(enviado)
        val evento = crash.messages.single()
        assertEquals(PaymentAlertKind.OfertaCentralIndisponivel.titulo, evento.message)
        assertEquals(CrashLevel.Error, evento.level)
        assertEquals("pagamento", evento.tags["area"])
        assertEquals("super-8", evento.tags["projeto"])
        assertEquals("oferta_central_indisponivel", evento.tags["tipo"])
        assertEquals("oferta=0 pacotes=2 token=ok", evento.tags["detalhe"])
    }

    @Test
    fun titulo_nao_carrega_dado_variavel() {
        // O GlitchTip agrupa issue por titulo: contador no titulo = issue nova a cada ocorrencia =
        // enxurrada no Discord. O variavel vive na tag `detalhe`.
        val crash = reporterAtivo()
        val a = PaymentAlertReporter(crash, projeto = "p", umaVezPorSessao = false)

        a.report(PaymentAlertKind.PaywallSemPlano, detalhe = "oferta=0 pacotes=0")
        a.report(PaymentAlertKind.PaywallSemPlano, detalhe = "oferta=0 pacotes=3")

        assertEquals(1, crash.messages.map { it.message }.toSet().size)
        assertEquals(2, crash.messages.size)
    }

    @Test
    fun paywall_sem_plano_e_fatal_o_pior_caso_do_funil() {
        assertEquals(CrashLevel.Fatal, PaymentAlertKind.PaywallSemPlano.nivel)
        // Fallback ainda vende — nao pode ser fatal, mas tambem nao pode ser warning.
        assertEquals(CrashLevel.Error, PaymentAlertKind.OfertaCentralIndisponivel.nivel)
    }

    @Test
    fun repete_na_sessao_nao_reenvia() {
        val crash = reporterAtivo()
        val alertas = PaymentAlertReporter(crash, projeto = "super-8")

        assertTrue(alertas.report(PaymentAlertKind.OfertaCentralIndisponivel))
        assertFalse(alertas.report(PaymentAlertKind.OfertaCentralIndisponivel))
        assertFalse(alertas.report(PaymentAlertKind.OfertaCentralIndisponivel))

        assertEquals(1, crash.messages.size)
    }

    @Test
    fun tipos_diferentes_sao_reportados_independentemente() {
        val crash = reporterAtivo()
        val alertas = PaymentAlertReporter(crash, projeto = "super-8")

        alertas.report(PaymentAlertKind.OfertaCentralIndisponivel)
        alertas.report(PaymentAlertKind.CompraFalhou, detalhe = "code=NETWORK_ERROR")

        assertEquals(2, crash.messages.size)
    }

    @Test
    fun reset_libera_reenvio() {
        val crash = reporterAtivo()
        val alertas = PaymentAlertReporter(crash, projeto = "super-8")

        alertas.report(PaymentAlertKind.CompraFalhou)
        alertas.reset(PaymentAlertKind.CompraFalhou)

        assertTrue(alertas.report(PaymentAlertKind.CompraFalhou))
        assertEquals(2, crash.messages.size)
    }

    @Test
    fun reporter_inativo_nao_envia_e_avisa_pelo_retorno() {
        // DSN ausente => CrashReporter no-op. O alerta de pagamento morre aqui, e quem chamou
        // precisa saber disso (o retorno false) em vez de achar que avisou o fundador.
        val crash = FakeCrashReporter().apply { init(CrashReporterConfig("", "test", "app@1+1")) }
        val alertas = PaymentAlertReporter(crash, projeto = "super-8")

        assertFalse(alertas.report(PaymentAlertKind.PaywallSemPlano))
        assertTrue(crash.messages.isEmpty())
    }

    @Test
    fun nivel_pode_ser_sobrescrito_pelo_chamador() {
        val crash = reporterAtivo()
        val alertas = PaymentAlertReporter(crash, projeto = "super-8")

        alertas.report(PaymentAlertKind.EntitlementIndisponivel, nivel = CrashLevel.Error)

        assertEquals(CrashLevel.Error, crash.messages.single().level)
    }

    // ===== Rede de seguranca de privacidade: o que sai do device =====

    @Test
    fun detalhe_preserva_diagnostico_util() {
        val crash = reporterAtivo()
        PaymentAlertReporter(crash, projeto = "super-8")
            .report(PaymentAlertKind.PaywallSemPlano, "oferta=0 leitura=falhou pacotes=2 token=ausente")

        // `token=ok`/`token=ausente` NAO podem ser redigidos: e o diagnostico que resolve o incidente.
        assertEquals(
            "oferta=0 leitura=falhou pacotes=2 token=ausente",
            crash.messages.single().tags["detalhe"],
        )
    }

    @Test
    fun detalhe_redige_email_cpf_e_telefone() {
        val crash = reporterAtivo()
        val alertas = PaymentAlertReporter(crash, projeto = "p", umaVezPorSessao = false)

        alertas.report(PaymentAlertKind.CompraFalhou, "falhou para joao@exemplo.com.br")
        alertas.report(PaymentAlertKind.CompraFalhou, "cpf 123.456.789-00 recusado")
        alertas.report(PaymentAlertKind.CompraFalhou, "contato (65) 99999-8888")

        val detalhes = crash.messages.map { it.tags["detalhe"] ?: "" }
        assertTrue(detalhes.none { it.contains("joao") || it.contains("exemplo.com") })
        assertTrue(detalhes.none { it.contains("123.456.789") })
        assertTrue(detalhes.none { it.contains("99999") })
        assertTrue(detalhes.all { it.contains("[oculto]") })
    }

    @Test
    fun detalhe_redige_chave_de_api_vinda_de_mensagem_de_sdk() {
        // Caso real: `exception.message` do Firebase traz a URL da requisicao com ?key=…
        val crash = reporterAtivo()
        PaymentAlertReporter(crash, projeto = "p").report(
            PaymentAlertKind.IdentidadeAusente,
            "erro em https://identitytoolkit.googleapis.com/v1/accounts?key=AIzaSyD1234567890abcdefghij",
        )

        val detalhe = crash.messages.single().tags["detalhe"] ?: ""
        assertTrue(detalhe.contains("[oculto]"), detalhe)
        assertFalse(detalhe.contains("AIzaSy"), detalhe)
    }

    @Test
    fun detalhe_e_truncado() {
        val crash = reporterAtivo()
        PaymentAlertReporter(crash, projeto = "p")
            .report(PaymentAlertKind.CompraFalhou, "erro " + "x ".repeat(400))

        val detalhe = crash.messages.single().tags["detalhe"] ?: ""
        assertTrue(detalhe.length <= PaymentAlertReporter.LIMITE_DETALHE + 1, "len=${detalhe.length}")
    }

    @Test
    fun tags_extra_tambem_passam_pelo_redator() {
        val crash = reporterAtivo()
        PaymentAlertReporter(crash, projeto = "p").report(
            PaymentAlertKind.CompraFalhou,
            tagsExtra = mapOf("usuario" to "maria@exemplo.com"),
        )

        assertEquals("[oculto]", crash.messages.single().tags["usuario"])
    }

    @Test
    fun tags_extra_do_chamador_entram_no_evento() {
        val crash = reporterAtivo()
        val alertas = PaymentAlertReporter(crash, projeto = "super-8")

        alertas.report(PaymentAlertKind.CompraFalhou, tagsExtra = mapOf("plataforma" to "android"))

        assertEquals("android", crash.messages.single().tags["plataforma"])
    }
}
