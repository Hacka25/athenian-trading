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
import com.github.pambrose.Item.Companion.toItem
import com.github.pambrose.PageUtils.authorizedUser
import com.github.pambrose.PageUtils.page
import com.github.pambrose.PageUtils.rawHtml
import com.github.pambrose.PageUtils.tradeChoices
import com.github.pambrose.PageUtils.tradingSheet
import com.github.pambrose.Paths.TRADE
import com.github.pambrose.TradingServer.googleCredential
import com.github.pambrose.User.Companion.toUser
import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.isNull
import com.github.pambrose.pages.TradePage.ParamNames.*
import com.github.pambrose.pages.TradePage.TradeActions.ADD
import com.github.pambrose.pages.TradePage.TradeActions.BALANCE
import io.ktor.application.*
import io.ktor.locations.*
import io.ktor.request.*
import kotlinx.html.*

object TradePage {

  enum class TradeActions {
    ADD, BALANCE;

    fun asPath() = "$TRADE/${name.toLowerCase()}"
  }

  enum class ParamNames { SELLER_NAME, SELLER_AMOUNT, SELLER_ITEM, BUYER_NAME, BUYER_AMOUNT, BUYER_ITEM }

  fun PipelineCall.addTradePage(arg: Trade) =
    page {
      tradeChoices()
      if (googleCredential.get().isNull())
        h3 { style = "color:red;"; +"Please ask your teacher to authorize the app" }
      else {
        val user = authorizedUser(false)
        val ts = tradingSheet()
        when (enumValueOf(arg.action.toUpperCase()) as TradeActions) {
          ADD -> {
            div {
              val params = call.request.queryParameters
              val buyer =
                TradeSide(user /*params[BUYER_NAME.name]?.toUser() ?: ts.users[0]*/,
                          ItemAmount(params[BUYER_AMOUNT.name]?.toInt() ?: 0,
                                     params[BUYER_ITEM.name]?.toItem() ?: ts.items[0]))
              val seller =
                TradeSide(params[SELLER_NAME.name]?.toUser() ?: (ts.users - user)[0],
                          ItemAmount(params[SELLER_AMOUNT.name]?.toInt() ?: 0,
                                     params[SELLER_ITEM.name]?.toItem() ?: ts.items[0]))

              h3 { +"Add a trade" }
              this@page.addTradeForm(ts, buyer, seller)
            }
          }
          BALANCE -> {
            val name = user.name
            ts.calcBalances()
              .filter { it.key.name == name }
              .map { it.key.name to it.value }
              .firstOrNull()
              ?.second
              ?.also {
                div {
                  style = "padding-left:20;"
                  +"Balance for $name"
                  table {
                    style = "padding-left:20; padding-top:10;"
                    it.sortedWith(compareBy { it.item.desc }).forEach { tr { td { +"$it" } } }
                  }
                }
              } ?: throw InvalidRequestException("Missing name $name")
          }
        }
      }
    }

  suspend fun PipelineCall.executeTrade() {
    val params = call.receiveParameters()
    val ts = tradingSheet()
    val buyer =
      TradeSide(ts.users.firstOrNull { it.name == params[BUYER_NAME.name] }
                  ?: throw InvalidRequestException("Buyer user"),
                ItemAmount(params[BUYER_AMOUNT.name]?.toInt()
                             ?: throw InvalidRequestException("Buyer amount"),
                           ts.items.firstOrNull { it.desc == params[BUYER_ITEM.name] }
                             ?: throw InvalidRequestException(
                               "Buyer item")))
    val seller =
      TradeSide(ts.users.firstOrNull { it.name == params[SELLER_NAME.name] }
                  ?: throw InvalidRequestException("Seller user"),
                ItemAmount(params[SELLER_AMOUNT.name]?.toInt()
                             ?: throw InvalidRequestException("Seller amount"),
                           ts.items.firstOrNull { it.desc == params[SELLER_ITEM.name] }
                             ?: throw InvalidRequestException("Seller item")))

    respondWith {
      page {
        tradeChoices()
        when {
          buyer.user == seller.user -> {
            h3 { style = "color:red;"; +"Error: names cannot be the same" }
            addTradeForm(ts, buyer, seller)
          }
          buyer.itemAmount.amount <= 0 || seller.itemAmount.amount <= 0 -> {
            h3 { style = "color:red;"; +"Error: amounts must be a positive number" }
            addTradeForm(ts, buyer, seller)
          }
          buyer.itemAmount.item == seller.itemAmount.item -> {
            h3 { style = "color:red;"; +"Error: items cannot be the same" }
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

  fun BODY.addTradeForm(ts: TradingSheet, buyer: TradeSide, seller: TradeSide) {
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
              buyer.name.also { option { value = it; +it } }
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
              name = BUYER_ITEM.name
              ts.items.map { it.desc }
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
              (ts.users - buyer.user).map { it.name }
                .forEach { option { value = it; selected = (it == seller.name); +it } }
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
              name = SELLER_ITEM.name
              ts.items.map { it.desc }
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