package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

/**
 * [KeyboardOptions] com **capitalização e autocorreção coerentes com o tipo do campo** — use esta
 * fábrica em vez de montar `KeyboardOptions(keyboardType, imeAction)` na mão.
 *
 * ### Por que isto existe (bug real, jul/2026)
 * `KeyboardOptions` não declarada é `KeyboardCapitalization.Unspecified` + `autoCorrectEnabled = null`,
 * e cada plataforma resolve o "não especificado" do seu jeito:
 *
 * | | Android | iOS (UIKit) |
 * |---|---|---|
 * | Capitalização | o IME desliga sozinho em `KeyboardType.Email` | `.sentences` — **capitaliza a 1ª letra** |
 * | Autocorreção | idem | `.default` — **troca a palavra digitada por uma sugestão** |
 *
 * Resultado observado no app Meu Barbeiro: o mesmo usuário logava no Android e recebia
 * "e-mail ou senha inválidos" no iPhone — o teclado do iOS autocorrigia o e-mail digitado no campo de
 * login para outra palavra antes do envio (o backend recebia um e-mail que não existia). O login não
 * é o único ferido: qualquer campo de e-mail, telefone, documento, código ou senha sofre o mesmo.
 *
 * Por isso o default aqui é **derivado do [keyboardType]**: tudo que não é texto corrido entra sem
 * capitalização e sem autocorreção; só [KeyboardType.Text] mantém o comportamento natural de escrita
 * (frase capitalizada, autocorreção ligada). Quem precisar de outra combinação passa [capitalization]
 * e [autoCorrect] explicitamente.
 */
fun appKeyboardOptions(
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    capitalization: KeyboardCapitalization = defaultCapitalizationFor(keyboardType),
    autoCorrect: Boolean = defaultAutoCorrectFor(keyboardType),
): KeyboardOptions = KeyboardOptions(
    keyboardType = keyboardType,
    imeAction = imeAction,
    capitalization = capitalization,
    autoCorrectEnabled = autoCorrect,
)

/**
 * Texto corrido escreve como frase; identificador (e-mail, senha, telefone, número, URI…) nunca —
 * capitalizar a 1ª letra de um e-mail ou de uma senha é erro de digitação embutido pelo teclado.
 */
fun defaultCapitalizationFor(keyboardType: KeyboardType): KeyboardCapitalization =
    if (keyboardType == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None

/**
 * Autocorreção só faz sentido em texto corrido. Num identificador ela **substitui** o que a pessoa
 * digitou por uma palavra do dicionário — silenciosamente, no momento do envio.
 */
fun defaultAutoCorrectFor(keyboardType: KeyboardType): Boolean = keyboardType == KeyboardType.Text
