package com.github.pambrose

import com.github.pambrose.Config.BASE_URL
import com.github.pambrose.Config.SS_ID
import com.github.pambrose.Constants.ADD_TRADE
import com.github.pambrose.Constants.ADMIN
import com.github.pambrose.Constants.ALLOCATIONS
import com.github.pambrose.Constants.APP_TITLE
import com.github.pambrose.Constants.AUTH
import com.github.pambrose.Constants.CALC
import com.github.pambrose.Constants.CLEAR_TRADES
import com.github.pambrose.Constants.ITEMS
import com.github.pambrose.Constants.PAUSE
import com.github.pambrose.Constants.RANDOM_TRADE
import com.github.pambrose.Constants.REFRESH_ITEMS
import com.github.pambrose.Constants.REFRESH_USERS
import com.github.pambrose.Constants.USERS
import com.github.pambrose.GoogleApiUtils.getLocalAppCredentials
import com.github.pambrose.Installs.installs
import com.github.pambrose.Item.Companion.toItem
import com.github.pambrose.ParamNames.*
import com.github.pambrose.TradingServer.credential
import com.github.pambrose.User.Companion.toUser
import com.github.pambrose.common.response.redirectTo
import com.github.pambrose.common.response.respondWith
import io.ktor.application.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.routing.*
import kotlinx.coroutines.delay
import kotlinx.html.*
import kotlinx.html.Entities.nbsp
import kotlinx.html.stream.createHTML
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.time.seconds

object Config {
  const val SS_ID = "1hrY-aJXVx2bpyT5K98GQERHAhz_CeQQoM3x7ITpg9e4"

  //const val BASE_URL = "http://localhost:8080"
  const val BASE_URL = "https://athenian-trading-app.herokuapp.com"
}

enum class Actions {

}

object Constants {
  const val USERS = "users"
  const val REFRESH_USERS = "refresh-users"
  const val ITEMS = "items"
  const val REFRESH_ITEMS = "refresh-items"
  const val ALLOCATIONS = "allocations"
  const val RANDOM_TRADE = "random"
  const val CLEAR_TRADES = "clear"
  const val CALC = "calc"

  const val ADMIN = "/admin"
  const val ADD_TRADE = "/trade"
  const val AUTH = "/auth"
  const val PAUSE = "/pause"

  const val APP_TITLE = "Athenian Trading App"
}

enum class ParamNames { SELLER_NAME, SELLER_AMOUNT, SELLER_ITEM, BUYER_NAME, BUYER_AMOUNT, BUYER_ITEM }

fun BODY.rootChoices() {
  h1 { +APP_TITLE }

  ul {
    style = "padding-left:0; margin-bottom:0; list-style-type:none"
    if (credential == null) {
      li { a { href = AUTH; +"Authorize app" } }
    } else {
      li { a { href = ADMIN; +"Admin tasks" } }
      li { a { href = ADD_TRADE; +"Add a trade" } }
    }
  }
}

fun BODY.adminChoices() {
  h1 { +APP_TITLE }

  ul {
    style = "padding-left:0; margin-bottom:0; list-style-type:none"
    if (credential == null) {
      li { a { href = AUTH; +"Authorize app" } }
    } else {
      li {
        a { href = "$ADMIN/$USERS"; +"Users" }
        rawHtml(nbsp.text); rawHtml(nbsp.text)
        a { href = "$ADMIN/$REFRESH_USERS"; +"(Refresh)" }
      }
      li {
        a { href = "$ADMIN/$ITEMS"; +"Goods and services" }
        rawHtml(nbsp.text); rawHtml(nbsp.text)
        a { href = "$ADMIN/$REFRESH_ITEMS"; +"(Refresh)" }
      }
      li { a { href = "$ADMIN/$ALLOCATIONS"; +"Allocations" } }
      li { a { href = "$ADMIN/$RANDOM_TRADE"; +"Add random trade" } }
      li { a { href = "$ADMIN/$CALC"; +"Calculate balances" } }
    }
  }
}

fun BODY.tradeChoices() {
  h1 { +APP_TITLE }

  ul {
    style = "padding-left:0; margin-bottom:0; list-style-type:none"
    if (credential == null) {
      li { a { href = AUTH; +"Authorize app" } }
    } else {
      //li { a { href = ADD_TRADE; +"Add trade" } }
    }
  }
}

fun stackTracePage(e: Throwable) =
  createHTML()
    .html {
      body {
        adminChoices()
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        h2 { +"Error" }
        pre { +sw.toString() }
      }
    }

fun page(backLink: Boolean = true, block: BODY.() -> Unit) =
  createHTML()
    .html {
      body {
        block()
        if (backLink)
          p { a { href = "/"; rawHtml("&larr; Back") } }
      }
    }

