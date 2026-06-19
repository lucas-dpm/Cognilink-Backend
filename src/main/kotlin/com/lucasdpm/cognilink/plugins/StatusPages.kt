package com.lucasdpm.cognilink.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*

fun Application.configureStatusPages() {
    install(StatusPages) {
        // Trata erro de Content-Type ausente ou inválido
        exception<UnsupportedMediaTypeException> { call, cause ->
            call.respondText(text = "415: ${cause.message}", status = HttpStatusCode.UnsupportedMediaType)
        }

        // Trata erros de parsing de multipart (como boundary ausente)
        exception<java.io.IOException> { call, cause ->
            if (cause.message?.contains("multipart", ignoreCase = true) == true) {
                call.respondText(text = "400: Erro ao processar multipart. Certifique-se de que o Content-Type está correto e contém o 'boundary'.", status = HttpStatusCode.BadRequest)
            } else {
                call.respondText(text = "500: Erro de I/O: ${cause.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // Trata outros erros genéricos
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }
}
