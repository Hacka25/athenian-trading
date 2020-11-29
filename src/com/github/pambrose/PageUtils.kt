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

import com.github.pambrose.Paths.ADMIN
import com.github.pambrose.Paths.GOOGLE_AUTH
import com.github.pambrose.Paths.LOGOUT
import com.github.pambrose.Paths.STATIC_ROOT
import com.github.pambrose.Paths.STYLES_CSS
import com.github.pambrose.Paths.TRADE
import com.github.pambrose.TradingServer.APP_TITLE
import com.github.pambrose.TradingServer.authMap
import com.github.pambrose.TradingServer.googleCredential
import com.github.pambrose.TradingServer.spreadsheetId
import com.github.pambrose.common.util.pathOf
import com.github.pambrose.pages.AdminPage.AdminActions.*
import com.github.pambrose.pages.TradePage.TradeActions.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.pipeline.*
import kotlinx.css.CSSBuilder
import kotlinx.html.*
import kotlinx.html.Entities.nbsp
import kotlinx.html.stream.createHTML
import mu.KLogging
import java.io.PrintWriter
import java.io.StringWriter
import java.time.format.DateTimeFormatter
import java.util.*

typealias PipelineCall = PipelineContext<Unit, ApplicationCall>

object PageUtils : KLogging() {

  val inputFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yy HH:mm:ss", Locale.ENGLISH)
  val outputFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("E h:mm a")

  const val RECORD_TO_SHEET = "recordToSheet"

  fun HTMLTag.rawHtml(html: String) = unsafe { raw(html) }

  fun tradingSheet() = TradingSheet(spreadsheetId, googleCredential.get() ?: throw MissingCredential("No credential"))

  fun PipelineCall.authorizedUser(reset: Boolean = false): User {
    val auth = call.request.header(HttpHeaders.Authorization)?.removePrefix("Basic ") ?: ""
    return authMap[auth]
      ?.let {
        if (reset) {
          PageUtils.logger.info { "Resetting login for ${it.first.username}" }
          authMap[auth] = it.first to true
        }
        it.first
      } ?: throw InvalidRequestException("Unrecognized user")
  }

  fun page(addHomeLink: Boolean = true, block: BODY.() -> Unit) =
    createHTML()
      .html {
        head {
          link(rel = "stylesheet", href = STYLES_CSS, type = "text/css")
        }
        body {
          pageTitle()
          div {
            style = "padding-left:20;"
            this@body.block()

            if (addHomeLink)
              this@body.homeLink()
          }
        }
      }

  fun stackTracePage(e: Throwable) =
    createHTML()
      .html {
        body {
          val sw = StringWriter()
          e.printStackTrace(PrintWriter(sw))
          pageTitle()
          homeLink()
          h3 { +"Error" }
          pre { +sw.toString() }
        }
      }

  fun BODY.pageTitle() = h2 {
    span {
      img { style = "width:1em;height:1em;"; src = pathOf(STATIC_ROOT, "athenian.png") }
      rawHtml(nbsp.text)
      +APP_TITLE
    }
  }

  fun BODY.homeLink() = p { a { href = "/"; rawHtml("&larr; Home") } }

  fun BODY.rootChoices(errorMsg: String = "") {
    if (errorMsg.isNotBlank())
      h3 { style = "color:red;"; +errorMsg }

    ul {
      li { a { href = ADMIN; +"Admin Tasks" } }
      li { a { href = TRADE; +"Trade Actions" } }
    }
  }

  fun BODY.adminChoices() {
    ul {
      li {
        a { href = "https://docs.google.com/spreadsheets/d/$spreadsheetId/"; target = "_blank"; +"Google Sheet" }
      }
      li {
        a { href = GOOGLE_AUTH; +"Reauthorize with Google" }
      }
      li {
        a { href = USERS.asPath(); +"Users" }
        rawHtml(nbsp.text); rawHtml(nbsp.text)
        a { href = REFRESH_USERS.asPath(); +"(Refresh)" }
      }
      li {
        a { href = UNITS.asPath(); +"Units" }
        rawHtml(nbsp.text); rawHtml(nbsp.text)
        a { href = REFRESH_UNITS.asPath(); +"(Refresh)" }
      }
      li { a { href = ALLOCATIONS.asPath(); +"Allocations" } }
      li { a { href = ALL_TRANSACTIONS.asPath(); +"Transactions" } }
      li {
        a { href = "${BALANCES.asPath()}?$RECORD_TO_SHEET=false"; +"Balances" }
        rawHtml(nbsp.text); rawHtml(nbsp.text)
        a { href = "${BALANCES.asPath()}?$RECORD_TO_SHEET=true"; +"(Record to Spreadsheet)" }
      }
    }
  }

  fun BODY.tradeChoices() {
    ul {
      li { a { href = LOGOUT; +"Logout" } }
      li { a { href = USER_TRANSACTIONS.asPath(); +"Transactions" } }
      li { a { href = BALANCE.asPath(); +"Balance" } }
      li { a { href = ADD.asPath(); +"Add a trade" } }
    }
  }

  fun getResourceAsText(path: String) = PageUtils::class.java.getResource(path).readText()
}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
  this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}

fun FlowOrMetaDataContent.styleCss(builder: CSSBuilder.() -> Unit) {
  style(type = ContentType.Text.CSS.toString()) {
    +CSSBuilder().apply(builder).toString()
  }
}

fun CommonAttributeGroupFacade.style(builder: CSSBuilder.() -> Unit) {
  this.style = CSSBuilder().apply(builder).toString().trim()
}

fun PipelineCall.queryParam(key: String, default: String = "") = call.request.queryParameters[key] ?: default
