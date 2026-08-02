package com.a02.draw.data.remote.crypto

import com.google.gson.Gson
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

class AesPayloadDecryptor internal constructor(
    private val gson: Gson,
    secretKey: String,
    iv: String,
) {
    private val keyBytes = secretKey.toByteArray(StandardCharsets.UTF_8)
    private val ivBytes = iv.toByteArray(StandardCharsets.UTF_8)

    fun <T> decrypt(payload: String, responseType: Class<T>): T {
        require(keyBytes.size == AES_256_KEY_BYTES) {
            "AES-256 key must contain exactly 32 UTF-8 bytes"
        }
        require(ivBytes.size == AES_CBC_IV_BYTES) {
            "AES-CBC IV must contain exactly 16 UTF-8 bytes"
        }
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyBytes, AES_ALGORITHM),
            IvParameterSpec(ivBytes),
        )
        val encryptedBytes = Base64.decode(payload)
        val json = String(cipher.doFinal(encryptedBytes), StandardCharsets.UTF_8)
        return gson.fromJson(json, responseType)
    }

    private companion object {
        const val AES_ALGORITHM = "AES"
        const val AES_TRANSFORMATION = "AES/CBC/PKCS5Padding"
        const val AES_256_KEY_BYTES = 32
        const val AES_CBC_IV_BYTES = 16
    }
}
