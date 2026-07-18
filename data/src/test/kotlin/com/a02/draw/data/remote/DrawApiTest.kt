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
}
