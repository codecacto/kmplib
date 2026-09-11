package br.com.codecacto.kmplib.video.feed

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * O **som do feed** — um estado só, compartilhado por todos os vídeos.
 *
 * É assim no Instagram, e é o que a pessoa espera: ligou o som num vídeo, o próximo que entrar na vez
 * **já vem com som**; desligou, todos voltam mudos. Um botão de som por vídeo, cada um com a sua
 * memória, obrigaria a ligar o som de novo a cada post.
 *
 * Todo feed nasce **mudo**. Vídeo que começa a tocar sozinho com som é o que faz a pessoa fechar o
 * app no meio do ônibus — e, no iOS, é também o que interrompe a música que ela estava ouvindo.
 *
 * ### Escopo
 * [Shared] é o default de [rememberFeedVideoController]: o som vale para o **processo** — o feed da
 * Início e o detalhe da publicação concordam, e o app morto volta mudo (que é o certo). Um feed que
 * precise de memória própria cria a sua instância e a passa ao controller.
 *
 * O que **não** mora aqui: o volume. Quem manda no volume é o botão físico do aparelho.
 */
@Stable
class FeedVideoSoundState(initialMuted: Boolean = true) {

    /**
     * `true` = os vídeos do feed tocam sem som.
     *
     * `val` com apoio privado, e não `var … private set`: o setter compilaria como `setMuted(Z)V` e
     * colidiria com a ação [setMuted] no alvo JVM (*platform declaration clash*).
     */
    val isMuted: Boolean get() = mudo

    private var mudo: Boolean by mutableStateOf(initialMuted)

    /** Liga ou desliga o som do feed inteiro. */
    fun setMuted(muted: Boolean) {
        mudo = muted
    }

    /** O que o botão de alto-falante faz. */
    fun toggle() {
        mudo = !mudo
    }

    companion object {
        /** O som do feed para o processo inteiro. Ver o KDoc da classe. */
        val Shared: FeedVideoSoundState = FeedVideoSoundState(initialMuted = true)
    }
}
