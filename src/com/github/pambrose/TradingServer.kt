package com.github.pambrose

import com.github.pambrose.GoogleApiUtils.codeFlow
import com.github.pambrose.common.util.Version
import com.github.pambrose.common.util.Version.Companion.versionDesc
import com.github.pambrose.common.util.getBanner
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.randomId
import com.google.api.client.auth.oauth2.Credential
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import mu.KLogging
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.TimeSource


@Version(version = "1.0.0", date = "11/14/20")
object TradingServer : KLogging() {
  private val startTime = TimeSource.Monotonic.markNow()
  internal val serverSessionId = randomId(10)
  internal val timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("M/d/y H:m:ss"))
  internal val upTime get() = startTime.elapsedNow()
  internal val userId = "owlsowls"
  internal var credential: Credential? = null

  internal val flow = codeFlow()

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

    credential = flow.loadCredential(userId).also { logger.info { "Credential found: ${it.isNotNull()}" } }

    embeddedServer(CIO, environment).start(wait = true)
  }
}