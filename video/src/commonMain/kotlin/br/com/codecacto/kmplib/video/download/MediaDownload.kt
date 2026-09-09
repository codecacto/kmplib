package br.com.codecacto.kmplib.video.download

import br.com.codecacto.kmplib.video.VideoErrorKind
import br.com.codecacto.kmplib.video.videoErrorKindForHttpStatus

/**
 * Em que ponto um download está — a máquina de estados que a tela de Downloads desenha.
 *
 * Os estados são **exclusivos**, pelo mesmo motivo do `VideoStatus`: com booleanos independentes
 * (`baixando` + `pausado`) a tela acaba mostrando o ⏸ e o ● ao mesmo tempo, ou nenhum dos dois.
 * Quem decide qual deles vale, a partir do que cada plataforma informa, é [mediaDownloadStatusOf] —
 * uma função só, comum às duas, e coberta por teste.
 */
sealed interface MediaDownloadStatus {

    /**
     * Na fila. Pode estar esperando a vez **ou** esperando um requisito (rede não tarifada, com
     * "baixar só no Wi-Fi" ligado). Ver [MediaDownload.waitingForNetwork] — sem essa distinção na
     * tela, o usuário no 4G acha que o app travou.
     */
    data object Queued : MediaDownloadStatus

    /** Baixando agora. */
    data object Downloading : MediaDownloadStatus

    /** Pausado **por quem usa** — nunca pelo sistema. Retomar é [MediaDownloadManager.resume]. */
    data object Paused : MediaDownloadStatus

    /** Terminado: a mídia toca sem rede. */
    data object Completed : MediaDownloadStatus

    /**
     * O direito de acesso venceu ([MediaDownloadRequest.expiresAtMillis]).
     *
     * É estado próprio, e não "sumiu da lista", de propósito: o aluno que perdeu o acesso precisa
     * ver **por que** a aula não abre mais. Os bytes só saem do disco em
     * [MediaDownloadManager.purgeExpired] — quem decide é o app.
     */
    data object Expired : MediaDownloadStatus

    /**
     * Falhou. [message] é para o **usuário** — a tela mostra essa frase.
     *
     * @param cause a mensagem técnica, para log. Nunca vai para a tela.
     */
    data class Failed(
        val kind: MediaDownloadErrorKind,
        val message: String,
        val cause: String? = null,
    ) : MediaDownloadStatus
}

/**
 * A natureza da falha de um download — o que decide o que a tela oferece.
 *
 * [Expired] e [NoSpace] existem separados porque as três saídas são diferentes: repetir (rede),
 * pedir URL nova ao servidor (token vencido) e **liberar espaço** (disco cheio). "Não foi possível
 * baixar" para os três é o que faz o usuário tentar dez vezes a mesma coisa.
 */
enum class MediaDownloadErrorKind {
    /** Sem rede, tempo esgotado, servidor fora. Repetir faz sentido. */
    Network,

    /** 401/403/410 — URL assinada vencida. A saída é **URL nova**, não repetir. Ver o `onRenewUrl`. */
    Expired,

    /** 404 — a mídia não está mais lá. */
    NotFound,

    /** Não coube no aparelho. Ver [checkMediaDownloadSpace]. */
    NoSpace,

    /** Manifesto/codec que o aparelho não baixa nem toca. Repetir não adianta. */
    Unsupported,

    /** Nenhuma das anteriores. */
    Unknown,
}

/**
 * Traduz um status HTTP no tipo de falha do download.
 *
 * Reusa `videoErrorKindForHttpStatus` de propósito: a regra "401/403/410 = token vencido" é a mesma
 * na reprodução e no download, e duplicá-la faria uma das duas envelhecer sozinha.
 */
fun mediaDownloadErrorKindForHttpStatus(status: Int): MediaDownloadErrorKind =
    when (videoErrorKindForHttpStatus(status)) {
        VideoErrorKind.Expired -> MediaDownloadErrorKind.Expired
        VideoErrorKind.NotFound -> MediaDownloadErrorKind.NotFound
        VideoErrorKind.Network -> MediaDownloadErrorKind.Network
        VideoErrorKind.Unsupported -> MediaDownloadErrorKind.Unsupported
        VideoErrorKind.Unknown -> MediaDownloadErrorKind.Unknown
    }

/**
 * Um item da tela de Downloads: o que se sabe da mídia **e** do andamento dela.
 *
 * @param id o mesmo [MediaDownloadRequest.id] que o app pediu.
 * @param downloadedBytes quanto já está no disco. Para um item [MediaDownloadStatus.Completed], é
 *   **o espaço que ele ocupa** — é este número que a tela soma no "1,4 GB em 12 aulas".
 * @param totalBytes o total esperado, ou `0` quando ainda não se sabe (manifesto não lido, servidor
 *   sem `Content-Length`).
 * @param percent `0..100`, ou `null` enquanto o total é desconhecido — e é `null` **de propósito**:
 *   uma barra parada em 0% mente, um anel indeterminado não (`ProgressRing(progress = null)`).
 * @param waitingForNetwork o download está na fila **porque a rede atual não serve** (só-Wi-Fi
 *   ligado, aparelho no 4G). A tela diz "aguardando Wi-Fi" em vez de "na fila".
 */
