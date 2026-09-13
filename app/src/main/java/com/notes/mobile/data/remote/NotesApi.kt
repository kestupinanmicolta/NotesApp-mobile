package com.notes.mobile.data.remote

import retrofit2.Response
import retrofit2.http.*

interface NotesApi {

    @POST("api/auth/register")
    suspend fun register(@Body request: AuthRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    @GET("api/notes")
    suspend fun getNotes(): Response<List<NoteResponse>>

    @GET("api/notes/{id}")
    suspend fun getNoteById(@Path("id") id: Long): Response<NoteResponse>

    @POST("api/notes")
    suspend fun createNote(@Body request: NoteRequest): Response<NoteResponse>

    @PUT("api/notes/{id}")
    suspend fun updateNote(@Path("id") id: Long, @Body request: NoteRequest): Response<NoteResponse>

    @DELETE("api/notes/{id}")
    suspend fun deleteNote(@Path("id") id: Long): Response<Unit>
}
