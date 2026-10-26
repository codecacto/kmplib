package br.com.codecacto.kmplib.platform.photos

/** Desfecho de [GallerySaver.save]. */
sealed interface GallerySaveResult {
    /** Está na galeria, com os bytes do arquivo como vieram. */
    data object Saved : GallerySaveResult

    /**
     * Sem permissão: no iOS, a pessoa negou "Adicionar fotos" (ofereça abrir os Ajustes); no Android 9 e
     * anteriores, falta `WRITE_EXTERNAL_STORAGE` (declarada pelo APP com `maxSdkVersion="28"` e pedida
     * antes). No Android 10+ nunca acontece — gravar a própria mídia no MediaStore não pede permissão.
     */
    data object PermissionDenied : GallerySaveResult

    /** O tipo não é imagem nem vídeo — a galeria não guarda. */
    data object UnsupportedType : GallerySaveResult

    /** Arquivo ilegível, disco cheio ou a fototeca recusou. Nada fica pela metade na galeria. */
    data object Failed : GallerySaveResult
}

/**
 * Grava um arquivo de imagem ou vídeo na **galeria do aparelho sem recomprimir** (2.327.0) — os bytes
 * vão como estão (HEIC fica HEIC, EXIF fica), pela API oficial de cada plataforma:
 *
 * - **Android 10+:** `MediaStore` com `RELATIVE_PATH` (`Pictures/<album>` ou `Movies/<album>`) e
 *   `IS_PENDING` — a entrada só aparece na galeria depois de o arquivo inteiro ser escrito, e falha no
 *   meio apaga a entrada. **Sem permissão.** Android 9 e anteriores: pasta pública + `MediaScanner`,
 *   com `WRITE_EXTERNAL_STORAGE` (o app declara `maxSdkVersion="28"`; a lib não declara para não
 *   impor a permissão a quem não salva).
 * - **iOS:** `PHPhotoLibrary.performChanges` + `PHAssetCreationRequest.addResource(with: .photo|.video,
 *   fileURL:)` — a fototeca guarda o recurso a partir do ARQUIVO (não de um `UIImage`, que reencodaria),
 *   com o `originalFilename`. Pede o acesso **só de adição** (`PHAccessLevel.addOnly`) na primeira vez;
 *   o app declara `NSPhotoLibraryAddUsageDescription` no `Info.plist`. O [album] é ignorado no iOS (criar
 *   álbum exige acesso de leitura à fototeca inteira, que o "só adicionar" não dá).
 *
 * Não apaga o arquivo de origem: quem chamou decide (ex.: o download temporário sai depois do `Saved`).
 * Nunca lança, salvo cancelamento da corrotina.
 */
interface GallerySaver {
    /**
     * @param path caminho ABSOLUTO do arquivo.
     * @param fileName nome de exibição na galeria (`IMG_0042.HEIC`).
     * @param mimeType `image/…` ou `video/…`.
     * @param album subpasta da galeria no Android (`"Lumio"` → `Pictures/Lumio`); `null` = a raiz de
     *   Imagens/Filmes.
     */
    suspend fun save(path: String, fileName: String, mimeType: String, album: String? = null): GallerySaveResult
}

/** O [GallerySaver] da plataforma. No Android precisa do `initKmpLibPlatform(context)`. */
expect fun getGallerySaver(): GallerySaver

/** Que tipo de mídia a galeria guardaria para [mimeType] (`null` = não guarda). */
enum class GalleryMediaKind { IMAGE, VIDEO }

/** `image/…` → [GalleryMediaKind.IMAGE], `video/…` → [GalleryMediaKind.VIDEO], resto `null`. */
fun galleryMediaKindOf(mimeType: String): GalleryMediaKind? {
    val t = mimeType.lowercase().trim()
    return when {
        t.startsWith("image/") && t.length > 6 -> GalleryMediaKind.IMAGE
        t.startsWith("video/") && t.length > 6 -> GalleryMediaKind.VIDEO
        else -> null
    }
}

/**
 * Nome de pasta de álbum seguro para o `RELATIVE_PATH` do Android: sem barra, sem `..`, sem caractere
 * de controle; `null`/branco = sem álbum.
 */
fun sanitizeGalleryAlbum(album: String?): String? =
    album?.replace(Regex("[\\u0000-\\u001F/\\\\\"*:<>?|]"), "_")?.trim()?.trim('.')?.takeIf { it.isNotBlank() }
