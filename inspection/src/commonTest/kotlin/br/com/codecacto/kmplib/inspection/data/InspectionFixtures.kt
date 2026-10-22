package br.com.codecacto.kmplib.inspection.data

/**
 * Respostas REAIS do `backlib-inspection` 0.203.0 (formato do `routeJson`: sem `null` explícito), montadas a
 * partir de `InspectionDtos.kt` do servidor. Inclui de propósito um campo desconhecido (`novoCampo`) e um
 * valor de enum desconhecido (`status: "ARCHIVED"` numa mídia) — o leitor tem de seguir.
 */
object InspectionFixtures {
    const val INSPECTION_ID = "0190f3a1-0000-7000-8000-000000000001"
    const val GROUP_SALA = "0190f3a1-0000-7000-8000-0000000000a1"
    const val GROUP_COZINHA = "0190f3a1-0000-7000-8000-0000000000a2"
    const val ITEM_PISO = "0190f3a1-0000-7000-8000-0000000000b1"
    const val ITEM_PAREDE = "0190f3a1-0000-7000-8000-0000000000b2"
    const val ITEM_HIDROMETRO = "0190f3a1-0000-7000-8000-0000000000b3"
    const val ITEM_PIA = "0190f3a1-0000-7000-8000-0000000000b4"
    const val MEDIA_PISO_1 = "0190f3a1-0000-7000-8000-0000000000c1"
    const val SHA_PISO_1 = "1111111111111111111111111111111111111111111111111111111111111111"
    const val SHA_PIA_GALERIA = "2222222222222222222222222222222222222222222222222222222222222222"

    /** Vistoria de ENTRADA em rascunho: 2 cômodos, 4 itens, tarefa de leitura de medidor no hidrômetro. */
    val ENTRY_DRAFT: String = """
    {
      "id": "$INSPECTION_ID",
      "clientId": "0190f3a1-0000-7000-8000-00000000ffff",
      "objectId": "0190f3a1-0000-7000-8000-0000000000aa",
      "subjectType": "PROPERTY",
      "object": {"title": "Apto 302", "address": "Rua das Flores, 10"},
      "templateId": "0190f3a1-0000-7000-8000-0000000000ab",
      "templateName": "Apartamento padrão",
      "context": "ENTRY",
      "status": "DRAFT",
      "performer": "OWNER",
      "resultAxis": "CONFORMITY",
      "headerAnswers": {},
      "headerMedia": [],
      "periodoCota": "2026-10",
      "createdAt": "2026-10-22T12:00:00Z",
      "updatedAt": "2026-10-22T12:05:00Z",
      "novoCampo": {"qualquer": true},
      "groups": [
        {
          "id": "$GROUP_SALA", "name": "Sala", "position": 0, "origin": "TEMPLATE", "duplicable": false,
          "items": [
            {
              "id": "$ITEM_PISO", "groupId": "$GROUP_SALA", "name": "Piso", "position": 0, "required": true, "minPhotos": 2,
              "answers": {}, "state": "GOOD", "updatedAt": "2026-10-22T12:01:00Z",
              "media": [
                {"id": "$MEDIA_PISO_1", "inspectionId": "$INSPECTION_ID", "itemId": "$ITEM_PISO", "kind": "PHOTO", "source": "CAMERA",
                 "evidence": true, "contentType": "image/jpeg", "sizeBytes": 120000, "sha256": "$SHA_PISO_1", "status": "CONFIRMED",
                 "capturedAtDevice": "2026-10-22T12:01:00Z", "receivedAtServer": "2026-10-22T12:01:05Z", "lat": -15.6, "lng": -56.1,
                 "hasThumb": true, "uploadedBy": "OWNER", "createdAt": "2026-10-22T12:01:00Z"}
              ]
            },
            {
              "id": "$ITEM_PAREDE", "groupId": "$GROUP_SALA", "name": "Paredes", "position": 1, "required": false, "minPhotos": 0,
              "answers": {}, "state": "FAIR", "observation": "Mancha perto da janela", "functioning": "NOT_TESTED",
              "updatedAt": "2026-10-22T12:02:00Z", "media": []
            }
          ],
          "media": []
        },
        {
          "id": "$GROUP_COZINHA", "name": "Cozinha", "position": 1, "origin": "TEMPLATE", "duplicable": true,
          "items": [
            {
              "id": "$ITEM_HIDROMETRO", "groupId": "$GROUP_COZINHA", "name": "Hidrômetro", "position": 0, "required": false, "minPhotos": 0,
              "mediaTasks": [{"id": "leitura", "kind": "PHOTO", "label": "Foto do visor com a leitura", "required": true, "reading": "M3"}],
              "answers": {}, "state": "GOOD", "updatedAt": "2026-10-22T12:03:00Z", "media": []
            },
            {
              "id": "$ITEM_PIA", "groupId": "$GROUP_COZINHA", "name": "Pia", "position": 1, "required": false, "minPhotos": 0,
              "answers": {}, "state": "NA", "updatedAt": "2026-10-22T12:03:00Z",
              "media": [
                {"id": "0190f3a1-0000-7000-8000-0000000000c2", "inspectionId": "$INSPECTION_ID", "itemId": "$ITEM_PIA", "kind": "PHOTO",
                 "source": "GALLERY", "evidence": false, "contentType": "image/jpeg", "sizeBytes": 90000, "sha256": "$SHA_PIA_GALERIA",
                 "status": "ARCHIVED", "hasThumb": false, "uploadedBy": "OWNER", "createdAt": "2026-10-22T12:03:00Z"}
              ]
            }
          ],
          "media": []
        }
      ],
      "signers": [],
      "reports": [],
      "mediaTotal": 2,
      "mediaConfirmed": 1
    }
    """.trimIndent()

