# Sincronizacao de usuario no FeedbackService

## Contexto

A `kmplib` possui dois caminhos principais para envio de feedback:

- `FeedbackScreen`: tela completa de feedback.
- `AppReviewDialog`: dialog de avaliacao que pode persistir feedback negativo.

O problema identificado foi que alguns feedbacks chegaram somente com mensagem e data, sem dados do usuario. Isso acontece porque o `FeedbackService` so grava `usuarioId` e `usuarioEmail` quando o aplicativo consumidor chama:

```kotlin
FeedbackService.updateUser(
    userId = user.id,
    userEmail = user.email
)
```

Sem essa chamada, o feedback e persistido sem vinculo com a conta logada.

## Regra atual na kmplib

A regra desejada e:

- O formulario deve nascer vazio.
- A mensagem e obrigatoria.
- O WhatsApp e obrigatorio.
- O e-mail e opcional.
- O e-mail da conta logada nao deve preencher automaticamente o campo de e-mail do formulario.
- `usuarioId` e `usuarioEmail` sao apenas metadados internos para rastrear qual conta logada enviou o feedback.

Assim, o contato principal para resposta e o WhatsApp digitado pela pessoa, enquanto o usuario logado fica registrado para auditoria e cruzamento interno.

## Alteracoes ja feitas na kmplib

Arquivos alterados:

- `library/src/commonMain/kotlin/br/com/codecacto/kmplib/ui/screens/feedback/FeedbackScreen.kt`
- `library/src/commonMain/kotlin/br/com/codecacto/kmplib/ui/screens/feedback/FeedbackTexts.kt`
- `library/src/commonMain/kotlin/br/com/codecacto/kmplib/ui/components/AppReviewDialog.kt`
- `library/src/commonMain/kotlin/br/com/codecacto/kmplib/feedback/FeedbackService.kt`

Comportamento:

- `FeedbackScreen` exige WhatsApp com 11 digitos e mensagem preenchida.
- `FeedbackScreen` permite e-mail vazio, mas valida se for preenchido.
- `AppReviewDialog` tambem passa a pedir WhatsApp obrigatorio e e-mail opcional antes de persistir feedback negativo.
- `FeedbackService.sendFeedback` grava os dados digitados e faz `trim`, mas nao substitui o e-mail digitado pelo e-mail da conta.

## Por que ainda chamar updateUser nos apps

Mesmo com WhatsApp obrigatorio, `FeedbackService.updateUser(...)` continua necessario porque:

- permite saber qual conta logada enviou o feedback;
- ajuda a cruzar feedback com dados internos do app;
- evita feedbacks marcados como anonimos quando o usuario estava autenticado;
- nao altera o formulario e nao preenche nada para o usuario.

Essa chamada deve ser feita sempre que o estado de autenticacao mudar:

- apos login;
- ao restaurar sessao;
- apos logout, chamando `FeedbackService.updateUser()` para limpar os dados.

## Projetos encontrados em E:\CodeCacto

### Foco

Status: ja esta correto.

Arquivo encontrado:

```text
E:\CodeCacto\Foco\composeApp\src\commonMain\kotlin\br\com\codecacto\foco\App.kt
```

Ja existe chamada para:

```kotlin
FeedbackService.updateUser(
    userId = currentUser?.id.orEmpty(),
    userEmail = currentUser?.email.orEmpty()
)
```

Nao precisa alterar.

### locadora

Status: precisa alterar.

Arquivo:

```text
E:\CodeCacto\locadora\composeApp\src\commonMain\kotlin\br\com\codecacto\locadora\core\ui\RootNavigation.kt
```

Adicionar import:

```kotlin
import br.com.codecacto.kmplib.feedback.FeedbackService
```

Dentro de:

```kotlin
authRepository.observeAuthState().collect { user ->
```

adicionar:

```kotlin
FeedbackService.updateUser(
    userId = user?.id.orEmpty(),
    userEmail = user?.email.orEmpty()
)
```

Exemplo:

```kotlin
LaunchedEffect(Unit) {
    authRepository.observeAuthState().collect { user ->
        FeedbackService.updateUser(
            userId = user?.id.orEmpty(),
            userEmail = user?.email.orEmpty()
        )
        isAuthenticated = user != null
        isCheckingAuth = false
    }
}
```

### Meu Advogado

Status: precisa alterar.

Arquivo:

```text
E:\CodeCacto\Meu Advogado\composeApp\src\commonMain\kotlin\br\com\codecacto\meuadvogado\App.kt
```

Adicionar import:

```kotlin
import br.com.codecacto.kmplib.feedback.FeedbackService
```

Depois de obter `authRepository`, adicionar:

```kotlin
LaunchedEffect(Unit) {
    authRepository.currentUser.collect { user ->
        FeedbackService.updateUser(
            userId = user?.id.orEmpty(),
            userEmail = user?.email.orEmpty()
        )
    }
}
```

### Meu Fisio

Status: precisa alterar.

