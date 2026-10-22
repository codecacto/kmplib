// GERADO por inspection/tools/gerar_textos.py a partir de inspection/tools/textos.tsv — não editar à mão.
package br.com.codecacto.kmplib.inspection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_condition_new
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_condition_good
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_condition_regular
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_condition_bad
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_condition_not_applicable
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_result_conforming
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_result_reservation
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_result_non_conforming
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_functioning_yes
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_functioning_no
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_functioning_not_tested
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_functioning_works
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_functioning_broken
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_divergence_wear
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_divergence_damage
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_divergence_missing
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_divergence_cleaning
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_media_queued
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_media_uploading
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_media_confirmed
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_media_failed
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_signature_pending
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_signature_awaiting_code
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_signature_signed
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_verification_authentic
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_verification_newer
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_verification_mismatch
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_verification_unavailable
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_guided_task
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_exit_in_review
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_back
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_close
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_save
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_cancel
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_retry
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_more_options
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_copy
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_copied
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_rooms_title
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_review
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_rooms_ready
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_room_ready
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_room_not_started
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_room_to_review_one
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_room_to_review_other
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_room_progress_description
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_add_room
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_add_room_short
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_duplicate
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_rename
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_remove
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_rename_room_title
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_room_name_label
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_remove_room_title
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_remove_room_with_media
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_remove_room_no_media
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_empty_rooms
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_reorder_hint
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_room_photos
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_record_video
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_record_voice
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_filter_all
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_filter_problem
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_filter_no_photo
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_filter_changed
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_filter_pending
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_items_with_problem_one
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_items_with_problem_other
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_photo_count_one
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_photo_count_other
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_voice_note
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_no_photo
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_required_missing_one
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_required_missing_other
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_guided_task_meta_one
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_guided_task_meta_other
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_room_done
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_empty_filter
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_mark_all_conforming
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_mark_all_conforming_title
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_mark_all_conforming_message
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_mark_action
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_item_position
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_condition_label
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_functioning_label
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_note_label
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_photo
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_video
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_voice
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_attach_from_gallery
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_attachment_badge
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_add_photo
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_discard_title
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_discard_message
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_discard_confirm
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_keep_editing
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_save_failed
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_guided_minimum_one
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_guided_minimum_other
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_media_description
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_video_description
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_remove_media_title
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_more_media
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_tab_entry
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_tab_exit
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_tab_side_by_side
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_result_label
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_divergence_label
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_required_suffix
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_divergence_required
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_exit_photo
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_no_entry_photo
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_no_exit_photo
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_align_with_entry
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_overlay_opacity
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_minimum_progress
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_minimum_done
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_done
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_last_photo
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_no_location
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_time_not_confirmed
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_low_storage
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_free_space
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_capture_save_failed
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_zoom_label
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_review_summary
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_pendencies
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_go_to_item
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_summary_by_condition
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_problem_items
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_divergences
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_resolve_to_finish_one
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_resolve_to_finish_other
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_all_ready
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_finish
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_finish_title
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_finish_message
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_finish_failed
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_generating_report
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_pending_media
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_page_of
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_signed_by
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_awaiting_signatures
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_hash_short
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_share_pdf
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_copy_verify_link
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_versions
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_new_version
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_report_error
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_current_version
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_hash_label
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_copy_hash
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_qr_description
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_queue_progress
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_queue_waiting_wifi_one
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_queue_waiting_wifi_other
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_send_with_mobile_data
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_mobile_data_title
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_mobile_data_message
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_send
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_queue_offline
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_queue_failed
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_queue_done
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_open_queue
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_queue_title
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_queue_empty
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_confirmed_section
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_storage_title
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_storage_reclaimable
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_storage_nothing
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_templates_title
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_my_templates
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_ready_templates
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_template_summary
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_template_usage
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_start_from_ready
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_copy_and_edit
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_new_blank_template
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_editor_subtitle
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_preview
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_add_item
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_changes_note
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_required_tag
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_edit_item_title
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_new_item_title
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_item_name_label
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_min_photos_label
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_default_condition_label
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_item_questions
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_own_question
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_question_material
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_question_color
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_question_brand
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_question_quantity
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_question_name_label
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_question_type_label
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_type_text
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_type_choice
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_type_number
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_type_yes_no
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_type_date
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_question_required
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_choice_options_label
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_add_question
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_name_required
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_choice_options_required
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_template_conflict
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_reload
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_go_to_first_error
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_leave_editor_title
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_leave_editor_message
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_leave
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_empty_room_items
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_read_only_template
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_voice_title
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_hold_to_talk
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_listening
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_structuring
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_proposals_title
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_not_found
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_apply
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_voice_offline
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_voice_failed
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_mic_permission
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_allow_mic
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_transcript_label
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_sign_title
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_signature_label
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_undo
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_clear
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_code_sent_sms
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_code_sent_email
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_code_sent_whatsapp
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_code_wrong_one
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_code_wrong_other
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_code_expired
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_resend_in
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_resend
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_sign_offline
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_sign_agree_required
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_sign_stroke_required
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_code_label
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_signed_done
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_signature_link_title
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_creating_link
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_link_error
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_share_link
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_copy_link
import br.com.codecacto.kmplib.generated.resources.kmplib_insp_link_shared
import br.com.codecacto.kmplib.ui.locale.kmpStringResource

/**
 * Todos os textos do Laudo Kit, por tela. Defaults em pt-BR; [rememberInspectionTexts] traz os 4
 * idiomas da fábrica (pt-BR, en, es, pt-PT) no idioma da tela. Campos com `%1$s`/`%2$s` são MODELOS:
 * formate com `fmt` (ver `InspectionTextFormats.kt`). Para trocar uma frase de domínio ("cômodo" →
 * "ambiente", "laudo" → "documento"): `t.copy(rooms = t.rooms.copy(roomsTitle = …))`.
 */
