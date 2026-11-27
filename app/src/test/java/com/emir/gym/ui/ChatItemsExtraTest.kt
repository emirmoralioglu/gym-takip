package com.emir.gym.ui

import com.emir.gym.assistant.ActionPayload
import com.emir.gym.assistant.ActionRef
import com.emir.gym.assistant.Block
import com.emir.gym.assistant.ChatMessage
import com.emir.gym.assistant.Role
import com.emir.gym.assistant.ToolDefs
import com.emir.gym.assistant.ToolValidator
import com.emir.gym.data.PendingActionEntity
import com.emir.gym.data.PendingStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatItemsExtraTest {
    private val day = LocalDate.now().toEpochDay()
    private val water = ActionPayload.AddWater(day, 500)
    private val edited = ActionPayload.AddWater(day, 750)
    private val msgs = listOf(ChatMessage(Role.USER, listOf(Block.ToolResult("t", "x", false, 7L)), id = 3))

    private fun card(status: PendingStatus, ref: String?) = (ChatItems.build(
        msgs, listOf(PendingActionEntity(7, 1L, ToolDefs.ADD_WATER, ToolValidator.encode(water), status, ref)),
    ).single() as ChatItem.Card).card

    @Test fun editedDataIsReadOnlyWhenConfirmed() {
        val ref = ActionRef.withEdit("water:1", edited)
        assertEquals(750, (card(PendingStatus.CONFIRMED, ref).payload as ActionPayload.AddWater).ml)
        assertEquals(500, (card(PendingStatus.PENDING, ref).payload as ActionPayload.AddWater).ml)
        assertEquals(500, (card(PendingStatus.REJECTED, ref).payload as ActionPayload.AddWater).ml)
        assertFalse(card(PendingStatus.PENDING, ref).edited)
    }

    @Test fun retryRowHiddenWhenDailyLimitFull() {
        val u = ChatMessage(Role.USER, listOf(Block.Text("a")), id = 1)
        assertTrue(ChatItems.withRetry(emptyList(), listOf(u), false, hideRetry = true).isEmpty())
        assertEquals(1, ChatItems.withRetry(emptyList(), listOf(u), false, hideRetry = false).size)
    }
}
