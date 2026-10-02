package br.com.codecacto.kmplib.platform.automation

// O iOS não tem um "modo de teste" do aparelho: o sinal vem só do dublê da loja ou do `activate`.
internal actual fun isDeviceInTestHarness(): Boolean = false
