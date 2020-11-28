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

package com.github.pambrose

import com.github.pambrose.GoogleApiUtils.append
import com.github.pambrose.GoogleApiUtils.clear
import com.github.pambrose.GoogleApiUtils.nowDateTime
import com.github.pambrose.GoogleApiUtils.query
import com.github.pambrose.GoogleApiUtils.sheetsService
import com.github.pambrose.InsertDataOption.OVERWRITE
import com.github.pambrose.TradingServer.APP_TITLE
import com.github.pambrose.TradingSheet.Ranges.*
import com.github.pambrose.Units.Companion.toUnit
import com.github.pambrose.User.Companion.toUser
import com.google.api.client.auth.oauth2.Credential
import mu.KLogging
import kotlin.time.measureTimedValue

class TradingSheet(private val ssId: String, credential: Credential) {

  enum class Ranges { UsersRange, UnitsRange, AllocationsRange, TradesRange, BalancesRange }

  private val service by lazy { sheetsService(APP_TITLE, credential) }

  val users get() = ServiceCache.users { fetchUsers() }

  fun refreshUsers() = ServiceCache.refreshUsers { fetchUsers() }

  private fun fetchUsers() =
    measureTimedValue {
      service.query(ssId, UsersRange.name) {
        User(this[0] as String, this[1] as String, this[2] as String, this[3] as String)
      }
    }.let {
      logger.info { "Fetched users: ${it.duration}" }
      it.value
    }

  val units get() = ServiceCache.units { fetchUnits() }

  fun refreshUnits() = ServiceCache.refreshUnits { fetchUnits() }

  private fun fetchUnits() =
    measureTimedValue {
      service.query(ssId, UnitsRange.name) { (this[0] as String).toUnit() }
    }.let {
      logger.info { "Fetched units: ${it.duration}" }
      it.value
    }

  val allocations
    get() =
      measureTimedValue {
        service.query(ssId, AllocationsRange.name) {
          TradeSide((this[0] as String).toUser(users),
                    UnitAmount((this[1] as String).toInt(), (this[2] as String).toUnit()))
        }
      }.let {
        logger.info { "Fetched Allocations: ${it.duration}" }
        it.value
      }

  private val trades
    get() =
      measureTimedValue {
        service.query(ssId, TradesRange.name) {
          if (size == 7) {
            val buyer = (this[1] as String).toUser(users)
            val buyerAmount = (this[2] as String).toInt()
            val buyerUnit = (this[3] as String).toUnit()
            val seller = (this[4] as String).toUser(users)
            val sellerAmount = (this[5] as String).toInt()
            val sellerUnit = (this[6] as String).toUnit()
            listOf(
              TradeSide(buyer, UnitAmount(-1 * buyerAmount, buyerUnit)),
              TradeSide(seller, UnitAmount(buyerAmount, buyerUnit)),
              TradeSide(buyer, UnitAmount(sellerAmount, sellerUnit)),
              TradeSide(seller, UnitAmount(-1 * sellerAmount, sellerUnit))
            )
          } else {
            logger.error { "Missing trade data" }
            emptyList()
          }
        }.flatten()
      }.let {
        logger.info { "Fetched Trades: ${it.duration}" }
        it.value
      }

  fun clearTrades() =
    measureTimedValue {
      service.clear(ssId, TradesRange.name)
    }.let {
      logger.info { "Cleared trades: ${it.duration}" }
      it.value
    }

  fun addTrade(buyer: TradeSide, seller: TradeSide) =
    measureTimedValue {
      service.append(ssId,
                     TradesRange.name,
                     listOf(listOf(nowDateTime(),
                                   buyer.username, buyer.amount, buyer.desc,
                                   seller.username, seller.amount, seller.desc)))
        .let { response ->
          "${buyer.user} traded ${buyer.unitAmount} for ${seller.unitAmount} with ${seller.user}" to response
        }
    }.let {
      logger.info { "Added a trade: ${it.duration}" }
      it.value
    }

  fun calculateBalances() =
    measureTimedValue {
      (allocations + trades)
        .groupBy({ it.user to it.unit }, { it.amount })
        .map { TradeSide(it.key.first, UnitAmount(it.value.sum(), it.key.second)) }
        .filter { it.amount != 0 }
        .groupBy({ it.user }, { UnitAmount(it.amount, it.unit) })
        .toSortedMap(compareBy { it.username })
    }.let {
      logger.info { "Balances: ${it.duration}" }
      it.value
    }

  fun writeBalancesToSpreadsheet(map: Map<User, List<UnitAmount>>) {
    val inserts = mutableListOf<List<Any>>()
    val names = mutableListOf<String>()

    map.forEach { (user, list) ->
      list.sortedWith(compareBy { it.desc })
        .forEach { unitAmount ->
          inserts += listOf(user.username.takeUnless { names.contains(it) } ?: "",
                            unitAmount.amount,
                            unitAmount.desc)
          names += user.username
        }
    }

    service
      .apply {
        val range = BalancesRange.name
        clear(ssId, range)
        append(ssId, range, inserts, insertDataOption = OVERWRITE)
      }
  }

  companion object : KLogging()
}