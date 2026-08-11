package br.com.codecacto.kmplib.qr

/**
 * Nível de correção de erro do QR Code (ISO/IEC 18004, *error correction level*).
 *
 * Quanto mais alto, mais módulos do símbolo são gastos com redundância — o código lê mesmo sujo,
 * rasgado ou parcialmente coberto, mas **cabe menos dado na mesma versão**.
 *
 * **O trade-off que interessa na prática, e por isso o nível é PARÂMETRO e não constante escondida:**
 *
 * | Nível | Recupera até | Quando usar |
 * |---|---|---|
 * | [L] | ~7% | **QR lido de TELA para TELA** (o caso de transferência/pareamento entre dois aparelhos): a imagem é nítida, iluminada e sem dobra — a redundância extra só tira capacidade. É aqui que se ganha payload. |
 * | [M] | ~15% | **Default equilibrado.** Tela em condição normal, papel limpo. |
 * | [Q] | ~25% | Impresso em ambiente de trabalho (balcão, oficina), sujeito a marca de dedo e desgaste. |
 * | [H] | ~30% | Impresso pequeno, plaquinha exposta, ou QR com **logo sobreposto** no meio (o logo cobre módulos, e é a redundância que segura a leitura). |
 *
 * Errar para cima custa capacidade; errar para baixo custa leitura. Para um QR de transferência que
 * o funcionário vai apontar a câmera de um celular para a tela de outro, [L] é a escolha
 * tecnicamente correta — e é o que permite caber muito mais dado num único código.
 *
 * [formatBits] é o valor de 2 bits que entra na *format information* do símbolo (não é o ordinal:
 * a ordem no padrão é L=01, M=00, Q=11, H=10).
 */
enum class QrErrorCorrection(val formatBits: Int) {

    /** ~7% de recuperação. Mais capacidade. Ideal para leitura tela↔tela. */
    L(0b01),

    /** ~15%. Default equilibrado. */
    M(0b00),

    /** ~25%. Impresso em ambiente sujeito a desgaste. */
    Q(0b11),

    /** ~30%. Impressão pequena, plaquinha exposta ou QR com logo sobreposto. */
    H(0b10),
}

/**
 * Modo de codificação dos dados dentro do QR (ISO/IEC 18004, *mode indicator*).
 *
 * Os três que importam, do mais compacto ao mais geral. O modo é **escolhido automaticamente** por
 * `encodeQr` (o mais econômico que consiga representar o texto inteiro) — a economia é real, não
 * cosmética: 100 dígitos ocupam 334 bits em [Numeric] e 800 bits em [Byte], o que costuma ser a
 * diferença entre caber numa versão pequena e precisar de um símbolo bem maior.
 *
 * [modeBits] é o indicador de 4 bits gravado antes do contador de caracteres.
 */
enum class QrMode(val modeBits: Int) {

    /** Só `0`–`9`. 3 dígitos em 10 bits (~3,33 bits/dígito). */
    Numeric(0b0001),

    /**
     * Subconjunto de 45 caracteres: `0`–`9`, `A`–`Z` **maiúsculo**, espaço e `$ % * + - . / :`.
     * 2 caracteres em 11 bits (5,5 bits/caractere).
     */
    Alphanumeric(0b0010),

    /**
     * Bytes arbitrários — texto livre em **UTF-8** (8 bits por byte). É o modo de qualquer conteúdo
     * com minúscula, acento, JSON ou URL com maiúscula/minúscula misturada.
     */
    Byte(0b0100);

    /**
     * Quantos bits o **contador de caracteres** ocupa nesta versão (o padrão usa três faixas de
     * versão: 1–9, 10–26 e 27–40). Errar isto é o defeito clássico que gera um QR que *parece* certo
     * e nenhum leitor decodifica.
     */
    fun characterCountBits(version: Int): Int {
        require(version in QrCode.MIN_VERSION..QrCode.MAX_VERSION) { "versão inválida: $version" }
        val index = when {
            version <= 9 -> 0
            version <= 26 -> 1
            else -> 2
        }
        return when (this) {
            Numeric -> intArrayOf(10, 12, 14)[index]
            Alphanumeric -> intArrayOf(9, 11, 13)[index]
            Byte -> intArrayOf(8, 16, 16)[index]
        }
    }
}

