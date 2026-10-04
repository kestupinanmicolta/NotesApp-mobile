package com.notes.mobile.data.remote

data class AuthRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val token: String?,
    val email: String?,
    val message: String?,
    val userId: Long? = null
)

data class NoteRequest(
    val title: String,
    val content: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String? = null
)

data class NoteResponse(
    val id: Long,
    val title: String,
    val content: String,
    val userId: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String? = null,
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