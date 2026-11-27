package com.emir.gym.assistant

import com.emir.gym.data.ActivityType
import com.emir.gym.data.MealType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolValidatorTest {
    private val today = LocalDate.of(2026, 11, 27)

    private fun v(name: String, json: String) = ToolValidator.validate(name, ToolValidator.parseArgs(json)!!, today)
    private fun ok(name: String, json: String) = (v(name, json) as Validation.Ok).payload
    private fun invalid(name: String, json: String) = (v(name, json) as Validation.Invalid).message

    private val meal = """{"meal_type":"ogle","description":"Tavuk pilav","protein_g":35,"calories_kcal":650}"""

    @Test fun mealOkDefaultsToToday() {
        val p = ok("add_meal", meal) as ActionPayload.AddMeal
        assertEquals(today.toEpochDay(), p.epochDay)
        assertEquals(MealType.OGLE, p.mealType)
        assertEquals(650, p.calories)
        assertNull(p.carbsG)
    }

    @Test fun mealBoundaries() {
        // Formlarla ayni sinirlar: protein <=300, kalori <=5000, karb <=1000, yag <=500, aciklama <=80.
        ok("add_meal", """{"meal_type":"ara","description":"x","protein_g":300,"calories_kcal":5000,"carbs_g":1000,"fat_g":500}""")
        assertTrue(invalid("add_meal", """{"meal_type":"ara","description":"x","protein_g":300.1,"calories_kcal":1}""").startsWith("Hata"))
        assertTrue(invalid("add_meal", """{"meal_type":"ara","description":"x","protein_g":1,"calories_kcal":5001}""").startsWith("Hata"))
        assertTrue(invalid("add_meal", """{"meal_type":"ara","description":"x","protein_g":1,"calories_kcal":1,"carbs_g":1001}""").startsWith("Hata"))
        assertTrue(invalid("add_meal", """{"meal_type":"ara","description":"x","protein_g":1,"calories_kcal":1,"fat_g":501}""").startsWith("Hata"))
        ok("add_meal", """{"meal_type":"ara","description":"${"a".repeat(80)}","protein_g":1,"calories_kcal":1}""")
        invalid("add_meal", """{"meal_type":"ara","description":"${"a".repeat(81)}","protein_g":1,"calories_kcal":1}""")
    }

    @Test fun mealRejectsBadValues() {
        invalid("add_meal", """{"meal_type":"ara","description":"x","protein_g":-1,"calories_kcal":1}""")
        invalid("add_meal", """{"meal_type":"ara","description":"x","protein_g":1,"calories_kcal":-5}""")
        invalid("add_meal", """{"meal_type":"ara","description":"x","protein_g":1,"calories_kcal":10.5}""")
        invalid("add_meal", """{"meal_type":"ara","description":"x","protein_g":"abc","calories_kcal":1}""")
        invalid("add_meal", """{"meal_type":"ara","description":"x","protein_g":"NaN","calories_kcal":1}""")
        invalid("add_meal", """{"meal_type":"brunch","description":"x","protein_g":1,"calories_kcal":1}""")
        invalid("add_meal", """{"meal_type":"ara","description":"   ","protein_g":1,"calories_kcal":1}""")
        invalid("add_meal", """{"meal_type":"ara","description":"x","calories_kcal":1}""")
    }

    @Test fun numericStringsAreAccepted() {
        val p = ok("add_meal", """{"meal_type":"ara","description":"x","protein_g":"12,5","calories_kcal":"300"}""") as ActionPayload.AddMeal
        assertEquals(12.5, p.proteinG, 0.0)
        assertEquals(300, p.calories)
    }

    @Test fun futureDateRejectedPastAndTodayAccepted() {
        assertTrue(invalid("add_water", """{"ml":250,"date":"2026-11-28"}""").contains("gelecek"))
        assertEquals(today.minusDays(1).toEpochDay(), ok("add_water", """{"ml":250,"date":"2026-11-26"}""").epochDay)
        assertEquals(today.toEpochDay(), ok("add_water", """{"ml":250,"date":"2026-11-27"}""").epochDay)
        invalid("add_water", """{"ml":250,"date":"yarin"}""")
        invalid("add_water", """{"ml":250,"date":"27.11.2026"}""")
    }

    @Test fun waterBounds() {
        ok("add_water", """{"ml":5000}""")
        ok("add_water", """{"ml":1}""")
        invalid("add_water", """{"ml":5001}""")
        invalid("add_water", """{"ml":0}""")
        invalid("add_water", """{"ml":-100}""")
        invalid("add_water", """{"ml":250.5}""")
        invalid("add_water", """{}""")
    }

    @Test fun weightBounds() {
        assertEquals(78.4, (ok("log_body_weight", """{"kg":78.4}""") as ActionPayload.LogWeight).kg, 0.0)
        ok("log_body_weight", """{"kg":500}""")
        invalid("log_body_weight", """{"kg":500.5}""")
        invalid("log_body_weight", """{"kg":0}""")
        invalid("log_body_weight", """{"kg":-3}""")
    }

    @Test fun activityRules() {
        val p = ok("log_activity", """{"type":"YUZME","minutes":45,"distance_m":1500,"note":"sabah"}""") as ActionPayload.LogActivity
        assertEquals(ActivityType.YUZME, p.activity)
        assertEquals(1500, p.distanceM)
        ok("log_activity", """{"type":"sauna","minutes":1000}""")
        invalid("log_activity", """{"type":"SAUNA","minutes":1001}""")
        invalid("log_activity", """{"type":"SAUNA","minutes":0}""")
        invalid("log_activity", """{"type":"KOSU","minutes":10}""")
        invalid("log_activity", """{"type":"SAUNA","minutes":10,"distance_m":100}""")
        invalid("log_activity", """{"type":"YUZME","minutes":10,"distance_m":100001}""")
        invalid("log_activity", """{"type":"YUZME","minutes":10,"note":"${"n".repeat(81)}"}""")
    }

    @Test fun payloadRoundTrips() {
        val cases = listOf(
            ok("add_meal", """{"meal_type":"kahvalti","description":"Yumurta","protein_g":12.5,"calories_kcal":200,"carbs_g":2,"fat_g":14}"""),
            ok("add_water", """{"ml":300}"""),
            ok("log_body_weight", """{"kg":80.25}"""),
            ok("log_activity", """{"type":"YUZME","minutes":30,"distance_m":900,"note":"n"}"""),
        )
        for (p in cases) assertEquals(p, ToolValidator.decode(p.type, ToolValidator.encode(p)))
        assertNull(ToolValidator.decode("add_water", "{bozuk"))
    }

    @Test fun badArgsJson() {
        assertNull(ToolValidator.parseArgs("[1,2]"))
        assertNull(ToolValidator.parseArgs("{x"))
        assertNotNull(ToolValidator.parseArgs(""))
    }

    @Test fun toolSchemasAreGeminiSafe() {
        for (t in ToolDefs.all) {
            val j = t.parametersJson
            assertNotNull(t.name, Js.parse(j))
            assertFalse(j.contains("additionalProperties"))
        }
    }
}

