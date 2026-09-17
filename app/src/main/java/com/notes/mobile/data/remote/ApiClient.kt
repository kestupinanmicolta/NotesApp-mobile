package com.notes.mobile.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.notes.mobile.data.local.NotesDatabase
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val TAG = "ApiClient"
    private const val BASE_URL = "http://192.168.1.10:8081/"
    private const val LEGACY_PREFS_NAME = "auth_prefs"
    private const val SECURE_PREFS_NAME = "secure_auth_prefs"
    private const val TOKEN_KEY = "jwt_token"
    private const val USER_ID_KEY = "user_id"
    private const val USERNAME_KEY = "username"

    private var retrofit: Retrofit? = null
    private var api: NotesApi? = null

    fun getApi(context: Context): NotesApi {
        if (api == null) {
            val client = createOkHttpClient(context)
            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            api = retrofit!!.create(NotesApi::class.java)
        }
        return api!!
    }

    private fun createOkHttpClient(context: Context): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val authInterceptor = Interceptor { chain ->
            val token = getToken(context)
            Log.d(TAG, "Request to: ${chain.request().url} | Token present: ${token != null}")
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Almacenamiento seguro: EncryptedSharedPreferences (AES256, Keystore).
     * Migra una sola vez desde las prefs planas anteriores y las limpia.
     * Si el Keystore no esta disponible, usa prefs planas como fallback.
     */
    private fun securePrefs(context: Context): SharedPreferences {
        try {
            val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val secure = EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            migrateLegacyPrefs(context, secure)
            return secure
        } catch (e: Exception) {
            Log.e(TAG, "Secure storage unavailable, using plain prefs: ${e.message}")
            return context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun migrateLegacyPrefs(context: Context, secure: SharedPreferences) {
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        if (!legacy.contains(TOKEN_KEY)) return
        if (!secure.contains(TOKEN_KEY)) {
            secure.edit()
                .putString(TOKEN_KEY, legacy.getString(TOKEN_KEY, null))
                .putLong(USER_ID_KEY, legacy.getLong(USER_ID_KEY, -1))
                .putString(USERNAME_KEY, legacy.getString(USERNAME_KEY, null))
                .apply()
            Log.d(TAG, "Session migrated to encrypted prefs")
        }
        legacy.edit().clear().apply()
    }

    fun saveToken(context: Context, token: String) {
        securePrefs(context).edit().putString(TOKEN_KEY, token).apply()
        Log.d(TAG, "Token saved (encrypted)")
    }

    fun saveUserId(context: Context, userId: Long) {
        securePrefs(context).edit().putLong(USER_ID_KEY, userId).apply()
        Log.d(TAG, "UserId saved: $userId")
    }

    fun saveUsername(context: Context, username: String) {
        securePrefs(context).edit().putString(USERNAME_KEY, username).apply()
    }

    fun getUserId(context: Context): Long {
        val userId = securePrefs(context).getLong(USER_ID_KEY, -1)
        Log.d(TAG, "Getting userId: $userId")
        return userId
    }

    fun getUsername(context: Context): String? {
        return securePrefs(context).getString(USERNAME_KEY, null)
    }

    fun getToken(context: Context): String? {
        return securePrefs(context).getString(TOKEN_KEY, null)
    }

    fun clearToken(context: Context) {
        securePrefs(context).edit().clear().apply()
        context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
        Log.d(TAG, "All auth data cleared")
    }

    fun isLoggedIn(context: Context): Boolean {
        return getToken(context) != null
    }
}