@Immutable
data class InspectionTexts(
    val values: InspectionValuesTexts = InspectionValuesTexts(),
    val common: InspectionCommonTexts = InspectionCommonTexts(),
    val rooms: InspectionRoomsTexts = InspectionRoomsTexts(),
    val items: InspectionItemsTexts = InspectionItemsTexts(),
    val item: InspectionItemSheetTexts = InspectionItemSheetTexts(),
    val exit: InspectionExitTexts = InspectionExitTexts(),
    val camera: InspectionCameraTexts = InspectionCameraTexts(),
    val review: InspectionReviewTexts = InspectionReviewTexts(),
    val report: InspectionReportTexts = InspectionReportTexts(),
    val queue: InspectionQueueTexts = InspectionQueueTexts(),
    val template: InspectionTemplateTexts = InspectionTemplateTexts(),
    val voice: InspectionVoiceTexts = InspectionVoiceTexts(),
    val signature: InspectionSignatureTexts = InspectionSignatureTexts(),
)

/** Textos do kit — values. */
@Immutable
data class InspectionValuesTexts(
    val conditionNew: String = "Novo",
    val conditionGood: String = "Bom",
    val conditionRegular: String = "Regular",
    val conditionBad: String = "Ruim",
    val conditionNotApplicable: String = "N/A",
    val resultConforming: String = "Conforme",
    val resultReservation: String = "Ressalva",
    val resultNonConforming: String = "Não conforme",
    val functioningYes: String = "Sim",
    val functioningNo: String = "Não",
    val functioningNotTested: String = "Não testado",
    val functioningWorks: String = "Funciona",
    val functioningBroken: String = "Não funciona",
    val divergenceWear: String = "Desgaste natural",
    val divergenceDamage: String = "Dano",
    val divergenceMissing: String = "Falta",
    val divergenceCleaning: String = "Limpeza",
    val mediaQueued: String = "Na fila",
    val mediaUploading: String = "Enviando",
    val mediaConfirmed: String = "Confirmada",
    val mediaFailed: String = "Falhou",
    val signaturePending: String = "Pendente",
    val signatureAwaitingCode: String = "Aguardando código",
    val signatureSigned: String = "Assinou",
    val verificationAuthentic: String = "Autêntico",
    val verificationNewer: String = "Há uma versão mais recente",
    val verificationMismatch: String = "Não confere",
    val verificationUnavailable: String = "Link indisponível",
    val guidedTask: String = "Tarefa",
    val exitInReview: String = "Saída em revisão",
)

/** Textos do kit — common. */
@Immutable
data class InspectionCommonTexts(
    val back: String = "Voltar",
    val close: String = "Fechar",
    val save: String = "Salvar",
    val cancel: String = "Cancelar",
    val retry: String = "Tentar de novo",
    val moreOptions: String = "Mais opções",
    val copy: String = "Copiar",
    val copied: String = "Copiado",
)

/** Textos do kit — rooms. */
@Immutable
data class InspectionRoomsTexts(
    val roomsTitle: String = "Cômodos",
    val review: String = "Revisão",
    val roomsReady: String = "%1\$s de %2\$s prontos",
    val roomReady: String = "Pronto",
    val roomNotStarted: String = "Não iniciado",
    val roomToReviewOne: String = "%1\$s item a revisar",
    val roomToReviewOther: String = "%1\$s itens a revisar",
    val roomProgressDescription: String = "%1\$s de %2\$s itens feitos",
    val addRoom: String = "Adicionar cômodo",
    val addRoomShort: String = "cômodo",
    val duplicate: String = "Duplicar",
    val rename: String = "Renomear",
    val remove: String = "Remover",
    val renameRoomTitle: String = "Renomear cômodo",
    val roomNameLabel: String = "Nome do cômodo",
    val removeRoomTitle: String = "Remover cômodo?",
    val removeRoomWithMedia: String = "%1\$s e as fotos dele saem desta vistoria.",
    val removeRoomNoMedia: String = "%1\$s sai desta vistoria.",
    val emptyRooms: String = "Adicione o primeiro cômodo",
    val reorderHint: String = "Segure e arraste para reordenar",
)

/** Textos do kit — items. */
@Immutable
data class InspectionItemsTexts(
    val roomPhotos: String = "Fotos do cômodo (%1\$s)",
    val recordVideo: String = "Gravar vídeo",
    val recordVoice: String = "Gravar nota de voz",
    val filterAll: String = "Todos · %1\$s",
    val filterProblem: String = "Com problema · %1\$s",
    val filterNoPhoto: String = "Sem foto · %1\$s",
    val filterChanged: String = "Só o que mudou · %1\$s",
    val filterPending: String = "Pendentes · %1\$s",
    val itemsWithProblemOne: String = "%1\$s item com problema",
    val itemsWithProblemOther: String = "%1\$s itens com problema",
    val photoCountOne: String = "%1\$s foto",
    val photoCountOther: String = "%1\$s fotos",
    val voiceNote: String = "Nota de voz",
    val noPhoto: String = "Sem foto",
    val requiredMissingOne: String = "Obrigatório · falta %1\$s foto",
    val requiredMissingOther: String = "Obrigatório · faltam %1\$s fotos",
    val guidedTaskMetaOne: String = "Tarefa guiada · %1\$s foto obrigatória",
    val guidedTaskMetaOther: String = "Tarefa guiada · %1\$s fotos obrigatórias",
    val roomDone: String = "Cômodo pronto",
    val emptyFilter: String = "Nenhum item neste filtro",
    val markAllConforming: String = "Marcar todos como Conforme",
    val markAllConformingTitle: String = "Marcar %1\$s itens como Conforme?",
    val markAllConformingMessage: String = "Os itens já avaliados não mudam.",
    val markAction: String = "Marcar",
)

