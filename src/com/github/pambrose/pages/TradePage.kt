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

package com.github.pambrose.pages

import com.github.pambrose.*
import com.github.pambrose.PageUtils.authorizedUser
import com.github.pambrose.PageUtils.outputFormatter
import com.github.pambrose.PageUtils.page
import com.github.pambrose.PageUtils.rawHtml
import com.github.pambrose.PageUtils.tradeChoices
import com.github.pambrose.PageUtils.tradingSheet
import com.github.pambrose.Paths.TRADE
import com.github.pambrose.TradingServer.googleCredential
import com.github.pambrose.Units.Companion.toUnit
import com.github.pambrose.User.Companion.toUser
import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.isNull
import com.github.pambrose.pages.TradePage.ParamNames.*
import com.github.pambrose.pages.TradePage.TradeActions.*
import io.ktor.application.*
import io.ktor.locations.*
import io.ktor.request.*
import kotlinx.html.*


object TradePage {

  enum class TradeActions {
    ADD, BALANCE, USER_TRANSACTIONS;

    fun asPath() = "$TRADE/${name.toLowerCase()}"
  }

  enum class ParamNames { SELLER_NAME, SELLER_AMOUNT, SELLER_UNIT, BUYER_NAME, BUYER_AMOUNT, BUYER_UNIT }

  fun PipelineCall.addTradePage(arg: Trade) =
    page {
      tradeChoices()
      if (googleCredential.get().isNull())
        h3 { style = "color:red;"; +"Please ask your teacher to authorize the app" }
      else {
        val user = authorizedUser(false)
        val username = user.username
        val ts = tradingSheet()
        when (enumValueOf(arg.action.toUpperCase()) as TradeActions) {
          ADD -> {
            div {
              val params = call.request.queryParameters
              val buyer =
                HalfTrade(user,
                          UnitAmount(params[BUYER_AMOUNT.name]?.toInt() ?: 0,
                                     params[BUYER_UNIT.name]?.toUnit() ?: ts.units[0]))
              val seller =
                HalfTrade(params[SELLER_NAME.name]?.toUser(ts.users) ?: (ts.users - user)[0],
                          UnitAmount(params[SELLER_AMOUNT.name]?.toInt() ?: 0,
                                     params[SELLER_UNIT.name]?.toUnit() ?: ts.units[0]))

              h3 { +"Add a trade" }
              this@page.addTradeForm(ts, buyer, seller)
            }
          }

          BALANCE -> {
            ts.calculateBalances()
              .filter { it.key.username == username }
              .map { it.value }
              .firstOrNull()
              ?.also {
                h3 { +"Balance for ${user.longName}" }
                table {
                  style = "padding-left:2em;"
                  it.sortedWith(compareBy { it.unit.desc }).forEach { tr { td { +"$it" } } }
                }
              } ?: throw InvalidRequestException("Missing name: $username")
          }

          USER_TRANSACTIONS -> {
            h3 { +"Transactions for ${user.longName}" }
            table {
              style = "padding-left:2em;font-size:20px"
              ts.transactions()
                .filter { it.buyer.username == username || it.seller.username == username }
                .forEach {
                  tr {
                    td {
                      style = "padding-right:1em;"
                      if (it.allocation)
                        +"Allocation"
                      else
                        +it.date.format(outputFormatter)
                    }
                    td {
                      if (it.allocation)
                        +"${it.buyer.fullName} ${it.buyerUnitAmount}"
                      else
                        +"${it.buyer.longName} traded ${it.buyerUnitAmount} for ${it.sellerUnitAmount} with ${it.seller.longName}"
                    }
                  }
                }
            }
          }
        }
      }
    }

