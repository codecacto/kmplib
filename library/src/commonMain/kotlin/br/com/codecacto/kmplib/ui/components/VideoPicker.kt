package br.com.codecacto.kmplib.ui.components

import androidx.compose.runtime.Composable

data class SelectedVideo(
    val bytes: ByteArray,
    val mimeType: String,
    val name: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SelectedVideo) return false
        if (!bytes.contentEquals(other.bytes)) return false
        if (mimeType != other.mimeType) return false
        if (name != other.name) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}

/**
 * Launcher para selecao de videos via camera ou galeria.
 *
 * Ao chamar [launch], exibe um seletor com as opcoes:
 * - Gravar video (camera)
 * - Escolher da galeria
 *
 * O video selecionado e retornado como [SelectedVideo] (bytes + mime + nome).
 */
expect class VideoPickerLauncher {
    fun launch()
}

@Composable
expect fun rememberVideoPickerLauncher(
    onVideoSelected: (SelectedVideo) -> Unit
): VideoPickerLauncher
