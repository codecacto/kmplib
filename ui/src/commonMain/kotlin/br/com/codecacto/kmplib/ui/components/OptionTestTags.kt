package br.com.codecacto.kmplib.ui.components

/**
 * Ids de automação POR OPÇÃO dos componentes de escolha única da lib — [SegmentedControl],
 * [ChoiceChipGroup] e [FilterChipRow] (2.247.0).
 *
 * Cada opção leva o id `<grupo>-<chave>`, no nó clicável do segmento/chip:
 * - **grupo** = o `testTag` passado ao componente (ex.: `calculadora-unidade`); sem ele, o prefixo
 *   padrão do componente ([SEGMENTED_GROUP], [CHOICE_CHIP_GROUP], [FILTER_CHIP_GROUP]) — que colide
 *   se houver dois do mesmo tipo na tela, por isso dê o grupo sempre que o flow for tocar nele;
 * - **chave** = a de `optionTestKeys` normalizada (minúsculo, hífen: `"Kg"` → `kg`); sem ela, o
 *   ÍNDICE da opção (`0`, `1`…).
 *
 * O índice é o default porque é o que não muda com o idioma do aparelho: o rótulo traduzido
 * ("Arroba"/"Arrobas") quebraria o flow em outra língua. Chave semântica (`kg`) é melhor para quem lê
 * o flow — e sobrevive a reordenar as opções.
 *
 * No Maestro: `tapOn: { id: "calculadora-unidade-kg" }`. Para afirmar a escolha, atenção ao mapa da
 * semântica `selected`: no **Android** o Compose só a publica como *selected* para `Role.Tab`; o
 * segmento (`Role.RadioButton`) e o chip (`Role.Checkbox`) saem como **`checked`** — então
 * `assertVisible: { id: …, checked: true }` dentro de `runFlow when platform Android`. No **iOS** vira o
 * trait *selected* do UIAccessibility; prefira provar a escolha pelo EFEITO na tela (o valor que muda),
 * que vale nas duas plataformas. Antes da 2.247.0 o segmento só se
 * tocava por POSIÇÃO (`point: "75%,50%"` no id do grupo — Arroba Certa, 03/out/2026): frágil a
 * qualquer mudança de largura ou de ordem.
 */
object OptionTestTags {
    /** Prefixo padrão do [SegmentedControl] sem `testTag`. */
    const val SEGMENTED_GROUP: String = "segmento"

    /** Prefixo padrão do [ChoiceChipGroup] sem `testTag`. */
    const val CHOICE_CHIP_GROUP: String = "chip-escolha"

    /** Prefixo padrão do [FilterChipRow] sem `testTag`. */
    const val FILTER_CHIP_GROUP: String = "chip-filtro"

    /** Id de uma opção: `<group>-<key>`, com [key] normalizada. */
    fun option(group: String, key: String): String = "$group-${normalizeKey(key)}"

    /**
     * Ids das [count] opções do grupo, na ordem — o que o componente aplica.
     *
     * Chave ausente, em branco ou que normaliza para vazio cai no índice. Chave repetida ganha o
     * índice como sufixo (`kg`, `kg-3`): dois nós com o mesmo id fariam o flow tocar no primeiro.
     */
    fun options(group: String, count: Int, keys: List<String>? = null): List<String> {
        val usados = mutableSetOf<String>()
        return List(count.coerceAtLeast(0)) { index ->
            val chave = keys?.getOrNull(index)?.let(::normalizeKey)?.takeIf { it.isNotEmpty() }
                ?: index.toString()
            var unica = chave
            var tentativa = 0
            while (!usados.add(unica)) {
                unica = if (tentativa == 0) "$chave-$index" else "$chave-$index-$tentativa"
                tentativa++
            }
            "$group-$unica"
        }
    }

    /** Minúsculo; o que não é letra ASCII ou dígito vira hífen; hífens repetidos e nas pontas saem. */
    fun normalizeKey(key: String): String =
        key.lowercase()
            .map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
}
