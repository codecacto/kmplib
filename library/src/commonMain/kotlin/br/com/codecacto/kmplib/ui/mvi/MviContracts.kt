package br.com.codecacto.kmplib.ui.mvi

/**
 * Marker interface para estados do padrão MVI.
 * Use em conjunto com [BaseViewModel].
 */
interface UiState

/**
 * Marker interface para ações do padrão MVI.
 * Use em conjunto com [BaseViewModel].
 */
interface UiAction

/**
 * Marker interface para efeitos (eventos one-shot) do padrão MVI.
 * Use em conjunto com [BaseViewModel].
 */
interface UiEffect
