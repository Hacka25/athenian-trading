package com.github.pambrose

import com.github.pambrose.GoogleApiUtils.append
import com.github.pambrose.GoogleApiUtils.clear
import com.github.pambrose.GoogleApiUtils.nowDateTime
import com.github.pambrose.GoogleApiUtils.query
import com.github.pambrose.GoogleApiUtils.sheetsService
import com.github.pambrose.InsertDataOption.OVERWRITE
import com.github.pambrose.TradingSheet.Ranges.*
import com.google.api.client.auth.oauth2.Credential

private const val APPLICATION_NAME = "Athenian Trading App"

private object ServiceCache {
  private lateinit var userCache: List<User>
  private lateinit var itemCache: List<Item>

  fun users(tradingSheet: TradingSheet): List<User> {
    if (!this::userCache.isInitialized)
      userCache = tradingSheet.fetchUsers()
    return userCache
  }

  fun refreshUsers(tradingSheet: TradingSheet): List<User> {
    userCache = tradingSheet.fetchUsers()
    return userCache
  }

  fun items(tradingSheet: TradingSheet): List<Item> {
    if (!this::itemCache.isInitialized)
      itemCache = tradingSheet.fetchItems()
    return itemCache
  }

  fun refreshItems(tradingSheet: TradingSheet): List<Item> {
    itemCache = tradingSheet.fetchItems()
    return itemCache
  }
}

class TradingSheet(private val ssId: String, credential: Credential) {
  enum class Ranges { UsersRange, GoodsAndServicesRange, AllocationsRange, TransactionsRange, BalancesRange }

  private val service = sheetsService(APPLICATION_NAME, credential)

  val users get() = ServiceCache.users(this)

  fun refreshUsers() = ServiceCache.refreshUsers(this)

  internal fun fetchUsers() = service.query(ssId, UsersRange.name) { User(this[0] as String) }

  val items get() = ServiceCache.items(this)

  fun refreshItems() = ServiceCache.refreshItems(this)

  internal fun fetchItems() = service.query(ssId, GoodsAndServicesRange.name) { Item(this[0] as String) }

  val allocations
    get() = service.query(ssId, AllocationsRange.name) {
      TxnHalf(User(this[0] as String), ItemAmount((this[1] as String).toInt(), Item(this[2] as String)))
    }

  private val transactions
    get() = service.query(ssId, TransactionsRange.name) {
      if (size == 7) {
        val date = this[0]
        val buyer = User(this[1] as String)
        val buyerAmount = (this[2] as String).toInt()
        val buyerItem = Item(this[3] as String)
        val seller = User(this[4] as String)
        val sellerAmount = (this[5] as String).toInt()
        val sellerItem = Item(this[6] as String)
        listOf(
          TxnHalf(buyer, ItemAmount(-1 * buyerAmount, buyerItem)),
          TxnHalf(seller, ItemAmount(buyerAmount, buyerItem)),
          TxnHalf(buyer, ItemAmount(sellerAmount, sellerItem)),
          TxnHalf(seller, ItemAmount(-1 * sellerAmount, sellerItem))
        )
      } else {
        emptyList()
      }
    }.flatten()

  fun clearTransactions() =
    service.clear(ssId, TransactionsRange.name)

  fun calcBalances(): Map<User, List<ItemAmount>> =
    (allocations + transactions)
      .groupBy({ it.user to it.itemAmount.item }, { it.itemAmount.amount })
      .map { TxnHalf(it.key.first, ItemAmount(it.value.sum(), it.key.second)) }
      .filter { it.itemAmount.amount != 0 }
      .groupBy({ it.user }, { ItemAmount(it.itemAmount.amount, it.itemAmount.item) })
      .toSortedMap(compareBy { it.name })
      .also { map ->
        val insertList = mutableListOf<List<Any>>()
        val nameList = mutableListOf<String>()
        map.forEach { (k, v) ->
          println(k.name)
          v.sortedWith(compareBy { it.item.desc })
            .forEach { itemAmount ->
              println("\t${itemAmount.amount} ${itemAmount.item.desc}")
              insertList += listOf(k.name.takeUnless { nameList.contains(it) } ?: "",
                                   itemAmount.amount,
                                   itemAmount.item.desc)
              nameList += k.name
            }
          println()
        }
        service.apply {
          val range = BalancesRange.name
          clear(ssId, range)
          append(ssId, range, insertList, insertDataOption = OVERWRITE)
        }
      }

  fun addTransaction(buyer: TxnHalf, seller: TxnHalf) =
    service.append(
      ssId,
      TransactionsRange.name,
      listOf(listOf(nowDateTime(),
                    buyer.user.name, buyer.itemAmount.amount, buyer.itemAmount.item.desc,
                    seller.user.name, seller.itemAmount.amount, seller.itemAmount.item.desc)))
      .let { response ->
        "${buyer.user.name} traded ${buyer.itemAmount} to ${seller.user.name} in exchange for ${seller.itemAmount}" to response
      }
}

data class User(val name: String) {
  companion object {
    fun String.toUser() = User(this)
  }
}

data class Item(val desc: String) {
  companion object {
    fun String.toItem() = Item(this)
  }
}

data class ItemAmount(val amount: Int, val item: Item) {
  override fun toString() = "$amount ${item.desc}"
}

data class TxnHalf(val user: User, val itemAmount: ItemAmount)