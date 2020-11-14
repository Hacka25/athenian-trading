package com.github.pambrose

import com.github.pambrose.Config.BASE_URL
import com.github.pambrose.Config.CLIENT_ID
import com.github.pambrose.Config.SS_ID
import com.github.pambrose.Constants.ADD_TXN
import com.github.pambrose.Constants.ALLOCATIONS
import com.github.pambrose.Constants.APP_TITLE
import com.github.pambrose.Constants.AUTH
import com.github.pambrose.Constants.CALC
import com.github.pambrose.Constants.CLEAR_TXNS
import com.github.pambrose.Constants.ITEMS
import com.github.pambrose.Constants.PAUSE
import com.github.pambrose.Constants.RANDOM_TXN
import com.github.pambrose.Constants.REFRESH_ITEMS
import com.github.pambrose.Constants.REFRESH_USERS
import com.github.pambrose.Constants.SIGN_IN_BUTTON
import com.github.pambrose.Constants.STORE_AUTH_CODE
import com.github.pambrose.Constants.USERS
import com.github.pambrose.GoogleApiUtils.getLocalAppCredentials
import com.github.pambrose.GoogleApiUtils.getWebServerCredentials
import com.github.pambrose.Installs.installs
import com.github.pambrose.ParamNames.*
import com.github.pambrose.common.response.redirectTo
import com.github.pambrose.common.response.respondWith
import com.google.api.client.auth.oauth2.Credential
import com.google.api.services.sheets.v4.SheetsScopes
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.ContentType.Text.Plain
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.delay
import kotlinx.html.*
import kotlinx.html.Entities.nbsp
import kotlinx.html.stream.createHTML
import java.io.PrintWriter
import java.io.StringWriter
import java.security.InvalidParameterException
import kotlin.time.seconds

typealias PipelineCall = PipelineContext<Unit, ApplicationCall>

object Config {
  const val CLIENT_ID = "344007939346-maouhkdjq9qdnnr68dn464c89p6lv8ef.apps.googleusercontent.com"
  const val SS_ID = "1hrY-aJXVx2bpyT5K98GQERHAhz_CeQQoM3x7ITpg9e4"
  const val BASE_URL = "http://localhost:8080"
}

object Constants {
  const val USERS = "/users"
  const val REFRESH_USERS = "/refresh-users"
  const val ITEMS = "/items"
  const val REFRESH_ITEMS = "/refresh-items"
  const val ALLOCATIONS = "/allocations"
  const val RANDOM_TXN = "/random"
  const val ADD_TXN = "/add"
  const val CLEAR_TXNS = "/clear"
  const val CALC = "/calc"
  const val AUTH = "/auth"
  const val STORE_AUTH_CODE = "/storeauthcode"
  const val PAUSE = "/pause"

  const val APP_TITLE = "Athenian Trading App"
  const val SIGN_IN_BUTTON = "signinButton"
}

enum class ParamNames { SELLER_NAME, SELLER_AMOUNT, SELLER_ITEM, BUYER_NAME, BUYER_AMOUNT, BUYER_ITEM }

var credential: Credential? = null

fun BODY.choices() {
  h1 { +APP_TITLE }

  ul {
    style = "padding-left:0; margin-bottom:0; list-style-type:none"
    if (credential == null) {
      li { a { href = AUTH; +"Authorize app" } }
    } else {
      li {
        a { href = USERS; +"Users" };
        rawHtml(nbsp.text); rawHtml(nbsp.text); a {
        href = REFRESH_USERS; +"(Refresh)"
      }
      }
      li {
        a { href = ITEMS; +"Goods and services" };
        rawHtml(nbsp.text); rawHtml(nbsp.text); a {
        href = REFRESH_ITEMS; +"(Refresh)"
      }
      }
      li { a { href = ALLOCATIONS; +"Allocations" } }
      //li { a { href = RANDOM_TXN; +"Add random transaction" } }
      li { a { href = ADD_TXN; +"Add transaction" } }
      li { a { href = CALC; +"Calculate balances" } }
    }
  }
}

fun stackTracePage(e: Throwable) =
  createHTML()
    .html {
      body {
        choices()
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        h2 { +"Error" }
        pre { +sw.toString() }
      }
    }

fun page(block: BODY.() -> Unit) =
  createHTML()
    .html {
      body {
        block()
      }
    }

