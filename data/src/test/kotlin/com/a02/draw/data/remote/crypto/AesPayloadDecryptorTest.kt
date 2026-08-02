package com.a02.draw.data.remote.crypto

import com.a02.draw.data.remote.dto.RemoteCategoriesEnvelopeDto
import com.google.gson.Gson
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class AesPayloadDecryptorTest {
    @Test
    fun `decrypts AES 256 CBC payload into response type`() {
        val payload = encrypt(
            """{"data":[{"slug":"animal","name":"Animal","count":273}]}""",
        )

        val result = AesPayloadDecryptor(Gson(), KEY, IV).decrypt(
            payload,
            RemoteCategoriesEnvelopeDto::class.java,
        )

        assertEquals("animal", result.data.single().slug)
        assertEquals(273, result.data.single().count)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid key length when decrypting`() {
        AesPayloadDecryptor(Gson(), "too-short", IV).decrypt(
            "not-used",
            RemoteCategoriesEnvelopeDto::class.java,
        )
    }

    private fun encrypt(json: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(KEY.toByteArray(StandardCharsets.UTF_8), "AES"),
            IvParameterSpec(IV.toByteArray(StandardCharsets.UTF_8)),
        )
        return Base64.encode(cipher.doFinal(json.toByteArray(StandardCharsets.UTF_8)))
    }

    private companion object {
        const val KEY = "0123456789abcdef0123456789abcdef"
        const val IV = "abcdef0123456789"
    }
}
