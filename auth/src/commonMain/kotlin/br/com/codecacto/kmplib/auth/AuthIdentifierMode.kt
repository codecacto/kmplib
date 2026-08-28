package br.com.codecacto.kmplib.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * O que o campo de login aceita — espelha o `AuthIdentifierMode` da `backlib-auth-local`.
 *
 * **O padrão da fábrica é [BOTH]** (constituição, 22/ago/2026): toda conta tem e-mail **e** nome de
 * usuário, e o campo aceita os dois — quem decide onde procurar é o `@`. Os outros dois existem
 * porque o contrato do servidor os tem, não porque sejam escolhas comuns:
 *  - [EMAIL] é o comportamento anterior, e continua sendo o **default local** para que um app que não
 *    consulte o servidor não mude de aparência sozinho;
 *  - [USERNAME] **proíbe** o login por e-mail — quem não tem usuário fica trancado para fora.
 */
@Serializable
enum class AuthIdentifierMode {
    @SerialName("EMAIL")
    EMAIL,

    @SerialName("USERNAME")
    USERNAME,

    @SerialName("BOTH")
    BOTH,
}

/**
 * Resposta de `GET {authBasePath}/config` — o que a tela de login precisa saber **antes** de existir
 * qualquer token, e por isso a rota é pública.
 *
 * `identifierLabel` vem pronto do servidor para o rótulo não ser remontado no cliente: num sistema
 * que a empresa configurou como "Matrícula", o app tem de dizer "Matrícula".
 */
@Serializable
data class OwnAuthIdentifierConfig(
    val identifierMode: AuthIdentifierMode = AuthIdentifierMode.EMAIL,
    val identifierLabel: String = "E-mail",
) {
    companion object {
        /**
         * O que vale quando o servidor não respondeu — rede fora, backend anterior à 0.80.0 (404),
         * corpo inesperado.
         *
         * **Nunca é um erro para o usuário:** uma tela de login que não abre porque o endpoint do
         * *rótulo* falhou seria trocar um inconveniente por uma porta trancada.
         */
        val Default = OwnAuthIdentifierConfig()
    }
}
