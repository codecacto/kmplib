package br.com.codecacto.kmplib.ads.custom

import coil3.compose.AsyncImagePainter

/**
 * Em que pé está a imagem de um criativo. Existe para o id `*-carregado` de [AdsTestTags] ser posto
 * **só** quando a arte de fato foi pintada — o contêiner montado não prova nada ao teste.
 */
internal enum class AdCreativeLoad { Loading, Loaded, Failed }

/** Lê o estado do `AsyncImage` (Coil). `Empty` e `Loading` contam como "ainda não apareceu". */
internal fun AsyncImagePainter.State.toAdCreativeLoad(): AdCreativeLoad = when (this) {
    is AsyncImagePainter.State.Success -> AdCreativeLoad.Loaded
    is AsyncImagePainter.State.Error -> AdCreativeLoad.Failed
    else -> AdCreativeLoad.Loading
}

/**
 * O id do NÓ DA IMAGEM: [loadedTag] quando carregou, `null` (sem id) em qualquer outro estado.
 * Imagem que falhou não ganha id — "falhou" e "não apareceu" são a mesma coisa para o teste, e o
 * contêiner (que tem id sempre) é o que permite diagnosticar a diferença.
 */
internal fun creativeTestTag(load: AdCreativeLoad, loadedTag: String): String? =
    if (load == AdCreativeLoad.Loaded) loadedTag else null
