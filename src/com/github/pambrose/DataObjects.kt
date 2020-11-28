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

import java.time.LocalDateTime

data class User(
  val username: String,
  val password: String,
  val fullName: String,
  val role: String
) {
  val longName get() = "$fullName ($role)"
  override fun toString() = username

  companion object {
    fun String.toUser(users: List<User>) =
      users.firstOrNull { it.username == this } ?: throw InvalidConfigurationException("Missing user: $this")
  }
}

data class Units(val desc: String) {
  override fun toString() = desc

  companion object {
    fun String.toUnit() = Units(this)
  }
}

data class UnitAmount(val amount: Int, val unit: Units) {
  val desc get() = unit.desc
  override fun toString() = "$amount $unit"
}

data class HalfTrade(val user: User, val unitAmount: UnitAmount, val date: LocalDateTime = LocalDateTime.now()) {
  val username get() = user.username
  val fullName get() = user.fullName
  val role get() = user.role
  val longName get() = user.longName
  val amount get() = unitAmount.amount
  val unit get() = unitAmount.unit
  val desc get() = unitAmount.unit.desc
}

data class FullTrade(
  val allocation: Boolean,
  val date: LocalDateTime = LocalDateTime.now(),
  val buyer: User,
  val buyerUnitAmount: UnitAmount,
  val seller: User,
  val sellerUnitAmount: UnitAmount
)