/** Textos do kit — item. */
@Immutable
data class InspectionItemSheetTexts(
    val itemPosition: String = "%1\$s · item %2\$s de %3\$s",
    val conditionLabel: String = "Estado",
    val functioningLabel: String = "Funciona?",
    val noteLabel: String = "Observação",
    val photo: String = "Foto",
    val video: String = "Vídeo",
    val voice: String = "Voz",
    val attachFromGallery: String = "Anexar da galeria",
    val attachmentBadge: String = "Anexo",
    val addPhoto: String = "Adicionar foto",
    val discardTitle: String = "Descartar alterações?",
    val discardMessage: String = "As mudanças deste item não foram salvas.",
    val discardConfirm: String = "Descartar",
    val keepEditing: String = "Continuar editando",
    val saveFailed: String = "Não foi possível salvar. Tente de novo.",
    val guidedMinimumOne: String = "Tire %1\$s foto para salvar.",
    val guidedMinimumOther: String = "Tire %1\$s fotos para salvar.",
    val mediaDescription: String = "Foto %1\$s, %2\$s",
    val videoDescription: String = "Vídeo %1\$s, %2\$s",
    val removeMediaTitle: String = "Remover esta mídia?",
    val moreMedia: String = "+%1\$s",
)

/** Textos do kit — exit. */
@Immutable
data class InspectionExitTexts(
    val tabEntry: String = "Entrada",
    val tabExit: String = "Saída",
    val tabSideBySide: String = "Lado a lado",
    val resultLabel: String = "Resultado",
    val divergenceLabel: String = "Divergência",
    val requiredSuffix: String = "obrigatória",
    val divergenceRequired: String = "Escolha a divergência.",
    val exitPhoto: String = "Foto de saída",
    val noEntryPhoto: String = "Sem foto na entrada",
    val noExitPhoto: String = "Sem foto na saída",
)

/** Textos do kit — camera. */
@Immutable
data class InspectionCameraTexts(
    val alignWithEntry: String = "Alinhe com a foto da entrada",
    val overlayOpacity: String = "Transparência da foto da entrada",
    val minimumProgress: String = "Mínimo: %1\$s fotos · %2\$s de %1\$s",
    val minimumDone: String = "Mínimo cumprido · %1\$s de %2\$s",
    val done: String = "OK",
    val lastPhoto: String = "Última foto",
    val noLocation: String = "Sem localização",
    val timeNotConfirmed: String = "hora não confirmada",
    val lowStorage: String = "Pouco espaço no aparelho",
    val freeSpace: String = "Liberar espaço",
    val captureSaveFailed: String = "Não foi possível guardar a foto. Tente de novo.",
    val zoomLabel: String = "Aproximação %1\$s",
)

/** Textos do kit — review. */
@Immutable
data class InspectionReviewTexts(
    val reviewSummary: String = "%1\$s cômodos · %2\$s itens · %3\$s fotos · %4\$s vídeos",
    val pendencies: String = "Pendências (%1\$s)",
    val goToItem: String = "Ir ao item",
    val summaryByCondition: String = "Resumo por estado",
    val problemItems: String = "Itens com problema (%1\$s)",
    val divergences: String = "Divergências (%1\$s)",
    val resolveToFinishOne: String = "Resolva a pendência para finalizar.",
    val resolveToFinishOther: String = "Resolva as %1\$s pendências para finalizar.",
    val allReady: String = "Tudo pronto para finalizar.",
    val finish: String = "Finalizar",
    val finishTitle: String = "Finalizar a vistoria?",
    val finishMessage: String = "O laudo será gerado para assinatura. Depois de assinar, o laudo não pode ser editado — só com uma nova versão.",
    val finishFailed: String = "Não foi possível finalizar. Tente de novo.",
)

/** Textos do kit — report. */
@Immutable
data class InspectionReportTexts(
    val generatingReport: String = "Gerando o laudo…",
    val pendingMedia: String = "Faltam %1\$s mídias · %2\$s de %3\$s enviadas",
    val pageOf: String = "página %1\$s de %2\$s",
    val signedBy: String = "Assinado por %1\$s",
    val awaitingSignatures: String = "Aguardando assinaturas",
    val hashShort: String = "hash %1\$s",
    val sharePdf: String = "Compartilhar PDF",
    val copyVerifyLink: String = "Copiar link de verificação",
    val versions: String = "Versões",
    val newVersion: String = "Criar nova versão (correção)",
    val reportError: String = "Não foi possível gerar o laudo.",
    val currentVersion: String = "Atual",
    val hashLabel: String = "Hash do documento",
    val copyHash: String = "Copiar hash",
    val qrDescription: String = "QR de verificação do laudo",
)

/** Textos do kit — queue. */
@Immutable
data class InspectionQueueTexts(
    val queueProgress: String = "%1\$s de %2\$s fotos enviadas",
    val queueWaitingWifiOne: String = "%1\$s vídeo aguardando Wi-Fi",
    val queueWaitingWifiOther: String = "%1\$s vídeos aguardando Wi-Fi",
    val sendWithMobileData: String = "Enviar agora com dados móveis",
    val mobileDataTitle: String = "Enviar com dados móveis?",
    val mobileDataMessage: String = "Vídeos gastam bastante do seu plano de dados.",
    val send: String = "Enviar",
    val queueOffline: String = "Aguardando conexão",
    val queueFailed: String = "%1\$s com falha",
    val queueDone: String = "Todas as mídias enviadas",
    val openQueue: String = "Ver fila de envio",
    val queueTitle: String = "Envio de mídias",
    val queueEmpty: String = "Nada na fila",
    val confirmedSection: String = "Confirmadas (%1\$s)",
    val storageTitle: String = "Espaço no aparelho",
    val storageReclaimable: String = "%1\$s já confirmados no servidor podem sair deste aparelho.",
    val storageNothing: String = "Nada para liberar agora.",
)

