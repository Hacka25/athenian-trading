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

import com.github.pambrose.HalfTrade
import com.github.pambrose.PageUtils.RECORD_TO_SHEET
import com.github.pambrose.PageUtils.adminChoices
import com.github.pambrose.PageUtils.homeLink
import com.github.pambrose.PageUtils.outputFormatter
import com.github.pambrose.PageUtils.page
import com.github.pambrose.PageUtils.rawHtml
import com.github.pambrose.PageUtils.tradingSheet
import com.github.pambrose.Paths.ADMIN
import com.github.pambrose.PipelineCall
import com.github.pambrose.UnitAmount
import com.github.pambrose.pages.AdminPage.AdminActions.*
import com.github.pambrose.pages.DIVS.SPACED_TABLE
import com.github.pambrose.queryParam
import io.ktor.locations.*
import kotlinx.html.*

object AdminPage {

  enum class AdminActions {
    USERS, REFRESH_USERS, UNITS,
    REFRESH_UNITS, ALLOCATIONS, TRANSACTIONS,
    RANDOM_TRADE, CLEAR_TRADES, BALANCES;

    fun asPath() = "$ADMIN/${name.toLowerCase()}"
  }

  fun PipelineCall.adminPage(arg: Admin) =
    page {
      adminChoices()
      val ts = tradingSheet()
      when (enumValueOf(arg.action.toUpperCase()) as AdminActions) {
        USERS -> {
          homeLink()
          h3 { +"Users" }
          table(classes = SPACED_TABLE.name) {
            ts.users.forEach {
              tr {
                td { +it.username }
                td { +it.longName }
              }
            }
          }
        }

        REFRESH_USERS -> {
          homeLink()
          h3 { +"Users refreshed" }
          div(classes = SPACED_TABLE.name) {
            table {
              ts.refreshUsers().forEach {
                tr {
                  td { +it.username }
                  td { +it.longName }
                }
              }
            }
          }
        }

        UNITS -> {
          homeLink()
          h3 { +"Units" }
          table { ts.units.forEach { tr { td { +it.desc } } } }
        }

        REFRESH_UNITS -> {
          homeLink()
          h3 { +"Units refreshed" }
          table { ts.refreshUnits().forEach { tr { td { +it.desc } } } }
        }

        ALLOCATIONS -> {
          homeLink()
          h3 { +"Allocations" }
          ts.allocations
            .also { tradeSides ->
              table(classes = SPACED_TABLE.name) {
                tradeSides.forEach {
                  tr {
                    td { +it.user.longName }
                    td { +"${it.unitAmount}" }
                  }
                }
              }
            }
        }

        TRANSACTIONS -> {
          homeLink()
          h3 { +"Transactions" }
          table {
            style = "font-size:20px"
            ts.transactions()
              .filterNot { it.allocation }
              .forEach {
                tr {
                  td {
                    style = "padding-right:10px;"
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

        BALANCES -> {
          homeLink()
          h3 { +"Balances" }
          ts.calculateBalances()
            .also { map ->
              if (queryParam(RECORD_TO_SHEET, "false").toBoolean())
                ts.writeBalancesToSpreadsheet(map)
            }
            .forEach { row ->
              val nameList = mutableListOf<String>()
              row.value
                .sortedWith(compareBy { it.unit.desc })
                .forEach {
                  val username = row.key.username
                  div {
                    if (username !in nameList) {
                      style = "padding-left:1em;padding-bottom:10px"
                      b { +row.key.longName }
                    }
                  }
                  div {
                    style = "padding-left:2em;"
                    +"$it"
                  }
                  nameList += username
                }
              div { rawHtml(Entities.nbsp.text) }
            }
        }

        RANDOM_TRADE -> {
          h3 { +"Random trade added" }
          val buyer = HalfTrade(ts.users.random(), UnitAmount((1..10).random(), ts.units.random()))
          val seller = HalfTrade((ts.users - buyer.user).random(),
                                 UnitAmount((1..10).random(), (ts.units - buyer.unitAmount.unit).random()))
          ts.addTrade(buyer, seller).apply { div { +first } }
        }

        CLEAR_TRADES -> {
          h3 { +"Trades cleared" }
          ts.clearTrades().also { pre { +it.toString() } }
        }
      }
    }
}

@Location("$ADMIN/{action}")
data class Admin(val action: String)