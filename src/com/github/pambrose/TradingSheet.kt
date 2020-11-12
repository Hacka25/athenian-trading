package com.github.pambrose

import com.github.pambrose.TradingSheet.Ranges.*
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
import com.google.api.services.sheets.v4.model.AppendValuesResponse
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


class TradingSheet(private val ssId: String, credentials: Credential) {
  enum class Ranges { UsersRange, GoodsAndServicesRange, AllocationsRange, TransactionsRange, BalancesRange }

  private val service =
    Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credentials)
      .setApplicationName(APPLICATION_NAME)
      .build() ?: throw GoogleApiException("Null sheets service")


  val users get() = service.query(ssId, UsersRange.name) { User(this[0] as String) }

  val items get() = service.query(ssId, GoodsAndServicesRange.name) { Item(this[0] as String) }

  val allocations
    get() = service.query(ssId, AllocationsRange.name) {
      Transaction(User(this[0] as String), ItemAmount((this[1] as String).toInt(), Item(this[2] as String)))
    }

  private val transactions
    get() = service.query(ssId, TransactionsRange.name) {
      if (size == 7) {
        val date = this[0]
        val buyer = User(this[1] as String)
        val buyerAmount = (this[2] as String).toInt()
        val buyerItem = Item(this[3] as String)
        val seller = User(this[4] as String)
        val sellerAmount = (this[5] as String).toInt()
        val sellerItem = Item(this[6] as String)
        listOf(
          Transaction(buyer, ItemAmount(-1 * buyerAmount, buyerItem)),
          Transaction(seller, ItemAmount(buyerAmount, buyerItem)),
          Transaction(buyer, ItemAmount(sellerAmount, sellerItem)),
          Transaction(seller, ItemAmount(-1 * sellerAmount, sellerItem))
        )
      } else {
        emptyList()
      }
    }.flatten()

  fun clearTransactions() =
    service.clear(ssId, TransactionsRange.name)

  fun calcUserSummary(): Map<User, List<ItemAmount>> =
    (allocations + transactions)
      .groupBy({ it.user to it.itemAmount.item }, { it.itemAmount.amount })
      .map { Transaction(it.key.first, ItemAmount(it.value.sum(), it.key.second)) }
      .filter { it.itemAmount.amount != 0 }
      .groupBy({ it.user }, { ItemAmount(it.itemAmount.amount, it.itemAmount.item) })
      .toSortedMap(compareBy { it.name })
      .also { map ->
        val range = BalancesRange.name
        service.clear(ssId, range)
        val nameList = mutableListOf<String>()
        map.forEach { (k, v) ->
          println(k.name)
          v.sortedWith(compareBy { it.item.desc })
            .forEach { itemAmount ->
              println("\t${itemAmount.amount} ${itemAmount.item.desc}")
              service.append(ssId,
                             range,
                             listOf(listOf(k.name.takeUnless { nameList.contains(it) } ?: "",
                                           itemAmount.amount,
                                           itemAmount.item.desc)),
                             insertDataOption = InsertDataOption.OVERWRITE)
              nameList += k.name
            }
          println()
        }
      }

  fun addItems(): Pair<String, AppendValuesResponse> {
    val userList = users
    val itemList = items
    val fromUser = userList.random()
    val toUser = (userList - fromUser).random()
    val fromItem = itemList.random()
    val toItem = (itemList - fromItem).random()
    val fromAmount = (1..10).random()
    val toAmount = (1..10).random()
    val response =
      service.append(
        ssId,
        TransactionsRange.name,
        listOf(listOf(nowForSheets(),
                      fromUser.name, fromAmount, fromItem.desc,
                      toUser.name, toAmount, toItem.desc)))
    return "${fromUser.name} $fromAmount ${fromItem.desc} ${toUser.name} $toAmount ${toItem.desc}" to response
  }

  companion object {
    private const val APPLICATION_NAME = "Athenian Trading App"
    private const val CREDENTIALS_FILE_PATH = "/credentials.json"
    private const val TOKENS_DIRECTORY_PATH = "tokens"
    private val JSON_FACTORY = JacksonFactory.getDefaultInstance()
    private val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
    private val SCOPES = listOf(SheetsScopes.SPREADSHEETS)

    private val fromDate = LocalDate.parse("1899-12-30")
    private const val secsInDay = 24 * 60 * 60

    fun nowForSheets(): Double {
      val toDate = LocalDate.now()
      val diff = ChronoUnit.DAYS.between(fromDate, toDate)
      val secsFraction = LocalDateTime.now().get(ChronoField.SECOND_OF_DAY) * 1.0 / secsInDay
      return diff + secsFraction
    }

    fun getLocalAppCredentials(): Credential {
      val inStream = Sheets::class.java.getResourceAsStream(CREDENTIALS_FILE_PATH)
        ?: throw FileNotFoundException("Resource not found: $CREDENTIALS_FILE_PATH")
      val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(inStream))
      val flow =
        GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
          .setDataStoreFactory(FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH)))
          .setAccessType("offline")
          .build()
      val receiver = LocalServerReceiver.Builder().setPort(8080).build()
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
          "http://localhost:8080")
          // Specify the same redirect URI that you use with your web
          // app. If you don't have a web version of your app, you can
          // specify an empty string.
          .execute() ?: throw GoogleApiException("Null auth response")

      val accessToken = tokenResponse.accessToken

      // Get profile info from ID token
      val idToken = tokenResponse.parseIdToken()
      val payload = idToken.payload
      val userId = payload.subject // Use this value as a key to identify a user.

      //println("payload = $payload")

      return GoogleCredential().setAccessToken(accessToken)
    }
  }
}

data class User(val name: String)
data class Item(val desc: String)
data class ItemAmount(val amount: Int, val item: Item)
data class Transaction(val user: User, val itemAmount: ItemAmount)

enum class ValueInputOption { USER_ENTERED, RAW }
enum class InsertDataOption { OVERWRITE, INSERT_ROWS }

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

class MissingCredentials(str: String) : Exception(str)

class GoogleApiException(str: String) : Exception(str)