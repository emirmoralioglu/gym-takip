package com.emir.gym.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID

/** Gorsel hazirlamanin saf (Android'siz) parcalari. */
object ImageMath {
    /** Uzun kenar siniri (px); ayarlanabilir sabit. */
    const val MAX_LONG_SIDE = 1568
    const val JPEG_QUALITY = 85
    const val PREVIEW_LONG_SIDE = 320
    /** Anthropic: gorsel basina 5 MB; base64 hali bu sinirin altinda kalmali. */
    const val MAX_BASE64_BYTES = 5_000_000

    fun base64Length(rawBytes: Int): Long = 4L * ((rawBytes + 2L) / 3L)
    fun fitsBase64Limit(rawBytes: Int): Boolean = base64Length(rawBytes) <= MAX_BASE64_BYTES

    /** Tam cozunurlukte bellege almamak icin: en buyuk 2 kuvveti ki cozulen uzun kenar >= [maxLong] kalsin. */
    fun sampleSize(width: Int, height: Int, maxLong: Int = MAX_LONG_SIDE): Int {
        val long = maxOf(width, height)
        var s = 1
        while (long / (s * 2) >= maxLong) s *= 2
        return s
    }

    /** Uzun kenari [maxLong]'a sigdirir; buyutmez. Doner: (genislik, yukseklik). */
    fun fit(width: Int, height: Int, maxLong: Int = MAX_LONG_SIDE): Pair<Int, Int> {
        val long = maxOf(width, height)
        if (long <= maxLong) return width to height
        val f = maxLong.toDouble() / long
        return maxOf(1, Math.round(width * f).toInt()) to maxOf(1, Math.round(height * f).toInt())
    }

    /** EXIF yonu -> (saat yonunde derece, yatay ayna). */
    fun orientation(exif: Int): Pair<Int, Boolean> = when (exif) {
        2 -> 0 to true
        3 -> 180 to false
        4 -> 180 to true
        5 -> 90 to true
        6 -> 90 to false
        7 -> 270 to true
        8 -> 270 to false
        else -> 0 to false
    }

    /** Sihirli baytlardan tur; izin verilen: jpeg, png, webp. Digerleri null. */
    fun detectMime(h: ByteArray): String? = when {
        h.size >= 3 && h[0] == 0xFF.toByte() && h[1] == 0xD8.toByte() && h[2] == 0xFF.toByte() -> "image/jpeg"
        h.size >= 8 && h[0] == 0x89.toByte() && h[1] == 'P'.code.toByte() && h[2] == 'N'.code.toByte() && h[3] == 'G'.code.toByte() -> "image/png"
        h.size >= 12 && String(h, 0, 4, Charsets.US_ASCII) == "RIFF" && String(h, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        else -> null
    }
}

data class PreparedImage(val path: String, val previewPath: String, val mime: String, val width: Int, val height: Int, val bytes: Int) {
    fun toBlock() = Block.Image(path, mime)
}

class ImageException(message: String) : Exception(message)

/** Gorseli gonderime hazirlar: EXIF yonu, <=1568 px, JPEG ~85, <5 MB; dosyalar filesDir/chat altinda. */
class ImagePreparer(context: Context) {
    val dir: File = File(context.applicationContext.filesDir, "chat")

    /**
     * @param open her cagrida yeni InputStream acar (uri.openInputStream). Birden fazla kez okunur.
     * @throws ImageException kullaniciya gosterilebilir Turkce mesajla
     */
    fun prepare(open: () -> InputStream?): PreparedImage {
        val head = ByteArray(16)
        val n = (open() ?: throw ImageException("Görsel açılamadı.")).use { it.read(head).coerceAtLeast(0) }
        ImageMath.detectMime(head.copyOf(n)) ?: throw ImageException("Yalnızca JPEG, PNG ve WebP görseller eklenebilir.")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        (open() ?: throw ImageException("Görsel açılamadı.")).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ImageException("Görsel okunamadı.")

        val exif = try {
            open()?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } ?: 1
        } catch (e: Exception) { 1 }

        val opts = BitmapFactory.Options().apply { inSampleSize = ImageMath.sampleSize(bounds.outWidth, bounds.outHeight) }
        val decoded = (open() ?: throw ImageException("Görsel açılamadı.")).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw ImageException("Görsel okunamadı.")

        val held = ArrayList<Bitmap>()
        held += decoded
        try {
            val oriented = applyOrientation(flattenAlpha(decoded), exif).also { held += it }
            val (w, h) = ImageMath.fit(oriented.width, oriented.height)
            var cur = if (w == oriented.width && h == oriented.height) oriented else Bitmap.createScaledBitmap(oriented, w, h, true).also { held += it }

            var q = ImageMath.JPEG_QUALITY
            var bytes = jpeg(cur, q)
            // 5 MB sinirinin altina inene dek once kalite, sonra boyut dusurulur.
            while (!ImageMath.fitsBase64Limit(bytes.size)) {
                if (q > 55) q -= 10 else {
                    val nw = maxOf(1, cur.width * 3 / 4)
                    val nh = maxOf(1, cur.height * 3 / 4)
                    if (nw < 64) throw ImageException("Görsel çok büyük.")
                    cur = Bitmap.createScaledBitmap(cur, nw, nh, true).also { held += it }
                }
                bytes = jpeg(cur, q)
            }
            dir.mkdirs()
            val name = "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
            val file = File(dir, "$name.jpg").also { it.writeBytes(bytes) }

            val (pw, ph) = ImageMath.fit(cur.width, cur.height, ImageMath.PREVIEW_LONG_SIDE)
            val thumb = Bitmap.createScaledBitmap(cur, pw, ph, true).also { held += it }
            val thumbFile = File(dir, "${name}_thumb.jpg").also { it.writeBytes(jpeg(thumb, 70)) }
            return PreparedImage(file.absolutePath, thumbFile.absolutePath, "image/jpeg", cur.width, cur.height, bytes.size)
        } finally {
            held.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    private fun flattenAlpha(src: Bitmap): Bitmap {
        if (!src.hasAlpha()) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(src, 0f, 0f, null) }
        src.recycle()
        return out
    }

    private fun applyOrientation(src: Bitmap, exif: Int): Bitmap {
        val (deg, flip) = ImageMath.orientation(exif)
        if (deg == 0 && !flip) return src
        val m = Matrix()
        if (flip) m.postScale(-1f, 1f)
        if (deg != 0) m.postRotate(deg.toFloat())
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return out
    }

    private fun jpeg(b: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    /** Gonderim icin dosyayi base64 okur; dosya yoksa null. */
    fun base64(path: String): String? = try {
        val f = File(path)
        // Yalnizca kendi dizinimizdeki dosyalar okunur.
        if (f.canonicalPath.startsWith(dir.canonicalPath + File.separator) && f.isFile)
            android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP) else null
    } catch (e: Exception) { null }

    fun deleteAll() { dir.deleteRecursively() }
}
