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

import com.github.pambrose.Actions.*
import com.github.pambrose.Actions.Companion.asAction
import com.github.pambrose.EnvVar.*
import com.github.pambrose.GoogleApiUtils.googleAuthPageUrl
import com.github.pambrose.Installs.installs
import com.github.pambrose.Item.Companion.toItem
import com.github.pambrose.PageUtils.adminChoices
import com.github.pambrose.PageUtils.authorizedUser
import com.github.pambrose.PageUtils.page
import com.github.pambrose.PageUtils.rootChoices
import com.github.pambrose.PageUtils.tradeChoices
import com.github.pambrose.PageUtils.tradingSheet
import com.github.pambrose.ParamNames.*
import com.github.pambrose.Paths.ADD_TRADE
import com.github.pambrose.Paths.ADMIN
import com.github.pambrose.Paths.LOGOUT
import com.github.pambrose.Paths.OAUTH_CB
import com.github.pambrose.Paths.STATIC_ROOT
import com.github.pambrose.TradingServer.APP_TITLE
import com.github.pambrose.TradingServer.adminAuth
import com.github.pambrose.TradingServer.authCodeFlow
import com.github.pambrose.TradingServer.authMap
import com.github.pambrose.TradingServer.baseUrl
import com.github.pambrose.TradingServer.googleCredential
import com.github.pambrose.TradingServer.serverSessionId
import com.github.pambrose.TradingServer.spreadsheetId
import com.github.pambrose.TradingServer.userAuth
import com.github.pambrose.TradingServer.userId
import com.github.pambrose.User.Companion.toUser
import com.github.pambrose.common.response.redirectTo
import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.pathOf
import com.google.api.services.sheets.v4.SheetsScopes
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import kotlinx.html.*
import kotlinx.html.Entities.nbsp
import kotlinx.html.stream.createHTML
import mu.KLogging
import java.io.PrintWriter
import java.io.StringWriter

enum class ParamNames { SELLER_NAME, SELLER_AMOUNT, SELLER_ITEM, BUYER_NAME, BUYER_AMOUNT, BUYER_ITEM }

enum class Actions(val action: String) {
  USERS("users"), REFRESH_USERS("refresh-users"), ITEMS("items"),
  REFRESH_ITEMS("refresh-items"), ALLOCATIONS("allocations"),
  RANDOM_TRADE("random"), CLEAR_TRADES("clear"), CALC("calc");

  fun asPath() = "$ADMIN/$action"

  companion object {
    fun String.asAction() =
      values().firstOrNull { this == it.action } ?: throw InvalidRequestException("Invalid action: $this")
  }
}

typealias PipelineCall = PipelineContext<Unit, ApplicationCall>

object Paths {
  const val ADMIN = "/admin"
  const val ADD_TRADE = "/trade"
  const val OAUTH_CB = "/oauth-cd"
  const val STATIC_ROOT = "/static"
  const val LOGOUT = "/logout"
}

object PageUtils : KLogging() {
  private const val lipad = 5

  fun tradingSheet() = TradingSheet(spreadsheetId, googleCredential.get() ?: throw MissingCredential("No credential"))

  fun PipelineCall.authorizedUser(reset: Boolean = false): User {
    val auth = call.request.header(HttpHeaders.Authorization)?.removePrefix("Basic ") ?: ""
    return authMap[auth]
      ?.let {
        if (reset) {
          logger.info { "Resetting login for ${it.first.name}" }
          authMap[auth] = it.first to true
        }
        it.first
      } ?: throw InvalidRequestException("Unrecognized user")
  }

  fun BODY.pageTitle() = h1 {
    span {
      img { height = "40"; src = pathOf(STATIC_ROOT, "athenian.png") }
      rawHtml(nbsp.text)
      +APP_TITLE
    }
  }

  fun BODY.homeLink() = p { a { href = "/"; rawHtml("&larr; Home") } }

  fun BODY.rootChoices(errorMsg: String = "") {
    if (errorMsg.isNotBlank())
      h2 { style = "color:red;"; +errorMsg }

    ul {
      style = "padding-left:0;list-style-type:none"
      li { style = "padding-bottom:$lipad;"; a { href = ADMIN; +"Admin tasks" } }
      li { style = "padding-bottom:$lipad;"; a { href = ADD_TRADE; +"Add a trade" } }
    }
  }

  fun BODY.adminChoices() {
    ul {
      style = "padding-left:0;list-style-type:none"
      li {
        style = "padding-bottom:$lipad;"
        a { href = "https://docs.google.com/spreadsheets/d/$spreadsheetId/"; target = "_blank"; +"Google Sheet" }
      }
      li {
        style = "padding-bottom:$lipad;"
        a { href = USERS.asPath(); +"Users" }
        rawHtml(nbsp.text); rawHtml(nbsp.text)
        a { href = REFRESH_USERS.asPath(); +"(Refresh)" }
      }
      li {
        style = "padding-bottom:$lipad;"
        a { href = ITEMS.asPath(); +"Goods and services" }
        rawHtml(nbsp.text); rawHtml(nbsp.text)
        a { href = REFRESH_ITEMS.asPath(); +"(Refresh)" }
      }
      li { style = "padding-bottom:$lipad;"; a { href = ALLOCATIONS.asPath(); +"Allocations" } }
      //li { style = "padding-bottom:$lipad;"; a { href = RANDOM_TRADE.asPath(); +"Add random trade" } }
      li { style = "padding-bottom:$lipad;"; a { href = CALC.asPath(); +"Calculate balances" } }
    }
  }

