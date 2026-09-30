package br.com.codecacto.kmplib.core.util

expect object BuildInfo {
    val isDebug: Boolean

    /**
     * O nome do app **como o sistema mostra** — o rótulo do launcher no Android
     * (`android:label`) e o `CFBundleDisplayName` no iOS (cai para `CFBundleName`).
     *
     * Serve para a lib dizer DE QUAL app algo veio (mensagem de WhatsApp, assunto de e-mail) sem o
     * app ter de repassar o próprio nome. Cada flavor tem o seu rótulo, então sai certo também nos
     * apps de N flavors. `null` quando não dá para ler (lib não inicializada no Android, rótulo vazio).
     */
    val appName: String?
}
