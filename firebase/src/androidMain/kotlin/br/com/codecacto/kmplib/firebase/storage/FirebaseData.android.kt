package br.com.codecacto.kmplib.firebase.storage

import dev.gitlive.firebase.storage.Data

internal actual fun ByteArray.toFirebaseData(): Data = Data(this)