Arquivo:

```text
E:\CodeCacto\Meu Fisio\composeApp\src\commonMain\kotlin\br\com\codecacto\meufisio\App.kt
```

Adicionar import:

```kotlin
import br.com.codecacto.kmplib.feedback.FeedbackService
```

Depois de obter `authRepository`, adicionar:

```kotlin
LaunchedEffect(Unit) {
    authRepository.currentUser.collect { user ->
        FeedbackService.updateUser(
            userId = user?.id.orEmpty(),
            userEmail = user?.email.orEmpty()
        )
    }
}
```

### Prospecta

Status: precisa alterar.

Arquivo:

```text
E:\CodeCacto\Prospecta\composeApp\src\commonMain\kotlin\br\com\codecacto\prospecta\App.kt
```

Adicionar import:

```kotlin
import br.com.codecacto.kmplib.feedback.FeedbackService
```

Como o app usa `ProspectaAuthStateManager`, adicionar um `LaunchedEffect` baseado em `authState`:

```kotlin
LaunchedEffect(authState) {
    val user = (authState as? ProspectaAuthState.Authenticated)?.user
    FeedbackService.updateUser(
        userId = user?.id.orEmpty(),
        userEmail = user?.email.orEmpty()
    )
}
```

Esse codigo tambem limpa os dados quando o estado deixar de ser autenticado.

### Residencial

Status: precisa alterar.

Arquivo:

```text
E:\CodeCacto\Residencial\composeApp\src\commonMain\kotlin\br\com\codecacto\residencial\domain\session\SessionManager.kt
```

Adicionar import:

```kotlin
import br.com.codecacto.kmplib.feedback.FeedbackService
```

Em `bootstrap()`, quando nao houver usuario logado, limpar o feedback:

```kotlin
if (firebaseUser == null) {
    FeedbackService.updateUser()
    AuthStateManager.setNotAuthenticated()
    _state.value = SessionState.NotAuthenticated
    return
}
```

Em `loadProfile(firebaseUser)`, apos carregar e validar o `profile`, sincronizar:

```kotlin
FeedbackService.updateUser(
    userId = profile.id,
    userEmail = profile.email
)
```

Em `signOut()`, limpar antes/depois do logout:

```kotlin
FeedbackService.updateUser()
```

### Meu Ritmo

Status: usa feedback, mas nao foi encontrado fluxo de login/usuario.

Arquivos com feedback:

```text
E:\CodeCacto\Meu Ritmo\composeApp\src\commonMain\kotlin\br\com\codecacto\meuritmo\navigation\AppNavHost.kt
E:\CodeCacto\Meu Ritmo\composeApp\src\commonMain\kotlin\br\com\codecacto\meuritmo\features\completion\CompletionScreen.kt
```

Nao ha `AuthRepository` ou usuario logado a sincronizar. Nao aplicar `updateUser` neste projeto, a menos que login seja adicionado futuramente.

### Super 8

Status: usa feedback/review, mas nao foi encontrado fluxo de login/usuario.

Arquivos com feedback:

```text
E:\CodeCacto\Super 8\composeApp\src\commonMain\kotlin\br\com\codecacto\superoito\navigation\AppNavHost.kt
E:\CodeCacto\Super 8\composeApp\src\commonMain\kotlin\br\com\codecacto\superoito\features\tournaments\details\TournamentDetailsScreen.kt
```

Nao ha usuario logado a sincronizar. Nao aplicar `updateUser` neste projeto, a menos que login seja adicionado futuramente.

### Calculadora BTU

Status: usa a `kmplib`, mas nao foi encontrado uso de `FeedbackService`, `FeedbackScreen` ou `AppReviewDialog`.

Nao precisa alterar.

### Repelente Anti-Mosquito

Status: usa a `kmplib`, mas nao foi encontrado uso de `FeedbackService`, `FeedbackScreen` ou `AppReviewDialog`.

Nao precisa alterar.

## Checklist de validacao

Depois de aplicar nos apps:

1. Fazer login.
2. Abrir a tela de feedback.
3. Enviar feedback com mensagem e WhatsApp, deixando e-mail vazio.
4. Verificar no Firestore:
   - `mensagem` preenchida;
   - `whatsapp` preenchido;
   - `email` vazio ou preenchido somente se digitado;
   - `usuarioId` preenchido;
   - `usuarioEmail` preenchido.
5. Fazer logout.
6. Enviar feedback em fluxo sem login, se existir.
7. Verificar que `usuarioId` e `usuarioEmail` ficam vazios nesse caso.

## Observacao

Na sessao atual, as alteracoes nos aplicativos fora de `E:\CodeCacto\Lib\kmplib` nao foram aplicadas diretamente porque a sandbox de escrita esta restrita a:

```text
E:\CodeCacto\Lib\kmplib
E:\tmp
C:\Users\rapha\.codex\memories
```

Por isso, este documento registra exatamente o que precisa ser aplicado em cada projeto.
