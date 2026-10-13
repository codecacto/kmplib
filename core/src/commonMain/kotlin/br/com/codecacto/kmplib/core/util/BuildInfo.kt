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

    /**
     * A versão do app **como a loja mostra** — `versionName` do Android (`PackageInfo`) e
     * `CFBundleShortVersionString` do iOS (ex.: `"1.4.2"`). É o que a tela **Sobre** exibe
     * (`AboutScreen`, 2.298.0) sem o app repassar nada, e sai certa em todo flavor.
     * `null` quando não dá para ler (lib não inicializada no Android, chave ausente).
     */
    val appVersionName: String?

    /**
     * O número do build — `versionCode` (`longVersionCode`) do Android e `CFBundleVersion` do iOS.
     * Serve para distinguir dois builds da mesma versão (TestFlight, teste interno). `null` quando
     * não dá para ler.
     */
    val appBuildNumber: String?
}
