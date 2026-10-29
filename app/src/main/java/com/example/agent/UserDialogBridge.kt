package com.example.agent

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

object UserDialogBridge {
    private const val TAG = "SAIF_AGENT"
    private const val TIMEOUT_MS = 20_000L // 20 seconds timeout for user speech

    private val isAwaiting = AtomicBoolean(false)
    private var pendingQuestion: String = ""
    private var pendingOptions: List<String>? = null
    private var responseDeferred: CompletableDeferred<String>? = null

    fun isAwaitingAnswer(): Boolean = isAwaiting.get()

    suspend fun askUser(question: String, options: List<String>? = null, listener: AgentListener? = null): String {
        Log.i(TAG, "Asking user: $question (options: $options)")
        isAwaiting.set(true)
        pendingQuestion = question
        pendingOptions = options

        val deferred = CompletableDeferred<String>()
        responseDeferred = deferred

        // Speak the question via listener
        listener?.onAskUser(question, options)

        val answer = withTimeoutOrNull(TIMEOUT_MS) {
            deferred.await()
        }

        isAwaiting.set(false)
        responseDeferred = null
        pendingQuestion = ""
        pendingOptions = null

        return answer ?: "nahi" // Default to "nahi" / no on timeout
    }

    fun provideAnswer(answerText: String): Boolean {
        if (!isAwaiting.get()) return false
        val clean = answerText.trim()
        Log.i(TAG, "User answered: $clean")
        val deferred = responseDeferred
        if (deferred != null && deferred.isActive) {
            deferred.complete(clean)
            isAwaiting.set(false)
            return true
        }
        return false
    }

    fun cancel() {
        if (isAwaiting.get()) {
            responseDeferred?.cancel()
            isAwaiting.set(false)
            responseDeferred = null
        }
    }
}
