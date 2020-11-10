package com.github.pambrose

import Sheets.CREDENTIALS2_FILE_PATH
import com.github.pambrose.common.response.respondWith
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.html.*
import io.ktor.request.*
import io.ktor.routing.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.slf4j.event.Level
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.security.InvalidParameterException

fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    val CLIENT_ID = "344007939346-maouhkdjq9qdnnr68dn464c89p6lv8ef"
    routing {
        get("/test") {
            call.respondHtml {
                body {
                    +"Hello"
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
                                            //scope: 'additional_scope'
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

            // Set path to the Web application client_secret_*.json file you downloaded from the
            // Google API Console: https://console.developers.google.com/apis/credentials
            // You can also find your Web application client ID and client secret from the
            // console and specify them directly when you create the GoogleAuthorizationCodeTokenRequest
            // object.

            // Set path to the Web application client_secret_*.json file you downloaded from the
            // Google API Console: https://console.developers.google.com/apis/credentials
            // You can also find your Web application client ID and client secret from the
            // console and specify them directly when you create the GoogleAuthorizationCodeTokenRequest
            // object.

            // Exchange auth code for access token
            val inStream = Application::class.java.getResourceAsStream(CREDENTIALS2_FILE_PATH)
                ?: throw FileNotFoundException("Resource not found: $CREDENTIALS2_FILE_PATH")
            val clientSecrets =
                GoogleClientSecrets.load(JacksonFactory.getDefaultInstance(), InputStreamReader(inStream))

            println("clientId: ${clientSecrets.details.clientId}")
            println("clientSecret: ${clientSecrets.details.clientSecret}")

            try {
                val tokenResponse =
                    GoogleAuthorizationCodeTokenRequest(
                        NetHttpTransport(),
                        JacksonFactory.getDefaultInstance(),
                        "https://oauth2.googleapis.com/token",
                        clientSecrets.details.clientId,
                        clientSecrets.details.clientSecret,
                        authCode,
                        "http://localhost:8080")
                        // Specify the same redirect URI that you use with your web
                        // app. If you don't have a web version of your app, you can
                        // specify an empty string.
                        .execute()

                val accessToken = tokenResponse.accessToken

                // Use access token to call API
                val credential = GoogleCredential().setAccessToken(accessToken)
                /*
            val drive = Drive.Builder(NetHttpTransport(), JSON_FACTORY, credential)
                .setApplicationName("Auth Code Exchange Demo")
                .build()
            val file: File = drive.files().get("appfolder").execute()
            */

                // Get profile info from ID token
                val idToken = tokenResponse.parseIdToken()
                val payload = idToken.payload
                val userId = payload.subject // Use this value as a key to identify a user.
                val email = payload.email
                val emailVerified = java.lang.Boolean.valueOf(payload.emailVerified)
                val name = payload["name"] as String?
                val pictureUrl = payload["picture"] as String?
                val locale = payload["locale"] as String?
                val familyName = payload["family_name"] as String?
                val givenName = payload["given_name"] as String?

                println("payload = $payload")
            } catch (e: Throwable) {
                e.printStackTrace()
            }

            call.respondHtml {
                body {
                    +"OK"
                }
            }
        }
    }
}

fun HTMLTag.rawHtml(html: String) = unsafe { raw(html) }
