package com.example.data.local

import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatDao: ChatDao) {

    val allSessions: Flow<List<ChatSessionEntity>> = chatDao.getAllSessions()
    val bookmarkedMessages: Flow<List<ChatMessageEntity>> = chatDao.getBookmarkedMessages()

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun getRecentMessages(sessionId: String, limit: Int = 20): List<ChatMessageEntity> {
        return chatDao.getRecentMessages(sessionId, limit)
    }

    suspend fun getSessionById(sessionId: String): ChatSessionEntity? {
        return chatDao.getSessionById(sessionId)
    }

    suspend fun createSession(title: String, mode: String = "general"): ChatSessionEntity {
        val session = ChatSessionEntity(
            title = title,
            mode = mode
        )
        chatDao.insertSession(session)
        return session
    }

    suspend fun updateSessionTitle(sessionId: String, newTitle: String) {
        chatDao.updateSessionTitle(sessionId, newTitle)
    }

    suspend fun updateSessionTimestamp(sessionId: String) {
        chatDao.updateSessionTimestamp(sessionId)
    }

    suspend fun deleteSession(sessionId: String) {
        chatDao.deleteSession(sessionId)
    }

    suspend fun deleteAllSessions() {
        chatDao.deleteAllSessions()
    }

    suspend fun insertMessage(message: ChatMessageEntity) {
        chatDao.insertMessage(message)
        chatDao.updateSessionTimestamp(message.sessionId)
    }

    suspend fun updateMessageContent(messageId: String, content: String) {
        chatDao.updateMessageContent(messageId, content)
    }

    suspend fun toggleBookmark(messageId: String, currentBookmarked: Boolean) {
        chatDao.setMessageBookmarked(messageId, !currentBookmarked)
    }

    suspend fun deleteMessage(messageId: String) {
        chatDao.deleteMessage(messageId)
    }

    suspend fun clearSessionMessages(sessionId: String) {
        chatDao.deleteMessagesForSession(sessionId)
    }
}
