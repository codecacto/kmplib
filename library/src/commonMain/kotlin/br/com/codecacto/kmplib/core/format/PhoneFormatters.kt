package br.com.codecacto.kmplib.core.format

/**
 * Formata telefone brasileiro a partir de digitos.
 *  - 10 digitos (fixo):     "(XX) XXXX-XXXX"
 *  - 11 digitos (celular):  "(XX) XXXXX-XXXX"
 *  - Demais comprimentos: retorna o input (sem formatacao).
 *
 * Equivale ao mesmo formato da [br.com.codecacto.kmplib.mask.PhoneVisualTransformation],
 * mas pode ser usado em logs, PDFs e relatorios.
 */
fun formatPhone(input: String): String {
    val digits = input.filter { it.isDigit() }
    return when (digits.length) {
        10 -> "(${digits.take(2)}) ${digits.drop(2).take(4)}-${digits.drop(6)}"
        11 -> "(${digits.take(2)}) ${digits.drop(2).take(5)}-${digits.drop(7)}"
        else -> input
    }
}
