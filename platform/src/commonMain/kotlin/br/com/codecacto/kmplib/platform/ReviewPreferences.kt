package br.com.codecacto.kmplib.platform

expect class ReviewPreferences() {
    fun hasReviewed(): Boolean
    fun markReviewed()
    fun getCompletionCount(): Int
    fun incrementCompletionCount(): Int
}
