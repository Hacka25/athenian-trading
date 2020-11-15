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
import com.github.pambrose.Item.Companion.toItem
import com.github.pambrose.TradingServer.APP_TITLE
import com.github.pambrose.TradingSheet.Ranges.*
import com.github.pambrose.User.Companion.toUser
import com.google.api.client.auth.oauth2.Credential

class TradingSheet(private val ssId: String, credential: Credential) {

  enum class Ranges { UsersRange, GoodsAndServicesRange, AllocationsRange, TradesRange, BalancesRange }

  private val service by lazy { sheetsService(APP_TITLE, credential) }

  val users get() = ServiceCache.users { fetchUsers() }
  fun refreshUsers() = ServiceCache.refreshUsers { fetchUsers() }
  private fun fetchUsers() = service.query(ssId, UsersRange.name) { (this[0] as String).toUser() }

  val items get() = ServiceCache.items { fetchItems() }
  fun refreshItems() = ServiceCache.refreshItems { fetchItems() }
  private fun fetchItems() = service.query(ssId, GoodsAndServicesRange.name) { (this[0] as String).toItem() }

  val allocations
    get() = service.query(ssId, AllocationsRange.name) {
      TradeSide((this[0] as String).toUser(), ItemAmount((this[1] as String).toInt(), (this[2] as String).toItem()))
    }

  private val trades
    get() = service.query(ssId, TradesRange.name) {
      if (size == 7) {
        val buyer = (this[1] as String).toUser()
        val buyerAmount = (this[2] as String).toInt()
        val buyerItem = (this[3] as String).toItem()
        val seller = (this[4] as String).toUser()
        val sellerAmount = (this[5] as String).toInt()
        val sellerItem = (this[6] as String).toItem()
        listOf(
          TradeSide(buyer, ItemAmount(-1 * buyerAmount, buyerItem)),
          TradeSide(seller, ItemAmount(buyerAmount, buyerItem)),
          TradeSide(buyer, ItemAmount(sellerAmount, sellerItem)),
          TradeSide(seller, ItemAmount(-1 * sellerAmount, sellerItem))
        )
      } else {
        emptyList()
      }
    }.flatten()

  fun clearTrades() = service.clear(ssId, TradesRange.name)

  fun addTrade(buyer: TradeSide, seller: TradeSide) =
    service.append(ssId,
                   TradesRange.name,
                   listOf(listOf(nowDateTime(),
                                 buyer.name, buyer.amount, buyer.desc,
                                 seller.name, seller.amount, seller.desc)))
      .let { response ->
        "${buyer.user} traded ${buyer.itemAmount} for ${seller.itemAmount} with ${seller.user}" to response
      }

  fun calcBalances(): Map<User, List<ItemAmount>> =
    (allocations + trades)
      .groupBy({ it.user to it.item }, { it.amount })
      .map { TradeSide(it.key.first, ItemAmount(it.value.sum(), it.key.second)) }
      .filter { it.amount != 0 }
      .groupBy({ it.user }, { ItemAmount(it.amount, it.item) })
      .toSortedMap(compareBy { it.name })
      .also { map ->
        val inserts = mutableListOf<List<Any>>()
        val names = mutableListOf<String>()
        map.forEach { (user, list) ->
          list.sortedWith(compareBy { it.desc })
            .forEach { itemAmount ->
              inserts += listOf(user.name.takeUnless { names.contains(it) } ?: "", itemAmount.amount, itemAmount.desc)
              names += user.name
            }
        }

        service
          .apply {
            val range = BalancesRange.name
            clear(ssId, range)
            append(ssId, range, inserts, insertDataOption = OVERWRITE)
          }
      }
}