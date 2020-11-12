package com.github.pambrose

import com.github.pambrose.Config.CLIENT_ID
import com.github.pambrose.Config.SS_ID
import com.github.pambrose.Constants.ADD_TXN
import com.github.pambrose.Constants.ALLOCATIONS
import com.github.pambrose.Constants.APP_TITLE
import com.github.pambrose.Constants.AUTH
import com.github.pambrose.Constants.CALC
import com.github.pambrose.Constants.CLEAR_TXNS
import com.github.pambrose.Constants.ITEMS
import com.github.pambrose.Constants.STORE_AUTH_CODE
import com.github.pambrose.Constants.USERS
import com.github.pambrose.TradingSheet.Companion.getWebServerCredentials
import com.github.pambrose.common.response.respondWith
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.services.sheets.v4.SheetsScopes
import io.ktor.application.*
import io.ktor.application.Application
import io.ktor.features.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import io.ktor.http.ContentType.Text.Plain
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.slf4j.event.Level
import java.io.PrintWriter
import java.io.StringWriter
import java.security.InvalidParameterException


typealias PipelineCall = PipelineContext<Unit, ApplicationCall>

object Config {
  const val CLIENT_ID = "344007939346-maouhkdjq9qdnnr68dn464c89p6lv8ef"
  const val SS_ID = "1hrY-aJXVx2bpyT5K98GQERHAhz_CeQQoM3x7ITpg9e4"
}

object Constants {
  const val USERS = "/users"
  const val ITEMS = "/items"
  const val ALLOCATIONS = "/allocations"
  const val ADD_TXN = "/add"
  const val CLEAR_TXNS = "/clear"
  const val CALC = "/calc"
  const val AUTH = "/auth"
  const val STORE_AUTH_CODE = "/storeauthcode"
  const val APP_TITLE = "Athenian Trading App"
}

var credential: GoogleCredential? = null

@Suppress("unused")
fun Application.module(testing: Boolean = false) {
  install(CallLogging) {
    level = Level.INFO
    filter { call -> call.request.path().startsWith("/") }
  }

  fun BODY.back() {
    p {
      a { href = "/"; rawHtml("&larr; Back") }
    }
  }

  fun BODY.choices() {
    h1 { +APP_TITLE }

    ul {
      style = "padding-left:0; margin-bottom:0; list-style-type:none"
      li { a { href = AUTH; +"Authorize app" } }
      if (credential != null) {
        li { a { href = USERS; +"Users" } }
        li { a { href = ITEMS; +"Goods and services" } }
        li { a { href = ALLOCATIONS; +"Allocations" } }
        li { a { href = ADD_TXN; +"Add random transaction" } }
        li { a { href = CLEAR_TXNS; +"Clear transactions" } }
        li { a { href = CALC; +"Calculate balances" } }
      }
    }
  }

  fun stackTrace(e: Throwable) =
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

  suspend fun PipelineCall.results(block: BODY.() -> Unit) =
    try {
      createHTML()
        .html {
          body {
            choices()
            block()
          }
        }
    } catch (e: Throwable) {
      stackTrace(e)
    }.let { html ->
      call.respondText(html, Text.Html)
    }

  fun tradingSheet() = TradingSheet(SS_ID, credential ?: throw MissingCredentials("No credentials"))

  routing {
    get("/") {
      call.respondHtml { body { choices() } }
    }

    get(ADD_TXN) {
      results {
        h2 { +"Random transaction added" }
        tradingSheet().addItems()
          .apply {
            div { +first }
            pre { +second.toString() }
          }
      }
    }

    get(CLEAR_TXNS) {
      results {
        h2 { +"Transactions cleared" }
        tradingSheet().clearTransactions()
          .also {
            pre { +it.toString() }
          }
      }
    }

    get(USERS) {
      results {
        h2 { +"Users" }
        tradingSheet().users
          .also { users ->
            table {
              users.forEach { tr { td { +it.name } } }
            }
          }
      }
    }

    get(ITEMS) {
      results {
        h2 { +"Goods and services" }
        tradingSheet().items
          .also { items ->
            table {
              items.forEach { tr { td { +it.desc } } }
            }
          }
      }
    }

    get(ALLOCATIONS) {
      results {
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

    get(CALC) {
      results {
        h2 { +"Balances" }
        tradingSheet().calcUserSummary()
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
                  td { rawHtml(Entities.nbsp.text) }
                  td {}
                }
              }
            }
          }
      }
    }

    get(AUTH) {
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
                rawHtml("""
                                   function start() {
                                     gapi.load('auth2', function() {
                                        auth2 = gapi.auth2.init({
                                            client_id: '$CLIENT_ID.apps.googleusercontent.com',
                                            // Scopes to request in addition to 'profile' and 'email'
                                            scope: '${SheetsScopes.SPREADSHEETS}'
                                        });
                                     });
                                   }
                                   """)
              }
            }
            body {

              h1 { +APP_TITLE }

              button {
                id = "signinButton"
                +"Sign in with Google"
              }

              back()

              script {
                rawHtml("""                                        
                                    ${'$'}('#signinButton').click(function() {
                                        auth2.grantOfflineAccess().then(signInCallback);
                                    });

                                    function signInCallback(authResult) {
                                      if (authResult['code']) {
                                        // Hide the sign-in button now that the user is authorized, for example:
                                        ${'$'}('#signinButton').attr('style', 'display: none');
                                    
                                        // Send the code to the server
                                        ${'$'}.ajax({
                                          type: 'POST',
                                          url: 'http://localhost:8080$STORE_AUTH_CODE',
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
                                    """)
              }
            }
          }
      }
    }

    post(STORE_AUTH_CODE) {
      try {
        call.request.headers["X-Requested-With"] ?: throw InvalidParameterException("Missing X-Requested-With header")
        val authCode = call.receive<String>()
        credential = getWebServerCredentials(authCode)
        call.respondText("OK", Plain, HttpStatusCode.OK)
      } catch (e: Throwable) {
        e.printStackTrace()
        call.respondText("Error", Plain, HttpStatusCode.Forbidden)
      }
    }
  }
}

fun HTMLTag.rawHtml(html: String) = unsafe { raw(html) }
