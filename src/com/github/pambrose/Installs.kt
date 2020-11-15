package com.github.pambrose

import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.simpleClassName
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.request.*
import mu.KLogging
import org.slf4j.event.Level

internal object Installs : KLogging() {
  fun Application.installs() {

    install(Locations)

    install(CallLogging) {
      level = Level.INFO
      filter { call -> call.request.path().startsWith("/") }
    }

    install(StatusPages) {
      exception<Throwable> { cause ->
        logger.info(cause) { "Throwable caught: ${cause.simpleClassName}" }
        respondWith {
          stackTracePage(cause)
        }
      }

      status(HttpStatusCode.NotFound) {
        //call.respond(TextContent("${it.value} ${it.description}", Plain.withCharset(UTF_8), it))
        respondWith {
          page(false) {
            rootChoices("Invalid URL")
          }
        }
      }
    }
  }
}