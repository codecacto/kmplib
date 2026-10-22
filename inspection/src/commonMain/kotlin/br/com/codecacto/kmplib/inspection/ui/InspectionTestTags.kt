package br.com.codecacto.kmplib.inspection.ui

/**
 * Ids de teste (Maestro) do kit: `vistoria-<tela>-<elemento>` (§5 do Laudo Kit). Opções de um eixo
 * viram `<grupo>-<key>` (`vistoria-item-estado-bom`, `vistoria-saida-resultado-nconf`); itens de
 * lista, `<lista>-<id>`. Nunca o texto da tela: muda nos 4 idiomas.
 */
object InspectionTestTags {
    const val TOPBAR_BACK = "vistoria-topo-voltar"
    const val TOPBAR_TITLE = "vistoria-topo-titulo"

    const val ROOMS_LIST = "vistoria-comodos-lista"
    const val ROOMS_REVIEW = "vistoria-comodos-revisao"
    const val ROOMS_ADD = "vistoria-comodos-adicionar"
    const val ROOMS_PROGRESS = "vistoria-comodos-progresso"
    fun room(id: String) = "vistoria-comodo-$id"
    fun roomMenu(id: String) = "vistoria-comodo-$id-menu"
    const val ROOM_MENU_DUPLICATE = "vistoria-comodo-menu-duplicar"
    const val ROOM_MENU_RENAME = "vistoria-comodo-menu-renomear"
    const val ROOM_MENU_REMOVE = "vistoria-comodo-menu-remover"

    const val ITEMS_LIST = "vistoria-itens-lista"
    const val ITEMS_FILTER = "vistoria-itens-filtro"
    const val ITEMS_ROOM_DONE = "vistoria-itens-comodo-pronto"
    const val ITEMS_ROOM_VIDEO = "vistoria-itens-video"
    const val ITEMS_ROOM_VOICE = "vistoria-itens-voz"
    const val ITEMS_MARK_ALL = "vistoria-itens-marcar-todos"
    fun item(id: String) = "vistoria-item-$id"

    const val ITEM_CONDITION = "vistoria-item-estado"
    const val ITEM_FUNCTIONING = "vistoria-item-funciona"
    const val ITEM_NOTE = "vistoria-item-observacao"
    const val ITEM_PHOTO = "vistoria-item-foto"
    const val ITEM_VIDEO = "vistoria-item-video"
    const val ITEM_VOICE = "vistoria-item-voz"
    const val ITEM_GALLERY = "vistoria-item-galeria"
    const val ITEM_SAVE = "vistoria-item-salvar"
    const val ITEM_CLOSE = "vistoria-item-fechar"

    const val MEDIA_STRIP = "vistoria-midias"
    const val MEDIA_ADD = "vistoria-midias-adicionar"
    fun media(id: String) = "vistoria-midia-$id"
    fun mediaRetry(id: String) = "vistoria-midia-$id-tentar"

    const val EXIT_TABS = "vistoria-saida-abas"
    const val EXIT_RESULT = "vistoria-saida-resultado"
    const val EXIT_DIVERGENCE = "vistoria-saida-divergencia"
    const val EXIT_NOTE = "vistoria-saida-observacao"
    const val EXIT_PHOTO = "vistoria-saida-foto"
    const val EXIT_SAVE = "vistoria-saida-salvar"

    const val CAMERA_SHUTTER = "vistoria-camera-disparo"
    const val CAMERA_DONE = "vistoria-camera-ok"
    const val CAMERA_CLOSE = "vistoria-camera-fechar"
    const val CAMERA_FLASH = "vistoria-camera-flash"
    const val CAMERA_TIMER = "vistoria-camera-temporizador"
    const val CAMERA_OPACITY = "vistoria-camera-transparencia"
    const val CAMERA_COUNTER = "vistoria-camera-minimo"

    const val REVIEW_PENDENCIES = "vistoria-revisao-pendencias"
    fun reviewGoTo(id: String) = "vistoria-revisao-ir-$id"
    const val REVIEW_FINISH = "vistoria-revisao-finalizar"
    const val REVIEW_FINISH_CONFIRM = "vistoria-revisao-finalizar-confirmar"
    const val REVIEW_FINISH_BACK = "vistoria-revisao-finalizar-voltar"

    const val REPORT_SHARE = "vistoria-laudo-compartilhar"
    const val REPORT_COPY_LINK = "vistoria-laudo-copiar-link"
    const val REPORT_PROOF = "vistoria-laudo-prova"
    const val REPORT_MENU = "vistoria-laudo-menu"

    const val HASH_COPY = "vistoria-hash-copiar"

    const val QUEUE_BANNER = "vistoria-fila-faixa"
    fun queueRetry(id: String) = "vistoria-fila-$id-tentar"

    const val TEMPLATES_TABS = "vistoria-modelos-abas"
    fun template(id: String) = "vistoria-modelo-$id"
    fun templateCopy(id: String) = "vistoria-modelo-$id-copiar"
    const val TEMPLATES_NEW = "vistoria-modelos-novo"
    const val EDITOR_SAVE = "vistoria-editor-salvar"
    const val EDITOR_ROOMS = "vistoria-editor-comodos"
    const val EDITOR_ADD_ITEM = "vistoria-editor-adicionar-item"
    fun editorItem(id: String) = "vistoria-editor-item-$id"
    const val EDIT_ITEM_NAME = "vistoria-editor-item-nome"
    const val EDIT_ITEM_REQUIRED = "vistoria-editor-item-obrigatorio"
    const val EDIT_ITEM_SAVE = "vistoria-editor-item-salvar"
    const val EDIT_ITEM_QUESTIONS = "vistoria-editor-item-perguntas"

    const val VOICE_TALK = "vistoria-voz-falar"
    const val VOICE_APPLY = "vistoria-voz-aplicar"

    const val SIGN_CONSENT = "vistoria-assinar-concordo"
    const val SIGN_PAD = "vistoria-assinar-quadro"
    const val SIGN_CODE = "vistoria-assinar-codigo"
    const val SIGN_SUBMIT = "vistoria-assinar-enviar"
    const val SIGN_RESEND = "vistoria-assinar-reenviar"
    const val SIGN_LINK_SHARE = "vistoria-assinar-link-compartilhar"
}
