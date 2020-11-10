import Sheets.InsertDataOption.INSERT_ROWS
import Sheets.InsertDataOption.OVERWRITE
import Sheets.ValueInputOption.USER_ENTERED
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ClearValuesRequest
import com.google.api.services.sheets.v4.model.ValueRange
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoField.SECOND_OF_DAY
import java.time.temporal.ChronoUnit


object Sheets {
    private const val APPLICATION_NAME = "Athenian Trading App"
    internal const val CREDENTIALS_FILE_PATH = "/credentials.json"
    internal const val CREDENTIALS2_FILE_PATH = "/credentials2.json"
    private const val TOKENS_DIRECTORY_PATH = "tokens"
    internal val JSON_FACTORY = JacksonFactory.getDefaultInstance()
    private val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
    private val SCOPES = listOf(SheetsScopes.SPREADSHEETS)
    private const val ssId = "1hrY-aJXVx2bpyT5K98GQERHAhz_CeQQoM3x7ITpg9e4"

    private fun getCredentials(): Credential {
        val inStream = Sheets::class.java.getResourceAsStream(CREDENTIALS_FILE_PATH)
            ?: throw FileNotFoundException("Resource not found: $CREDENTIALS_FILE_PATH")
        val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(inStream))
        val flow =
            GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build()
        val receiver = LocalServerReceiver.Builder().setPort(8888).build()
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }

    data class User(val name: String)
    data class Item(val desc: String)
    data class Transaction(val user: User, val amount: Int, val item: Item)

    enum class ValueInputOption { USER_ENTERED, RAW }
    enum class InsertDataOption { OVERWRITE, INSERT_ROWS }

    private val fromDate = LocalDate.parse("1899-12-30")
    private const val secsInDay = 24 * 60 * 60
    fun nowForSheets(): Double {
        val toDate = LocalDate.now()
        val diff = ChronoUnit.DAYS.between(fromDate, toDate)
        val secsFraction = LocalDateTime.now().get(SECOND_OF_DAY) * 1.0 / secsInDay
        return diff + secsFraction
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val service =
            Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials())
                .setApplicationName(APPLICATION_NAME)
                .build()

        /*
        val response =
            service.append(
                ssId,
                "TransactionsRange",
                listOf(listOf(nowForSheets(), "Paul", "4", "dollars", "Suzy", 5, "lbs of sugar"))
            )
        println(response)
*/

        //val users = service.values(spreadsheetId, "UsersRange") { User(this[0] as String) }
        //val items = service.values(spreadsheetId, "GoodsOrServicesRange") { Item(this[0] as String) }
        val allocations = service.query(ssId, "AllocationsRange") {
            Transaction(
                User(this[0] as String),
                (this[1] as String).toInt(),
                Item(this[2] as String)
            )
        }
        val transactions = service.query(ssId, "TransactionsRange") {
            if (size == 7) {
                val date = this[0]
                val buyer = User(this[1] as String)
                val buyerAmount = (this[2] as String).toInt()
                val buyerItem = Item(this[3] as String)
                val seller = User(this[4] as String)
                val sellerAmount = (this[5] as String).toInt()
                val sellerItem = Item(this[6] as String)
                listOf(
                    Transaction(buyer, -1 * buyerAmount, buyerItem),
                    Transaction(seller, buyerAmount, buyerItem),
                    Transaction(buyer, sellerAmount, sellerItem),
                    Transaction(seller, -1 * sellerAmount, sellerItem)
                )
            } else {
                emptyList()
            }
        }.flatten()

        println()

        println(service.clear(ssId, "UserSummaryRange"))

        val nameMap = mutableListOf<String>()
        (allocations + transactions)
            .groupBy({ it.user to it.item }, { it.amount })
            .map { Transaction(it.key.first, it.value.sum(), it.key.second) }
            .groupBy({ it.user }, { it.amount to it.item })
            .toSortedMap(compareBy({ it.name }))
            .forEach { k, v ->
                println(k.name)
                v.sortedWith(compareBy({ it.second.desc }))
                    .forEach {
                        println("\t${it.first} ${it.second.desc}")
                        service.append(ssId,
                                       "UserSummaryRange",
                                       listOf(listOf(if (nameMap.contains(k.name)) "" else k.name,
                                                     it.first,
                                                     it.second.desc)),
                                       insertDataOption = OVERWRITE)
                        nameMap += k.name
                    }
                println()

            }

    }

    fun <R> Sheets.query(ssId: String, range: String, mapper: List<Any>.() -> R) =
        spreadsheets().values().get(ssId, range).execute()
            .run {
                getValues().map { mapper(it) }
            }

    fun Sheets.append(
        ssId: String,
        range: String,
        values: List<List<Any>>,
        valueInputOption: ValueInputOption = USER_ENTERED,
        insertDataOption: InsertDataOption = INSERT_ROWS
    ) =
        spreadsheets().values().append(ssId, range, ValueRange().setValues(values))
            .also {
                it.valueInputOption = valueInputOption.name
                it.insertDataOption = insertDataOption.name
            }.execute()

    fun Sheets.clear(ssId: String, range: String) =
        spreadsheets().values().clear(ssId, range, ClearValuesRequest()).execute()
}