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

object ServiceCache {

  private lateinit var userCache: List<User>
  private lateinit var itemCache: List<Item>

  fun users(action: () -> List<User>): List<User> {
    if (!this::userCache.isInitialized)
      userCache = action()
    return userCache
  }

  fun refreshUsers(action: () -> List<User>): List<User> {
    userCache = action()
    return userCache
  }

  fun items(action: () -> List<Item>): List<Item> {
    if (!this::itemCache.isInitialized)
      itemCache = action()
    return itemCache
  }

  fun refreshItems(action: () -> List<Item>): List<Item> {
    itemCache = action()
    return itemCache
  }
}