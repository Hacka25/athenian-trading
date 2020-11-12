package com.github.pambrose

import TradingSheet
import TradingSheet.Companion.getWebServerCredentials
import TradingSheet.Companion.ssId
import com.github.pambrose.common.response.respondWith
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.services.sheets.v4.SheetsScopes
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.html.*
import io.ktor.http.*
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


fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

const val CLIENT_ID = "344007939346-maouhkdjq9qdnnr68dn464c89p6lv8ef"

const val ADD_TXN = "/add"
const val CLEAR_TXNS = "/clear"

var credential: GoogleCredential? = null


@Suppress("unused") // Referenced in application.conf
@JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    fun BODY.home() {
        p {
            a { href = "/"; rawHtml("&larr; Back") }
        }
    }

    fun BODY.choices() {
        ul {
            li { a { href = "/auth"; +"Auth" } }
            if (credential != null) {
                li { a { href = "/users"; +"Users" } }
                li { a { href = "/items"; +"Goods and services" } }
                li { a { href = ADD_TXN; +"Add random transaction" } }
                li { a { href = CLEAR_TXNS; +"Clear transactions" } }
                li { a { href = "/calc"; +"Calculate balances" } }
            }
        }
    }

    fun Routing.results(block: BODY.() -> Unit) =
        createHTML()
            .html {
                body {
                    choices()
                    block()
                }
            }

    fun Routing.stackTrace(e: Throwable) =
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

    fun tradingSheet() = TradingSheet(ssId, credential ?: throw IllegalArgumentException("No credentials"))


    routing {
        get("/") {
            call.respondHtml {
                body {
                    choices()
                }
            }
        }

        get(ADD_TXN) {
            call.respondText(
                try {
                    this@routing.results {
                        h2 { +"Random transaction added" }
                        tradingSheet().addItems()
                            .apply {
                                div { +first }
                                pre { +second.toString() }
                            }
                    }
                } catch (e: Throwable) {
                    this@routing.stackTrace(e)
                },
                ContentType.Text.Html
            )
        }

        get(CLEAR_TXNS) {
            call.respondText(
                try {
                    this@routing.results {
                        h2 { +"Transactions cleared" }
                        tradingSheet().clearTransactions()
                            .also {
                                pre { +it.toString() }
                            }
                    }
                } catch (e: Throwable) {
                    this@routing.stackTrace(e)
                },
                ContentType.Text.Html
            )
        }

        get("/users") {
            try {
                println(tradingSheet().users)
            } catch (e: Throwable) {
                e.printStackTrace()
            }

            call.respondHtml {
                body {
                    choices()
                    +"Users"
                }
            }
        }

        get("/calc") {
            try {
                val map = tradingSheet().calcUserSummary()
            } catch (e: Throwable) {
                e.printStackTrace()
            }

            call.respondHtml {
                body {
                    choices()
                    +"Calculated"
                }
            }
        }

        get("/auth") {
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
                            button {
                                id = "signinButton"
                                +"Sign in with Google"
                            }
                            home()

                            script {
                                rawHtml("""                                        
                                    ${'$'}('#signinButton').click(function() {
                                        auth2.grantOfflineAccess().then(signInCallback);
                                    });

                                    function signInCallback(authResult) {
                                      if (authResult['code']) {
                                        // Hide the sign-in button now that the user is authorized, for example:
                                        //${'$'}('#signinButton').attr('style', 'display: none');
                                    
                                        // Send the code to the server
                                        ${'$'}.ajax({
                                          type: 'POST',
                                          url: 'http://localhost:8080/storeauthcode',
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

        post("/storeauthcode") {
            call.request.headers["X-Requested-With"]
                ?: throw InvalidParameterException("Missing X-Requested-With header")

            val authCode = call.receive<String>()

            println("authCode = $authCode")

            credential = getWebServerCredentials(authCode)
            try {

            } catch (e: Throwable) {
                e.printStackTrace()
            }

            call.respondHtml {
                body {
                    +"Authorized"
                }
            }
        }
    }
}

fun HTMLTag.rawHtml(html: String) = unsafe { raw(html) }
