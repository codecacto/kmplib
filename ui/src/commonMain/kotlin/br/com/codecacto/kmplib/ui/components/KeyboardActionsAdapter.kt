package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.ui.text.input.ImeAction

/**
 * Leva o [KeyboardActions] da API antiga (`value`/`onValueChange`) para o [KeyboardActionHandler]
 * do campo com `TextFieldState` (2.262.1) — é o que deixa um componente público trocar de motor
 * sem mudar a assinatura que os apps já passam.
 *
 * Só a ação do [imeAction] do campo importa (é a única que o teclado dispara). Sem callback para ela,
 * devolve `null` e o campo segue o comportamento padrão (Done fecha o teclado, Next avança o foco).
 * Com callback, ele SUBSTITUI o padrão, como na API antiga; `defaultKeyboardAction(...)` dentro dele
 * executa o padrão.
 */
internal fun KeyboardActions.toKeyboardActionHandler(imeAction: ImeAction): KeyboardActionHandler? {
    val action: (KeyboardActionScope.() -> Unit) = when (imeAction) {
        ImeAction.Done -> onDone
        ImeAction.Go -> onGo
        ImeAction.Next -> onNext
        ImeAction.Previous -> onPrevious
        ImeAction.Search -> onSearch
        ImeAction.Send -> onSend
        else -> null
    } ?: return null
    return KeyboardActionHandler { performDefaultAction ->
        val scope = object : KeyboardActionScope {
            override fun defaultKeyboardAction(imeAction: ImeAction) = performDefaultAction()
        }
        scope.action()
    }
}
