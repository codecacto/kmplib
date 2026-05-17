package br.com.codecacto.kmplib.firebase.storage

import dev.gitlive.firebase.storage.Data

internal expect fun ByteArray.toFirebaseData(): Data
