package com.github.pambrose

import com.github.pambrose.GoogleApiUtils.append
import com.github.pambrose.GoogleApiUtils.clear
import com.github.pambrose.GoogleApiUtils.nowDateTime
import com.github.pambrose.GoogleApiUtils.query
import com.github.pambrose.GoogleApiUtils.sheetsService
import com.github.pambrose.InsertDataOption.OVERWRITE
import com.github.pambrose.Item.Companion.asItem
import com.github.pambrose.TradingSheet.Ranges.*
import com.github.pambrose.User.Companion.asUser
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

  internal fun fetchUsers() = service.query(ssId, UsersRange.name) { (this[0] as String).asUser() }

  val items get() = ServiceCache.items(this)

  fun refreshItems() = ServiceCache.refreshItems(this)

  internal fun fetchItems() = service.query(ssId, GoodsAndServicesRange.name) { (this[0] as String).asItem() }

  val allocations
    get() = service.query(ssId, AllocationsRange.name) {
      TxnHalf((this[0] as String).asUser(), ItemAmount((this[1] as String).toInt(), (this[2] as String).asItem()))
    }

  private val transactions
    get() = service.query(ssId, TransactionsRange.name) {
      if (size == 7) {
        val date = this[0]
        val buyer = (this[1] as String).asUser()
        val buyerAmount = (this[2] as String).toInt()
        val buyerItem = (this[3] as String).asItem()
        val seller = (this[4] as String).asUser()
        val sellerAmount = (this[5] as String).toInt()
        val sellerItem = (this[6] as String).asItem()
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
              println("\t$itemAmount")
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
        "${buyer.user} traded ${buyer.itemAmount} with ${seller.user} in exchange for ${seller.itemAmount}" to response
      }
}

data class User(val name: String) {
  override fun toString() = name

  companion object {
    fun String.asUser() = User(this)
  }
}

data class Item(val desc: String) {
  override fun toString() = desc

  companion object {
    fun String.asItem() = Item(this)
  }
}

data class ItemAmount(val amount: Int, val item: Item) {
  override fun toString() = "$amount $item"
}

data class TxnHalf(val user: User, val itemAmount: ItemAmount)