@Suppress("unused")
fun Application.module(testing: Boolean = false) {

  installs()

  fun tradingSheet() = TradingSheet(SS_ID, credential ?: throw MissingCredentials("No credentials"))

  fun BODY.addTradeForm(ts: TradingSheet, buyer: TradeHalf, seller: TradeHalf) {
    form {
      action = ADD_TRADE
      method = FormMethod.post

      table {

        tr { td { b { +"Buyer:" } } }

        tr {
          td { rawHtml(nbsp.text) }
          td {
            select {
              name = BUYER_NAME.name
              ts.users.map { it.name }.forEach { option { value = it; selected = (it == buyer.user.name); +it } }
            }
          }
          td {
            numberInput {
              size = "6"
              name = BUYER_AMOUNT.name
              value = buyer.itemAmount.amount.toString()
            }
          }
          td {
            select {
              name = BUYER_ITEM.name
              ts.items.map { it.desc }
                .forEach { option { value = it; selected = (it == buyer.itemAmount.item.desc); +it } }
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
              ts.users.map { it.name }.forEach { option { value = it; selected = (it == seller.user.name); +it } }
            }
          }
          td {
            numberInput {
              size = "6"
              name = SELLER_AMOUNT.name
              value = seller.itemAmount.amount.toString()
            }
          }
          td {
            select {
              name = SELLER_ITEM.name
              ts.items.map { it.desc }
                .forEach { option { value = it; selected = (it == seller.itemAmount.item.desc); +it } }
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

  routing {
    get("/") {
      if (credential == null)
        redirectTo { AUTH }
      else
        respondWith {
          page(false) {
            rootChoices()
          }
        }
    }

    // Call this to give credentials a chance to be assigned
    get(PAUSE) {
      delay(1.seconds)
      redirectTo { BASE_URL }
    }

    get(ADMIN) {
      if (credential == null)
        redirectTo { AUTH }
      else
        respondWith {
          page {
            adminChoices()
          }
        }
    }

    get<Admin> { arg ->
      if (credential == null)
        redirectTo { AUTH }
      else
        respondWith {
          page {
            adminChoices()
            val ts = tradingSheet()
            when (arg.action) {
              USERS -> {
                h2 { +"Users" }
                table { ts.users.forEach { tr { td { +it.name } } } }
              }
              REFRESH_USERS -> {
                h2 { +"Refreshed Users" }
                table { ts.refreshUsers().forEach { tr { td { +it.name } } } }
              }
              ITEMS -> {
                h2 { +"Goods and services" }
                table { ts.items.forEach { tr { td { +it.desc } } } }
              }
              REFRESH_ITEMS -> {
                h2 { +"Refreshed Goods and services" }
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

                val buyer = TradeHalf(ts.users.random(), ItemAmount((1..10).random(), ts.items.random()))
                val seller = TradeHalf((ts.users - buyer.user).random(),
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
                              td { +"${it}" }
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
              else -> +"Invalid request: ${arg.action}"
            }
          }
        }
    }

    get(ADD_TRADE) {
      if (credential == null)
        redirectTo { AUTH }
      else
        respondWith {
          val ts = tradingSheet()
          val params = call.request.queryParameters
          val buyer =
            TradeHalf(params[BUYER_NAME.name]?.toUser() ?: ts.users[0],
                      ItemAmount(params[BUYER_AMOUNT.name]?.toInt() ?: 0,
                                 params[BUYER_ITEM.name]?.toItem() ?: ts.items[0]))
          val seller =
            TradeHalf(params[SELLER_NAME.name]?.toUser() ?: ts.users[0],
                      ItemAmount(params[SELLER_AMOUNT.name]?.toInt() ?: 0,
                                 params[SELLER_ITEM.name]?.toItem() ?: ts.items[0]))

          page {
            tradeChoices()
            h2 { +"Add a trade" }
            addTradeForm(ts, buyer, seller)
          }
        }
    }

    post(ADD_TRADE) {
      val ts = tradingSheet()
      val params = call.receiveParameters()
      val buyer =
        TradeHalf(ts.users.firstOrNull { it.name == params[BUYER_NAME.name] }
                    ?: throw InvalidRequestException("Buyer user"),
                  ItemAmount(params[BUYER_AMOUNT.name]?.toInt() ?: throw InvalidRequestException("Buyer amount"),
                             ts.items.firstOrNull { it.desc == params[BUYER_ITEM.name] }
                               ?: throw InvalidRequestException(
                                 "Buyer item")))
      val seller =
        TradeHalf(ts.users.firstOrNull { it.name == params[SELLER_NAME.name] }
                    ?: throw InvalidRequestException("Seller user"),
                  ItemAmount(params[SELLER_AMOUNT.name]?.toInt() ?: throw InvalidRequestException("Seller amount"),
                             ts.items.firstOrNull { it.desc == params[SELLER_ITEM.name] }
                               ?: throw InvalidRequestException("Seller item")))

      if (credential == null)
        redirectTo { AUTH }
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

    get(AUTH) {
      try {
        credential = getLocalAppCredentials()
        redirectTo { BASE_URL }
      } catch (e: Throwable) {
        e.printStackTrace()
        throw e
      }
    }
  }
}

@Location("$ADMIN/{action}")
internal data class Admin(val action: String)

fun HTMLTag.rawHtml(html: String) = unsafe { raw(html) }
