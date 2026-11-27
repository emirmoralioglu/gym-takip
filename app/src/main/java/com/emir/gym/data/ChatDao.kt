package com.emir.gym.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY id")
    fun observeMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY id")
    suspend fun allMessages(): List<ChatMessageEntity>

    @Insert
    suspend fun insertMessage(m: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearMessages()

    @Insert
    suspend fun insertAction(a: PendingActionEntity): Long

    @Query("SELECT * FROM pending_actions WHERE id = :id")
    suspend fun action(id: Long): PendingActionEntity?

    @Query("SELECT * FROM pending_actions ORDER BY id")
    fun observeActions(): Flow<List<PendingActionEntity>>

    /** PENDING -> CONFIRMED tek adimda; etkilenen satir sayisi 1 ise bu cagri kazandi (cift dokunus korumasi). */
    @Query("UPDATE pending_actions SET status = 'CONFIRMED' WHERE id = :id AND status = 'PENDING'")
    suspend fun claim(id: Long): Int

    @Query("UPDATE pending_actions SET resultRef = :ref WHERE id = :id")
    suspend fun setResultRef(id: Long, ref: String)

    @Query("UPDATE pending_actions SET status = 'REJECTED' WHERE id = :id AND status = 'PENDING'")
    suspend fun reject(id: Long): Int

    @Query("DELETE FROM pending_actions")
    suspend fun clearActions()
}
