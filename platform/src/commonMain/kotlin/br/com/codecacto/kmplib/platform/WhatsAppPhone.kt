package br.com.codecacto.kmplib.platform

/**
 * Número de telefone pronto para o WhatsApp (`api.whatsapp.com/send?phone=…`): só algarismos, com
 * o DDI na frente. `null` quando não dá para conversar com aquele número.
 *
 * O telefone do cliente costuma estar no banco SEM DDI ("65999998888", "(65) 99999-8888") e às
 * vezes com ele ("5565999998888", "+55 65 99999-8888"). O link do WhatsApp exige o número
 * internacional completo — sem o 55 ele abre a conversa com outro país, ou com ninguém.
 *
 * Regras — as MESMAS do `normalizeWhatsAppPhone` da weblib (`src/utils/whatsApp.ts`, ≥ 0.318.0),
 * para web e app abrirem a conversa com o mesmo número:
 * 1. Só os algarismos contam (máscara, espaço, parênteses e traço saem). `+` ou `00` na frente
 *    = o número JÁ traz o DDI.
 * 2. Zeros à esquerda saem ("0 65 …" do discador nacional) — DDI e DDD nunca começam com 0.
 * 3. Com `+`/`00` explícito: de 8 a 15 algarismos fica como está (DDI curto + número curto).
 * 4. 10 ou 11 algarismos → DDD + número do Brasil (fixo ou celular): ganha o `55` na frente. Vale
 *    mesmo começando com 55 — 55 também é DDD (Santa Maria/RS).
 * 5. 12 a 15 algarismos → já com DDI (12–13 começando com 55 = Brasil; o resto, outro país): fica.
 * 6. Vazio, sem DDD ("99999-8888"), curto ou acima de 15 (teto do E.164) → `null`.
 *
 * ```kotlin
 * whatsAppPhoneNumber("(65) 99999-8888")   // "5565999998888"
 * whatsAppPhoneNumber("+55 65 99999-8888") // "5565999998888"
 * whatsAppPhoneNumber("+351 912 345 678")  // "351912345678"
 * whatsAppPhoneNumber("1234")              // null
 * ```
 *
 * Função pura, sem plataforma. É o que o `WhatsAppIconButton` (`kmplib-ui`) usa antes de chamar
 * [UrlLauncher.openWhatsApp]; use-a também para decidir se mostra outra ação de WhatsApp.
 */
fun whatsAppPhoneNumber(phone: String?): String? {
    val raw = phone?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val explicitPlus = raw.startsWith('+')
    val allDigits = raw.filter { it in '0'..'9' }
    val explicitZeroZero = !explicitPlus && allDigits.startsWith("00")
    val digits = allDigits.trimStart('0')
    if (digits.isEmpty()) return null
    return when {
        explicitPlus || explicitZeroZero ->
            digits.takeIf { it.length in MIN_EXPLICIT_INTERNATIONAL_DIGITS..MAX_E164_DIGITS }
        digits.length == 10 || digits.length == 11 -> BRAZIL_COUNTRY_CODE + digits
        digits.length in MIN_INTERNATIONAL_DIGITS..MAX_E164_DIGITS -> digits
        else -> null
    }
}

private const val BRAZIL_COUNTRY_CODE = "55"
private const val MIN_EXPLICIT_INTERNATIONAL_DIGITS = 8
private const val MIN_INTERNATIONAL_DIGITS = 12
private const val MAX_E164_DIGITS = 15
