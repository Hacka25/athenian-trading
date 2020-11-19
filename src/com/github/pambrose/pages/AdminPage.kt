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

import com.github.pambrose.ItemAmount
import com.github.pambrose.PageUtils.adminChoices
import com.github.pambrose.PageUtils.homeLink
import com.github.pambrose.PageUtils.page
import com.github.pambrose.PageUtils.rawHtml
import com.github.pambrose.PageUtils.tradingSheet
import com.github.pambrose.Paths.ADMIN
import com.github.pambrose.TradeSide
import com.github.pambrose.pages.AdminPage.AdminActions.*
import io.ktor.locations.*
import kotlinx.html.*

object AdminPage {

  enum class AdminActions {
    USERS, REFRESH_USERS, ITEMS,
    REFRESH_ITEMS, ALLOCATIONS,
    RANDOM_TRADE, CLEAR_TRADES, CALC;

    fun asPath() = "$ADMIN/${name.toLowerCase()}"
  }

  fun adminPage(arg: Admin) =
    page {
      adminChoices()
      val ts = tradingSheet()
      when (enumValueOf(arg.action.toUpperCase()) as AdminActions) {
        USERS -> {
          homeLink()
          h3 { +"Users" }
          table { ts.users.forEach { tr { td { +it.name } } } }
        }
        REFRESH_USERS -> {
          homeLink()
          h3 { +"Users Refreshed" }
          table { ts.refreshUsers().forEach { tr { td { +it.name } } } }
        }
        ITEMS -> {
          homeLink()
          h3 { +"Goods and services" }
          table { ts.items.forEach { tr { td { +it.desc } } } }
        }
        REFRESH_ITEMS -> {
          homeLink()
          h3 { +"Goods and services Refreshed" }
          table { ts.refreshItems().forEach { tr { td { +it.desc } } } }
        }
        ALLOCATIONS -> {
          homeLink()
          h3 { +"Allocations" }
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
          h3 { +"Random trade added" }

          val buyer = TradeSide(ts.users.random(), ItemAmount((1..10).random(), ts.items.random()))
          val seller = TradeSide((ts.users - buyer.user).random(),
                                 ItemAmount((1..10).random(), (ts.items - buyer.itemAmount.item).random()))
          ts.addTrade(buyer, seller).apply { div { +first } }
        }
        CLEAR_TRADES -> {
          h3 { +"Trades cleared" }
          ts.clearTrades().also { pre { +it.toString() } }
        }
        CALC -> {
          homeLink()
          h3 { +"Balances" }
          ts.writeBalances()
            .also { elems ->
              table {
                elems.forEach { row ->
                  val nameList = mutableListOf<String>()
                  row.value.sortedWith(compareBy { it.item.desc })
                    .forEach {
                      val name = row.key.name
                      tr {
                        td {
                          style = "padding-left:10;padding-right:5;"
                          b { +(name.takeUnless { nameList.contains(it) } ?: "") }
                        }
                        td { +"$it" }
                      }
                      nameList += name
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
    }
}

@Location("$ADMIN/{action}")
data class Admin(val action: String)