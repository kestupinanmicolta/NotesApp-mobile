package com.notes.mobile.data.remote

data class AuthRequest(
    val username: String,
    val password: String,
    val email: String? = null
)

data class AuthResponse(
    val token: String?,
    val username: String?,
    val message: String?,
    val userId: Long? = null
)

data class NoteRequest(
    val title: String,
    val content: String
)

data class NoteResponse(
    val id: Long,
    val title: String,
    val content: String,
    val userId: Long,
    val createdAt: String,
    val updatedAt: String
)

data class ErrorResponse(
    val timestamp: String?,
    val status: Int?,
    val error: String?,
    val message: String?,
    val path: String?
)