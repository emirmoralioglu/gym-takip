package com.emir.gym.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Sohbet mesaji. contentJson saglayicidan bagimsiz bicimdedir (assistant/ChatCodec); Anthropic
 * assistant mesajlarinda thinking dahil ham icerik de ayni JSON'un icinde saklanir ve sonraki turda aynen geri gonderilir.
 */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    /** USER | ASSISTANT */
    val role: String,
    val contentJson: String,
    /** Mesajdaki ilk gorselin uygulama ici yolu (varsa). */
    val imagePath: String? = null,
    val provider: String,
    val model: String,
)

enum class PendingStatus { PENDING, CONFIRMED, REJECTED }

/** Asistanin onerdigi, kullanici onayi bekleyen kayit. Onaylaninca mevcut tablolara yazilir. */
@Entity(tableName = "pending_actions")
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    /** ADD_MEAL | ADD_WATER | LOG_WEIGHT | LOG_ACTIVITY */
    val type: String,
    val payloadJson: String,
    val status: PendingStatus = PendingStatus.PENDING,
    /** Onaylaninca yazilan kaydin referansi (ornek "meal:12"). */
    val resultRef: String? = null,
)
