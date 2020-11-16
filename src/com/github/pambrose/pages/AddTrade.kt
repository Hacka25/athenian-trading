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
import com.github.pambrose.Routes.authPageUrl
import com.github.pambrose.TradingServer.googleCredential
import com.github.pambrose.User.Companion.toUser
import com.github.pambrose.common.response.redirectTo
import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.isNull
import com.github.pambrose.pages.AddTrade.ParamNames.*
import io.ktor.application.*
import io.ktor.request.*
import kotlinx.html.*

object AddTrade {

  enum class ParamNames { SELLER_NAME, SELLER_AMOUNT, SELLER_ITEM, BUYER_NAME, BUYER_AMOUNT, BUYER_ITEM }

  fun PipelineCall.addTradePage() =
    page {
      div {
        if (googleCredential.get().isNull())
          h2 { style = "color:red;"; +"Please ask your teacher to authorize the app" }
        else {
          val user = authorizedUser(false)
          val ts = PageUtils.tradingSheet()
          val params = call.request.queryParameters
          val buyer =
            TradeSide(user /*params[BUYER_NAME.name]?.toUser() ?: ts.users[0]*/,
                      ItemAmount(params[BUYER_AMOUNT.name]?.toInt() ?: 0,
                                 params[BUYER_ITEM.name]?.toItem() ?: ts.items[0]))
          val seller =
            TradeSide(params[SELLER_NAME.name]?.toUser() ?: (ts.users - user)[0],
                      ItemAmount(params[SELLER_AMOUNT.name]?.toInt() ?: 0,
                                 params[SELLER_ITEM.name]?.toItem() ?: ts.items[0]))

          this@page.tradeChoices()
          h2 { +"Add a trade" }
          this@page.addTradeForm(ts, buyer, seller)
        }
      }
    }

  suspend fun PipelineCall.executeTrade() {
    val params = call.receiveParameters()
    val ts = PageUtils.tradingSheet()
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

    if (googleCredential.get().isNull())
      redirectTo { authPageUrl() }
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

              p { a { href = Paths.ADD_TRADE; +"Add another trade" } }
            }
          }
        }
      }
  }

  fun BODY.addTradeForm(ts: TradingSheet, buyer: TradeSide, seller: TradeSide) {
    val width = 50
    form {
      action = Paths.ADD_TRADE
      method = FormMethod.post

      table {
        //tr { td { b { +"Buyer:" } } }
        tr {
          td { b { +"Buyer:" } }
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

        //tr { td { b { +"Seller:" } } }
        tr {
          td { b { +"Seller:" } }
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
          td { submitInput { } }
        }
      }
    }

  }
}
