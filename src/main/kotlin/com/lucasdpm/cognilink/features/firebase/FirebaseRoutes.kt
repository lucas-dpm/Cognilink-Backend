package com.lucasdpm.cognilink.features.firebase

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.firebaseRouting() {
    route("/firebase") {
        get("/status") {
            call.respond(mapOf("status" to "Firebase module ready"))
        }
    }
}