/** Textos do kit — template. */
@Immutable
data class InspectionTemplateTexts(
    val templatesTitle: String = "Modelos de vistoria",
    val myTemplates: String = "Meus modelos · %1\$s",
    val readyTemplates: String = "Modelos prontos · %1\$s",
    val templateSummary: String = "%1\$s cômodos · %2\$s itens",
    val templateUsage: String = "Usado em %1\$s vistorias · editado %2\$s",
    val startFromReady: String = "Começar de um modelo pronto",
    val copyAndEdit: String = "Copiar e editar",
    val newBlankTemplate: String = "Novo modelo em branco",
    val editorSubtitle: String = "%1\$s · %2\$s itens",
    val preview: String = "Prévia",
    val addItem: String = "Adicionar item",
    val changesNote: String = "Mudanças valem para vistorias novas. As já feitas não mudam.",
    val requiredTag: String = "Obrigatório",
    val editItemTitle: String = "Editar item",
    val newItemTitle: String = "Novo item",
    val itemNameLabel: String = "Nome do item",
    val minPhotosLabel: String = "Mínimo de fotos",
    val defaultConditionLabel: String = "Estado padrão",
    val itemQuestions: String = "Perguntas do item",
    val ownQuestion: String = "Pergunta própria",
    val questionMaterial: String = "Material",
    val questionColor: String = "Cor",
    val questionBrand: String = "Marca",
    val questionQuantity: String = "Quantidade",
    val questionNameLabel: String = "Nome da pergunta",
    val questionTypeLabel: String = "Tipo",
    val typeText: String = "Texto",
    val typeChoice: String = "Escolha",
    val typeNumber: String = "Número",
    val typeYesNo: String = "Sim/Não",
    val typeDate: String = "Data",
    val questionRequired: String = "Obrigatória",
    val choiceOptionsLabel: String = "Opções (uma por linha)",
    val addQuestion: String = "Adicionar pergunta",
    val nameRequired: String = "Informe o nome.",
    val choiceOptionsRequired: String = "Informe ao menos duas opções.",
    val templateConflict: String = "Este modelo foi alterado em outro aparelho. Recarregue para continuar.",
    val reload: String = "Recarregar",
    val goToFirstError: String = "Ir ao primeiro",
    val leaveEditorTitle: String = "Sair sem salvar?",
    val leaveEditorMessage: String = "As mudanças deste modelo serão perdidas.",
    val leave: String = "Sair",
    val emptyRoomItems: String = "Adicione o primeiro item",
    val readOnlyTemplate: String = "Modelo pronto: copie para editar.",
)

/** Textos do kit — voice. */
@Immutable
data class InspectionVoiceTexts(
    val voiceTitle: String = "Falar os itens",
    val holdToTalk: String = "Segure para falar",
    val listening: String = "Ouvindo…",
    val structuring: String = "Organizando o que você disse…",
    val proposalsTitle: String = "Confira antes de aplicar",
    val notFound: String = "Não encontrei este item",
    val apply: String = "Aplicar (%1\$s)",
    val voiceOffline: String = "Sem conexão: a fala ficou guardada para revisar depois.",
    val voiceFailed: String = "Não consegui organizar a fala. Ela virou observação do cômodo.",
    val micPermission: String = "Permita o microfone para falar os itens.",
    val allowMic: String = "Permitir microfone",
    val transcriptLabel: String = "O que você disse",
)

/** Textos do kit — signature. */
@Immutable
data class InspectionSignatureTexts(
    val signTitle: String = "Assinar",
    val signatureLabel: String = "Assinatura",
    val undo: String = "Desfazer",
    val clear: String = "Limpar",
    val codeSentSms: String = "Código enviado por SMS",
    val codeSentEmail: String = "Código enviado por e-mail",
    val codeSentWhatsapp: String = "Código enviado por WhatsApp",
    val codeWrongOne: String = "Código incorreto, resta %1\$s tentativa.",
    val codeWrongOther: String = "Código incorreto, restam %1\$s tentativas.",
    val codeExpired: String = "O código expirou. Peça outro.",
    val resendIn: String = "Reenviar em %1\$s",
    val resend: String = "Reenviar código",
    val signOffline: String = "Sem conexão: a assinatura fica aguardando o código.",
    val signAgreeRequired: String = "Marque a concordância para assinar.",
    val signStrokeRequired: String = "Faça a assinatura no quadro.",
    val codeLabel: String = "Código",
    val signedDone: String = "Assinado",
    val signatureLinkTitle: String = "Enviar para assinar",
    val creatingLink: String = "Criando o link…",
    val linkError: String = "Não foi possível criar o link.",
    val shareLink: String = "Compartilhar link",
    val copyLink: String = "Copiar link",
    val linkShared: String = "Link enviado",
)

/** Os textos do kit no idioma da tela. */
@Composable
fun rememberInspectionTexts(): InspectionTexts = InspectionTexts(
    values = rememberInspectionValuesTexts(),
    common = rememberInspectionCommonTexts(),
    rooms = rememberInspectionRoomsTexts(),
    items = rememberInspectionItemsTexts(),
    item = rememberInspectionItemSheetTexts(),
    exit = rememberInspectionExitTexts(),
    camera = rememberInspectionCameraTexts(),
    review = rememberInspectionReviewTexts(),
    report = rememberInspectionReportTexts(),
    queue = rememberInspectionQueueTexts(),
    template = rememberInspectionTemplateTexts(),
    voice = rememberInspectionVoiceTexts(),
    signature = rememberInspectionSignatureTexts(),
)

