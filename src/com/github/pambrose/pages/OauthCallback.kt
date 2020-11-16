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

import com.github.pambrose.PipelineCall
import com.github.pambrose.Routes.redirectUrl
import com.github.pambrose.TradingServer
import com.github.pambrose.TradingServer.authCodeFlow
import com.github.pambrose.TradingServer.baseUrl
import com.github.pambrose.TradingServer.googleCredential
import com.github.pambrose.TradingServer.userId
import com.github.pambrose.common.response.redirectTo
import com.google.api.services.sheets.v4.SheetsScopes
import io.ktor.application.*

object OauthCallback {

  suspend fun PipelineCall.oauthCallback() {
    val params = call.request.queryParameters
    val code = params["code"]
    val state = params["state"]
    val scope = params["scope"]

    check(state == TradingServer.serverSessionId)
    check(scope == SheetsScopes.SPREADSHEETS)

    val tokenRequest = authCodeFlow.newTokenRequest(code).setRedirectUri(redirectUrl).execute()
    googleCredential.set(authCodeFlow.createAndStoreCredential(tokenRequest, userId))

    redirectTo { baseUrl }
  }
}