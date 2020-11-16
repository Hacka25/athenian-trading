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

import com.github.pambrose.EnvVar.*
import com.github.pambrose.Installs.installs
import com.github.pambrose.Routes.routes
import com.github.pambrose.TradingServer.baseUrl
import io.ktor.application.*

fun Application.module(testing: Boolean = false) {

  installs(!baseUrl.contains("localhost"),
           REDIRECT_HOSTNAME.getEnv(default = ""),
           FORWARDED_ENABLED.getEnv(default = false),
           XFORWARDED_ENABLED.getEnv(false))

  routes()
}