@Composable
private fun rememberInspectionValuesTexts(): InspectionValuesTexts = InspectionValuesTexts(
        conditionNew = kmpStringResource(Res.string.kmplib_insp_condition_new),
        conditionGood = kmpStringResource(Res.string.kmplib_insp_condition_good),
        conditionRegular = kmpStringResource(Res.string.kmplib_insp_condition_regular),
        conditionBad = kmpStringResource(Res.string.kmplib_insp_condition_bad),
        conditionNotApplicable = kmpStringResource(Res.string.kmplib_insp_condition_not_applicable),
        resultConforming = kmpStringResource(Res.string.kmplib_insp_result_conforming),
        resultReservation = kmpStringResource(Res.string.kmplib_insp_result_reservation),
        resultNonConforming = kmpStringResource(Res.string.kmplib_insp_result_non_conforming),
        functioningYes = kmpStringResource(Res.string.kmplib_insp_functioning_yes),
        functioningNo = kmpStringResource(Res.string.kmplib_insp_functioning_no),
        functioningNotTested = kmpStringResource(Res.string.kmplib_insp_functioning_not_tested),
        functioningWorks = kmpStringResource(Res.string.kmplib_insp_functioning_works),
        functioningBroken = kmpStringResource(Res.string.kmplib_insp_functioning_broken),
        divergenceWear = kmpStringResource(Res.string.kmplib_insp_divergence_wear),
        divergenceDamage = kmpStringResource(Res.string.kmplib_insp_divergence_damage),
        divergenceMissing = kmpStringResource(Res.string.kmplib_insp_divergence_missing),
        divergenceCleaning = kmpStringResource(Res.string.kmplib_insp_divergence_cleaning),
        mediaQueued = kmpStringResource(Res.string.kmplib_insp_media_queued),
        mediaUploading = kmpStringResource(Res.string.kmplib_insp_media_uploading),
        mediaConfirmed = kmpStringResource(Res.string.kmplib_insp_media_confirmed),
        mediaFailed = kmpStringResource(Res.string.kmplib_insp_media_failed),
        signaturePending = kmpStringResource(Res.string.kmplib_insp_signature_pending),
        signatureAwaitingCode = kmpStringResource(Res.string.kmplib_insp_signature_awaiting_code),
        signatureSigned = kmpStringResource(Res.string.kmplib_insp_signature_signed),
        verificationAuthentic = kmpStringResource(Res.string.kmplib_insp_verification_authentic),
        verificationNewer = kmpStringResource(Res.string.kmplib_insp_verification_newer),
        verificationMismatch = kmpStringResource(Res.string.kmplib_insp_verification_mismatch),
        verificationUnavailable = kmpStringResource(Res.string.kmplib_insp_verification_unavailable),
        guidedTask = kmpStringResource(Res.string.kmplib_insp_guided_task),
        exitInReview = kmpStringResource(Res.string.kmplib_insp_exit_in_review),
)

@Composable
private fun rememberInspectionCommonTexts(): InspectionCommonTexts = InspectionCommonTexts(
        back = kmpStringResource(Res.string.kmplib_insp_back),
        close = kmpStringResource(Res.string.kmplib_insp_close),
        save = kmpStringResource(Res.string.kmplib_insp_save),
        cancel = kmpStringResource(Res.string.kmplib_insp_cancel),
        retry = kmpStringResource(Res.string.kmplib_insp_retry),
        moreOptions = kmpStringResource(Res.string.kmplib_insp_more_options),
        copy = kmpStringResource(Res.string.kmplib_insp_copy),
        copied = kmpStringResource(Res.string.kmplib_insp_copied),
)

@Composable
private fun rememberInspectionRoomsTexts(): InspectionRoomsTexts = InspectionRoomsTexts(
        roomsTitle = kmpStringResource(Res.string.kmplib_insp_rooms_title),
        review = kmpStringResource(Res.string.kmplib_insp_review),
        roomsReady = kmpStringResource(Res.string.kmplib_insp_rooms_ready),
        roomReady = kmpStringResource(Res.string.kmplib_insp_room_ready),
        roomNotStarted = kmpStringResource(Res.string.kmplib_insp_room_not_started),
        roomToReviewOne = kmpStringResource(Res.string.kmplib_insp_room_to_review_one),
        roomToReviewOther = kmpStringResource(Res.string.kmplib_insp_room_to_review_other),
        roomProgressDescription = kmpStringResource(Res.string.kmplib_insp_room_progress_description),
        addRoom = kmpStringResource(Res.string.kmplib_insp_add_room),
        addRoomShort = kmpStringResource(Res.string.kmplib_insp_add_room_short),
        duplicate = kmpStringResource(Res.string.kmplib_insp_duplicate),
        rename = kmpStringResource(Res.string.kmplib_insp_rename),
        remove = kmpStringResource(Res.string.kmplib_insp_remove),
        renameRoomTitle = kmpStringResource(Res.string.kmplib_insp_rename_room_title),
        roomNameLabel = kmpStringResource(Res.string.kmplib_insp_room_name_label),
        removeRoomTitle = kmpStringResource(Res.string.kmplib_insp_remove_room_title),
        removeRoomWithMedia = kmpStringResource(Res.string.kmplib_insp_remove_room_with_media),
        removeRoomNoMedia = kmpStringResource(Res.string.kmplib_insp_remove_room_no_media),
        emptyRooms = kmpStringResource(Res.string.kmplib_insp_empty_rooms),
        reorderHint = kmpStringResource(Res.string.kmplib_insp_reorder_hint),
)

