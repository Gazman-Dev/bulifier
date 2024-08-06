package com.bulifier.core.api.rest

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class RestApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)  // Increase connect timeout
        .readTimeout(5, TimeUnit.MINUTES)     // Increase read timeout
        .writeTimeout(60, TimeUnit.SECONDS)    // Increase write timeout
        .retryOnConnectionFailure(true)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("http://10.0.2.2:7071/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(MyApi::class.java)
}