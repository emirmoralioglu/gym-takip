package com.emir.gym.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.emir.gym.data.AppDatabase
import com.emir.gym.data.NutritionGoals
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeystoreStoreTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = ctx.getSharedPreferences("assistant_keys_test", Context.MODE_PRIVATE)
    private val store = ApiKeyStore(prefs, KeystoreCipher("assistant_test_alias"))

    @Before fun clean() { prefs.edit().clear().commit() }
    @After fun after() { prefs.edit().clear().commit() }

    @Test fun saveReadDeleteAndNoPlaintextInPrefs() {
        val key = "sk-test-key-123-abcdefgh"
        assertFalse(store.has(ProviderId.ANTHROPIC))
        assertTrue(store.set(ProviderId.ANTHROPIC, key))
        assertEquals(key, store.get(ProviderId.ANTHROPIC))
        assertTrue(store.has(ProviderId.ANTHROPIC))
        assertEquals("sk-…efgh", store.masked(ProviderId.ANTHROPIC))
        // SharedPreferences'ta duz metin yok (ne tam anahtar ne de govdesi).
        val all = prefs.all.values.joinToString("|")
        assertFalse(all, all.contains(key))
        assertFalse(all.contains("abcdefgh"))
        assertTrue(all.startsWith("v1:"))
        // Base64 cozulmus haliyle de duz metin yok (yalniz Base64'e kodlama sifreleme degildir).
        val decoded = String(android.util.Base64.decode(all.removePrefix("v1:"), android.util.Base64.NO_WRAP), Charsets.ISO_8859_1)
        assertFalse(decoded.contains("abcdefgh"))
        assertFalse(decoded.contains("sk-test"))
        // Dosyada da yok
        val xml = File(ctx.applicationInfo.dataDir, "shared_prefs/assistant_keys_test.xml")
        if (xml.exists()) assertFalse(xml.readText().contains("abcdefgh"))
        store.clear(ProviderId.ANTHROPIC)
        assertNull(store.get(ProviderId.ANTHROPIC))
        assertFalse(store.has(ProviderId.ANTHROPIC))
    }

    @Test fun keysArePerProviderAndEncryptionIsRandomized() {
        assertTrue(store.set(ProviderId.GEMINI, "gemini-key-12345678"))
        assertTrue(store.set(ProviderId.OPENAI_COMPAT, "gemini-key-12345678"))
        assertNull(store.get(ProviderId.ANTHROPIC))
        val a = prefs.getString("key_GEMINI", null); val b = prefs.getString("key_OPENAI_COMPAT", null)
        assertTrue("ayni anahtar farkli IV ile farkli sifreli metin uretir", a != b)
    }

    @Test fun tamperedCiphertextIsRejectedNotCrashing() {
        assertTrue(store.set(ProviderId.GEMINI, "gemini-key-12345678"))
        val tok = prefs.getString("key_GEMINI", null)!!
        prefs.edit().putString("key_GEMINI", tok.dropLast(4) + "AAAA").commit()
        assertNull(store.get(ProviderId.GEMINI))
        assertNull(KeystoreCipher("assistant_test_alias").decrypt("garbage"))
    }

    @Test fun invalidKeysRefused() {
        assertFalse(store.set(ProviderId.GEMINI, "short"))
        assertFalse(store.set(ProviderId.GEMINI, "has space in the key 123"))
        assertFalse(store.has(ProviderId.GEMINI))
    }

    @Test fun undecryptableRecordCountsAsNoKey() {
        // Baska cihazdan aktarilmis / bozuk sifreli kayit: has() false, get() null (tutarli).
        prefs.edit().putString("key_GEMINI", "v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA").apply()
        assertFalse(store.has(ProviderId.GEMINI))
        assertNull(store.get(ProviderId.GEMINI))
    }

    @Test fun maskHelper() {
        assertEquals("sk-…abcd", ApiKeyStore.mask("sk-test-xxxxxxxxxxxxxxabcd"))
        assertEquals("…", ApiKeyStore.mask("short"))
        assertEquals("…0123", ApiKeyStore.mask("1234567890123"))
    }
}

class RoomAssistantTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: AssistantRepository

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        repo = AssistantRepository(db, { NutritionGoals() })
    }

    @After fun close() { db.close() }

    private suspend fun propose(p: ActionPayload) = repo.actions.insert(p, 1L)
    private val day = 20_000L

    @Test fun confirmWritesOnceThenIsIdempotent() = runBlocking {
        val id = propose(ActionPayload.AddMeal(day, com.emir.gym.data.MealType.OGLE, "Pilav", 30.0, 600, null, null))
        val first = repo.confirm(id)
        val second = repo.confirm(id)
        assertTrue(first.toString(), first is ConfirmResult.Confirmed)
        assertTrue(second is ConfirmResult.AlreadyConfirmed)
        assertEquals(1, db.trackingDao().observeMeals().first().size)
        assertEquals("meal:1", db.chatDao().action(id)!!.resultRef)
    }

    @Test fun concurrentDoubleTapWritesExactlyOneRow() = runBlocking {
        val id = propose(ActionPayload.AddWater(day, 250))
        val results = (1..12).map { async(Dispatchers.IO) { repo.confirm(id) } }.awaitAll()
        assertEquals(1, results.count { it is ConfirmResult.Confirmed })
        assertEquals(1, db.trackingDao().observeWater().first().size)
    }

    @Test fun rejectedIsNeverWritten() = runBlocking {
        val id = propose(ActionPayload.LogWeight(day, 80.0))
        assertTrue(repo.reject(id))
        assertTrue(repo.confirm(id) is ConfirmResult.AlreadyRejected)
        assertTrue(db.trackingDao().observeBodyWeights().first().isEmpty())
        assertTrue(repo.confirm(12345) is ConfirmResult.NotFound)
    }

    @Test fun allFourTypesWriteToExistingTables() = runBlocking {
        repo.confirm(propose(ActionPayload.LogWeight(day, 80.0)))
        repo.confirm(propose(ActionPayload.LogWeight(day, 79.0))) // ayni gun tek tarti: degistirir
        repo.confirm(propose(ActionPayload.LogActivity(day, com.emir.gym.data.ActivityType.YUZME, 40, 1000, "n")))
        assertEquals(listOf(79.0), db.trackingDao().observeBodyWeights().first().map { it.kg })
        val a = db.trackingDao().observeActivities().first().single()
        assertEquals(40, a.minutes); assertEquals(1000, a.distanceM)
    }

    @Test fun failedWriteRollsBackStatus() = runBlocking {
        val id = propose(ActionPayload.AddWater(day, 250))
        val r = try {
            repo.actions.confirm(id) { throw IllegalStateException("disk dolu") }
        } catch (e: IllegalStateException) { null }
        assertNull(r)
        assertEquals(com.emir.gym.data.PendingStatus.PENDING, db.chatDao().action(id)!!.status)
        assertTrue(repo.confirm(id) is ConfirmResult.Confirmed)
    }

    @Test fun chatPersistsRawAnthropicAndClearRemovesEverything() = runBlocking {
        val raw = """[{"type":"thinking","thinking":"","signature":"SIGZ=="},{"type":"text","text":"a"}]"""
        repo.chat.append(ChatMessage(Role.USER, listOf(Block.Text("selam"), Block.Image("/x/y.jpg", "image/jpeg")), provider = "ANTHROPIC", model = "claude-opus-5-5"))
        repo.chat.append(ChatMessage(Role.ASSISTANT, listOf(Block.Text("a")), raw, "ANTHROPIC", "claude-opus-5-5"))
        val all = repo.chat.all()
        assertEquals(2, all.size)
        assertTrue(all[1].rawAnthropic!!.contains("SIGZ=="))
        assertEquals("/x/y.jpg", db.chatDao().allMessages()[0].imagePath)
        propose(ActionPayload.AddWater(day, 100))
        val dir = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "chat_test").also { it.mkdirs(); File(it, "a.jpg").writeText("x") }
        repo.clearChat(dir)
        assertTrue(repo.chat.all().isEmpty())
        assertTrue(db.chatDao().observeActions().first().isEmpty())
        assertFalse(dir.exists())
    }

    @Test fun readDataFeedsContext() = runBlocking {
        repo.confirm(propose(ActionPayload.AddWater(java.time.LocalDate.now().toEpochDay(), 300)))
        val s = AssistantContext.build(java.time.LocalDate.now(), repo.readData())
        assertTrue(s, s.contains("su 300 /"))
    }
}

class ImagePrepDeviceTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val prep = ImagePreparer(ctx)

    @After fun cleanup() { prep.deleteAll() }

    @Test fun deleteRemovesImageAndPreviewOnlyInsideOwnDir() {
        val p = prep.prepare { ByteArrayInputStream(jpeg(300, 200)) }
        assertTrue(File(p.path).exists() && File(p.previewPath).exists())
        prep.delete(p.path)
        assertFalse(File(p.path).exists()); assertFalse(File(p.previewPath).exists())
        val outside = File(ctx.cacheDir, "keep.txt").also { it.writeText("x") }
        prep.delete(outside.absolutePath)
        assertTrue(outside.exists()); outside.delete()
    }

    private fun jpeg(w: Int, h: Int, orientation: Int? = null): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 80, 40)) }
        val f = File(ctx.cacheDir, "src_${System.nanoTime()}.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        if (orientation != null) ExifInterface(f.absolutePath).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
        return f.readBytes().also { f.delete() }
    }

    @Test fun resizesAndAppliesExifRotation() {
        val src = jpeg(3200, 2000, ExifInterface.ORIENTATION_ROTATE_90)
        val p = prep.prepare { ByteArrayInputStream(src) }
        assertEquals("image/jpeg", p.mime)
        assertTrue("uzun kenar <= 1568: ${p.width}x${p.height}", maxOf(p.width, p.height) <= ImageMath.MAX_LONG_SIDE)
        assertTrue("EXIF 90: dikey olmali ${p.width}x${p.height}", p.height > p.width)
        val f = File(p.path)
        assertTrue(f.exists())
        assertTrue("filesDir/chat altinda", f.canonicalPath.startsWith(File(ctx.filesDir, "chat").canonicalPath))
        assertTrue(File(p.previewPath).exists())
        assertTrue(ImageMath.fitsBase64Limit(f.length().toInt()))
        val b = BitmapFactoryBounds.of(f)
        assertEquals(p.width, b.first); assertEquals(p.height, b.second)
        assertTrue(File(p.previewPath).length() < f.length())
        assertNotNull(prep.base64(p.path))
        assertNull("dizin disindaki dosya okunmaz", prep.base64(File(ctx.cacheDir, "x").absolutePath))
    }

    @Test fun smallImageIsNotUpscaled() {
        val p = prep.prepare { ByteArrayInputStream(jpeg(400, 300)) }
        assertEquals(400, p.width); assertEquals(300, p.height)
    }

    @Test fun pngWithAlphaBecomesJpeg() {
        val bmp = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }
        val out = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val p = prep.prepare { ByteArrayInputStream(out) }
        assertEquals("image/jpeg", p.mime)
        assertEquals(0xFF.toByte(), File(p.path).readBytes()[0])
    }

    @Test fun unsupportedTypesRejected() {
        val e = runCatching { prep.prepare { ByteArrayInputStream("GIF89a....................".toByteArray()) } }.exceptionOrNull()
        assertTrue(e is ImageException)
        assertTrue(runCatching { prep.prepare { ByteArrayInputStream(ByteArray(0)) } }.exceptionOrNull() is ImageException)
        assertTrue(runCatching { prep.prepare { null } }.exceptionOrNull() is ImageException)
    }
}

private object BitmapFactoryBounds {
    fun of(f: File): Pair<Int, Int> {
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(f.absolutePath, o)
        return o.outWidth to o.outHeight
    }
}

class SettingsDeviceTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clean() { ctx.getSharedPreferences("assistant_settings", Context.MODE_PRIVATE).edit().clear().commit() }
    @After fun after() { clean() }

    @Test fun defaultsAndValidation() {
        val s = AssistantSettings(ctx)
        val c = s.config.value
        assertEquals(ProviderId.ANTHROPIC, c.provider)
        assertEquals("claude-opus-5-5", c.model)
        assertEquals("medium", c.effort)
        assertFalse(c.autoAdd)
        assertFalse(c.supportsImages)
        assertTrue(c.supportsTools)
        assertNull(c.dailyLimit)
        assertEquals("gemini-2.5-flash", s.modelOf(ProviderId.GEMINI))
        assertEquals("", s.modelOf(ProviderId.OPENAI_COMPAT))

        assertFalse(s.setBaseUrl("http://openrouter.ai/api/v1"))
        assertTrue(s.setBaseUrl("https://openrouter.ai/api/v1/"))
        s.setProvider(ProviderId.OPENAI_COMPAT)
        assertEquals("https://openrouter.ai/api/v1", s.config.value.baseUrl)
        assertTrue(s.setModel(ProviderId.OPENAI_COMPAT, "deepseek/deepseek-chat"))
        assertFalse(s.setModel(ProviderId.OPENAI_COMPAT, "bad model"))
        assertTrue(s.setDailyLimit(ProviderId.OPENAI_COMPAT, 100))
        assertEquals(100, s.config.value.dailyLimit)
        assertFalse(s.setDailyLimit(ProviderId.OPENAI_COMPAT, 0))
        assertTrue(s.setDailyLimit(ProviderId.OPENAI_COMPAT, null))
        assertNull(s.config.value.dailyLimit)
        assertFalse(s.setEffort("ultra"))
        assertTrue(s.setEffort("high"))
        // kalicilik
        assertEquals("high", AssistantSettings(ctx).config.value.effort)
    }

    @Test fun usageCounterPersistsAndStopsAt99Percent() {
        val u = PrefsUsageStore(ctx)
        ctx.getSharedPreferences("assistant_usage", Context.MODE_PRIVATE).edit().clear().commit()
        repeat(2) { assertTrue(u.tryAcquire(ProviderId.GEMINI, 50L, 3)) }
        assertFalse(u.tryAcquire(ProviderId.GEMINI, 50L, 3))
        assertEquals(2, PrefsUsageStore(ctx).count(ProviderId.GEMINI, 50L))
        assertTrue(u.tryAcquire(ProviderId.GEMINI, 51L, 3)) // gun degisti
        ctx.getSharedPreferences("assistant_usage", Context.MODE_PRIVATE).edit().clear().commit()
    }
}