  suspend fun PipelineCall.executeTrade() {
    val params = call.receiveParameters()
    val ts = tradingSheet()
    val buyer =
      HalfTrade(ts.users.firstOrNull { it.username == params[BUYER_NAME.name] }
                  ?: throw InvalidRequestException("Buyer user"),
                UnitAmount(params[BUYER_AMOUNT.name]?.toInt()
                             ?: throw InvalidRequestException("Buyer amount"),
                           ts.units.firstOrNull { it.desc == params[BUYER_UNIT.name] }
                             ?: throw InvalidRequestException(
                               "Buyer unit")))
    val seller =
      HalfTrade(ts.users.firstOrNull { it.username == params[SELLER_NAME.name] }
                  ?: throw InvalidRequestException("Seller user"),
                UnitAmount(params[SELLER_AMOUNT.name]?.toInt()
                             ?: throw InvalidRequestException("Seller amount"),
                           ts.units.firstOrNull { it.desc == params[SELLER_UNIT.name] }
                             ?: throw InvalidRequestException("Seller unit")))

    val balances = ts.calculateBalances()
    val buyerUnitAmount = balances[buyer.user]?.firstOrNull { it.unit == buyer.unit }
    val sellerUnitAmount = balances[seller.user]?.firstOrNull { it.unit == seller.unit }

    respondWith {
      page {
        tradeChoices()
        when {
          buyer.user == seller.user -> {
            h3 { style = "color:red;"; +"Error: buyer and seller cannot be the same person" }
            addTradeForm(ts, buyer, seller)
          }
          buyer.unitAmount.amount <= 0 || seller.unitAmount.amount <= 0 -> {
            h3 { style = "color:red;"; +"Error: amounts must be a positive number" }
            addTradeForm(ts, buyer, seller)
          }
          buyer.unitAmount.unit == seller.unitAmount.unit -> {
            h3 { style = "color:red;"; +"Error: units cannot be the same" }
            addTradeForm(ts, buyer, seller)
          }
          buyerUnitAmount.isNull() || buyerUnitAmount.amount < buyer.amount -> {
            h3 { style = "color:red;"; +"Error: ${buyer.longName} does not have ${buyer.unitAmount}" }
            addTradeForm(ts, buyer, seller)
          }
          sellerUnitAmount.isNull() || sellerUnitAmount.amount < seller.amount -> {
            h3 { style = "color:red;"; +"Error: ${seller.longName} does not have ${seller.unitAmount}" }
            addTradeForm(ts, buyer, seller)
          }
          else -> {
            h3 { +"Trade added" }
            ts.addTrade(buyer, seller).apply { div { +first } }
          }
        }
      }
    }
  }

  fun BODY.addTradeForm(ts: TradingSheet, buyer: HalfTrade, seller: HalfTrade) {
    val width = 50
    form {
      action = "$TRADE/$ADD"
      method = FormMethod.post

      table {
        tr {
          td { b { style = "font-size:20px;"; +"Buyer:" } }
          td {
            select {
              name = BUYER_NAME.name
              buyer.username.also {
                option {
                  value = it
                  +it.toUser(ts.users).longName
                }
              }
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
              name = BUYER_UNIT.name
              ts.units.map { it.desc }
                .forEach { option { value = it; selected = (it == buyer.desc); +it } }
            }
          }
        }

        tr { td { rawHtml(Entities.nbsp.text) } }

        tr {
          td { b { style = "font-size:20px;"; +"Seller:" } }
          td {
            select {
              name = SELLER_NAME.name
              (ts.users - buyer.user).map { it.username }
                .forEach { username ->
                  option {
                    value = username
                    selected = (username == seller.username)
                    +username.toUser(ts.users).longName
                  }
                }
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
              name = SELLER_UNIT.name
              ts.units.map { it.desc }
                .forEach { option { value = it; selected = (it == seller.desc); +it } }
            }
          }
        }

        tr { td { rawHtml(Entities.nbsp.text) } }

        tr {
          td {}
          td {
            submitInput {
              style =
                "font-size:20px; font-weight:bold; background-color:white; border:1px solid black; border-radius: 5px; padding: 0px 7px; cursor: pointer; height:30; width:100"
            }
          }
        }
      }
    }
  }
}

@Location("$TRADE/{action}")
data class Trade(val action: String)