@Composable
private fun rememberInspectionItemsTexts(): InspectionItemsTexts = InspectionItemsTexts(
        roomPhotos = kmpStringResource(Res.string.kmplib_insp_room_photos),
        recordVideo = kmpStringResource(Res.string.kmplib_insp_record_video),
        recordVoice = kmpStringResource(Res.string.kmplib_insp_record_voice),
        filterAll = kmpStringResource(Res.string.kmplib_insp_filter_all),
        filterProblem = kmpStringResource(Res.string.kmplib_insp_filter_problem),
        filterNoPhoto = kmpStringResource(Res.string.kmplib_insp_filter_no_photo),
        filterChanged = kmpStringResource(Res.string.kmplib_insp_filter_changed),
        filterPending = kmpStringResource(Res.string.kmplib_insp_filter_pending),
        itemsWithProblemOne = kmpStringResource(Res.string.kmplib_insp_items_with_problem_one),
        itemsWithProblemOther = kmpStringResource(Res.string.kmplib_insp_items_with_problem_other),
        photoCountOne = kmpStringResource(Res.string.kmplib_insp_photo_count_one),
        photoCountOther = kmpStringResource(Res.string.kmplib_insp_photo_count_other),
        voiceNote = kmpStringResource(Res.string.kmplib_insp_voice_note),
        noPhoto = kmpStringResource(Res.string.kmplib_insp_no_photo),
        requiredMissingOne = kmpStringResource(Res.string.kmplib_insp_required_missing_one),
        requiredMissingOther = kmpStringResource(Res.string.kmplib_insp_required_missing_other),
        guidedTaskMetaOne = kmpStringResource(Res.string.kmplib_insp_guided_task_meta_one),
        guidedTaskMetaOther = kmpStringResource(Res.string.kmplib_insp_guided_task_meta_other),
        roomDone = kmpStringResource(Res.string.kmplib_insp_room_done),
        emptyFilter = kmpStringResource(Res.string.kmplib_insp_empty_filter),
        markAllConforming = kmpStringResource(Res.string.kmplib_insp_mark_all_conforming),
        markAllConformingTitle = kmpStringResource(Res.string.kmplib_insp_mark_all_conforming_title),
        markAllConformingMessage = kmpStringResource(Res.string.kmplib_insp_mark_all_conforming_message),
        markAction = kmpStringResource(Res.string.kmplib_insp_mark_action),
)

@Composable
private fun rememberInspectionItemSheetTexts(): InspectionItemSheetTexts = InspectionItemSheetTexts(
        itemPosition = kmpStringResource(Res.string.kmplib_insp_item_position),
        conditionLabel = kmpStringResource(Res.string.kmplib_insp_condition_label),
        functioningLabel = kmpStringResource(Res.string.kmplib_insp_functioning_label),
        noteLabel = kmpStringResource(Res.string.kmplib_insp_note_label),
        photo = kmpStringResource(Res.string.kmplib_insp_photo),
        video = kmpStringResource(Res.string.kmplib_insp_video),
        voice = kmpStringResource(Res.string.kmplib_insp_voice),
        attachFromGallery = kmpStringResource(Res.string.kmplib_insp_attach_from_gallery),
        attachmentBadge = kmpStringResource(Res.string.kmplib_insp_attachment_badge),
        addPhoto = kmpStringResource(Res.string.kmplib_insp_add_photo),
        discardTitle = kmpStringResource(Res.string.kmplib_insp_discard_title),
        discardMessage = kmpStringResource(Res.string.kmplib_insp_discard_message),
        discardConfirm = kmpStringResource(Res.string.kmplib_insp_discard_confirm),
        keepEditing = kmpStringResource(Res.string.kmplib_insp_keep_editing),
        saveFailed = kmpStringResource(Res.string.kmplib_insp_save_failed),
        guidedMinimumOne = kmpStringResource(Res.string.kmplib_insp_guided_minimum_one),
        guidedMinimumOther = kmpStringResource(Res.string.kmplib_insp_guided_minimum_other),
        mediaDescription = kmpStringResource(Res.string.kmplib_insp_media_description),
        videoDescription = kmpStringResource(Res.string.kmplib_insp_video_description),
        removeMediaTitle = kmpStringResource(Res.string.kmplib_insp_remove_media_title),
        moreMedia = kmpStringResource(Res.string.kmplib_insp_more_media),
)

@Composable
private fun rememberInspectionExitTexts(): InspectionExitTexts = InspectionExitTexts(
        tabEntry = kmpStringResource(Res.string.kmplib_insp_tab_entry),
        tabExit = kmpStringResource(Res.string.kmplib_insp_tab_exit),
        tabSideBySide = kmpStringResource(Res.string.kmplib_insp_tab_side_by_side),
        resultLabel = kmpStringResource(Res.string.kmplib_insp_result_label),
        divergenceLabel = kmpStringResource(Res.string.kmplib_insp_divergence_label),
        requiredSuffix = kmpStringResource(Res.string.kmplib_insp_required_suffix),
        divergenceRequired = kmpStringResource(Res.string.kmplib_insp_divergence_required),
        exitPhoto = kmpStringResource(Res.string.kmplib_insp_exit_photo),
        noEntryPhoto = kmpStringResource(Res.string.kmplib_insp_no_entry_photo),
        noExitPhoto = kmpStringResource(Res.string.kmplib_insp_no_exit_photo),
)

@Composable
private fun rememberInspectionCameraTexts(): InspectionCameraTexts = InspectionCameraTexts(
        alignWithEntry = kmpStringResource(Res.string.kmplib_insp_align_with_entry),
        overlayOpacity = kmpStringResource(Res.string.kmplib_insp_overlay_opacity),
        minimumProgress = kmpStringResource(Res.string.kmplib_insp_minimum_progress),
        minimumDone = kmpStringResource(Res.string.kmplib_insp_minimum_done),
        done = kmpStringResource(Res.string.kmplib_insp_done),
        lastPhoto = kmpStringResource(Res.string.kmplib_insp_last_photo),
        noLocation = kmpStringResource(Res.string.kmplib_insp_no_location),
        timeNotConfirmed = kmpStringResource(Res.string.kmplib_insp_time_not_confirmed),
        lowStorage = kmpStringResource(Res.string.kmplib_insp_low_storage),
        freeSpace = kmpStringResource(Res.string.kmplib_insp_free_space),
        captureSaveFailed = kmpStringResource(Res.string.kmplib_insp_capture_save_failed),
        zoomLabel = kmpStringResource(Res.string.kmplib_insp_zoom_label),
)

