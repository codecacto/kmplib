package br.com.codecacto.kmplib.ui.screens.login

/**
 * **Ids canônicos da tela de login para automação de UI** — o mesmo desenho de [PaywallTestTags], e
 * pelo mesmo motivo: quem renderiza a tela é a lib, então um id plantado no app não alcançaria nada.
 *
 * Login e pagamento são os dois fluxos onde uma quebra silenciosa custa cliente. O de pagamento já
 * era automatizável; este fechava o outro lado — sem ids, um flow de login só consegue procurar por
 * TEXTO ("Entrar", "E-mail"), e aí ele quebra a cada ajuste de copy, em cada idioma, e o teste
 * "descobre" um defeito que não existe.
 *
 * Para o Maestro/Appium enxergarem estes ids como `resource-id`, a raiz da hierarquia declara
 * `testTagsAsResourceId` — o `AppTheme` faz isso desde a 2.107.0, sem o app configurar nada.
 *
 * ## O vocabulário é o mesmo da web
 *
 * `login-input-email`, `login-btn-entrar`… são as mesmas strings que o formulário da weblib emite.
 * App e portal do mesmo produto se automatizam com um vocabulário só — foi essa a decisão tomada
 * quando o paywall ganhou os dele.
 */
object LoginTestTags {

    /** Campo de e-mail. */
    const val INPUT_EMAIL: String = "login-input-email"

    /** Campo de senha. */
    const val INPUT_SENHA: String = "login-input-senha"

    /** CTA principal — entrar com e-mail e senha. */
    const val BOTAO_ENTRAR: String = "login-btn-entrar"

    /** "Esqueci minha senha". */
    const val BOTAO_ESQUECI_SENHA: String = "login-btn-esqueci-senha"

    /** Link para a tela de cadastro. */
    const val BOTAO_CADASTRAR: String = "login-btn-cadastrar"

    /**
     * Botões de login social. **Um id por provedor**, nunca um só para os dois — a mesma armadilha
     * do CTA do paywall: id compartilhado faz o teste tocar no primeiro da tela e passar verde tendo
     * exercitado o provedor errado.
     */
    const val BOTAO_GOOGLE: String = "login-btn-google"
    const val BOTAO_APPLE: String = "login-btn-apple"

    /**
     * Mensagem de erro do login (credencial inválida, rede, provedor recusado).
     *
     * É o id que distingue "a tela não abriu" de "a tela abriu e recusou a senha" — sem ele, um teste
     * de credencial errada não tem como afirmar que o app AVISOU, e login que falha em silêncio é
     * exatamente o defeito que ninguém percebe até o cliente reclamar.
     */
    const val ERRO: String = "login-erro"
}
