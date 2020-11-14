package com.github.pambrose

import com.github.pambrose.Config.BASE_URL
import com.github.pambrose.Constants.AUTH
import com.github.pambrose.Constants.PAUSE
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ClearValuesRequest
import com.google.api.services.sheets.v4.model.ValueRange
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.io.StringReader
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit

object GoogleApiUtils {
  private const val CREDENTIALS_FILE_PATH = "/credentials.json"
  private const val TOKENS_DIRECTORY_PATH = "tokens"
  private val JSON_FACTORY = JacksonFactory.getDefaultInstance()
  private val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
  private val SCOPES = listOf(SheetsScopes.SPREADSHEETS)

  private val fromDate = LocalDate.parse("1899-12-30")
  private const val secsInDay = 24 * 60 * 60

  fun nowDateTime() =
    LocalDateTime.now()
      .let {
        val diff = ChronoUnit.DAYS.between(fromDate, it)
        val secsFraction = it.get(ChronoField.SECOND_OF_DAY) * 1.0 / secsInDay
        diff + secsFraction
      }

  fun sheetsService(applicationName: String, credential: Credential) =
    Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
      .setApplicationName(applicationName)
      .build() ?: throw GoogleApiException("Null sheets service")

  fun getLocalAppCredentials(): Credential {
    val inStream = Sheets::class.java.getResourceAsStream(CREDENTIALS_FILE_PATH)
      ?: throw FileNotFoundException("Resource not found: $CREDENTIALS_FILE_PATH")
    val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(inStream))
    val flow =
      GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
        .setDataStoreFactory(FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH)))
        .setAccessType("offline")
        .build()
    val receiver =
      LocalServerReceiver.Builder()
        .setPort(8888)
        .setLandingPages("$BASE_URL$PAUSE", "$BASE_URL$AUTH")
        .build()
    return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
  }

  fun getWebServerCredentials(authCode: String): GoogleCredential {
    val strReader = StringReader(System.getenv("AUTH_CREDENTIALS"))
    val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, strReader)
    val tokenResponse =
      GoogleAuthorizationCodeTokenRequest(
        NetHttpTransport(),
        JSON_FACTORY,
        "https://oauth2.googleapis.com/token",
        clientSecrets.details.clientId,
        clientSecrets.details.clientSecret,
        authCode,
        Config.BASE_URL)
        // Specify the same redirect URI that you use with your web
        // app. If you don't have a web version of your app, you can
        // specify an empty string.
        .execute() ?: throw GoogleApiException("Null auth response")

    val accessToken = tokenResponse.accessToken

    // Get profile info from ID token
    val idToken = tokenResponse.parseIdToken()
    val payload = idToken.payload
    val userId = payload.subject // Use this value as a key to identify a user.


    return GoogleCredential().setAccessToken(accessToken)
  }

  fun <R> Sheets.query(ssId: String, range: String, mapper: List<Any>.() -> R) =
    spreadsheets().values().get(ssId, range).execute()
      ?.run {
        getValues()?.map { mapper(it) } ?: emptyList()
      } ?: throw GoogleApiException("Null get response")

  fun Sheets.append(
    ssId: String,
    range: String,
    values: List<List<Any>>,
    valueInputOption: ValueInputOption = ValueInputOption.USER_ENTERED,
    insertDataOption: InsertDataOption = InsertDataOption.OVERWRITE
  ) =
    spreadsheets().values().append(ssId, range, ValueRange().setValues(values))
      .also {
        it.valueInputOption = valueInputOption.name
        it.insertDataOption = insertDataOption.name
      }.execute() ?: throw GoogleApiException("Null append response")

  fun Sheets.clear(ssId: String, range: String) =
    spreadsheets().values().clear(ssId, range, ClearValuesRequest()).execute()
      ?: throw GoogleApiException("Null clear response")

}

enum class ValueInputOption { USER_ENTERED, RAW }
enum class InsertDataOption { OVERWRITE, INSERT_ROWS }

class MissingCredentials(msg: String) : Exception(msg)
class GoogleApiException(msg: String) : Exception(msg)
class InvalidRequestException(msg: String) : Exception(msg)
