package br.com.codecacto.kmplib.video.feed

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedVideoSoundStateTest {

    @Test
    fun nasceMudoPorPadrao() {
        assertTrue(FeedVideoSoundState().isMuted)
        assertTrue(FeedVideoSoundState.Shared.isMuted)
    }

    @Test
    fun toggleAlternaNosDoisSentidos() {
        val som = FeedVideoSoundState()
        som.toggle()
        assertFalse(som.isMuted)
        som.toggle()
        assertTrue(som.isMuted)
    }

    @Test
    fun setMutedEhIdempotente() {
        val som = FeedVideoSoundState(initialMuted = false)
        som.setMuted(false)
        assertFalse(som.isMuted)
        som.setMuted(true)
        som.setMuted(true)
        assertTrue(som.isMuted)
    }

    @Test
    fun doisControllersComOMesmoSomConcordam() {
        val som = FeedVideoSoundState()
        val feed = FeedVideoController(FeedVideoConfig(), som) { error("sem player neste teste") }
        val detalhe = FeedVideoController(FeedVideoConfig(), som) { error("sem player neste teste") }
        feed.toggleMuted()
        assertFalse(detalhe.sound.isMuted)
    }
}
