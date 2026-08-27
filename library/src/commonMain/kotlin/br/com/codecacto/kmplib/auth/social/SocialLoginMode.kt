package br.com.codecacto.kmplib.auth.social

/**
 * Como este aplicativo negocia o login social — a escolha é **por projeto**, e é do fundador.
 *
 * Os dois caminhos terminam no mesmo lugar (sessão own-auth adotada) e são igualmente oficiais. O
 * que muda é **quem conversa com o provedor**, e o custo disso na conta do Google/Apple.
 *
 * ## [NATIVE] — o aplicativo conversa com o provedor
 * Credential Manager no Android, `GoogleSignIn-iOS`/`AuthenticationServices` no iOS. É a experiência
 * que a plataforma desenha: o seletor de contas do sistema, sem sair do app.
 *
 * **O preço:** o Google identifica o aplicativo pelo par *package + SHA-1* (Android) e pelo *bundle
 * id* (iOS), e exige **um cliente OAuth para cada um** — mais o cliente Web, que vira o `aud` do
 * `idToken`. Uma família de aplicativos sobre a mesma base de código multiplica isso, e o projeto do
 * Google Cloud tem teto: a família de 13 apps do Meu Advogado bateu nele em 24/ago/2026, com sintoma
 * mudo (o console mostra a impressão digital cadastrada, o `google-services.json` sai sem o cliente,
 * e o botão devolve `DEVELOPER_ERROR`).
 *
 * ## [BACKEND] — o nosso servidor conversa com o provedor
 * O app abre o navegador do sistema numa rota nossa, o backend negocia, e a sessão volta por *deep
 * link* ([SocialBrowserLogin] + `authLocalSocialFlowRoutes` da backlib). Para o provedor isso é um
 * fluxo web comum: ele **nunca vê o aplicativo**, então **um cliente Web serve o portfólio inteiro**
 * e app novo entra só na allowlist do backend — sem cadastro no console, sem SHA-1, sem cliente por
 * bundle id. É o desenho da RFC 8252 com o padrão BFF.
 *
 * **O preço:** o login sai do app e acontece numa aba do navegador (é o que os aplicativos grandes
 * fazem), e exige um backend nosso publicado — o que um app 100% offline não tem.
 *
 * ## Qual usar
 * Projeto **interno da empresa** nasce em [BACKEND] (default da casca): é o que faz o portfólio
 * crescer sem esbarrar no teto. Projeto de **parceria ou de cliente**, que publica na conta dele e
 * traz o próprio projeto no Google Cloud, pode ficar em [NATIVE] — ali o teto não é problema, e a
 * experiência nativa é a preferida. **A decisão é do fundador, perguntada em todo projeto novo.**
 */
enum class SocialLoginMode {
    /** Fluxo nativo da plataforma. Exige um cliente OAuth por app (e por SHA-1, no Android). */
    NATIVE,

    /** Fluxo pelo navegador contra o nosso backend. Um cliente OAuth serve todos os apps. */
    BACKEND,
}
