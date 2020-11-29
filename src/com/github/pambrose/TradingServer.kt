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

import com.github.pambrose.EnvVar.BASE_URL
import com.github.pambrose.EnvVar.SPREADSHEET_ID
import com.github.pambrose.GoogleApiUtils.authorizationCodeFlow
import com.github.pambrose.common.util.Version
import com.github.pambrose.common.util.Version.Companion.versionDesc
import com.github.pambrose.common.util.getBanner
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.randomId
import com.google.api.client.auth.oauth2.Credential
import com.google.api.services.sheets.v4.SheetsScopes.SPREADSHEETS
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import mu.KLogging
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.TimeSource

@Version(version = "1.0.0", date = "11/14/20")
object TradingServer : KLogging() {
  const val APP_TITLE = "Athenian Trading App"
  const val userId = "owlsowls"
  const val adminAuth = "adminAuth"
  const val tradingAuth = "userAuth"

  private val startTime = TimeSource.Monotonic.markNow()
  val serverSessionId = randomId(10)
  val timeStamp: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("M/d/y H:m:ss"))
  val upTime get() = startTime.elapsedNow()
  val baseUrl = BASE_URL.getEnv("http://localhost:8080")
  val spreadsheetId = SPREADSHEET_ID.getEnv("1hrY-aJXVx2bpyT5K98GQERHAhz_CeQQoM3x7ITpg9e4")
  val googleCredential = AtomicReference<Credential>()
  val authCodeFlow = authorizationCodeFlow("AUTH_CREDENTIALS", listOf(SPREADSHEETS))
  val authMap = mutableMapOf<String, Pair<User, Boolean>>()

  @JvmStatic
  fun main(args: Array<String>) {
    start(args)
  }

  fun start(args: Array<String>) {

    logger.apply {
      info { getBanner("banners/serverbanner.txt", this) }
      info { TradingServer::class.versionDesc() }
    }

    val configFilename =
      args.asSequence()
        .filter { it.startsWith("-config=") }
        .map { it.replaceFirst("-config=", "") }
        .firstOrNull()
        ?: "resources/application.conf"

    val newArgs =
      if (args.any { it.startsWith("-config=") })
        args
      else
        args.toMutableList().apply { add("-config=$configFilename") }.toTypedArray()

    val environment = commandLineEnvironment(newArgs)

    googleCredential.set(authCodeFlow.loadCredential(userId)
                           .also { logger.info { "Credential found: ${it.isNotNull()}" } })

    embeddedServer(CIO, environment).start(wait = true)
  }
}

class MissingCredential(msg: String) : Exception(msg)
class GoogleApiException(msg: String) : Exception(msg)
class InvalidRequestException(msg: String) : Exception(msg)
class InvalidConfigurationException(msg: String) : Exception(msg)