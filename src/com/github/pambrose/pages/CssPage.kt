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
import com.github.pambrose.respondCss
import io.ktor.application.*
import kotlinx.css.*

object CssPage {

  suspend fun PipelineCall.cssPage() =
    call.respondCss {
      rule("html, body") {
        fontSize = 24.px
        fontFamily = "verdana, arial, helvetica, sans-serif"
      }
      rule("ul") {
        paddingLeft = 0.px
        listStyleType = ListStyleType.none
      }
      rule("li") {
        paddingBottom = 15.px
      }
      // Turn links red on mouse hovers.
      rule("a:hover") {
        color = Color.red
      }
    }
}