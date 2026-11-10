package br.com.codecacto.kmplib.torch

/** Desktop: computador não tem lanterna. */
actual fun createTorchController(): TorchController = UnavailableTorchController(TorchError.NoTorch)
