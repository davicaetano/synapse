package com.synapse.di

import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.remote.SynapseAIApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module for network dependencies
 * Provides Retrofit, OkHttp, and API interfaces
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    // TODO: Change to production URL when deploying
    // For Emulator: use 10.0.2.2
    // For Physical Device: use your machine's IP (check with: ipconfig getifaddr en0)
    private const val BASE_URL = "http://192.168.1.156:8000/api/"  // Your Mac's IP
    // private const val BASE_URL = "https://synapse-ai.onrender.com/api/"  // Production
    
    /**
     * Provides Firebase Auth interceptor
     * Adds Firebase ID token to all requests as Bearer token
     */
    @Provides
    @Singleton
    fun provideAuthInterceptor(auth: FirebaseAuth): Interceptor {
        return Interceptor { chain ->
            val originalRequest = chain.request()
            
            android.util.Log.d("NetworkModule", "🌐 Request: ${originalRequest.method} ${originalRequest.url}")
            
            // Get Firebase ID token synchronously (blocking)
            val token = runBlocking {
                try {
                    val user = auth.currentUser
                    android.util.Log.d("NetworkModule", "🔐 User: ${user?.uid ?: "NO USER"}")
                    
                    val result = user?.getIdToken(false)?.await()?.token
                    android.util.Log.d("NetworkModule", "✅ Token: ${if (result != null) "OK (${result.takeLast(10)})" else "NULL"}")
                    result
                } catch (e: Exception) {
                    android.util.Log.e("NetworkModule", "❌ Token error: ${e.message}", e)
                    null
                }
            }
            
            // Add Authorization header if token exists
            val newRequest = if (token != null) {
                originalRequest.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                android.util.Log.w("NetworkModule", "⚠️ Proceeding without auth token")
                originalRequest
            }
            
            val response = chain.proceed(newRequest)
            android.util.Log.d("NetworkModule", "📥 Response: ${response.code} ${response.message}")
            response
        }
    }
    
    /**
     * Provides logging interceptor for debugging
     */
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }
    
    /**
     * Provides OkHttpClient with auth and logging interceptors
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: Interceptor,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * Provides Retrofit instance
     */
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    /**
     * Provides Synapse AI API interface
     */
    @Provides
    @Singleton
    fun provideSynapseAIApi(retrofit: Retrofit): SynapseAIApi {
        return retrofit.create(SynapseAIApi::class.java)
    }
}

