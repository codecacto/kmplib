package br.com.codecacto.kmplib.signature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import br.com.codecacto.kmplib.core.locale.FactoryLocales
import br.com.codecacto.kmplib.core.locale.appLanguageTag

/**
 * Textos de acessibilidade do [SignaturePad] — o que o TalkBack/VoiceOver anuncia.
 *
 * @property contentDescription nome do quadro ("Quadro de assinatura"). Quem tem mais de um quadro
 *   na tela passa o próprio pelo parâmetro `contentDescription` do [SignaturePad]
 *   ("Assinatura do emitente", "Assinatura do pagador").
 * @property stateEmpty estado anunciado enquanto nada foi desenhado.
 * @property stateSigned estado anunciado depois do primeiro traço.
 * @property clearAction rótulo da ação de acessibilidade que limpa o quadro.
 * @property undoAction rótulo da ação de acessibilidade que desfaz o último traço.
 */
data class SignaturePadTexts(
    val contentDescription: String,
    val stateEmpty: String,
    val stateSigned: String,
    val clearAction: String,
    val undoAction: String,
)

/**
 * Os textos do [SignaturePad] no idioma da tela.
 *
 * O módulo `kmplib-platform` não carrega compose-resources (o `Res` da lib é gerado só no
 * `kmplib-ui`, que depende DESTE módulo), então as quatro traduções da fábrica moram aqui e a
 * escolha usa [appLanguageTag] — o mesmo [FactoryLocales.match] que decide a pasta do
 * compose-resources. Resultado: o quadro fala o mesmo idioma do resto da tela (pt-AO e fr-FR caem
 * em pt-BR, como os recursos).
 */
fun signaturePadTexts(languageTag: String = appLanguageTag()): SignaturePadTexts =
    when (FactoryLocales.match(languageTag)) {
        FactoryLocales.EN -> SignaturePadTexts(
            contentDescription = "Signature pad",
            stateEmpty = "Not signed",
            stateSigned = "Signed",
            clearAction = "Clear signature",
            undoAction = "Undo last stroke",
        )
        FactoryLocales.ES -> SignaturePadTexts(
            contentDescription = "Recuadro de firma",
            stateEmpty = "Sin firmar",
            stateSigned = "Firmado",
            clearAction = "Borrar firma",
            undoAction = "Deshacer último trazo",
        )
        FactoryLocales.PT_PT -> SignaturePadTexts(
            contentDescription = "Quadro de assinatura",
            stateEmpty = "Por assinar",
            stateSigned = "Assinado",
            clearAction = "Limpar assinatura",
            undoAction = "Anular último traço",
        )
        else -> SignaturePadTexts(
            contentDescription = "Quadro de assinatura",
            stateEmpty = "Sem assinatura",
            stateSigned = "Assinado",
            clearAction = "Limpar assinatura",
            undoAction = "Desfazer último traço",
        )
    }

/** [signaturePadTexts] lembrado na composição — o default do [SignaturePad]. */
@Composable
fun rememberSignaturePadTexts(): SignaturePadTexts = remember { signaturePadTexts() }
