package com.emir.gym.assistant

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecretCipher {
    fun encrypt(plain: String): String
    /** Cozulemezse (anahtar gecersiz, bozuk veri) null. */
    fun decrypt(token: String): String?
}

/** Android Keystore AES-256/GCM. Anahtar cihazdan cikmaz; SharedPreferences'ta yalnizca sifreli metin tutulur. */
class KeystoreCipher(private val alias: String = "assistant_api_keys_v1") : SecretCipher {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    override fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return "v1:" + Base64.encodeToString(c.iv + ct, Base64.NO_WRAP)
    }

    override fun decrypt(token: String): String? = try {
        if (!token.startsWith("v1:")) null else {
            val all = Base64.decode(token.removePrefix("v1:"), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all, 0, 12))
            String(c.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
        }
    } catch (e: Exception) { null }
}

/** Saglayici basina API anahtari deposu. Duz metin hicbir yere yazilmaz, loglanmaz. */
class ApiKeyStore(private val prefs: SharedPreferences, private val cipher: SecretCipher) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("assistant_keys", Context.MODE_PRIVATE), KeystoreCipher(),
    )

    private fun k(p: ProviderId) = "key_${p.name}"

    /** @return false: anahtar gecersiz (bos, bosluklu, cok uzun) ya da sifrelenemedi; hicbir sey yazilmaz. */
    fun set(provider: ProviderId, key: String): Boolean {
        val t = key.trim()
        if (!isPlausible(t)) return false
        val enc = try { cipher.encrypt(t) } catch (e: Exception) { return false }
        return prefs.edit().putString(k(provider), enc).commit()
    }

    fun get(provider: ProviderId): String? = prefs.getString(k(provider), null)?.let { cipher.decrypt(it) }

    /** Yalniz bu cihazda cozulebilen anahtar "var" sayilir; cozulemeyen/aktarilmis kayit "yok" (kullanici yeniden girer). */
    fun has(provider: ProviderId): Boolean = get(provider) != null

    fun clear(provider: ProviderId) { prefs.edit().remove(k(provider)).commit() }

    fun masked(provider: ProviderId): String? = get(provider)?.let { mask(it) }

    companion object {
        fun isPlausible(t: String) = t.length in 8..512 && t.none { it.isWhitespace() || it.isISOControl() }

        /** 'sk-…abcd' bicimi; kisa anahtarlarda daha az gosterir. */
        fun mask(key: String): String {
            val t = key.trim()
            return when {
                t.length < 10 -> "…"
                t.length < 16 -> "…" + t.takeLast(4)
                else -> t.take(3) + "…" + t.takeLast(4)
            }
        }
    }
}
