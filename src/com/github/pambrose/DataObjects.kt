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

data class User(val name: String, val password: String) {
  override fun toString() = name

  companion object {
    fun String.toUser() = User(this, "")
  }
}

data class Item(val desc: String) {
  override fun toString() = desc

  companion object {
    fun String.toItem() = Item(this)
  }
}

data class ItemAmount(val amount: Int, val item: Item) {
  val desc get() = item.desc
  override fun toString() = "$amount $item"
}

data class TradeSide(val user: User, val itemAmount: ItemAmount) {
  val name get() = user.name
  val amount get() = itemAmount.amount
  val item get() = itemAmount.item
  val desc get() = itemAmount.item.desc
}