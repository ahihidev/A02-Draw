package com.a02.draw.data.di

import com.a02.draw.data.BuildConfig
import com.a02.draw.data.remote.api.DrawApi
import com.a02.draw.data.remote.api.EncryptedDrawApiAdapter
import com.a02.draw.data.remote.api.ToroArApi
import com.a02.draw.data.remote.crypto.AesPayloadDecryptor
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideAesPayloadDecryptor(gson: Gson): AesPayloadDecryptor =
        AesPayloadDecryptor(
            gson = gson,
            secretKey = BuildConfig.API_AES_KEY,
            iv = BuildConfig.API_AES_IV,
        )

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Accept", "application/json")
                chain.proceed(request.build())
            }
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    @Provides
    @Singleton
    fun provideToroArApi(retrofit: Retrofit): ToroArApi = retrofit.create(ToroArApi::class.java)

    @Provides
    @Singleton
    fun provideDrawApi(api: ToroArApi, decryptor: AesPayloadDecryptor): DrawApi =
        EncryptedDrawApiAdapter(api, decryptor)
}