@Suppress("unused")
fun Application.module(testing: Boolean = false) {

  installs()

  fun tradingSheet() = TradingSheet(SS_ID, credential ?: throw MissingCredentials("No credentials"))

  routing {
    get("/") {
      if (credential == null)
        redirectTo { AUTH }
      else
        respondWith {
          page {
            choices()
          }
        }
    }

    get(PAUSE) {
      delay(1.seconds)
      redirectTo { BASE_URL }
    }

    get(USERS) {
      if (credential == null)
        redirectTo { AUTH }
      else
        respondWith {
          page {
            choices()
            h2 { +"Users" }
            table {
              tradingSheet().users.forEach { tr { td { +it.name } } }
            }
          }
        }
    }

    get(REFRESH_USERS) {
      if (credential == null)
        redirectTo { AUTH }
      else
        respondWith {
          page {
            choices()
            h2 { +"Refreshed Users" }
            table {
              tradingSheet().refreshUsers().forEach { tr { td { +it.name } } }
            }
          }
        }
    }

    get(ITEMS) {
      if (credential == null)
        redirectTo { AUTH }
      else
        respondWith {
          page {
            choices()
            h2 { +"Goods and services" }
            table {
              tradingSheet().items.forEach { tr { td { +it.desc } } }
            }
          }
        }
    }

    get(REFRESH_ITEMS) {
      if (credential == null)
        redirectTo { AUTH }
      else
        respondWith {
          page {
            choices()
            h2 { +"Refreshed Goods and services" }
            table {
              tradingSheet().refreshItems().forEach { tr { td { +it.desc } } }
            }
          }
        }
    }

    get(ALLOCATIONS) {
      if (credential == null)
        redirectTo { AUTH }
      else
        respondWith {
          page {
            choices()
            h2 { +"Allocations" }
            tradingSheet().allocations
              .also { items ->
                table {
                  items.forEach {
                    tr {
                      td {
                        style = "padding-right:5;"
                        b { +it.user.name }
                      }
                      td { +"${it.itemAmount.amount} ${it.itemAmount.item.desc}" }
                    }
                  }
                }
              }
          }
        }
    }

    get(RANDOM_TXN) {
      if (credential == null)
        redirectTo { AUTH }
      else
        respondWith {
          page {
            choices()
            h2 { +"Random transaction added" }

            val ts = tradingSheet()
            val userList = ts.users
            val itemList = ts.items
            val buyerUser = userList.random()
            val sellerUser = (userList - buyerUser).random()
            val buyerItem = itemList.random()
            val sellerItem = (itemList - buyerItem).random()

            ts.addItems(buyerUser, (1..10).random(), buyerItem, sellerUser, (1..10).random(), sellerItem)
              .apply {
                div { +first }
                //pre { +second.toString() }
              }
          }
        }
    }

    get(ADD_TXN) {
      if (credential == null)
        redirectTo { AUTH }
      else
        respondWith {
          page {
            choices()
            h2 { +"Add transaction" }
            form {
              action = ADD_TXN
              method = FormMethod.post

              val ts = tradingSheet()
              table {

                tr { td { b { +"Buyer:" } } }

                tr {
                  td { rawHtml(nbsp.text) }
                  td {
                    select {
                      name = BUYER_NAME.name
                      ts.users.forEach { option { value = it.name; +it.name } }
                    }
                  }
                  td {
                    numberInput {
                      size = "6"
                      name = BUYER_AMOUNT.name
                      value = "0"
                    }
                  }
                  td {
                    select {
                      name = BUYER_ITEM.name
                      ts.items.forEach { option { value = it.desc; +it.desc } }
                    }
                  }
                }

                tr { td { rawHtml(nbsp.text) } }

                tr { td { b { +"Seller" } } }
                tr {
                  td { rawHtml(nbsp.text) }
                  td {
                    select {
                      name = SELLER_NAME.name
                      ts.users.map { it.name }.forEach { option { value = it; +it } }
                    }
                  }
                  td {
                    numberInput {
                      size = "6"
                      name = SELLER_AMOUNT.name
                      value = "0"
                    }
                  }
                  td {
                    select {
                      name = SELLER_ITEM.name
                      ts.items.map { it.desc }.forEach { option { value = it; +it } }
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
        }
    }

    post(ADD_TXN) {
      val ts = tradingSheet()
      val params = call.receiveParameters()
      val buyerUser =
        ts.users.filter { it.name == params[BUYER_NAME.name] }
          .firstOrNull() ?: throw InvalidRequestException("Buyer user")
      val buyerAmount = params[BUYER_AMOUNT.name]?.toInt() ?: throw InvalidRequestException("Buyer amount")
      val buyerItem =
        ts.items.filter { it.desc == params[BUYER_ITEM.name] }
          .firstOrNull() ?: throw InvalidRequestException("Buyer item")
      val sellerUser =
        ts.users.filter { it.name == params[SELLER_NAME.name] }
          .firstOrNull() ?: throw InvalidRequestException("Seller user")
      val sellerAmount = params[SELLER_AMOUNT.name]?.toInt() ?: throw InvalidRequestException("Seller amount")
      val sellerItem =
        ts.items.filter { it.desc == params[SELLER_ITEM.name] }
          .firstOrNull() ?: throw InvalidRequestException("Seller item")

      if (credential == null)
        redirectTo { AUTH }
      else
        respondWith {
          page {
            choices()
            h2 { +"Transaction added" }
            ts.addItems(buyerUser, buyerAmount, buyerItem, sellerUser, sellerAmount, sellerItem)
              .apply {
                div { +first }
                //pre { +second.toString() }
              }
          }
        }
    }

    get(CLEAR_TXNS) {
      if (credential == null)
        redirectTo { AUTH }
      else
        respondWith {
          page {
            choices()
            h2 { +"Transactions cleared" }
            tradingSheet().clearTransactions()
              .also {
                pre { +it.toString() }
              }
          }
        }
    }

    get(CALC) {
      if (credential == null)
        redirectTo { AUTH }
      else
        respondWith {
          page {
            choices()
            h2 { +"Balances" }
            tradingSheet().calcBalances()
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
                          td { +"${it.amount} ${it.item.desc}" }
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

    get(AUTH) {
      try {
        credential = getLocalAppCredentials()
        redirectTo { BASE_URL }
      } catch (e: Throwable) {
        e.printStackTrace()
        throw e
      }
    }

    post(STORE_AUTH_CODE) {
      try {
        "X-Requested-With".also {
          call.request.headers[it] ?: throw InvalidParameterException("Missing $it header")
        }
        val authCode = call.receive<String>()
        credential = getWebServerCredentials(authCode)
        call.respondText("OK", Plain, HttpStatusCode.OK)
      } catch (e: Throwable) {
        e.printStackTrace()
        call.respondText("Error", Plain, HttpStatusCode.Forbidden)
      }
    }

    get("/oldAuth") {
      respondWith {
        createHTML()
          .html {
            //itemscope = true
            //itemtype = "http://schema.org/Article"
            head {
              script {
                src = "//ajax.googleapis.com/ajax/libs/jquery/1.8.2/jquery.min.js"
              }
              script {
                src = "https://apis.google.com/js/client:platform.js?onload=start"
                async = true
                defer = true
              }
              script {
                rawHtml(
                  """
                   function start() {
                     gapi.load('auth2', function() {
                        auth2 = gapi.auth2.init({
                            client_id: '$CLIENT_ID',
                            // Scopes to request in addition to 'profile' and 'email'
                            scope: '${SheetsScopes.SPREADSHEETS}'
                        });
                     });
                   }
                   """.trimIndent())
              }
            }
            body {
              h1 { +APP_TITLE }

              button {
                id = SIGN_IN_BUTTON
                +"Authorize app with Google"
              }

              p { a { href = "/"; rawHtml("&larr; Back") } }

              script {
                rawHtml(
                  """                                        
                  ${"$"}('#$SIGN_IN_BUTTON').click(function() {
                    auth2.grantOfflineAccess().then(signInCallback);
                  });

                  function signInCallback(authResult) {
                    if (authResult['code']) {
                      // Hide the sign-in button now that the user is authorized
                      ${'$'}('#$SIGN_IN_BUTTON').attr('style', 'display: none');
                  
                      // Send the code to the server
                      ${'$'}.ajax({
                        type: 'POST',
                        url: '$BASE_URL$STORE_AUTH_CODE',
                        // Always include an `X-Requested-With` header in every AJAX request,
                        // to protect against CSRF attacks.
                        headers: { 'X-Requested-With': 'XMLHttpRequest' },
                        contentType: 'application/octet-stream; charset=utf-8',
                        success: function(result) {
                          // Handle or verify the server response.
                        },
                        processData: false,
                        data: authResult['code']
                      });
                    } else {
                      // There was an error.
                      console.log('Error in ajax call');
                    }
                  }                                        
                  """.trimIndent())
              }
            }
          }
      }
    }
  }
}

fun HTMLTag.rawHtml(html: String) = unsafe { raw(html) }
