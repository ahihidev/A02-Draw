package com.a02.draw.data.remote

import com.a02.draw.data.remote.api.DrawApi
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

class DrawApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: DrawApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
            .create(DrawApi::class.java)
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
        server.enqueue(MockResponse(body = """{"data":{"items":[],"total":0}}"""))

        val result = api.getAssets(
            category = "animal",
            subcategory = "dog",
            search = "puppy",
            page = 2,
            limit = 50,
        )

        val request = server.takeRequest()
        assertEquals(0, result.data.total)
        assertEquals(null, request.headers["X-Enable-AES"])
        assertEquals(
            "/api/v1/assets?category=animal&subcategory=dog&search=puppy&page=2&limit=50",
            request.url.encodedPath + "?" + request.url.encodedQuery,
        )
    }

    @Test
    fun `lessons request parses JSON and sends pagination parameters`() = runBlocking {
        server.enqueue(
            MockResponse(
                body = """{"data":{"items":[],"page":2,"limit":20,"total":0,"totalPages":0}}""",
            ),
        )

        val result = api.getLessons(category = "animal", page = 2, limit = 20)

        val request = server.takeRequest()
        assertEquals(2, result.data.page)
        assertEquals(null, request.headers["X-Enable-AES"])
        assertEquals(
            "/api/v1/lessons?category=animal&page=2&limit=20",
            request.url.encodedPath + "?" + request.url.encodedQuery,
        )
    }
}
