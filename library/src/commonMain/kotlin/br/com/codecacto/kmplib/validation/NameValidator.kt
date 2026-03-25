package br.com.codecacto.kmplib.validation

/**
 * Validador de nomes.
 */
object NameValidator {

    /**
     * Verifica se o nome é válido.
     * @param name Nome a validar
     * @param minLength Comprimento mínimo (default: 2)
     * @return true se o nome for válido
     */
    fun isValid(name: String, minLength: Int = 2): Boolean {
        return name.isNotBlank() && name.trim().length >= minLength
    }

    /**
     * Valida o nome e retorna mensagem de erro ou null se válido.
     */
    fun validate(name: String, minLength: Int = 2): String? {
        return when {
            name.isBlank() -> "Nome é obrigatório"
            name.trim().length < minLength -> "Nome deve ter pelo menos $minLength caracteres"
            else -> null
        }
    }
}
