package com.emir.gym.assistant

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gercek AssistantController: diskte olmayan gorselle gonderim ENGELLENIR (ag/sayac/mesaj kaydi olmaz). */
class ControllerGuardDeviceTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val ctl = AssistantController(app, CoroutineScope(SupervisorJob() + Dispatchers.Default)) { com.emir.gym.data.NutritionGoals() }

    @Test fun sendPreparedWithMissingFileIsRejectedBeforeAnything() {
        val missing = PreparedImage("${ctl.images.dir.absolutePath}/yok_${System.nanoTime()}.jpg", "/x_thumb.jpg", "image/jpeg", 1, 1, 1)
        var result: SendResult? = null
        val before = ctl.usage.count(ProviderId.OPENAI_COMPAT) + ctl.usage.count(ProviderId.ANTHROPIC) + ctl.usage.count(ProviderId.GEMINI)
        ctl.sendPrepared("merhaba", listOf(missing)) { result = it }
        val r = result
        assertNotNull(r)
        assertEquals(false, r!!.stored)
        assertEquals(AssistantError.Kind.BAD_REQUEST, r.error?.kind)
        assertTrue(r.error!!.message.contains("Görsel bulunamadı"))
        assertEquals(false, ctl.sending.value)
        val after = ctl.usage.count(ProviderId.OPENAI_COMPAT) + ctl.usage.count(ProviderId.ANTHROPIC) + ctl.usage.count(ProviderId.GEMINI)
        assertEquals(before, after) // istek atilmadi / sayac artmadi
    }
}
