package com.a02.draw.data.remote

import com.a02.draw.data.remote.api.DrawApi
import com.a02.draw.data.remote.api.EncryptedDrawApiAdapter
import com.a02.draw.data.remote.api.ToroArApi
import com.a02.draw.data.remote.crypto.AesPayloadDecryptor
import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

class DrawApiTest {
    private lateinit var server: MockWebServer
    private lateinit var toroArApi: ToroArApi
    private lateinit var api: DrawApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val gson = GsonBuilder().create()
        toroArApi = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ToroArApi::class.java)
        api = EncryptedDrawApiAdapter(
            api = toroArApi,
            decryptor = AesPayloadDecryptor(gson, AES_KEY, AES_IV),
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `successful response is parsed`() = runBlocking {
        server.enqueue(
            MockResponse(
                body = """[{"id":4,"title":"Remote","updatedAtEpochMillis":99}]""",
            ),
        )

        val result = api.getDrawings()

        assertEquals(4, result.single().id)
        assertEquals("Remote", result.single().title)
    }

    @Test(expected = Exception::class)
    fun `malformed response fails conversion`() = runBlocking {
        server.enqueue(MockResponse(body = "not-json"))

        api.getDrawings()
        Unit
    }

    @Test
    fun `assets request parses JSON and sends documented query parameters`() = runBlocking {
        server.enqueue(encryptedResponse("""{"data":{"items":[],"total":0}}"""))

        val result = api.getAssets(
            category = "animal",
            subcategory = "dog",
            search = "puppy",
            page = 2,
            limit = 50,
        )

        val request = server.takeRequest()
        assertEquals(0, result.data.total)
        assertEquals("true", request.headers["X-Enable-AES"])
        assertEquals(
            "/api/v1/assets?category=animal&subcategory=dog&search=puppy&page=2&limit=50",
            request.url.encodedPath + "?" + request.url.encodedQuery,
        )
    }

    @Test
    fun `lesson assets request sends root family and pagination parameters`() = runBlocking {
        server.enqueue(
            encryptedResponse(
                """{"data":{"items":[],"page":2,"limit":20,"total":0,"totalPages":0}}""",
            ),
        )

        val result = api.getAssets(
            rootFamily = "lesson",
            category = "animal",
            page = 2,
            limit = 20,
        )

        val request = server.takeRequest()
        assertEquals(2, result.data.page)
        assertEquals("true", request.headers["X-Enable-AES"])
        assertEquals(
            "/api/v1/assets?rootFamily=lesson&category=animal&page=2&limit=20",
            request.url.encodedPath + "?" + request.url.encodedQuery,
        )
    }

    @Test
    fun `categories request decrypts all category fields`() = runBlocking {
        server.enqueue(
            encryptedResponse(
                """{"data":[{"slug":"anime","name":"Anime","count":275}]}""",
            ),
        )

        val result = api.getCategories()

        val request = server.takeRequest()
        assertEquals("/api/v1/categories", request.url.encodedPath)
        assertEquals("true", request.headers["X-Enable-AES"])
        assertEquals("anime", result.data.single().slug)
        assertEquals("Anime", result.data.single().name)
        assertEquals(275, result.data.single().count)
    }

    private fun encryptedResponse(json: String): MockResponse {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(AES_KEY.toByteArray(StandardCharsets.UTF_8), "AES"),
            IvParameterSpec(AES_IV.toByteArray(StandardCharsets.UTF_8)),
        )
        val payload = Base64.encode(cipher.doFinal(json.toByteArray(StandardCharsets.UTF_8)))
        return MockResponse(body = """{"payload":"$payload"}""")
    }

    private companion object {
        const val AES_KEY = "0123456789abcdef0123456789abcdef"
        const val AES_IV = "abcdef0123456789"
    }
}