    const val EXIT_ID = "0190f3a1-0000-7000-8000-000000000002"
    const val EXIT_ITEM_PISO = "0190f3a1-0000-7000-8000-0000000000d1"

    /** SAÍDA assinada (versão 2 vigente, v1 substituída), com o item da entrada embutido. */
    val EXIT_SIGNED: String = """
    {
      "id": "$EXIT_ID", "clientId": "0190f3a1-0000-7000-8000-00000000fffe", "objectId": "0190f3a1-0000-7000-8000-0000000000aa",
      "subjectType": "PROPERTY", "object": {"title": "Apto 302"}, "templateName": "Apartamento padrão",
      "context": "EXIT", "entryInspectionId": "$INSPECTION_ID", "status": "SIGNED", "performer": "OWNER", "resultAxis": "CONFORMITY",
      "headerAnswers": {}, "headerMedia": [], "periodoCota": "2026-10",
      "createdAt": "2026-10-22T13:00:00Z", "updatedAt": "2026-10-22T14:00:00Z",
      "groups": [
        {"id": "g-exit", "name": "Sala", "position": 0, "origin": "ENTRY", "duplicable": false, "entryGroupId": "$GROUP_SALA",
         "items": [
           {"id": "$EXIT_ITEM_PISO", "groupId": "g-exit", "name": "Piso", "position": 0, "required": true, "minPhotos": 0,
            "answers": {}, "state": "POOR", "result": "NON_CONFORM", "functioning": "YES", "observation": "Riscado",
            "entryItemId": "$ITEM_PISO", "updatedAt": "2026-10-22T13:10:00Z", "media": [],
            "entry": {"id": "$ITEM_PISO", "state": "GOOD", "functioning": "YES", "answers": {},
              "media": [{"id": "$MEDIA_PISO_1", "inspectionId": "$INSPECTION_ID", "itemId": "$ITEM_PISO", "kind": "PHOTO", "source": "CAMERA",
                "evidence": true, "contentType": "image/jpeg", "sizeBytes": 120000, "sha256": "$SHA_PISO_1", "status": "CONFIRMED",
                "hasThumb": true, "uploadedBy": "OWNER", "createdAt": "2026-10-22T12:01:00Z"}]}}
         ], "media": []}
      ],
      "signers": [{"id": "s1", "role": "LOCATARIO", "name": "Mariana Costa", "email": "mariana@exemplo.com.br", "createdAt": "2026-10-22T13:20:00Z"}],
      "reports": [
        {"id": "r1", "version": 1, "kind": "CONTENT", "pdfSha256": "aaaa", "pdfSizeBytes": 1000, "contentSha256": "bbbb",
         "verifyCode": "ABCD-EFGH-JKMN", "verifyUrl": "https://minhavistoria.codecacto.com.br/verificar/ABCD-EFGH-JKMN",
         "locked": true, "supersededAt": "2026-10-22T13:50:00Z", "generatedAt": "2026-10-22T13:30:00Z", "signatures": []},
        {"id": "r2", "version": 2, "kind": "CONTENT", "pdfSha256": "cccc", "pdfSizeBytes": 1100, "contentSha256": "dddd",
         "verifyCode": "PQRS-TVWX-YZ12", "verifyUrl": "https://minhavistoria.codecacto.com.br/verificar/PQRS-TVWX-YZ12",
         "locked": true, "generatedAt": "2026-10-22T13:55:00Z", "bilateralBasis": "SIGNATURE", "tenantSigned": true,
         "signatures": [
           {"id": "sig1", "signerId": "s1", "role": "LOCATARIO", "name": "Mariana Costa", "method": "PRESENCIAL", "otpChannel": "SMS",
            "signedAt": "2026-10-22T13:58:00Z", "signedHash": "cccc", "lat": -15.6, "lng": -56.1, "hasTrace": true}
         ]}
      ],
      "mediaTotal": 0, "mediaConfirmed": 0
    }
    """.trimIndent()