class ErrorMapperTest {
    @Test fun statusMapping() {
        assertEquals(AssistantError.Kind.UNAUTHORIZED, ErrorMapper.fromHttp(401).kind)
        assertEquals(AssistantError.Kind.UNAUTHORIZED, ErrorMapper.fromHttp(403).kind)
        assertEquals(AssistantError.Kind.MODEL_NOT_FOUND, ErrorMapper.fromHttp(404).kind)
        assertEquals(AssistantError.Kind.RATE_LIMITED, ErrorMapper.fromHttp(429).kind)
        assertEquals(AssistantError.Kind.SERVER, ErrorMapper.fromHttp(500).kind)
        assertEquals(AssistantError.Kind.SERVER, ErrorMapper.fromHttp(529).kind)
        assertEquals(AssistantError.Kind.TOO_LARGE, ErrorMapper.fromHttp(413).kind)
        assertEquals(AssistantError.Kind.BAD_REQUEST, ErrorMapper.fromHttp(400, null, "bad").kind)
        assertEquals(AssistantError.Kind.TOOL_SCHEMA, ErrorMapper.fromHttp(400, null, """{"error":"Invalid function_declarations schema"}""", ProviderId.OPENAI_COMPAT).kind)
    }

    @Test fun retryAfterIsUsed() {
        val e = ErrorMapper.fromHttp(429, "30")
        assertEquals(30, e.retryAfterSec)
        assertTrue(e.message.contains("30"))
        assertNull(ErrorMapper.fromHttp(429, "Wed, 21 Oct 2026 07:28:00 GMT").retryAfterSec)
        assertNull(ErrorMapper.fromHttp(429, "-5").retryAfterSec)
    }

    @Test fun neverLeaksBodyOrKey() {
        val body = """{"error":{"message":"Invalid key sk-secret-12345 for project"}}"""
        for (code in listOf(400, 401, 403, 404, 429, 500, 418)) {
            val m = ErrorMapper.fromHttp(code, null, body).message
            assertFalse(m, m.contains("sk-secret"))
            assertFalse(m, m.contains("Invalid key"))
        }
        val t = ErrorMapper.fromThrowable(java.io.IOException("connect to https://x/?key=sk-secret-12345 failed")).message
        assertFalse(t.contains("sk-secret"))
    }

