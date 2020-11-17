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

import com.github.pambrose.GoogleApiUtils.googleAuthPageUrl
import com.github.pambrose.PageUtils.adminChoices
import com.github.pambrose.PageUtils.authorizedUser
import com.github.pambrose.PageUtils.getResourceAsText
import com.github.pambrose.PageUtils.page
import com.github.pambrose.PageUtils.rootChoices
import com.github.pambrose.PageUtils.tradeChoices
import com.github.pambrose.Paths.ADMIN
import com.github.pambrose.Paths.FAV_ICON
import com.github.pambrose.Paths.GOOGLE_AUTH
import com.github.pambrose.Paths.LOGOUT
import com.github.pambrose.Paths.OAUTH_CB
import com.github.pambrose.Paths.ROBOTS
import com.github.pambrose.Paths.ROOT
import com.github.pambrose.Paths.STATIC_ROOT
import com.github.pambrose.Paths.STYLES_CSS
import com.github.pambrose.Paths.TRADE
import com.github.pambrose.TradingServer.adminAuth
import com.github.pambrose.TradingServer.authCodeFlow
import com.github.pambrose.TradingServer.baseUrl
import com.github.pambrose.TradingServer.googleCredential
import com.github.pambrose.TradingServer.serverSessionId
import com.github.pambrose.TradingServer.userAuth
import com.github.pambrose.common.response.redirectTo
import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.pathOf
import com.github.pambrose.pages.Admin
import com.github.pambrose.pages.AdminPage.adminPage
import com.github.pambrose.pages.CssPage.cssPage
import com.github.pambrose.pages.OauthCallback.oauthCallback
import com.github.pambrose.pages.Trade
import com.github.pambrose.pages.TradePage.TradeActions.ADD
import com.github.pambrose.pages.TradePage.addTradePage
import com.github.pambrose.pages.TradePage.executeTrade
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.http.ContentType.Text.Plain
import io.ktor.http.content.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.routing.*
import kotlinx.css.*
import kotlinx.html.*
import mu.KLogging

object Routes : KLogging() {

  val redirectUrl = "$baseUrl$OAUTH_CB"

  fun googleAuthPageUrl() =
    googleAuthPageUrl(authCodeFlow, serverSessionId, redirectUrl)

  fun Application.routes() {
    routing {
      get(ROOT) {
        respondWith {
          page(false) {
            div { this@page.rootChoices() }
          }
        }
      }

      authenticate(adminAuth) {
        get(GOOGLE_AUTH) { redirectTo { googleAuthPageUrl() } }

        get(ADMIN) {
          if (googleCredential.get().isNull())
            redirectTo { googleAuthPageUrl() }
          else
            respondWith { page { adminChoices() } }
        }

        get<Admin> { arg ->
          if (googleCredential.get().isNull())
            redirectTo { googleAuthPageUrl() }
          else
            respondWith { adminPage(arg) }
        }
      }

      authenticate(userAuth) {
        get(LOGOUT) {
          authorizedUser(true)
          redirectTo { baseUrl }
        }

        get(TRADE) { respondWith { page { tradeChoices() } } }

        get<Trade> { arg -> respondWith { addTradePage(arg) } }

        post("$TRADE/$ADD") { executeTrade() }
      }

      get(OAUTH_CB) { oauthCallback() }

      get(STYLES_CSS) { cssPage() }

      get(FAV_ICON) { redirectTo { pathOf(STATIC_ROOT, "favicon.ico") } }

      get(ROBOTS) { respondWith(Plain) { getResourceAsText("/static/robots.txt") } }

      static(STATIC_ROOT) { resources("static") }
    }
  }
}

object Paths {
  const val ROOT = "/"
  const val GOOGLE_AUTH = "/auth"
  const val ADMIN = "/admin"
  const val TRADE = "/trade"
  const val OAUTH_CB = "/oauth-cd"
  const val STATIC_ROOT = "/static"
  const val STYLES_CSS = "/styles.css"
  const val LOGOUT = "/logout"
  const val FAV_ICON = "favicon.ico"
  const val ROBOTS = "robots.txt"
}