    /** Corpo do 422 `INSPECTION_INCOMPLETE` como o `configureErrorHandling` da backlib escreve. */
    val INCOMPLETE_BODY: String = """
    {"message": "Faltam itens obrigatórios ou fotos", "code": "INSPECTION_INCOMPLETE", "timestamp": "2026-10-22T12:10:00Z",
     "details": {"issues": [
       {"itemId": "$ITEM_PISO", "groupName": "Sala", "itemName": "Piso", "reason": "MIN_PHOTOS", "required": 2, "have": 1},
       {"itemId": "$ITEM_HIDROMETRO", "groupName": "Cozinha", "itemName": "Hidrômetro", "reason": "MEDIA_TASK", "taskId": "leitura"},
       {"headerField": "medidor_luz", "reason": "HEADER_REQUIRED"},
       {"semReason": true}
     ]}}
    """.trimIndent()

    fun mediaIntentSingle(mediaId: String, sha: String, size: Long) = """
    {"media": {"id": "$mediaId", "inspectionId": "$INSPECTION_ID", "itemId": "$ITEM_PISO", "kind": "PHOTO", "source": "CAMERA",
      "evidence": true, "contentType": "image/jpeg", "sizeBytes": $size, "sha256": "$sha", "status": "QUEUED", "hasThumb": false,
      "uploadedBy": "OWNER", "createdAt": "2026-10-22T12:00:00Z"},
     "upload": {"mode": "SINGLE", "single": {"method": "PUT", "url": "https://r2.example.com/b/k?X-Amz-Signature=segredo",
       "headers": {"content-type": "image/jpeg", "if-none-match": "*", "x-amz-checksum-sha256": "abc="},
       "expiresAt": "2099-01-01T00:00:00Z"}}}
    """.trimIndent()

    fun mediaConfirmed(mediaId: String, sha: String, size: Long) = """
    {"id": "$mediaId", "inspectionId": "$INSPECTION_ID", "itemId": "$ITEM_PISO", "kind": "PHOTO", "source": "CAMERA",
      "evidence": true, "contentType": "image/jpeg", "sizeBytes": $size, "sha256": "$sha", "status": "CONFIRMED", "hasThumb": true,
      "receivedAtServer": "2026-10-22T12:00:10Z", "uploadedBy": "OWNER", "createdAt": "2026-10-22T12:00:00Z"}
    """.trimIndent()

    fun entryDraft(): InspectionDto = InspectionJson.decodeFromString(InspectionDto.serializer(), ENTRY_DRAFT)
    fun exitSigned(): InspectionDto = InspectionJson.decodeFromString(InspectionDto.serializer(), EXIT_SIGNED)
}