@Composable
private fun rememberInspectionReviewTexts(): InspectionReviewTexts = InspectionReviewTexts(
        reviewSummary = kmpStringResource(Res.string.kmplib_insp_review_summary),
        pendencies = kmpStringResource(Res.string.kmplib_insp_pendencies),
        goToItem = kmpStringResource(Res.string.kmplib_insp_go_to_item),
        summaryByCondition = kmpStringResource(Res.string.kmplib_insp_summary_by_condition),
        problemItems = kmpStringResource(Res.string.kmplib_insp_problem_items),
        divergences = kmpStringResource(Res.string.kmplib_insp_divergences),
        resolveToFinishOne = kmpStringResource(Res.string.kmplib_insp_resolve_to_finish_one),
        resolveToFinishOther = kmpStringResource(Res.string.kmplib_insp_resolve_to_finish_other),
        allReady = kmpStringResource(Res.string.kmplib_insp_all_ready),
        finish = kmpStringResource(Res.string.kmplib_insp_finish),
        finishTitle = kmpStringResource(Res.string.kmplib_insp_finish_title),
        finishMessage = kmpStringResource(Res.string.kmplib_insp_finish_message),
        finishFailed = kmpStringResource(Res.string.kmplib_insp_finish_failed),
)

@Composable
private fun rememberInspectionReportTexts(): InspectionReportTexts = InspectionReportTexts(
        generatingReport = kmpStringResource(Res.string.kmplib_insp_generating_report),
        pendingMedia = kmpStringResource(Res.string.kmplib_insp_pending_media),
        pageOf = kmpStringResource(Res.string.kmplib_insp_page_of),
        signedBy = kmpStringResource(Res.string.kmplib_insp_signed_by),
        awaitingSignatures = kmpStringResource(Res.string.kmplib_insp_awaiting_signatures),
        hashShort = kmpStringResource(Res.string.kmplib_insp_hash_short),
        sharePdf = kmpStringResource(Res.string.kmplib_insp_share_pdf),
        copyVerifyLink = kmpStringResource(Res.string.kmplib_insp_copy_verify_link),
        versions = kmpStringResource(Res.string.kmplib_insp_versions),
        newVersion = kmpStringResource(Res.string.kmplib_insp_new_version),
        reportError = kmpStringResource(Res.string.kmplib_insp_report_error),
        currentVersion = kmpStringResource(Res.string.kmplib_insp_current_version),
        hashLabel = kmpStringResource(Res.string.kmplib_insp_hash_label),
        copyHash = kmpStringResource(Res.string.kmplib_insp_copy_hash),
        qrDescription = kmpStringResource(Res.string.kmplib_insp_qr_description),
)

@Composable
private fun rememberInspectionQueueTexts(): InspectionQueueTexts = InspectionQueueTexts(
        queueProgress = kmpStringResource(Res.string.kmplib_insp_queue_progress),
        queueWaitingWifiOne = kmpStringResource(Res.string.kmplib_insp_queue_waiting_wifi_one),
        queueWaitingWifiOther = kmpStringResource(Res.string.kmplib_insp_queue_waiting_wifi_other),
        sendWithMobileData = kmpStringResource(Res.string.kmplib_insp_send_with_mobile_data),
        mobileDataTitle = kmpStringResource(Res.string.kmplib_insp_mobile_data_title),
        mobileDataMessage = kmpStringResource(Res.string.kmplib_insp_mobile_data_message),
        send = kmpStringResource(Res.string.kmplib_insp_send),
        queueOffline = kmpStringResource(Res.string.kmplib_insp_queue_offline),
        queueFailed = kmpStringResource(Res.string.kmplib_insp_queue_failed),
        queueDone = kmpStringResource(Res.string.kmplib_insp_queue_done),
        openQueue = kmpStringResource(Res.string.kmplib_insp_open_queue),
        queueTitle = kmpStringResource(Res.string.kmplib_insp_queue_title),
        queueEmpty = kmpStringResource(Res.string.kmplib_insp_queue_empty),
        confirmedSection = kmpStringResource(Res.string.kmplib_insp_confirmed_section),
        storageTitle = kmpStringResource(Res.string.kmplib_insp_storage_title),
        storageReclaimable = kmpStringResource(Res.string.kmplib_insp_storage_reclaimable),
        storageNothing = kmpStringResource(Res.string.kmplib_insp_storage_nothing),
)

