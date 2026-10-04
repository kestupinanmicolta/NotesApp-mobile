package com.notes.mobile.ui.common

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.notes.mobile.R

/**
 * Avisos visibles al usuario. Centraliza los dialogs para no usar Toast
 * en mensajes que deben leerse completos (errores del backend, etc.).
 */
object Dialogs {

    fun show(
        context: Context,
        message: String,
        title: String? = null,
        onAccept: (() -> Unit)? = null
    ) {
        AlertDialog.Builder(context)
            .setTitle(title ?: context.getString(R.string.info_title))
            .setMessage(message)
            .setPositiveButton(context.getString(R.string.accept)) { _, _ ->
                onAccept?.invoke()
            }
            .setCancelable(onAccept == null)
            .show()
    }

    fun error(context: Context, message: String?, onAccept: (() -> Unit)? = null) {
        show(
            context,
            message ?: context.getString(R.string.error_unexpected),
            context.getString(R.string.error_title),
            onAccept
        )
    }

    fun confirm(
        context: Context,
        message: String,
        title: String,
        positiveText: String,
        onPositive: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText) { _, _ -> onPositive() }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }

    fun success(context: Context, message: String, onAccept: (() -> Unit)? = null) {
        show(
            context,
            message,
            context.getString(R.string.success_title),
            onAccept
        )
    }
}
