package com.example.monisom.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun friendlyError(e: Throwable): String {
    val msg = e.message.orEmpty()
    return when {
        msg.contains("Invalid login credentials", ignoreCase = true) -> "E-mail ou senha incorretos."
        msg.contains("Email not confirmed", ignoreCase = true) -> "Confirme seu e-mail antes de entrar."
        msg.contains("Unable to resolve host", ignoreCase = true) ||
            msg.contains("Failed to connect", ignoreCase = true) ||
            msg.contains("network", ignoreCase = true) -> "Falha de conexão. Verifique sua internet."
        msg.contains("For security purposes", ignoreCase = true) -> "Aguarde alguns segundos e tente novamente."
        else -> msg.ifBlank { "Algo deu errado. Tente novamente." }
    }
}

private val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

fun formatTimestamp(iso: String): String = try {
    val instant = Instant.parse(iso)
    val zoned = instant.atZone(ZoneId.systemDefault())
    formatter.format(zoned)
} catch (e: Exception) {
    iso
}
