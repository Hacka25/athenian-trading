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

import com.github.pambrose.InvalidRequestException
import com.github.pambrose.ItemAmount
import com.github.pambrose.PageUtils
import com.github.pambrose.PageUtils.adminChoices
import com.github.pambrose.PageUtils.homeLink
import com.github.pambrose.PageUtils.page
import com.github.pambrose.PageUtils.rawHtml
import com.github.pambrose.Paths.ADMIN
import com.github.pambrose.TradeSide
import com.github.pambrose.pages.AdminPage.Actions.*
import com.github.pambrose.pages.AdminPage.Actions.Companion.asAction
import io.ktor.locations.*
import kotlinx.html.*

object AdminPage {

  enum class Actions(val action: String) {
    USERS("users"), REFRESH_USERS("refresh-users"), ITEMS("items"),
    REFRESH_ITEMS("refresh-items"), ALLOCATIONS("allocations"),
    RANDOM_TRADE("random"), CLEAR_TRADES("clear"), CALC("calc");

    fun asPath() = "$ADMIN/$action"

    companion object {
      fun String.asAction() =
        values().firstOrNull { this == it.action } ?: throw InvalidRequestException("Invalid action: $this")
    }
  }

  fun adminPage(arg: Admin) =
    page {
      adminChoices()
      val ts = PageUtils.tradingSheet()
      when (arg.action.asAction()) {
        USERS -> {
          homeLink()
          h2 { +"Users" }
          table { ts.users.forEach { tr { td { +it.name } } } }
        }
        REFRESH_USERS -> {
          homeLink()
          h2 { +"Users Refreshed" }
          table { ts.refreshUsers().forEach { tr { td { +it.name } } } }
        }
        ITEMS -> {
          homeLink()
          h2 { +"Goods and services" }
          table { ts.items.forEach { tr { td { +it.desc } } } }
        }
        REFRESH_ITEMS -> {
          homeLink()
          h2 { +"Goods and services Refreshed" }
          table { ts.refreshItems().forEach { tr { td { +it.desc } } } }
        }
        ALLOCATIONS -> {
          homeLink()
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

          val buyer = TradeSide(ts.users.random(), ItemAmount((1..10).random(), ts.items.random()))
          val seller = TradeSide((ts.users - buyer.user).random(),
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
          homeLink()
          h2 { +"Balances" }
          ts.calcBalances()
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
