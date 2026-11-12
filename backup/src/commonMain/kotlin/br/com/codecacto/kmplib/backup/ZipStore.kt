package br.com.codecacto.kmplib.backup

/**
 * ZIP mínimo e **padrão** (PKWARE APPNOTE 6.3): entradas *stored* (método 0) com CRC-32, nomes em UTF-8
 * (bit 11). Qualquer descompactador abre o arquivo. Escrito em Kotlin comum para a cópia ser idêntica
 * nas duas plataformas e testável sem aparelho; o conteúdo é JSON pequeno e fotos já comprimidas, onde
 * compressão não paga.
 *
 * Leitura: aceita só o que esta lib grava (stored, sem criptografia, sem ZIP64) e confere o CRC de
 * cada entrada — arquivo truncado ou alterado vira [BackupFormatException], nunca dado pela metade.
 */
internal object ZipStore {
    private const val LOCAL_SIG = 0x04034b50
    private const val CENTRAL_SIG = 0x02014b50
    private const val END_SIG = 0x06054b50
    private const val UTF8_FLAG = 0x0800
    private const val MAX_ENTRIES = 10_000

    fun write(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = Bytes()
        val central = Bytes()
        entries.forEach { (name, data) ->
            require(isSafeName(name)) { "nome de entrada inválido: $name" }
            val nameBytes = name.encodeToByteArray()
            val crc = crc32(data)
            val offset = out.size
            out.int(LOCAL_SIG); out.short(20); out.short(UTF8_FLAG); out.short(0); out.short(0); out.short(0x21)
            out.int(crc); out.int(data.size); out.int(data.size); out.short(nameBytes.size); out.short(0)
            out.bytes(nameBytes); out.bytes(data)
            central.int(CENTRAL_SIG); central.short(20); central.short(20); central.short(UTF8_FLAG); central.short(0)
            central.short(0); central.short(0x21); central.int(crc); central.int(data.size); central.int(data.size)
            central.short(nameBytes.size); central.short(0); central.short(0); central.short(0); central.short(0)
            central.int(0); central.int(offset); central.bytes(nameBytes)
        }
        val cdOffset = out.size
        out.bytes(central.toByteArray())
        out.int(END_SIG); out.short(0); out.short(0); out.short(entries.size); out.short(entries.size)
        out.int(central.size); out.int(cdOffset); out.short(0)
        return out.toByteArray()
    }

    fun read(zip: ByteArray): Map<String, ByteArray> {
        val end = findEnd(zip) ?: throw BackupFormatException("fim do ZIP não encontrado")
        val count = u16(zip, end + 10)
        val cdSize = u32(zip, end + 12)
        val cdOffset = u32(zip, end + 16)
        if (count > MAX_ENTRIES || cdOffset + cdSize > zip.size) throw BackupFormatException("diretório inválido")
        val result = LinkedHashMap<String, ByteArray>()
        var p = cdOffset
        repeat(count) {
            if (p + 46 > zip.size || i32(zip, p) != CENTRAL_SIG) throw BackupFormatException("entrada central inválida")
            val flags = u16(zip, p + 8)
            val method = u16(zip, p + 10)
            val crc = i32(zip, p + 16)
            val size = u32(zip, p + 20)
            val usize = u32(zip, p + 24)
            val nameLen = u16(zip, p + 28)
            val extraLen = u16(zip, p + 30)
            val commentLen = u16(zip, p + 32)
            val localOffset = u32(zip, p + 42)
            if (flags and 1 != 0) throw BackupFormatException("entrada criptografada")
            if (method != 0 || size != usize) throw BackupFormatException("compressão não suportada")
            if (p + 46 + nameLen > zip.size) throw BackupFormatException("nome truncado")
            val name = zip.copyOfRange(p + 46, p + 46 + nameLen).decodeToString()
            if (!isSafeName(name)) throw BackupFormatException("nome de entrada inseguro")
            if (localOffset + 30 > zip.size || i32(zip, localOffset) != LOCAL_SIG) throw BackupFormatException("cabeçalho local inválido")
            val dataStart = localOffset + 30 + u16(zip, localOffset + 26) + u16(zip, localOffset + 28)
            if (dataStart + size > zip.size || size < 0) throw BackupFormatException("dados truncados")
            val data = zip.copyOfRange(dataStart, dataStart + size)
            if (crc32(data) != crc) throw BackupFormatException("CRC não confere")
            if (!name.endsWith("/")) result[name] = data
            p += 46 + nameLen + extraLen + commentLen
        }
        return result
    }

    /** Nome relativo, sem `..`, sem barra inicial nem invertida (nada de escrever fora da pasta). */
    fun isSafeName(name: String): Boolean =
        name.isNotEmpty() && !name.startsWith("/") && '\\' !in name && ':' !in name &&
            name.split('/').none { it == ".." || it == "." } && name.length <= 512

    private fun findEnd(z: ByteArray): Int? {
        var i = z.size - 22
        val min = maxOf(0, z.size - 22 - 0xFFFF)
        while (i >= min) {
            if (i32(z, i) == END_SIG) return i
            i--
        }
        return null
    }

    private fun u16(b: ByteArray, i: Int): Int = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
    private fun i32(b: ByteArray, i: Int): Int = u16(b, i) or (u16(b, i + 2) shl 16)
    private fun u32(b: ByteArray, i: Int): Int {
        val v = i32(b, i)
        if (v < 0) throw BackupFormatException("tamanho acima do suportado")
        return v
    }

    private val CRC_TABLE: IntArray = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
        c
    }

    /** CRC-32 (IEEE 802.3), o do ZIP. */
    fun crc32(data: ByteArray): Int {
        var c = -1
        for (b in data) c = CRC_TABLE[(c xor b.toInt()) and 0xFF] xor (c ushr 8)
        return c.inv()
    }

    private class Bytes {
        private var buf = ByteArray(4096)
        var size = 0
            private set

        private fun ensure(n: Int) {
            if (size + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + n))
        }

        fun short(v: Int) { ensure(2); buf[size++] = v.toByte(); buf[size++] = (v ushr 8).toByte() }
        fun int(v: Int) { short(v and 0xFFFF); short(v ushr 16) }
        fun bytes(b: ByteArray) { ensure(b.size); b.copyInto(buf, size); size += b.size }
        fun toByteArray(): ByteArray = buf.copyOf(size)
    }
}

/** O arquivo não é uma cópia legível (corrompido, truncado ou de outro formato). */
class BackupFormatException(message: String) : Exception(message)