  fun BODY.tradeChoices() {
    ul {
      style = "padding-left:0;list-style-type:none"
      li { style = "padding-bottom:$lipad;"; a { href = LOGOUT; +"Logout" } }
    }
  }

  fun page(addHomeLink: Boolean = true, block: BODY.() -> Unit) =
    createHTML()
      .html {
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
          h2 { +"Error" }
          pre { +sw.toString() }
        }
      }
}

fun Application.module(testing: Boolean = false) {

  val redirectUrl = "$baseUrl$OAUTH_CB"

  fun BODY.addTradeForm(ts: TradingSheet, buyer: TradeSide, seller: TradeSide) {
    val width = 50
    form {
      action = ADD_TRADE
      method = FormMethod.post

      table {

        //tr { td { b { +"Buyer:" } } }
        tr {
          td { b { +"Buyer:" } }
          td {
            select {
              name = BUYER_NAME.name
              buyer.name.also { option { value = it; +it } }
            }
          }
          td {
            numberInput {
              style = "width:$width;"
              name = BUYER_AMOUNT.name
              value = buyer.amount.toString()
            }
          }
          td {
            select {
              name = BUYER_ITEM.name
              ts.items.map { it.desc }
                .forEach { option { value = it; selected = (it == buyer.desc); +it } }
            }
          }
        }

        tr { td { rawHtml(nbsp.text) } }

        //tr { td { b { +"Seller:" } } }
        tr {
          td { b { +"Seller:" } }
          td {
            select {
              name = SELLER_NAME.name
              (ts.users - buyer.user).map { it.name }
                .forEach { option { value = it; selected = (it == seller.name); +it } }
            }
          }
          td {
            numberInput {
              style = "width:$width;"
              name = SELLER_AMOUNT.name
              value = seller.amount.toString()
            }
          }
          td {
            select {
              name = SELLER_ITEM.name
              ts.items.map { it.desc }
                .forEach { option { value = it; selected = (it == seller.desc); +it } }
            }
          }
        }

        tr { td { rawHtml(nbsp.text) } }

        tr {
          td {}
          td { submitInput { } }
        }
      }
    }

  }

  fun authPageUrl() = googleAuthPageUrl(authCodeFlow, serverSessionId, redirectUrl)

  installs(!baseUrl.contains("localhost"),
           REDIRECT_HOSTNAME.getEnv(default = ""),
           FORWARDED_ENABLED.getEnv(default = false),
           XFORWARDED_ENABLED.getEnv(false))

  routing {

    static(STATIC_ROOT) { resources("static") }

    get("/") {
      respondWith {
        page(false) {
          div {
            this@page.rootChoices()
          }
        }
      }
    }

    authenticate(adminAuth) {
      get(ADMIN) {
        if (googleCredential.get().isNull())
          redirectTo { authPageUrl() }
        else
          respondWith {
            page {
              adminChoices()
            }
          }
      }

      get<Admin> { arg ->
        if (googleCredential.get().isNull())
          redirectTo { authPageUrl() }
        else
          respondWith {
            page {
              adminChoices()
              val ts = tradingSheet()
              when (arg.action.asAction()) {
                USERS -> {
                  h2 { +"Users" }
                  table { ts.users.forEach { tr { td { +it.name } } } }
                }
                REFRESH_USERS -> {
                  h2 { +"Users Refreshed" }
                  table { ts.refreshUsers().forEach { tr { td { +it.name } } } }
                }
                ITEMS -> {
                  h2 { +"Goods and services" }
                  table { ts.items.forEach { tr { td { +it.desc } } } }
                }
                REFRESH_ITEMS -> {
                  h2 { +"Goods and services Refreshed" }
                  table { ts.refreshItems().forEach { tr { td { +it.desc } } } }
                }
                ALLOCATIONS -> {
                  h2 { +"Allocations" }
                  ts.allocations
                    .also { items ->
                      table {
                        items.forEach {
                          tr {
                            td { style = "padding-right:5;"; b { +it.user.name } }
                            td { +"${it.itemAmount}" }
                          }
                        }
                      }
                    }
                }
                RANDOM_TRADE -> {
                  h2 { +"Random trade added" }

                  val buyer = TradeSide(ts.users.random(), ItemAmount((1..10).random(), ts.items.random()))
                  val seller = TradeSide((ts.users - buyer.user).random(),
                                         ItemAmount((1..10).random(), (ts.items - buyer.itemAmount.item).random()))
                  ts.addTrade(buyer, seller)
                    .apply {
                      div { +first }
                    }
                }
                CLEAR_TRADES -> {
                  h2 { +"Trades cleared" }
                  ts.clearTrades().also { pre { +it.toString() } }
                }
                CALC -> {
                  h2 { +"Balances" }
                  ts.calcBalances()
                    .also { elems ->
                      table {
                        elems.forEach { row ->
                          val nameList = mutableListOf<String>()
                          row.value.sortedWith(compareBy { it.item.desc })
                            .forEach {
                              tr {
                                td {
                                  style = "padding-left:10;padding-right:5;"
                                  b { +(row.key.name.takeUnless { nameList.contains(it) } ?: "") }
                                }
                                td { +"$it" }
                              }
                              nameList += row.key.name
                            }
                          tr {
                            td { rawHtml(nbsp.text) }
                            td {}
                          }
                        }
                      }
                    }
                }
              }
            }
          }
      }
    }

    authenticate(userAuth) {
      get(LOGOUT) {
        authorizedUser(true)
        redirectTo {
          baseUrl
        }
      }

      get(ADD_TRADE) {
        respondWith {
          page {
            div {
              if (googleCredential.get().isNull())
                h2 { style = "color:red;"; +"Please ask your teacher to authorize the app" }
              else {
                val user = authorizedUser(false)
                val ts = tradingSheet()
                val params = call.request.queryParameters
                val buyer =
                  TradeSide(user /*params[BUYER_NAME.name]?.toUser() ?: ts.users[0]*/,
                            ItemAmount(params[BUYER_AMOUNT.name]?.toInt() ?: 0,
                                       params[BUYER_ITEM.name]?.toItem() ?: ts.items[0]))
                val seller =
                  TradeSide(params[SELLER_NAME.name]?.toUser() ?: (ts.users - user)[0],
                            ItemAmount(params[SELLER_AMOUNT.name]?.toInt() ?: 0,
                                       params[SELLER_ITEM.name]?.toItem() ?: ts.items[0]))

                this@page.tradeChoices()
                h2 { +"Add a trade" }
                this@page.addTradeForm(ts, buyer, seller)
              }
            }
          }
        }
      }

      post(ADD_TRADE) {
        val ts = tradingSheet()
        val params = call.receiveParameters()
        val buyer =
          TradeSide(ts.users.firstOrNull { it.name == params[BUYER_NAME.name] }
                      ?: throw InvalidRequestException("Buyer user"),
                    ItemAmount(params[BUYER_AMOUNT.name]?.toInt() ?: throw InvalidRequestException("Buyer amount"),
                               ts.items.firstOrNull { it.desc == params[BUYER_ITEM.name] }
                                 ?: throw InvalidRequestException(
                                   "Buyer item")))
        val seller =
          TradeSide(ts.users.firstOrNull { it.name == params[SELLER_NAME.name] }
                      ?: throw InvalidRequestException("Seller user"),
                    ItemAmount(params[SELLER_AMOUNT.name]?.toInt() ?: throw InvalidRequestException("Seller amount"),
                               ts.items.firstOrNull { it.desc == params[SELLER_ITEM.name] }
                                 ?: throw InvalidRequestException("Seller item")))

        if (googleCredential.get().isNull())
          redirectTo { authPageUrl() }
        else
          respondWith {
            page {
              tradeChoices()
              when {
                buyer.user == seller.user -> {
                  h2 { style = "color:red;"; +"Error: names cannot be the same" }
                  addTradeForm(ts, buyer, seller)
                }
                buyer.itemAmount.amount <= 0 || seller.itemAmount.amount <= 0 -> {
                  h2 { style = "color:red;"; +"Error: amounts must be a positive number" }
                  addTradeForm(ts, buyer, seller)
                }
                buyer.itemAmount.item == seller.itemAmount.item -> {
                  h2 { style = "color:red;"; +"Error: items cannot be the same" }
                  addTradeForm(ts, buyer, seller)
                }
                else -> {
                  h2 { +"Trade added" }
                  ts.addTrade(buyer, seller).apply { div { +first } }

                  p { a { href = ADD_TRADE; +"Add another trade" } }
                }
              }
            }
          }
      }
    }

    get(OAUTH_CB) {
      val params = call.request.queryParameters
      val code = params["code"]
      val state = params["state"]
      val scope = params["scope"]

      check(state == serverSessionId)
      check(scope == SheetsScopes.SPREADSHEETS)

      val tokenRequest = authCodeFlow.newTokenRequest(code).setRedirectUri(redirectUrl).execute()
      googleCredential.set(authCodeFlow.createAndStoreCredential(tokenRequest, userId))

      redirectTo { baseUrl }
    }

    get("/favicon.ico") {
      redirectTo { pathOf(STATIC_ROOT, "favicon.ico") }
    }
  }
}

@Location("$ADMIN/{action}")
data class Admin(val action: String)

fun HTMLTag.rawHtml(html: String) = unsafe { raw(html) }