data class MediaDownload(
    val id: String,
    val url: String,
    val title: String? = null,
    val groupId: String? = null,
    val status: MediaDownloadStatus = MediaDownloadStatus.Queued,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val percent: Int? = null,
    val waitingForNetwork: Boolean = false,
    val expiresAtMillis: Long? = null,
) {
    /** `true` quando a mídia já toca sem rede. */
    val isPlayableOffline: Boolean get() = status == MediaDownloadStatus.Completed

    /** `true` enquanto o item ainda vai consumir rede — o que a tela agrupa como "fila". */
    val isActive: Boolean
        get() = status == MediaDownloadStatus.Queued || status == MediaDownloadStatus.Downloading
}

/**
 * O percentual concluído, ou `null` quando o total é desconhecido.
 *
 * **Nunca devolve 100 antes de terminar**, e não é preciosismo: um `Content-Length` levemente menor
 * que o real (acontece com resposta comprimida) faria a conta bater 100% com o arquivo pela metade,
 * e a tela ficaria em "100%" por minutos — o jeito mais rápido de o usuário concluir que travou.
 * O teto é 99 até o download ser marcado como concluído.
 */
fun mediaDownloadPercent(downloadedBytes: Long, totalBytes: Long): Int? {
    if (totalBytes <= 0L) return null
    if (downloadedBytes <= 0L) return 0
    val bruto = (downloadedBytes.toDouble() / totalBytes.toDouble() * 100.0).toInt()
    return bruto.coerceIn(0, 99)
}

/**
 * **A máquina de estados da fila** — a decisão de qual estado exclusivo vale, dado o que a
 * plataforma informa.
 *
 * Existe uma só, em `commonMain`, porque o Media3 e o `AVAssetDownloadTask` têm vocabulários
 * diferentes (`STATE_QUEUED`/`STATE_STOPPED`/`STATE_FAILED` de um lado; `URLSessionTask.State` e
 * "sem task nenhuma" do outro) e, sem este ponto único, as duas plataformas divergiriam justamente
 * nos casos de borda — com build verde nos dois lados.
 *
 * **A ordem de precedência é a regra, e é o que os testes fixam:**
 * 1. **[MediaDownloadStatus.Expired] vence tudo, inclusive concluído.** Direito que caiu não pode
 *    aparecer como "disponível offline" só porque os bytes ainda estão no disco.
 * 2. **Concluído vence falha.** Um erro de rede registrado depois do término (a última tentativa de
 *    limpeza, um requisito reavaliado) não pode desfazer um download que já terminou.
 * 3. **Pausa vence fila e falha.** Quem pausou vê "pausado", não "erro" nem "na fila" — e continua
 *    vendo isso depois de fechar e reabrir o app.
 * 4. Rodando vence fila; falha vence fila.
 *
 * @param completed a plataforma diz que terminou.
 * @param running há transferência acontecendo agora.
 * @param pausedByUser o app chamou [MediaDownloadManager.pause] (estado **persistido**).
 * @param failure a última falha, ou `null`.
 * @param expired [isMediaDownloadExpired] deu `true`.
 */
fun mediaDownloadStatusOf(
    completed: Boolean,
    running: Boolean,
    pausedByUser: Boolean,
    failure: MediaDownloadStatus.Failed?,
    expired: Boolean,
): MediaDownloadStatus = when {
    expired -> MediaDownloadStatus.Expired
    completed -> MediaDownloadStatus.Completed
    pausedByUser -> MediaDownloadStatus.Paused
    failure != null -> failure
    running -> MediaDownloadStatus.Downloading
    else -> MediaDownloadStatus.Queued
}

/** O que a tela de Downloads mostra de cabeçalho: quanto ocupa, quantos itens, quanto ainda cabe. */
data class MediaStorageUsage(
    /** Espaço ocupado pelas mídias baixadas por este gerenciador. */
    val usedBytes: Long,
    /** Quantos itens estão no disco (concluídos e parciais). */
    val itemCount: Int,
    /** Espaço livre no aparelho, já **descontada** a reserva de [MediaDownloadConfig]. */
    val availableBytes: Long,
)

/** As frases que a lib devolve ao usuário. Defaults em pt-BR; troque para traduzir. */
data class MediaDownloadTexts(
    val errorNetwork: String = "Não foi possível baixar. Verifique a conexão e tente de novo.",
    val errorExpired: String = "O link expirou. Abra a aula para baixar de novo.",
    val errorNotFound: String = "Este conteúdo não está mais disponível.",
    val errorNoSpace: String = "Não há espaço suficiente no aparelho para baixar.",
    val errorUnsupported: String = "Este conteúdo não pode ser baixado neste aparelho.",
    val errorUnknown: String = "Não foi possível baixar.",
    val invalidId: String = "Não foi possível baixar: identificador inválido.",
) {
    fun messageFor(kind: MediaDownloadErrorKind): String = when (kind) {
        MediaDownloadErrorKind.Network -> errorNetwork
        MediaDownloadErrorKind.Expired -> errorExpired
        MediaDownloadErrorKind.NotFound -> errorNotFound
        MediaDownloadErrorKind.NoSpace -> errorNoSpace
        MediaDownloadErrorKind.Unsupported -> errorUnsupported
        MediaDownloadErrorKind.Unknown -> errorUnknown
    }

    /** Açúcar: monta o estado de falha já com a frase certa. */
    fun failure(kind: MediaDownloadErrorKind, cause: String? = null): MediaDownloadStatus.Failed =
        MediaDownloadStatus.Failed(kind = kind, message = messageFor(kind), cause = cause)
}
