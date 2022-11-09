package com.luxand.livenessrecognition

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

val okHttpClientBuilder = OkHttpClient.Builder()
    .retryOnConnectionFailure(true)

    .addInterceptor(HttpLoggingInterceptor().also {
        it.level = HttpLoggingInterceptor.Level.BODY
    })
    .addInterceptor {
        it.proceed(
            it.request()
                .newBuilder()
                .addHeader("Token", "F5883EC5-85BD-47DB-AC8F-2920A494AB25")
                .build()
        )
    }
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()


fun provideRetrofit(): Retrofit {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    return Retrofit.Builder().baseUrl(API_URL)
        .client(okHttpClientBuilder)
        .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
        .build()
}

val API_URL =  "http://18.169.134.124:80/api/VerifyFace/"

val apiService = provideRetrofit().create(ApiInterface::class.java)

fun getMoshi(): Moshi =
    Moshi.Builder()
        .build()


interface ApiInterface {
    @POST("verify-with-profile-picture")
    fun getFaceMatch(@Body verifyBody: VerifyBody): Call<Any>
}

@JsonClass(generateAdapter = true)
data class VerifyBody (
    val email: String,
    val selfie: String
)