    @Test fun throwableMapping() {
        assertEquals(AssistantError.Kind.TIMEOUT, ErrorMapper.fromThrowable(java.net.SocketTimeoutException()).kind)
        assertEquals(AssistantError.Kind.TIMEOUT, ErrorMapper.fromThrowable(RuntimeException(java.net.SocketTimeoutException())).kind)
        assertEquals(AssistantError.Kind.NETWORK, ErrorMapper.fromThrowable(java.net.UnknownHostException("h")).kind)
        assertEquals(AssistantError.Kind.NETWORK, ErrorMapper.fromThrowable(java.net.ConnectException()).kind)
        assertEquals(AssistantError.Kind.UNKNOWN, ErrorMapper.fromThrowable(IllegalStateException("x")).kind)
    }
}

class UrlAndConfigTest {
    @Test fun httpsOnlyInRelease() {
        assertNull(UrlPolicy.check("https://openrouter.ai/api/v1", false))
        assertNotNull(UrlPolicy.check("http://openrouter.ai/api/v1", false))
        assertNotNull(UrlPolicy.check("http://127.0.0.1:8080", false))
        assertNull(UrlPolicy.check("http://127.0.0.1:8080", true))
        assertNull(UrlPolicy.check("http://10.0.2.2:8080", true))
        assertNotNull(UrlPolicy.check("http://evil.example.com", true))
        assertNotNull(UrlPolicy.check("ftp://x.com", true))
        assertNotNull(UrlPolicy.check("https://user:pw@x.com", false))
        assertNotNull(UrlPolicy.check("notaurl", false))
        assertEquals("https://a.com/v1", UrlPolicy.normalize(" https://a.com/v1/ "))
    }

    @Test fun modelNames() {
        assertEquals("gemini-2.5-flash", ConfigRules.cleanModel(ProviderId.GEMINI, "models/gemini-2.5-flash"))
        assertNull(ConfigRules.cleanModel(ProviderId.GEMINI, "a/b"))
        assertEquals("meta-llama/llama-3:free", ConfigRules.cleanModel(ProviderId.OPENAI_COMPAT, "meta-llama/llama-3:free"))
        assertNull(ConfigRules.cleanModel(ProviderId.OPENAI_COMPAT, "bad name"))
        assertNull(ConfigRules.cleanModel(ProviderId.ANTHROPIC, ""))
        assertTrue(ConfigRules.validLimit(null))
        assertFalse(ConfigRules.validLimit(1)) // floor(%99)=0: hicbir istek atilamaz
        assertFalse(ConfigRules.validLimit(0))
        assertFalse(ConfigRules.validLimit(-1))
    }
}

class ImageMathTest {
    @Test fun sampleSizeKeepsDecodedAtLeastTarget() {
        assertEquals(1, ImageMath.sampleSize(1000, 800))
        assertEquals(1, ImageMath.sampleSize(3135, 2000)) // 3135/2=1567 < 1568
        assertEquals(2, ImageMath.sampleSize(3136, 2000))
        assertEquals(4, ImageMath.sampleSize(8000, 6000))
    }

    @Test fun fitScalesLongSideAndNeverUpscales() {
        assertEquals(1568 to 1176, ImageMath.fit(4000, 3000))
        assertEquals(1176 to 1568, ImageMath.fit(3000, 4000))
        assertEquals(800 to 600, ImageMath.fit(800, 600))
    }

    @Test fun exifOrientation() {
        assertEquals(0 to false, ImageMath.orientation(1))
        assertEquals(90 to false, ImageMath.orientation(6))
        assertEquals(270 to false, ImageMath.orientation(8))
        assertEquals(180 to false, ImageMath.orientation(3))
        assertEquals(0 to true, ImageMath.orientation(2))
    }

    @Test fun mimeDetection() {
        assertEquals("image/jpeg", ImageMath.detectMime(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
        assertEquals("image/png", ImageMath.detectMime(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)))
        assertEquals("image/webp", ImageMath.detectMime("RIFF....WEBP".toByteArray()))
        assertNull(ImageMath.detectMime("GIF89a......".toByteArray()))
        assertNull(ImageMath.detectMime(ByteArray(0)))
    }

    @Test fun base64SizeLimit() {
        assertEquals(4L, ImageMath.base64Length(1))
        assertEquals(4L, ImageMath.base64Length(3))
        assertEquals(8L, ImageMath.base64Length(4))
        assertTrue(ImageMath.fitsBase64Limit(3_700_000))
        assertFalse(ImageMath.fitsBase64Limit(3_800_000))
    }
}
