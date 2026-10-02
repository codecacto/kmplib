package br.com.codecacto.kmplib.platform.automation

/**
 * **Ids dos diálogos, folhas e menus da lib para automação de UI** (2.234.0) — mesmo desenho de
 * `LoginTestTags`, `PaywallTestTags` e `AdsTestTags`: quem renderiza é a lib, então um id plantado no
 * app não alcançaria nada.
 *
 * ## Onde cada id aparece
 *
 * | id | componentes |
 * |---|---|
 * | [CONTAINER] | `AppDialog` (e os que se montam sobre ele: `AppAlertDialog`, `AppInputDialog`, `ForcePasswordChangeDialog`), `ConfirmationDialog`, `InputDialog`, `ErrorModal`, `NoInternetDialog`, `NoInternetModal`, `AppTimePicker`, `AppDatePicker`/`AppDatePickerDialog`, `AppTimeField`/`AppTimePickerDialog`, `SoftUpdateDialog`, `AppReviewDialog`, `DictationOverlay` |
 * | [TITULO] / [MENSAGEM] | o título e o texto desses diálogos, quando existem |
 * | [INPUT] | o campo do `AppInputDialog` (o "digite EXCLUIR" da exclusão de conta) e do `InputDialog` |
 * | [BTN_CONFIRMAR] | a ação principal — confirmar, OK, salvar, "Entendi", "Atualizar". Diálogo de **um botão só** usa este |
 * | [BTN_CANCELAR] | a ação de recuar — cancelar, "Agora não", "Depois" |
 * | — | `ForcePasswordChangeDialog` mantém os ids próprios de antes (`force_password_new`, `force_password_confirm`, `force_password_submit`) — contrato já em uso |
 * | [BTN_FECHAR] | o "X" das telas cheias (`FullScreenImageViewer`, `FullScreenGallery`) |
 * | [FOLHA] | `AppBottomSheet` e a folha "câmera ou galeria" dos seletores de foto/vídeo (Android) |
 * | [MENU] | os menus suspensos (`AppDropdownField`, `AppMultiDropdownField`, seletores do calendário, velocidade/legenda do `VideoPlayer`) |
 *
 * Diálogo de **conteúdo livre** (`AppDialog` com `content` do app) leva só [CONTAINER] e [TITULO]:
 * os botões que o app desenha lá dentro recebem o id que o app der — e aparecem para o Maestro,
 * porque a janela inteira já expõe `testTag` como `resource-id`.
 *
 * ## Como o Maestro enxerga
 *
 * No Android, todo diálogo é **outra janela**, que NÃO herda a flag `testTagsAsResourceId` que o
 * `AppTheme` liga na raiz. Até a 2.233.0 todo nó dentro de um diálogo da lib saía com
 * `resource-id=""`. Desde a 2.234.0 cada janela da lib liga a flag de novo no nó-raiz
 * (`Modifier.exposeTestTagsAsResourceId()`). iOS: a `testTag` vira `accessibilityIdentifier` sem flag.
 *
 * ```yaml
 * - tapOn: { id: "dialogo-input" }
 * - inputText: "EXCLUIR"
 * - tapOn: { id: "dialogo-btn-confirmar" }
 * ```
 *
 * Um diálogo aberto por vez é o caso normal; se dois se empilharem, o Maestro pega o primeiro da
 * hierarquia — ancore no [TITULO]/[MENSAGEM] antes de tocar.
 */
object DialogTestTags {

    /** Superfície do diálogo — presente enquanto ele está aberto. */
    const val CONTAINER: String = "dialogo"

    /** Título do diálogo. */
    const val TITULO: String = "dialogo-titulo"

    /** Texto/mensagem do diálogo. */
    const val MENSAGEM: String = "dialogo-mensagem"

    /** Campo de texto do diálogo de entrada. */
    const val INPUT: String = "dialogo-input"

    /** Ação principal (confirmar/OK/salvar). Diálogo de um botão só usa este. */
    const val BTN_CONFIRMAR: String = "dialogo-btn-confirmar"

    /** Ação de recuar (cancelar/agora não/depois). */
    const val BTN_CANCELAR: String = "dialogo-btn-cancelar"

    /** "X" que fecha uma tela cheia modal. */
    const val BTN_FECHAR: String = "dialogo-btn-fechar"

    /** Folha inferior modal. */
    const val FOLHA: String = "dialogo-folha"

    /** Menu suspenso aberto. */
    const val MENU: String = "dialogo-menu"

    /** Todos os ids, para conferência (unicidade, vocabulário) e para quem gera flows. */
    val all: List<String> = listOf(
        CONTAINER,
        TITULO,
        MENSAGEM,
        INPUT,
        BTN_CONFIRMAR,
        BTN_CANCELAR,
        BTN_FECHAR,
        FOLHA,
        MENU,
    )
}
