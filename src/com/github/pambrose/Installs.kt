/*
 * Copyright © 2020 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.pambrose

import com.github.pambrose.EnvVar.*
import com.github.pambrose.PageUtils.page
import com.github.pambrose.PageUtils.rootChoices
import com.github.pambrose.PageUtils.stackTracePage
import com.github.pambrose.PageUtils.tradingSheet
import com.github.pambrose.Paths.STATIC_ROOT
import com.github.pambrose.TradingServer.adminAuth
import com.github.pambrose.TradingServer.authMap
import com.github.pambrose.TradingServer.tradingAuth
import com.github.pambrose.common.features.HerokuHttpsRedirect
import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.simpleClassName
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.request.*
import mu.KLogging
import org.slf4j.event.Level
import java.util.*

object Installs : KLogging() {

  fun Application.installs(production: Boolean) {

    install(Locations)

    install(CallLogging) {
      level = Level.INFO

      if (FILTER_LOG.getEnv(true))
        filter { call ->
          call.request.path().let { it.startsWith("/") && !it.startsWith("$STATIC_ROOT/") }
        }

      format { call ->
        val request = call.request
        val response = call.response
        val logStr = request.toLogString()
        val remote = request.origin.remoteHost

        when (val status = response.status() ?: HttpStatusCode(-1, "Unknown")) {
          HttpStatusCode.Found -> "Redirect: $logStr -> ${response.headers[HttpHeaders.Location]} - $remote"
          else -> "$status: $logStr - $remote"
        }
      }
    }

    install(DefaultHeaders) {
      header("X-Engine", "Ktor")
    }

    install(Compression) {
      gzip {
        priority = 1.0
      }
      deflate {
        priority = 10.0
        minimumSize(1024) // condition
      }
    }

    install(Authentication) {
      basic(name = adminAuth) {
        realm = "Admin Auth"
        validate { cred ->
          if (cred.name.toLowerCase() == "admin" && cred.password.toLowerCase() == "admin") UserIdPrincipal(cred.name) else null
        }
      }

      basic(name = tradingAuth) {
        realm = "Trading Auth"
        validate { cred ->
          val users = tradingSheet().users
          val user = users.firstOrNull { it.name.equals(cred.name, ignoreCase = true) }
          if (user.isNotNull() && user.password.equals(cred.password, ignoreCase = true)) {
            val str = "${cred.name}:${cred.password}"
            val encodedString: String = Base64.getEncoder().encodeToString(str.toByteArray())
            if (authMap.containsKey(encodedString)) {
              val (_, block) = authMap[encodedString] ?: throw InvalidRequestException("Auth problems")
              if (block) {
                logger.info { "Denied login for ${user.name}" }
                authMap[encodedString] = user to false
                null
              } else {
                logger.info { "Granted login for ${user.name}" }
                UserIdPrincipal(user.name)
              }
            } else {
              authMap[encodedString] = user to false
              logger.info { "Created login for ${user.name}" }
              UserIdPrincipal(user.name)
            }
          } else null
        }
      }
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

    if (FORWARDED_ENABLED.getEnv(default = false)) {
      logger.info { "Enabling ForwardedHeaderSupport" }
      install(ForwardedHeaderSupport)
    } else {
      logger.info { "Not enabling ForwardedHeaderSupport" }
    }


    if (XFORWARDED_ENABLED.getEnv(false)) {
      logger.info { "Enabling XForwardedHeaderSupport" }
      install(XForwardedHeaderSupport)
    } else {
      logger.info { "Not enabling XForwardedHeaderSupport" }
    }

    REDIRECT_HOSTNAME.getEnv(default = "")
      .also { redirectHostname ->
        if (production && redirectHostname.isNotBlank()) {
          logger.info { "Installing HerokuHttpsRedirect using: $redirectHostname" }
          install(HerokuHttpsRedirect) {
            host = redirectHostname
            permanentRedirect = false
          }
        } else {
          logger.info { "Not installing HerokuHttpsRedirect" }
        }
      }
  }
}