/**
 * Um QR Code **já codificado**: a matriz de módulos pronta para desenhar, mais os parâmetros que o
 * encoder escolheu.
 *
 * **A matriz é separada da renderização de propósito.** Este objeto não sabe desenhar: é dado puro,
 * determinístico e testável, e serve tanto ao `@Composable QrCodeView` (Canvas) quanto ao
 * `renderQrCodeToPng` (bitmap para anexar/compartilhar) — um composable não serviria para o
 * segundo caso.
 *
 * **A quiet zone já está embutida.** [size] é o lado da matriz **incluindo** a margem clara de
 * [quietZone] módulos em cada lado, e [isDark] recebe coordenadas nesse espaço. Isso é deliberado:
 * a quiet zone de 4 módulos é **obrigatória** pelo padrão e é o esquecimento clássico de quem
 * renderiza QR — sem ela, muitos leitores simplesmente não decodificam, e o defeito aparece como
 * "não lê no celular do funcionário" (não como erro de código).
 */
class QrCode internal constructor(

    /** Versão do símbolo, 1–40. Define o tamanho: `21 + 4 * (version - 1)` módulos de lado. */
    val version: Int,

    /** Nível de correção de erro efetivamente usado. */
    val errorCorrection: QrErrorCorrection,

    /** Modo escolhido para os dados. */
    val mode: QrMode,

    /** Padrão de máscara escolhido, 0–7 (o de menor penalidade — ver `QrMask`). */
    val mask: Int,

    /** Margem clara em módulos, em cada lado. Sempre ≥ 4 (exigência do padrão). */
    val quietZone: Int,

    /** Lado do símbolo em módulos, **sem** a quiet zone (21–177). */
    val symbolSize: Int,

    /** `true` = módulo escuro. Indexado `y * size + x`, com a quiet zone embutida. */
    private val modules: BooleanArray,
) {

    /**
     * Lado da matriz a desenhar, em módulos, **incluindo** a quiet zone dos dois lados.
     *
     * É este o número que o renderizador usa: `moduleSizePx = larguraDesejada / size`.
     */
    val size: Int get() = symbolSize + quietZone * 2

    /**
     * `true` se o módulo em ([x], [y]) é escuro. Coordenada fora da matriz devolve `false`
     * (nunca lança) — a quiet zone é área clara legítima, não erro.
     */
    fun isDark(x: Int, y: Int): Boolean {
        if (x < 0 || y < 0 || x >= size || y >= size) return false
        return modules[y * size + x]
    }

    /**
     * A matriz como lista de linhas (`[y][x]`), incluindo a quiet zone.
     *
     * Cópia defensiva: um `QrCode` é imutável, e devolver o array interno permitiria a um chamador
     * corromper um código já validado.
     */
    fun toMatrix(): List<BooleanArray> =
        List(size) { y -> BooleanArray(size) { x -> modules[y * size + x] } }

    /**
     * Representação em texto para depuração/log (`"##"` escuro, `"  "` claro).
     *
     * Não é para exibir ao usuário — é o que permite inspecionar um símbolo no terminal quando algo
     * não lê.
     */
    fun toDebugString(dark: String = "##", light: String = "  "): String =
        (0 until size).joinToString("\n") { y ->
            (0 until size).joinToString("") { x -> if (isDark(x, y)) dark else light }
        }

    companion object {

        /** Menor versão do padrão (21×21 módulos). */
        const val MIN_VERSION: Int = 1

        /** Maior versão do padrão (177×177 módulos). */
        const val MAX_VERSION: Int = 40

        /**
         * Quiet zone mínima **exigida** pelo padrão: 4 módulos claros em cada lado.
         *
         * Não é enfeite. Sem ela o algoritmo de localização do leitor não separa o símbolo do que
         * está em volta, e a leitura falha de forma intermitente — o pior tipo de defeito, porque
         * funciona no aparelho de quem testou.
         */
        const val QUIET_ZONE: Int = 4

        /** Lado do símbolo (sem quiet zone) para uma versão. */
        fun symbolSizeOf(version: Int): Int {
            require(version in MIN_VERSION..MAX_VERSION) { "versão inválida: $version" }
            return version * 4 + 17
        }
    }
}
