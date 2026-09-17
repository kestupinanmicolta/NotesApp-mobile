package com.notes.mobile.data.session

/**
 * Se lanza cuando el backend responde 401: el token expira o es invalido.
 * La UI lo usa para cerrar sesion y volver al login.
 */
class SessionExpiredException : Exception("Sesion expirada")
