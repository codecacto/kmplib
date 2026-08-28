package br.com.codecacto.kmplib.brdata

import kotlinx.serialization.Serializable

/**
 * Endereço brasileiro — **o mesmo formato do `Address` da weblib**, campo a campo.
 *
 * Os nomes das propriedades ficam em português porque é assim que aparecem na tela e no banco dos
 * produtos da fábrica; a tradução para o vocabulário de cada gateway (o Asaas chama o bairro de
 * `province`) é trabalho da borda que fala com ele, não do formulário.
 *
 * **Paridade com a web não é estética.** App e portal do mesmo produto falam com a MESMA rota: um
 * campo com nome diferente aqui grava `null` no servidor sem nenhum erro de compilação denunciando.
 */
@Serializable
data class Address(
    val cep: String = "",
    val logradouro: String = "",
    val numero: String = "",
    val complemento: String = "",
    val bairro: String = "",
    val cidade: String = "",
    /** Sigla da UF, maiúscula (ex.: "SP"). */
    val uf: String = "",
) {
    /**
     * `true` se qualquer campo foi informado.
     *
     * É a diferença entre "está em branco" e "nunca preencheu" — e ela decide se o `PATCH` manda o
     * bloco ou o omite. Mandar sete vazios onde a pessoa não mexeu **apaga** o endereço no servidor.
     */
    val temAlgumCampo: Boolean
        get() = listOf(cep, logradouro, numero, complemento, bairro, cidade, uf).any { it.isNotBlank() }

    /**
     * `true` quando o endereço tem tudo que um gateway de pagamento precisa.
     *
     * **`complemento` fica de fora de propósito**: é o único campo que um endereço válido pode não
     * ter. Espelha o `isAddressComplete` da weblib — as duas libs respondem a mesma pergunta, e uma
     * divergência aqui faria o app dizer "pronto" onde o portal diz "falta".
     */
    val completo: Boolean
        get() = listOf(cep, logradouro, numero, bairro, cidade, uf).all { it.isNotBlank() }

    /** Só os dígitos do CEP — o formato que serviço de CEP e gateway esperam. */
    val cepDigits: String get() = cep.filter { it.isDigit() }

    fun normalized(): Address = Address(
        cep = cep.trim(),
        logradouro = logradouro.trim(),
        numero = numero.trim(),
        complemento = complemento.trim(),
        bairro = bairro.trim(),
        cidade = cidade.trim(),
        uf = uf.trim().uppercase(),
    )

    public companion object {
        /** Endereço vazio — ponto de partida de um formulário novo. Espelha `EMPTY_ADDRESS`. */
        public val EMPTY: Address = Address()
    }
}

/**
 * O que uma consulta de CEP devolve. **Número nunca vem daqui** — nenhum serviço de CEP sabe.
 *
 * A lib não escolhe o serviço nem faz a chamada, de propósito: o transporte é do consumidor. Um
 * `fetch` embutido amarraria todo app a um fornecedor que só se troca publicando versão nova na
 * loja — e loja leva semanas. O caminho recomendado é o backend do produto expor a consulta.
 */
public data class CepLookupResult(
    val logradouro: String = "",
    val bairro: String = "",
    val cidade: String = "",
    val uf: String = "",
)

/**
 * Aplica o resultado de uma consulta de CEP **sem apagar o que a pessoa já digitou**.
 *
 * Função pura, e é por isso que ela existe separada do composable: é aqui que mora a regra, e é ela
 * que os testes cobrem (a fábrica não escreve teste de UI em KMP).
 *
 * Duas decisões:
 *
 *  - **O que já está preenchido vence.** Quem corrigiu o nome da rua não vê a correção sumir porque
 *    o CEP genérico do bairro devolveu outro nome. Só campo em branco é preenchido.
 *  - **Um valor em branco no resultado não apaga nada.** Serviço de CEP devolve `logradouro` vazio
 *    para CEP de cidade inteira, e sobrescrever com vazio seria piorar o que já estava lá.
 */
public fun Address.mergedWith(lookup: CepLookupResult?): Address {
    if (lookup == null) return this
    return copy(
        logradouro = logradouro.ifBlank { lookup.logradouro.trim() },
        bairro = bairro.ifBlank { lookup.bairro.trim() },
        cidade = cidade.ifBlank { lookup.cidade.trim() },
        uf = uf.ifBlank { lookup.uf.trim().uppercase() },
    )
}

/**
 * `true` quando a sigla existe de verdade (confere contra [BrazilianStates]).
 *
 * **Vazio passa**: o bloco inteiro pode ser opcional, e recusar campo em branco transformaria um
 * endereço opcional em obrigatório sem ninguém decidir isso.
 */
public fun isValidUf(uf: String): Boolean {
    val sigla = uf.trim()
    if (sigla.isEmpty()) return true
    return BrazilianStates.all.any { it.abbreviation.equals(sigla, ignoreCase = true) }
}

/** Filtra a digitação da UF: só letras, maiúsculas, no máximo duas. */
public fun filterUfInput(input: String): String =
    input.filter { it.isLetter() }.take(2).uppercase()
