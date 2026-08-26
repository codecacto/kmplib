package br.com.codecacto.kmplib.media

/**
 * Formato do áudio de um efeito curto, **detectado pelos bytes** (assinatura do contêiner), nunca
 * pelo nome do arquivo.
 *
 * O nome não chega até aqui: o app entrega [ByteArray] (`Res.readBytes("files/beep.wav")`), e é o
 * cabeçalho que diz o que é. Detectar serve a duas coisas concretas:
 *
 * 1. **Recusar cedo** o que não é áudio — bytes vazios, um PNG trocado, um arquivo truncado. Sem
 *    isso o Android só falharia no callback do `SoundPool` (com `status != 0` e nenhuma pista) e o
 *    iOS devolveria um `OSStatus` numérico.
 * 2. **Nomear o arquivo temporário** com a extensão certa. Tanto o `SoundPool` quanto o
 *    `AudioServicesCreateSystemSoundID` recebem um caminho de arquivo, e extensão coerente é o que
 *    evita heurística errada do decodificador.
 */
enum class SoundEffectFormat(

    /** Extensão usada no arquivo temporário materializado pela lib (sem o ponto). */
    val fileExtension: String,

    /**
     * `true` quando o formato toca **nas duas plataformas** com a API de efeito curto de cada uma.
     *
     * Só o WAV (PCM) satisfaz isso. O `SoundPool` do Android aceita MP3/OGG/M4A, mas o *System
     * Sound Services* do iOS aceita **apenas Linear PCM ou IMA4** em contêiner `.caf`, `.aif` ou
     * `.wav` — um MP3 carrega no Android e falha calado no iOS. Daí o aviso no log em vez de um
     * "não tocou" sem explicação.
     */
    val isCrossPlatform: Boolean,
) {

    /** WAV/RIFF. **Formato recomendado da fábrica** (PCM 16-bit, mono, 44.1 kHz). */
    WAV("wav", isCrossPlatform = true),

    /** Core Audio Format — nativo do iOS; o `SoundPool` do Android não lê. */
    CAF("caf", isCrossPlatform = false),

    /** AIFF/AIFC — aceito pelo iOS; o Android não lê no `SoundPool`. */
    AIFF("aif", isCrossPlatform = false),

    /** MP3 — Android sim, iOS **não** (System Sound Services não decodifica MP3). */
    MP3("mp3", isCrossPlatform = false),

    /** MPEG-4 / AAC (m4a) — Android sim, iOS não. */
    M4A("m4a", isCrossPlatform = false),

    /** Ogg Vorbis — Android sim, iOS não. */
    OGG("ogg", isCrossPlatform = false),

    /** Nenhuma assinatura conhecida: não é áudio utilizável como efeito. */
    UNKNOWN("bin", isCrossPlatform = false),
    ;

    /** `true` para tudo que não é [UNKNOWN] — atalho de leitura nas implementações. */
    val isPlayable: Boolean get() = this != UNKNOWN
}

/**
 * Detecta o [SoundEffectFormat] pela assinatura dos primeiros bytes.
 *
 * Devolve [SoundEffectFormat.UNKNOWN] para conteúdo vazio, curto demais ou não reconhecido — o que
 * as implementações tratam como [SoundEffectError.InvalidAudio], sem lançar.
 */
fun detectSoundEffectFormat(bytes: ByteArray): SoundEffectFormat {
    if (bytes.size < MIN_HEADER_BYTES) return SoundEffectFormat.UNKNOWN

    val head = bytes.ascii(0, 4)
    val atFour = bytes.ascii(4, 4)
    val atEight = bytes.ascii(8, 4)

    return when {
        head == "RIFF" && atEight == "WAVE" -> SoundEffectFormat.WAV
        head == "caff" -> SoundEffectFormat.CAF
        head == "FORM" && (atEight == "AIFF" || atEight == "AIFC") -> SoundEffectFormat.AIFF
        head == "OggS" -> SoundEffectFormat.OGG
        atFour == "ftyp" -> SoundEffectFormat.M4A
        bytes.ascii(0, 3) == "ID3" -> SoundEffectFormat.MP3
        // Frame sync de MPEG audio: 11 bits em 1 (0xFF seguido de 111xxxxx).
        bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0 -> SoundEffectFormat.MP3
        else -> SoundEffectFormat.UNKNOWN
    }
}

/** Menor cabeçalho que permite decidir qualquer um dos formatos acima (`RIFF....WAVE`). */
private const val MIN_HEADER_BYTES = 12

private fun ByteArray.ascii(offset: Int, length: Int): String {
    if (offset + length > size) return ""
    val chars = CharArray(length) { index ->
        val code = this[offset + index].toInt() and 0xFF
        code.toChar()
    }
    return chars.concatToString()
}