@Composable
private fun rememberInspectionTemplateTexts(): InspectionTemplateTexts = InspectionTemplateTexts(
        templatesTitle = kmpStringResource(Res.string.kmplib_insp_templates_title),
        myTemplates = kmpStringResource(Res.string.kmplib_insp_my_templates),
        readyTemplates = kmpStringResource(Res.string.kmplib_insp_ready_templates),
        templateSummary = kmpStringResource(Res.string.kmplib_insp_template_summary),
        templateUsage = kmpStringResource(Res.string.kmplib_insp_template_usage),
        startFromReady = kmpStringResource(Res.string.kmplib_insp_start_from_ready),
        copyAndEdit = kmpStringResource(Res.string.kmplib_insp_copy_and_edit),
        newBlankTemplate = kmpStringResource(Res.string.kmplib_insp_new_blank_template),
        editorSubtitle = kmpStringResource(Res.string.kmplib_insp_editor_subtitle),
        preview = kmpStringResource(Res.string.kmplib_insp_preview),
        addItem = kmpStringResource(Res.string.kmplib_insp_add_item),
        changesNote = kmpStringResource(Res.string.kmplib_insp_changes_note),
        requiredTag = kmpStringResource(Res.string.kmplib_insp_required_tag),
        editItemTitle = kmpStringResource(Res.string.kmplib_insp_edit_item_title),
        newItemTitle = kmpStringResource(Res.string.kmplib_insp_new_item_title),
        itemNameLabel = kmpStringResource(Res.string.kmplib_insp_item_name_label),
        minPhotosLabel = kmpStringResource(Res.string.kmplib_insp_min_photos_label),
        defaultConditionLabel = kmpStringResource(Res.string.kmplib_insp_default_condition_label),
        itemQuestions = kmpStringResource(Res.string.kmplib_insp_item_questions),
        ownQuestion = kmpStringResource(Res.string.kmplib_insp_own_question),
        questionMaterial = kmpStringResource(Res.string.kmplib_insp_question_material),
        questionColor = kmpStringResource(Res.string.kmplib_insp_question_color),
        questionBrand = kmpStringResource(Res.string.kmplib_insp_question_brand),
        questionQuantity = kmpStringResource(Res.string.kmplib_insp_question_quantity),
        questionNameLabel = kmpStringResource(Res.string.kmplib_insp_question_name_label),
        questionTypeLabel = kmpStringResource(Res.string.kmplib_insp_question_type_label),
        typeText = kmpStringResource(Res.string.kmplib_insp_type_text),
        typeChoice = kmpStringResource(Res.string.kmplib_insp_type_choice),
        typeNumber = kmpStringResource(Res.string.kmplib_insp_type_number),
        typeYesNo = kmpStringResource(Res.string.kmplib_insp_type_yes_no),
        typeDate = kmpStringResource(Res.string.kmplib_insp_type_date),
        questionRequired = kmpStringResource(Res.string.kmplib_insp_question_required),
        choiceOptionsLabel = kmpStringResource(Res.string.kmplib_insp_choice_options_label),
        addQuestion = kmpStringResource(Res.string.kmplib_insp_add_question),
        nameRequired = kmpStringResource(Res.string.kmplib_insp_name_required),
        choiceOptionsRequired = kmpStringResource(Res.string.kmplib_insp_choice_options_required),
        templateConflict = kmpStringResource(Res.string.kmplib_insp_template_conflict),
        reload = kmpStringResource(Res.string.kmplib_insp_reload),
        goToFirstError = kmpStringResource(Res.string.kmplib_insp_go_to_first_error),
        leaveEditorTitle = kmpStringResource(Res.string.kmplib_insp_leave_editor_title),
        leaveEditorMessage = kmpStringResource(Res.string.kmplib_insp_leave_editor_message),
        leave = kmpStringResource(Res.string.kmplib_insp_leave),
        emptyRoomItems = kmpStringResource(Res.string.kmplib_insp_empty_room_items),
        readOnlyTemplate = kmpStringResource(Res.string.kmplib_insp_read_only_template),
)

@Composable
private fun rememberInspectionVoiceTexts(): InspectionVoiceTexts = InspectionVoiceTexts(
        voiceTitle = kmpStringResource(Res.string.kmplib_insp_voice_title),
        holdToTalk = kmpStringResource(Res.string.kmplib_insp_hold_to_talk),
        listening = kmpStringResource(Res.string.kmplib_insp_listening),
        structuring = kmpStringResource(Res.string.kmplib_insp_structuring),
        proposalsTitle = kmpStringResource(Res.string.kmplib_insp_proposals_title),
        notFound = kmpStringResource(Res.string.kmplib_insp_not_found),
        apply = kmpStringResource(Res.string.kmplib_insp_apply),
        voiceOffline = kmpStringResource(Res.string.kmplib_insp_voice_offline),
        voiceFailed = kmpStringResource(Res.string.kmplib_insp_voice_failed),
        micPermission = kmpStringResource(Res.string.kmplib_insp_mic_permission),
        allowMic = kmpStringResource(Res.string.kmplib_insp_allow_mic),
        transcriptLabel = kmpStringResource(Res.string.kmplib_insp_transcript_label),
)

@Composable
private fun rememberInspectionSignatureTexts(): InspectionSignatureTexts = InspectionSignatureTexts(
        signTitle = kmpStringResource(Res.string.kmplib_insp_sign_title),
        signatureLabel = kmpStringResource(Res.string.kmplib_insp_signature_label),
        undo = kmpStringResource(Res.string.kmplib_insp_undo),
        clear = kmpStringResource(Res.string.kmplib_insp_clear),
        codeSentSms = kmpStringResource(Res.string.kmplib_insp_code_sent_sms),
        codeSentEmail = kmpStringResource(Res.string.kmplib_insp_code_sent_email),
        codeSentWhatsapp = kmpStringResource(Res.string.kmplib_insp_code_sent_whatsapp),
        codeWrongOne = kmpStringResource(Res.string.kmplib_insp_code_wrong_one),
        codeWrongOther = kmpStringResource(Res.string.kmplib_insp_code_wrong_other),
        codeExpired = kmpStringResource(Res.string.kmplib_insp_code_expired),
        resendIn = kmpStringResource(Res.string.kmplib_insp_resend_in),
        resend = kmpStringResource(Res.string.kmplib_insp_resend),
        signOffline = kmpStringResource(Res.string.kmplib_insp_sign_offline),
        signAgreeRequired = kmpStringResource(Res.string.kmplib_insp_sign_agree_required),
        signStrokeRequired = kmpStringResource(Res.string.kmplib_insp_sign_stroke_required),
        codeLabel = kmpStringResource(Res.string.kmplib_insp_code_label),
        signedDone = kmpStringResource(Res.string.kmplib_insp_signed_done),
        signatureLinkTitle = kmpStringResource(Res.string.kmplib_insp_signature_link_title),
        creatingLink = kmpStringResource(Res.string.kmplib_insp_creating_link),
        linkError = kmpStringResource(Res.string.kmplib_insp_link_error),
        shareLink = kmpStringResource(Res.string.kmplib_insp_share_link),
        copyLink = kmpStringResource(Res.string.kmplib_insp_copy_link),
        linkShared = kmpStringResource(Res.string.kmplib_insp_link_shared),
)
